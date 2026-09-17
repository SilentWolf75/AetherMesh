import Foundation

/// Node ids are unsigned 32-bit; 0 is never a real node.
public enum MeshNodeId {
    public static let broadcast: UInt32 = 0xFFFF_FFFF

    /// Firmware advertises as "AetherMesh-XXXX" (low 16 bits of the node id)
    /// unless the node has a custom name. iOS cannot read the BLE MAC Android
    /// uses, so this suffix is the only pre-auth hint of which node it is.
    public static func advertisedSuffix(fromName name: String?) -> UInt16? {
        guard let name, name.hasPrefix("AetherMesh-") else { return nil }
        return UInt16(name.dropFirst("AetherMesh-".count), radix: 16)
    }

    public static func hex(_ id: UInt32) -> String {
        String(format: "%08X", id)
    }
}

/// Crypto AAD / chat identity labels, identical to Android `ChatContext`.
public enum ChatContext {
    public static func authenticatedLabel(senderId: UInt32, recipientId: UInt32, channel: String) -> String {
        if recipientId == MeshNodeId.broadcast {
            return "CHANNEL_\(channel.prefixUTF16(31))"
        }
        let first = min(senderId, recipientId)
        let second = max(senderId, recipientId)
        return "DM_\(String(first, radix: 16))_\(String(second, radix: 16))"
    }
}

/// Outbound chat rules, ported from Android `ChatSendPolicy`.
public enum ChatSendPolicy {
    public static let maxText = 127
    public static let maxEncrypted = 76
    public static let maxChannel = 31
    /// Older firmware sends phone chat with exactly this; extended-range firmware
    /// replaces it with the node's configured hop limit.
    public static let hopLimit: UInt32 = 4
    public static let defaultChannel = "General"

    public static func boundChannel(_ channel: String) -> String { channel.prefixUTF16(maxChannel) }

    public static func boundContent(_ content: String, encrypted: Bool) -> String {
        content.prefixUTF8(maxBytes: encrypted ? maxEncrypted : maxText)
    }

    public static func channelKey(_ channel: String) -> String { "CHANNEL_\(boundChannel(channel))" }

    public static func dmKey(peerId: UInt32) -> String { "DM_\(peerId)" }

    public static func chatIdentifier(recipientId: UInt32, channel: String) -> String {
        recipientId == MeshNodeId.broadcast ? channelKey(channel) : dmKey(peerId: recipientId)
    }

    public static func buildPacket(
        localNodeId: UInt32,
        recipientId: UInt32,
        packetId: UInt32,
        content: String,
        channel: String,
        isEncrypted: Bool,
        hearerReceipts: Bool = true
    ) -> Aethermesh_MeshPacket {
        let isChannel = recipientId == MeshNodeId.broadcast
        var packet = Aethermesh_MeshPacket()
        packet.senderID = localNodeId
        packet.recipientID = recipientId
        packet.packetID = packetId
        packet.hopLimit = hopLimit
        packet.wantAck = !isChannel || hearerReceipts
        packet.prevHopID = localNodeId
        packet.text = Aethermesh_TextMessage.with {
            $0.content = content
            $0.channel = isChannel ? boundChannel(channel) : ""
            $0.isEncrypted = isEncrypted
        }
        return packet
    }
}

/// Local BLE login packets (recipient 0), ported from Android `AuthRequestApply`.
public enum AuthRequests {
    public static func unlock(localNodeId: UInt32, packetId: UInt32, password: String) -> Aethermesh_MeshPacket {
        envelope(localNodeId, packetId, Aethermesh_AuthRequest.with {
            $0.password = password
            $0.isChangePassword = false
        })
    }

    public static func changePassword(
        localNodeId: UInt32, packetId: UInt32, current: String, new newPassword: String
    ) -> Aethermesh_MeshPacket? {
        // Firmware rejects an empty new password; do not send one.
        guard !current.trimmingCharacters(in: .whitespaces).isEmpty,
              !newPassword.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return envelope(localNodeId, packetId, Aethermesh_AuthRequest.with {
            $0.password = current
            $0.isChangePassword = true
            $0.newPassword = newPassword
        })
    }

    static func envelope(_ localNodeId: UInt32, _ packetId: UInt32, _ auth: Aethermesh_AuthRequest) -> Aethermesh_MeshPacket {
        var packet = Aethermesh_MeshPacket()
        packet.senderID = localNodeId
        packet.recipientID = 0
        packet.packetID = packetId
        packet.hopLimit = 1
        packet.prevHopID = localNodeId
        packet.authRequest = auth
        return packet
    }
}

/// Hop limit range; see Android `HopRangePolicy` and firmware `MeshMath.h`.
public enum HopRangePolicy {
    public static let legacyMax = 8
    public static let extendedMax = 16

    public static func firmwareMax(reportedMaxHopLimit: UInt32) -> Int {
        reportedMaxHopLimit >= UInt32(extendedMax) ? extendedMax : legacyMax
    }

    public static func clamp(_ hops: Int, maxHopLimit: Int = legacyMax) -> Int {
        let ceiling = min(max(maxHopLimit, legacyMax), extendedMax)
        return min(max(hops, 1), ceiling)
    }

