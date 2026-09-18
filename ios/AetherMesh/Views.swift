import AetherMeshKit
import SwiftUI

@main
struct AetherMeshApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(model)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        TabView {
            ConnectView().tabItem { Label("Connect", systemImage: "antenna.radiowaves.left.and.right") }
            ChatView().tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right") }
            NodesView().tabItem { Label("Nodes", systemImage: "point.3.connected.trianglepath.dotted") }
        }
        .sheet(isPresented: $model.showUnlock) { UnlockView() }
        .alert("AetherMesh", isPresented: Binding(
            get: { model.lastError != nil },
            set: { if !$0 { model.lastError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(model.lastError ?? "")
        }
    }
}

struct ConnectView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationStack {
            List {
                Section("Status") {
                    Text(statusText)
                    if model.localNodeId != 0 {
                        LabeledContent("Node", value: MeshNodeId.hex(model.localNodeId))
                    }
                }
                Section("Nearby nodes") {
                    ForEach(model.discovered) { node in
                        Button {
                            model.connect(node)
                        } label: {
                            HStack {
                                Text(node.name)
                                Spacer()
                                Text("\(node.rssi) dBm").foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("AetherMesh")
            .toolbar {
                if model.connection == .connected {
                    Button("Disconnect") { model.disconnect() }
                } else {
                    Button("Scan") { model.startScan() }
                        .disabled(model.connection == .bluetoothOff || model.connection == .bluetoothDenied)
                }
            }
        }
    }

    private var statusText: String {
        switch model.connection {
        case .bluetoothOff: return "Bluetooth is off"
        case .bluetoothDenied: return "Bluetooth permission denied. Allow it in Settings."
        case .disconnected: return "Not connected"
        case .scanning: return "Scanning…"
        case .connecting: return "Connecting…"
        case .connected:
            switch model.authState {
            case .authenticated: return "Connected and unlocked"
            case .required(let passwordNotSet): return passwordNotSet ? "Set a node password" : "Password required"
            case .rejected: return "Password rejected"
            case .unknown: return "Connected, waiting for node"
            }
        }
    }
}

struct UnlockView: View {
    @EnvironmentObject private var model: AppModel
    @State private var password = ""
    @State private var remember = true

    var body: some View {
        NavigationStack {
            Form {
                if case .required(let passwordNotSet) = model.authState, passwordNotSet {
                    Text("This node has no password yet. The one you enter becomes its password.")
                }
                SecureField("Node password", text: $password)
                Toggle("Remember on this iPhone", isOn: $remember)
                Button("Unlock") { model.unlock(password: password, remember: remember) }
                    .disabled(password.isEmpty)
            }
            .navigationTitle("Unlock node")
        }
        .presentationDetents([.medium])
    }
}

struct ChatView: View {
    @EnvironmentObject private var model: AppModel
    @State private var channel = ChatSendPolicy.defaultChannel
    @State private var draft = ""
    @State private var keyDraft = ""
    @State private var editingKey = false

    private var chatIdentifier: String { ChatSendPolicy.channelKey(channel) }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                List(model.messages[chatIdentifier] ?? []) { message in
                    MessageRow(message: message, delivery: model.deliveryStates[message.packetId],
                               senderName: model.nodes[message.senderId]?.name)
                }
                .listStyle(.plain)
                HStack {
                    TextField("Message", text: $draft, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                    Button("Send") {
                        model.send(draft, channel: channel)
                        draft = ""
                    }
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                              model.authState != .authenticated)
                }
                .padding()
            }
            .navigationTitle(channel)
            .toolbar {
                Button {
                    keyDraft = ""
                    editingKey = true
                } label: {
                    Image(systemName: model.hasChannelKey(channel) ? "lock.fill" : "lock.open")
                }
                .accessibilityLabel("Channel key")
            }
            .alert("Channel key for \(channel)", isPresented: $editingKey) {
                SecureField("Key (empty removes it)", text: $keyDraft)
                Button("Save") { model.setChannelKey(keyDraft, channel: channel) }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Messages are encrypted with this key. Everyone on the channel needs the same key.")
            }
        }
    }
}

struct MessageRow: View {
    let message: MeshChatMessage
    let delivery: Aethermesh_DeliveryStatus.State?
    let senderName: String?

    var body: some View {
        VStack(alignment: message.direction == .outgoing ? .trailing : .leading, spacing: 2) {
            if message.direction == .incoming {
                Text(senderName?.isEmpty == false ? senderName! : MeshNodeId.hex(message.senderId))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Text(message.text)
                .italic(message.unreadable)
                .foregroundStyle(message.unreadable ? .secondary : .primary)
            HStack(spacing: 4) {
                if message.isEncrypted { Image(systemName: "lock.fill") }
                if message.direction == .outgoing, let delivery { Text(deliveryText(delivery)) }
            }
            .font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: message.direction == .outgoing ? .trailing : .leading)
    }

    private func deliveryText(_ state: Aethermesh_DeliveryStatus.State) -> String {
        switch state {
        case .delivered: return "Delivered"
        case .heard: return "Heard"
        case .failed: return "Failed"
        case .retrying: return "Retrying"
        case .queued, .stored: return "Queued"
        case .expired: return "Expired"
        default: return ""
        }
    }
}

struct NodesView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationStack {
            List {
                if let trace = model.lastTrace {
                    Section("Last traceroute") {
                        Text("Out: " + trace.forward.map { MeshNodeId.hex($0.nodeId) }.joined(separator: " → "))
                        Text("Back: " + trace.returning.map { MeshNodeId.hex($0.nodeId) }.joined(separator: " → "))
                        if trace.forwardTruncated || trace.returnTruncated {
                            Text("Path longer than the trace could record").foregroundStyle(.secondary)
                        }
                    }
                }
                Section("Heard nodes") {
                    // Sorted into a typed local first: inside a ForEach argument
                    // the compiler resolves this against the Binding overload and
                    // reports the failure as unrelated errors in the row body.
                    let heard: [MeshNodeInfo] =
                        model.nodes.values.sorted { $0.lastHeard > $1.lastHeard }
                    ForEach(heard, id: \MeshNodeInfo.nodeId) { (node: MeshNodeInfo) in
                        VStack(alignment: .leading) {
                            Text(node.name.isEmpty ? MeshNodeId.hex(node.nodeId) : node.name)
                            Text(verbatim: String(format: "Battery %u%% · SNR %.1f dB · %@",
                                                  node.batteryLevel, node.lastSnr, node.firmwareVersion))
                                .font(.caption).foregroundStyle(.secondary)
                            // SwiftProtobuf strips the GPS_STATE_ prefix, so the
                            // cases are .unknown/.absent/.fix, not .gpsStateUnknown.
                            if let identity = model.identities[node.nodeId] {
                                Text(verbatim: NodeIdentityPolicy.label(identity.state) + " · " + identity.fingerprint)
                                    .font(.caption.monospaced())
                                    .foregroundStyle(identity.needsAttention ? Color.orange : Color.secondary)
                            }
                            if node.gpsState != .unknown && node.gpsState != .absent {
                                Text(verbatim: "GPS \(node.gpsState == .fix ? "locked" : "no fix") · " +
                                    "\(node.satellitesUsed) used · \(node.satellitesInView) in view")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .swipeActions {
                            if node.nodeId != model.localNodeId {
                                Button("Trace") { model.traceRoute(to: node.nodeId) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Nodes")
        }
    }
}
