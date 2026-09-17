import Foundation
import XCTest
@testable import AetherMeshKit

private final class Keys: ChatKeyProvider {
    var keys: [String: String] = [:]
    func chatKey(for chatIdentifier: String) -> String? { keys[chatIdentifier] }
}

final class MeshSessionTests: XCTestCase {
    private let node: UInt32 = 0x14D3228C
    private var writes: [Data] = []
    private var events: [MeshSession.Event] = []
    private var keys = Keys()

    private func session() -> MeshSession {
        let session = MeshSession(keys: keys) { [unowned self] data in
            writes.append(data)
            return true
        }
        session.onEvent = { [unowned self] in events.append($0) }
        return session
    }

    private func frame(_ build: (inout Aethermesh_MeshPacket) -> Void) -> Data {
        var packet = Aethermesh_MeshPacket()
        build(&packet)
        return try! packet.serializedBytes()
    }

    private func authResponse(success: Bool, message: String, sender: UInt32? = nil, rssi: Float = 0) -> Data {
        frame {
            $0.senderID = sender ?? node
            $0.recipientID = 0
            $0.rxRssi = rssi
            $0.authResponse = Aethermesh_AuthResponse.with {
                $0.success = success
                $0.message = message
            }
        }
    }

    private func authenticated() -> MeshSession {
        let s = session()
        s.handleIncoming(authResponse(success: false, message: "Authentication required"))
        XCTAssertTrue(s.unlock(password: "admin"))
        s.handleIncoming(authResponse(success: true, message: "Authenticated successfully"))
        return s
    }

    func testLoginFlowLearnsLocalNode() throws {
        let s = session()
        s.handleIncoming(authResponse(success: false, message: "Authentication required"))
        XCTAssertEqual(s.authState, .required(passwordNotSet: false))
        s.unlock(password: "admin")
        let request = try Aethermesh_MeshPacket(serializedBytes: writes.last!)
        XCTAssertEqual(request.authRequest.password, "admin")
        XCTAssertEqual(request.recipientID, 0)
        s.handleIncoming(authResponse(success: true, message: "Authenticated successfully"))
        XCTAssertEqual(s.authState, .authenticated)
        XCTAssertEqual(s.localNodeId, node)
    }

    func testAuthResponseOverRadioIsIgnored() {
        let s = session()
        // Signal metadata means it came over LoRa, not from the BLE-connected node.
        s.handleIncoming(authResponse(success: true, message: "Authenticated successfully", rssi: -80))
        XCTAssertEqual(s.authState, .unknown)
    }

    func testResetDropsAuthentication() {
        let s = authenticated()
        s.reset()
        XCTAssertEqual(s.authState, .unknown)
        XCTAssertEqual(s.sendText("hi"), .notAuthenticated)
    }

    func testEncryptedChannelSendNeverLeaksPlaintext() throws {
        keys.keys["CHANNEL_Trail"] = "trail-key"
        let s = authenticated()
        guard case .sent(let packetId) = s.sendText("secret plan", channel: "Trail") else {
            return XCTFail("not sent")
        }
        let packet = try Aethermesh_MeshPacket(serializedBytes: writes.last!)
        XCTAssertEqual(packet.packetID, packetId)
        XCTAssertTrue(packet.text.isEncrypted)
        XCTAssertTrue(packet.text.content.hasPrefix("v2:"))
        XCTAssertFalse(packet.text.content.contains("secret"))
        XCTAssertEqual(ChatCrypto.decrypt(packet.text.content, passcode: "trail-key", chatContext: "CHANNEL_Trail"),
                       "secret plan")
    }

    func testIncomingEncryptedChatDecryptsAndDeduplicates() {
        keys.keys["CHANNEL_Trail"] = "trail-key"
        let s = authenticated()
        events.removeAll()
        let incoming = frame {
            $0.senderID = 0xC504A6B0
            $0.recipientID = MeshNodeId.broadcast
            $0.packetID = 77
            $0.text = Aethermesh_TextMessage.with {
                $0.channel = "Trail"
                $0.isEncrypted = true
                $0.content = CryptoVectorTests.chatVector
            }
        }
        s.handleIncoming(incoming)
        s.handleIncoming(incoming) // relayed copy
        let chats = events.compactMap { event -> MeshChatMessage? in
            if case .chat(let message) = event { return message }
            return nil
        }
        XCTAssertEqual(chats.count, 1)
        XCTAssertEqual(chats.first?.text, "meet at the ridge ✓")
        XCTAssertEqual(chats.first?.unreadable, false)
    }

    func testIncomingEncryptedChatWithoutKeyIsMarkedUnreadable() {
        let s = authenticated()
        events.removeAll()
        s.handleIncoming(frame {
            $0.senderID = 0xC504A6B0
            $0.recipientID = MeshNodeId.broadcast
            $0.packetID = 78
            $0.text = Aethermesh_TextMessage.with {
                $0.channel = "Trail"
                $0.isEncrypted = true
                $0.content = CryptoVectorTests.chatVector
            }
        })
        guard case .chat(let message)? = events.last else { return XCTFail("no chat event") }
        XCTAssertTrue(message.unreadable)
        XCTAssertEqual(message.text, IncomingChatPolicy.errorNoKey)
    }

    func testTraceRouteResultIsDelivered() {
        let s = authenticated()
        guard let traceId = s.startTraceRoute(to: 0xC504A6B0) else { return XCTFail("trace not started") }
        events.removeAll()
        s.handleIncoming(frame {
            $0.senderID = 0xC504A6B0
            $0.recipientID = node
            $0.rxSnr = 3.5
            $0.traceRoute = Aethermesh_TraceRoute.with {
                $0.type = .response
                $0.traceID = traceId
                $0.originID = node
                $0.targetID = 0xC504A6B0
                $0.forwardHops = Data([0xB0, 0xA6, 0x04, 0xC5, 8])
            }
        })
        guard case .traceRoute(let result)? = events.last else { return XCTFail("no trace event") }
        XCTAssertEqual(result.forward.map(\.nodeId), [0xC504A6B0])
        XCTAssertEqual(result.returning.last?.nodeId, node)
    }

    func testDeliveryStatusRequiresAuthenticatedLocalNode() {
        let s = session()
        let status = frame {
            $0.senderID = node
            $0.recipientID = 0
            $0.deliveryStatus = Aethermesh_DeliveryStatus.with {
                $0.packetID = 5
                $0.state = .delivered
            }
        }
        s.handleIncoming(status)
        XCTAssertTrue(events.isEmpty)
        let authed = authenticated()
        events.removeAll()
        authed.handleIncoming(status)
        guard case .delivery(let packetId, let state, _)? = events.last else { return XCTFail("no delivery") }
        XCTAssertEqual(packetId, 5)
        XCTAssertEqual(state, .delivered)
    }
}
