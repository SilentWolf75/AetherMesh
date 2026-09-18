import Foundation
import SwiftProtobuf

/// Supplies stored chat keys (the app keeps them in the Keychain).
public protocol ChatKeyProvider: AnyObject {
    func chatKey(for chatIdentifier: String) -> String?
}

public struct MeshChatMessage: Equatable, Identifiable, Sendable {
    public enum Direction: Sendable { case incoming, outgoing }

    public var id: String { "\(senderId)-\(packetId)" }
    public let senderId: UInt32
    public let recipientId: UInt32
    public let packetId: UInt32
    /// Channel name for channel traffic, empty for direct messages.
    public let channel: String
    public let chatIdentifier: String
    public let text: String
    public let isEncrypted: Bool
    /// Encrypted but unreadable (no key, wrong key). `text` holds the sentinel.
    public let unreadable: Bool
    public let direction: Direction
    public let date: Date
}

/// What our radio decided about one node's announced identity keys. The phone
/// keeps the verdict and fingerprint, never key material.
public struct NodeIdentityReport: Equatable, Sendable {
    public let nodeId: UInt32
    public let fingerprint: String
    public let state: NodeIdentityPolicy.State
    public let keyEpoch: UInt32

    public var needsAttention: Bool { NodeIdentityPolicy.needsAttention(state) }
}

public struct MeshNodeInfo: Equatable, Sendable {
    public let nodeId: UInt32
    public let name: String
    public let batteryLevel: UInt32
    public let latitude: Float
    public let longitude: Float
    public let firmwareVersion: String
    public let lastRssi: Float
    public let lastSnr: Float
    public let lastHeard: Date
    /// Firmware GNSS snapshot; `.unknown` from older firmware.
    public let gpsState: Aethermesh_Telemetry.GpsState
    public let positionSource: Aethermesh_Telemetry.PositionSource
    public let satellitesUsed: UInt32
    public let satellitesInView: UInt32
}

/// Protocol state machine for one BLE session with a node. Transport-agnostic:
/// the app feeds notified bytes to `handleIncoming` and supplies a `write`
/// closure for outbound frames.
///
/// Not thread-safe, and decryption runs PBKDF2 (120k iterations) per message:
/// call it from one serial background queue, never the main thread.
public final class MeshSession {
    public enum AuthState: Equatable, Sendable {
        case unknown
        case required(passwordNotSet: Bool)
        case authenticated
        case rejected(message: String)
    }

    public enum Event {
        case authChanged(AuthState)
        case localNodeIdentified(UInt32)
        case configReport(Aethermesh_NodeConfig)
        case chat(MeshChatMessage)
        case node(MeshNodeInfo)
        case delivery(packetId: UInt32, state: Aethermesh_DeliveryStatus.State, heardCount: UInt32)
        case traceRoute(TraceRouteResult)
        /// Our own radio's verdict on a node's announced keys, plus the
        /// fingerprint to read against that node's serial log.
        case identity(NodeIdentityReport)
    }

    public enum SendResult: Equatable {
        case sent(packetId: UInt32)
        case notAuthenticated
        case emptyMessage
        case encryptionFailed
        case transportFailed
    }

    public private(set) var authState: AuthState = .unknown
    public private(set) var localNodeId: UInt32 = 0
    public var onEvent: ((Event) -> Void)?

    private let write: (Data) -> Bool
    private weak var keys: ChatKeyProvider?
    private var nextPacketId: UInt32
    private var pendingTraceId: UInt32?
    private var seen: [String] = []
    private var seenSet: Set<String> = []
    private static let seenCapacity = 256

    public init(keys: ChatKeyProvider?, write: @escaping (Data) -> Bool) {
        self.keys = keys
        self.write = write
        nextPacketId = UInt32.random(in: 1 ... 0x7FFF_FFFF)
    }

    /// Clear per-connection state. Call on every disconnect: authentication never
    /// carries over to a new connection (firmware enforces the same rule).
    public func reset() {
        authState = .unknown
        passwordAwaitingChallenge = nil
        pendingTraceId = nil
    }

    // MARK: Inbound

