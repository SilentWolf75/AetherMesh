import AetherMeshKit
import Foundation
import SwiftUI

/// Owns the BLE transport and MeshSession. Every call hops onto one serial
/// queue, so neither is ever touched from two threads, and decryption (PBKDF2
/// per message) never runs on the main thread. Callbacks fire on that queue.
final class MeshController {
    var onTransportState: ((BLETransport.State) -> Void)?
    var onDiscovered: ((DiscoveredNode) -> Void)?
    var onEvent: ((MeshSession.Event) -> Void)?

    private let queue = DispatchQueue(label: "aethermesh.session")
    private let transport: BLETransport
    private let session: MeshSession

    init(keys: ChatKeyProvider) {
        let transport = BLETransport(queue: queue)
        self.transport = transport
        session = MeshSession(keys: keys) { [weak transport] frame in
            transport?.write(frame) ?? false
        }
        transport.onStateChange = { [weak self] state in
            guard let self else { return }
            // Authentication never outlives the connection it was granted on.
            if case .idle = state { self.session.reset() }
            self.onTransportState?(state)
        }
        transport.onDiscovered = { [weak self] in self?.onDiscovered?($0) }
        transport.onFrame = { [weak self] in self?.session.handleIncoming($0) }
        session.onEvent = { [weak self] in self?.onEvent?($0) }
    }

    func startScan() { queue.async { self.transport.startScan() } }
    func connect(_ id: UUID) { queue.async { self.transport.connect(id) } }
    func disconnect() { queue.async { self.transport.disconnect() } }
    func unlock(password: String) { queue.async { _ = self.session.unlock(password: password) } }

    func send(_ text: String, channel: String, completion: @escaping (MeshSession.SendResult) -> Void) {
        queue.async { completion(self.session.sendText(text, channel: channel)) }
    }

    func traceRoute(to nodeId: UInt32, completion: @escaping (Bool) -> Void) {
        queue.async { completion(self.session.startTraceRoute(to: nodeId) != nil) }
    }
}

/// UI state, mutated on the main actor only.
@MainActor
final class AppModel: ObservableObject {
    enum Connection: Equatable {
        case bluetoothOff
        case bluetoothDenied
        case disconnected
        case scanning
        case connecting
        case connected
    }

    @Published private(set) var connection: Connection = .disconnected
    @Published private(set) var discovered: [DiscoveredNode] = []
    @Published private(set) var authState: MeshSession.AuthState = .unknown
    @Published private(set) var localNodeId: UInt32 = 0
    @Published private(set) var nodes: [UInt32: MeshNodeInfo] = [:]
    @Published private(set) var messages: [String: [MeshChatMessage]] = [:]
    @Published private(set) var deliveryStates: [UInt32: Aethermesh_DeliveryStatus.State] = [:]
    @Published private(set) var lastTrace: TraceRouteResult?
    @Published private(set) var nodeConfig: Aethermesh_NodeConfig?
    @Published var showUnlock = false
    @Published var lastError: String?

    let keychain = KeychainStore()
    private let controller: MeshController
    private var connectedPeripheral: UUID?
    private var pendingPassword: String?
    private var triedSavedPassword = false

    init() {
        controller = MeshController(keys: keychain)
        controller.onTransportState = { [weak self] state in
            Task { @MainActor in self?.transportChanged(state) }
        }
        controller.onDiscovered = { [weak self] node in
            Task { @MainActor in self?.discoveredNode(node) }
        }
        controller.onEvent = { [weak self] event in
            Task { @MainActor in self?.handle(event) }
        }
    }

    // MARK: Actions

    func startScan() {
        discovered.removeAll()
        controller.startScan()
    }

    func connect(_ node: DiscoveredNode) {
        connectedPeripheral = node.id
        triedSavedPassword = false
        controller.connect(node.id)
    }

    func disconnect() { controller.disconnect() }

    func unlock(password: String, remember: Bool) {
        pendingPassword = remember ? password : nil
        controller.unlock(password: password)
    }

    func send(_ text: String, channel: String) {
        controller.send(text, channel: channel) { [weak self] result in
            Task { @MainActor in
                switch result {
                case .sent: self?.lastError = nil
                case .notAuthenticated: self?.lastError = "Connect and unlock a node first."
                case .emptyMessage: break
                case .encryptionFailed: self?.lastError = "Could not encrypt; message not sent."
                case .transportFailed: self?.lastError = "Bluetooth write failed."
                }
            }
        }
    }

    func traceRoute(to nodeId: UInt32) {
        controller.traceRoute(to: nodeId) { [weak self] started in
            guard !started else { return }
            Task { @MainActor in self?.lastError = "Traceroute needs an unlocked connection." }
        }
    }

    func setChannelKey(_ key: String, channel: String) {
        keychain.setChatKey(key, for: ChatSendPolicy.channelKey(channel))
        objectWillChange.send()
    }

    func hasChannelKey(_ channel: String) -> Bool {
        keychain.chatKey(for: ChatSendPolicy.channelKey(channel)) != nil
    }

    // MARK: Events

    private func transportChanged(_ state: BLETransport.State) {
        switch state {
        case .poweredOff: connection = .bluetoothOff
        case .unauthorized: connection = .bluetoothDenied
        case .idle:
            connection = .disconnected
            authState = .unknown
            showUnlock = false
        case .scanning: connection = .scanning
        case .connecting: connection = .connecting
        case .ready: connection = .connected
        }
    }

    private func discoveredNode(_ node: DiscoveredNode) {
        if let index = discovered.firstIndex(where: { $0.id == node.id }) {
            discovered[index] = node
        } else {
            discovered.append(node)
        }
    }

    private func handle(_ event: MeshSession.Event) {
        switch event {
        case .authChanged(let state):
            authState = state
            handleAuth(state)
        case .localNodeIdentified(let id):
            localNodeId = id
        case .configReport(let config):
            nodeConfig = config
        case .chat(let message):
            messages[message.chatIdentifier, default: []].append(message)
        case .node(let info):
            nodes[info.nodeId] = info
        case .delivery(let packetId, let state, _):
            deliveryStates[packetId] = state
        case .traceRoute(let result):
            lastTrace = result
        }
    }

    /// Same idea as Android AutoAuthPolicy: try a saved password once, prompt otherwise.
    private func handleAuth(_ state: MeshSession.AuthState) {
        guard let peripheral = connectedPeripheral else { return }
        switch state {
        case .required:
            if !triedSavedPassword, let saved = keychain.nodePassword(for: peripheral) {
                triedSavedPassword = true
                unlock(password: saved, remember: true)
            } else {
                showUnlock = true
            }
        case .authenticated:
            showUnlock = false
            if let pendingPassword { keychain.setNodePassword(pendingPassword, for: peripheral) }
            pendingPassword = nil
        case .rejected(let message):
            if triedSavedPassword { keychain.setNodePassword(nil, for: peripheral) }
            lastError = message
            showUnlock = true
        case .unknown:
            break
        }
    }
}
