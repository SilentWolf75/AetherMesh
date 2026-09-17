import Foundation
import XCTest
@testable import AetherMeshKit

final class PolicyTests: XCTestCase {
    func testUtf8BoundsNeverSplitAScalar() {
        XCTAssertEqual("héllo".prefixUTF8(maxBytes: 2), "h")
        XCTAssertEqual("héllo".prefixUTF8(maxBytes: 3), "hé")
        XCTAssertEqual("✓✓".prefixUTF8(maxBytes: 5), "✓")
        XCTAssertEqual("abc".prefixUTF8(maxBytes: 0), "")
    }

    func testChatContextLabelsMatchAndroid() {
        XCTAssertEqual(ChatContext.authenticatedLabel(senderId: 0xC504A6B0, recipientId: MeshNodeId.broadcast, channel: "Trail"),
                       "CHANNEL_Trail")
        // DM labels are order independent and lowercase hex.
        XCTAssertEqual(ChatContext.authenticatedLabel(senderId: 0x14D3228C, recipientId: 0xC504A6B0, channel: ""),
                       "DM_14d3228c_c504a6b0")
        XCTAssertEqual(ChatContext.authenticatedLabel(senderId: 0xC504A6B0, recipientId: 0x14D3228C, channel: ""),
                       "DM_14d3228c_c504a6b0")
        XCTAssertEqual(ChatSendPolicy.dmKey(peerId: 0xC504A6B0), "DM_3305416368")
    }

    func testChannelPacketShape() {
        let packet = ChatSendPolicy.buildPacket(
            localNodeId: 0x14D3228C, recipientId: MeshNodeId.broadcast, packetId: 9,
            content: "hi", channel: String(repeating: "x", count: 40), isEncrypted: false
        )
        XCTAssertEqual(packet.hopLimit, 4)
        XCTAssertTrue(packet.wantAck)
        XCTAssertEqual(packet.text.channel.count, 31)
        let dm = ChatSendPolicy.buildPacket(localNodeId: 1, recipientId: 2, packetId: 10, content: "hi",
                                            channel: "General", isEncrypted: true)
        XCTAssertEqual(dm.text.channel, "")
        XCTAssertTrue(dm.text.isEncrypted)
    }

    func testEncryptedContentBoundIsSmaller() {
        let long = String(repeating: "a", count: 200)
        XCTAssertEqual(ChatSendPolicy.boundContent(long, encrypted: false).utf8.count, 127)
        XCTAssertEqual(ChatSendPolicy.boundContent(long, encrypted: true).utf8.count, 76)
    }

    func testChangePasswordRefusesEmpty() {
        XCTAssertNil(AuthRequests.changePassword(localNodeId: 1, packetId: 1, current: "old", new: "  "))
        XCTAssertNotNil(AuthRequests.changePassword(localNodeId: 1, packetId: 1, current: "old", new: "new"))
    }

    func testHopRange() {
        XCTAssertEqual(HopRangePolicy.firmwareMax(reportedMaxHopLimit: 0), 8)
        XCTAssertEqual(HopRangePolicy.firmwareMax(reportedMaxHopLimit: 16), 16)
        XCTAssertEqual(HopRangePolicy.clamp(12), 8)
        XCTAssertEqual(HopRangePolicy.clamp(12, maxHopLimit: 16), 12)
        XCTAssertTrue(HopRangePolicy.needsUpgradedMesh(9))
    }

    func testCompactTraceHopsMatchFirmwareBytes() {
        // Same bytes as firmware test_tracehops and Android HopRangePolicyTest.
        let bytes = Data([0xB0, 0xA6, 0x04, 0xC5, 27, 0x8C, 0x22, 0xD3, 0x14, UInt8(bitPattern: -49)])
        let hops = TraceRoutePolicy.decodeCompactHops(bytes)
        XCTAssertEqual(hops.map(\.nodeId), [0xC504A6B0, 0x14D3228C])
        XCTAssertEqual(hops.map(\.snr), [6.75, -12.25])
    }

    func testIncomingPlanMatchesAndroid() {
        let channel = IncomingChatPolicy.plan(senderId: 1, recipientId: MeshNodeId.broadcast, channel: "")
        XCTAssertEqual(channel.chatIdentifier, "CHANNEL_General")
        XCTAssertEqual(channel.channelForRow, "General")
        XCTAssertEqual(channel.cryptoContext, "CHANNEL_General")
        // Catch-up unicast keeps the channel AAD even though recipient is a node.
        let catchUp = IncomingChatPolicy.plan(senderId: 1, recipientId: 5, channel: "Trail")
        XCTAssertEqual(catchUp.cryptoContext, "CHANNEL_Trail")
        XCTAssertTrue(catchUp.isBroadcast)
        let dm = IncomingChatPolicy.plan(senderId: 1, recipientId: 5, channel: "")
        XCTAssertEqual(dm.chatIdentifier, "DM_1")
        XCTAssertEqual(dm.cryptoContext, "DM_1_5")
        XCTAssertFalse(dm.isBroadcast)
    }
}