    public static func needsUpgradedMesh(_ hops: Int) -> Bool { hops > legacyMax }
}

public struct TraceHop: Equatable, Sendable {
    public let nodeId: UInt32
    /// dBm; 0 when unknown (compact extended-range hops carry no RSSI).
    public let rssi: Int
    public let snr: Float
}

public struct TraceRouteResult: Equatable, Sendable {
    public let traceId: UInt32
    public let targetId: UInt32
    public let forward: [TraceHop]
    public let returning: [TraceHop]
    public let forwardTruncated: Bool
    public let returnTruncated: Bool
}

/// Traceroute request/response handling, ported from Android `TraceRoutePolicy`.
public enum TraceRoutePolicy {
    public static let hopLimit: UInt32 = 7
    /// firmware TraceHops.h: uint32 little-endian node id + int8 SNR quarter dB.
    public static let compactHopBytes = 5

    public static func buildRequest(localNodeId: UInt32, targetId: UInt32, traceId: UInt32) -> Aethermesh_MeshPacket {
        var packet = Aethermesh_MeshPacket()
        packet.senderID = localNodeId
        packet.recipientID = targetId
        packet.packetID = traceId
        packet.hopLimit = hopLimit
        packet.prevHopID = localNodeId
        packet.traceRoute = Aethermesh_TraceRoute.with {
            $0.type = .request
            $0.traceID = traceId
            $0.originID = localNodeId
            $0.targetID = targetId
        }
        return packet
    }

    public static func decodeCompactHops(_ bytes: Data) -> [TraceHop] {
        let raw = [UInt8](bytes)
        return stride(from: 0, to: raw.count - raw.count % compactHopBytes, by: compactHopBytes).map { offset in
            let id = UInt32(raw[offset]) | UInt32(raw[offset + 1]) << 8 |
                UInt32(raw[offset + 2]) << 16 | UInt32(raw[offset + 3]) << 24
            return TraceHop(nodeId: id, rssi: 0, snr: Float(Int8(bitPattern: raw[offset + 4])) / 4)
        }
    }

    /// Result for a RESPONSE addressed to this origin, or nil if it is not ours.
    public static func result(from packet: Aethermesh_MeshPacket, localNodeId: UInt32, expectedTraceId: UInt32) -> TraceRouteResult? {
        guard case .traceRoute(let trace)? = packet.payload,
              trace.type == .response,
              trace.traceID == expectedTraceId,
              trace.originID == localNodeId else { return nil }
        func legacy(_ ids: [UInt32], _ rssi: [Int32], _ snr: [Int32]) -> [TraceHop] {
            ids.enumerated().map { index, id in
                TraceHop(
                    nodeId: id,
                    rssi: index < rssi.count ? Int(rssi[index]) : 0,
                    snr: index < snr.count ? Float(snr[index]) / 4 : 0
                )
            }
        }
        let forward = decodeCompactHops(trace.forwardHops) +
            legacy(trace.forwardNodeIds, trace.forwardRssi, trace.forwardSnrQuarterDb)
        var returning = decodeCompactHops(trace.returnHops) +
            legacy(trace.returnNodeIds, trace.returnRssi, trace.returnSnrQuarterDb)
        if returning.last?.nodeId != localNodeId {
            returning.append(TraceHop(nodeId: localNodeId, rssi: Int(packet.rxRssi), snr: packet.rxSnr))
        }
        return TraceRouteResult(
            traceId: trace.traceID,
            targetId: trace.targetID,
            forward: forward,
            returning: returning,
            forwardTruncated: trace.forwardTruncated,
            returnTruncated: trace.returnTruncated
        )
    }
}

public struct IncomingChatPlan: Equatable, Sendable {
    public let chatIdentifier: String
    public let cryptoContext: String
    public let channelForRow: String
    public let isBroadcast: Bool
}

/// Inbound text routing, ported from Android `IncomingChatPolicy`.
public enum IncomingChatPolicy {
    public static let errorNoKey = "[Encrypted Message - No Key Configured]"

    public static func isControlPing(_ content: String) -> Bool { content.hasPrefix("PING_") }

    public static func plan(senderId: UInt32, recipientId: UInt32, channel: String) -> IncomingChatPlan {
        let named = channel.isEmpty ? ChatSendPolicy.defaultChannel : channel
        let channelFrame = recipientId == MeshNodeId.broadcast || !channel.isEmpty
        let chatIdentifier = channelFrame
            ? ChatSendPolicy.channelKey(named)
            : ChatSendPolicy.dmKey(peerId: senderId)
        let cryptoRecipient = channelFrame ? MeshNodeId.broadcast : recipientId
        let channelForRow: String
        if recipientId == MeshNodeId.broadcast {
            channelForRow = named
        } else {
            channelForRow = channel
        }
        return IncomingChatPlan(
            chatIdentifier: chatIdentifier,
            cryptoContext: ChatContext.authenticatedLabel(senderId: senderId, recipientId: cryptoRecipient, channel: named),
            channelForRow: channelForRow,
            isBroadcast: !channelForRow.isEmpty || recipientId == MeshNodeId.broadcast
        )
    }
}