    public func handleIncoming(_ data: Data) {
        guard let packet = try? Aethermesh_MeshPacket(serializedBytes: data) else { return }
        guard acceptControl(packet) else { return }

        switch packet.payload {
        case .authResponse(let response)?:
            if answerChallenge(response) { return }
            if response.success {
                setAuth(.authenticated)
                learnLocalNode(packet.senderID)
            } else if isLockChallenge(response.message) {
                setAuth(.required(passwordNotSet: response.passwordNotSet))
            } else {
                setAuth(.rejected(message: response.message))
            }
        case .config(let config)? where config.reportOnly && packet.recipientID == 0:
            learnLocalNode(packet.senderID)
            onEvent?(.configReport(config))
        case .deliveryStatus(let status)?:
            onEvent?(.delivery(packetId: status.packetID, state: status.state, heardCount: status.heardCount))
        case .telemetry(let telemetry)?:
            if packet.recipientID == 0 { learnLocalNode(packet.senderID) }
            onEvent?(.node(MeshNodeInfo(
                nodeId: packet.senderID,
                name: telemetry.nodeName,
                batteryLevel: telemetry.batteryLevel,
                latitude: telemetry.latitude,
                longitude: telemetry.longitude,
                firmwareVersion: telemetry.firmwareVersion,
                lastRssi: packet.rxRssi,
                lastSnr: packet.rxSnr,
                lastHeard: Date(),
                gpsState: telemetry.gpsState,
                positionSource: telemetry.positionSource,
                satellitesUsed: telemetry.gpsSatellitesUsed,
                satellitesInView: telemetry.gpsSatellitesInView
            )))
        case .nodeIdentity(let identity)?:
            let state = NodeIdentityPolicy.state(of: identity.trust)
            let fingerprint = NodeIdentityPolicy.fingerprint(identity.ed25519Public)
            // A verdict with no usable key, or none at all, is not reported:
            // an older node never gets shown as verified by default.
            guard state != .unknown, !fingerprint.isEmpty else { break }
            onEvent?(.identity(NodeIdentityReport(
                nodeId: packet.senderID,
                fingerprint: fingerprint,
                state: state,
                keyEpoch: identity.keyEpoch
            )))
        case .text(let text)?:
            handleText(packet, text)
        case .traceRoute?:
            if let traceId = pendingTraceId,
               let result = TraceRoutePolicy.result(from: packet, localNodeId: localNodeId, expectedTraceId: traceId) {
                pendingTraceId = nil
                onEvent?(.traceRoute(result))
            }
        default:
            break
        }
    }

    private func handleText(_ packet: Aethermesh_MeshPacket, _ text: Aethermesh_TextMessage) {
        guard authState == .authenticated,
              packet.senderID != 0, packet.recipientID != 0,
              !IncomingChatPolicy.isControlPing(text.content),
              markSeen("\(packet.senderID)-\(packet.packetID)") else { return }
        let plan = IncomingChatPolicy.plan(senderId: packet.senderID, recipientId: packet.recipientID, channel: text.channel)
        var content = text.content
        var unreadable = false
        if text.isEncrypted {
            if let key = keys?.chatKey(for: plan.chatIdentifier), !key.isEmpty {
                content = ChatCrypto.decrypt(text.content, passcode: key, chatContext: plan.cryptoContext)
                unreadable = ChatCrypto.isDecryptFailure(content)
            } else {
                content = IncomingChatPolicy.errorNoKey
                unreadable = true
            }
        }
        onEvent?(.chat(MeshChatMessage(
            senderId: packet.senderID,
            recipientId: packet.recipientID,
            packetId: packet.packetID,
            channel: plan.channelForRow,
            chatIdentifier: plan.chatIdentifier,
            text: content,
            isEncrypted: text.isEncrypted,
            unreadable: unreadable,
            direction: .incoming,
            date: Date()
        )))
    }

    /// BLE-local responses must come from the connected node over BLE: recipient 0
    /// and no radio signal metadata (Android `IncomingPacketPolicy.acceptControl`).
    private func acceptControl(_ packet: Aethermesh_MeshPacket) -> Bool {
        switch packet.payload {
        case .authResponse?:
            return packet.senderID != 0 && packet.recipientID == 0 &&
                packet.rxRssi == 0 && packet.rxSnr == 0 &&
                (localNodeId == 0 || packet.senderID == localNodeId)
        case .deliveryStatus?, .otaStatus?, .diagnostics?:
            return authState == .authenticated && packet.recipientID == 0 &&
                localNodeId != 0 && packet.senderID == localNodeId
        case .config(let config)? where config.reportOnly && packet.recipientID == 0:
            return authState == .authenticated && packet.senderID != 0 &&
                (localNodeId == 0 || packet.senderID == localNodeId)
        default:
            return true
        }
    }

    // MARK: Outbound

    /// Unlock the node. With a password this first asks for a one-time
    /// challenge (an empty request) and answers it with a proof, so the
    /// password never crosses the air. Firmware that predates proofs replies
    /// without a challenge and gets the password as before.
    @discardableResult
    public func unlock(password: String) -> Bool {
        passwordAwaitingChallenge = password.isEmpty ? nil : password
        return send(AuthRequests.unlock(localNodeId: localNodeId, packetId: takePacketId(), password: ""))
    }

    private var passwordAwaitingChallenge: String?

    /// Second half of `unlock`. True when the reply was the challenge and has been answered.
    private func answerChallenge(_ response: Aethermesh_AuthResponse) -> Bool {
        guard let password = passwordAwaitingChallenge, !response.success else { return false }
        passwordAwaitingChallenge = nil
        if !response.passwordNotSet && AuthProof.isUsable(response.challenge) {
            let proof = AuthProof.compute(password: password, challenge: response.challenge)
            _ = send(AuthRequests.proof(localNodeId: localNodeId, packetId: takePacketId(), proof: proof))
        } else {
            // Older firmware, or a node taking this as its first password:
            // send it, over the now-encrypted link.
            _ = send(AuthRequests.unlock(localNodeId: localNodeId, packetId: takePacketId(), password: password))
        }
        return true
    }

    /// Send chat. Encrypts whenever a key is stored for the chat; never falls back
    /// to plaintext if encryption fails.
    public func sendText(_ text: String, to recipientId: UInt32 = MeshNodeId.broadcast,
                         channel: String = ChatSendPolicy.defaultChannel) -> SendResult {
        guard authState == .authenticated, localNodeId != 0 else { return .notAuthenticated }
        let boundedChannel = ChatSendPolicy.boundChannel(channel)
        let chatIdentifier = ChatSendPolicy.chatIdentifier(recipientId: recipientId, channel: boundedChannel)
        let key = keys?.chatKey(for: chatIdentifier).flatMap { $0.isEmpty ? nil : $0 }
        let bounded = ChatSendPolicy.boundContent(text, encrypted: key != nil)
        guard !bounded.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return .emptyMessage }

        var wire = bounded
        if let key {
            let context = ChatContext.authenticatedLabel(senderId: localNodeId, recipientId: recipientId, channel: boundedChannel)
            guard let sealed = ChatCrypto.encrypt(bounded, passcode: key, chatContext: context) else {
                return .encryptionFailed
            }
            wire = sealed
        }
        let packetId = takePacketId()
        let packet = ChatSendPolicy.buildPacket(
            localNodeId: localNodeId, recipientId: recipientId, packetId: packetId,
            content: wire, channel: boundedChannel, isEncrypted: key != nil
        )
        guard send(packet) else { return .transportFailed }
        onEvent?(.chat(MeshChatMessage(
            senderId: localNodeId, recipientId: recipientId, packetId: packetId,
            channel: recipientId == MeshNodeId.broadcast ? boundedChannel : "",
            chatIdentifier: chatIdentifier, text: bounded, isEncrypted: key != nil,
            unreadable: false, direction: .outgoing, date: Date()
        )))
        return .sent(packetId: packetId)
    }

    /// Returns the trace id, or nil when not ready.
    public func startTraceRoute(to targetId: UInt32) -> UInt32? {
        guard authState == .authenticated, localNodeId != 0, targetId != 0, targetId != localNodeId else { return nil }
        let traceId = takePacketId()
        guard send(TraceRoutePolicy.buildRequest(localNodeId: localNodeId, targetId: targetId, traceId: traceId)) else {
            return nil
        }
        pendingTraceId = traceId
        return traceId
    }

    // MARK: Helpers

    private func send(_ packet: Aethermesh_MeshPacket) -> Bool {
        guard let bytes: Data = try? packet.serializedBytes() else { return false }
        return write(bytes)
    }

    private func takePacketId() -> UInt32 {
        nextPacketId = nextPacketId >= 0x7FFF_FFFF ? 1 : nextPacketId + 1
        return nextPacketId
    }

    private func setAuth(_ state: AuthState) {
        guard state != authState else { return }
        authState = state
        onEvent?(.authChanged(state))
    }

    private func learnLocalNode(_ nodeId: UInt32) {
        guard nodeId != 0, nodeId != localNodeId else { return }
        localNodeId = nodeId
        onEvent?(.localNodeIdentified(nodeId))
    }

    private func isLockChallenge(_ message: String) -> Bool {
        message.localizedCaseInsensitiveContains("Authentication required") ||
            message.localizedCaseInsensitiveContains("Password required")
    }

    private func markSeen(_ key: String) -> Bool {
        guard !seenSet.contains(key) else { return false }
        seen.append(key)
        seenSet.insert(key)
        if seen.count > Self.seenCapacity {
            seenSet.remove(seen.removeFirst())
        }
        return true
    }
}

/// GATT contract, identical to firmware `BLEManager.h`.
public enum AetherMeshBLE {
    public static let serviceUUID = "a75e0001-8b01-4475-bf7d-9477b83e7953"
    /// Phone → node, write.
    public static let txCharacteristicUUID = "a75e0002-8b01-4475-bf7d-9477b83e7953"
    /// Node → phone, notify.
    public static let rxCharacteristicUUID = "a75e0003-8b01-4475-bf7d-9477b83e7953"
}
