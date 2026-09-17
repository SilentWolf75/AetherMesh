import Crypto
import Foundation
import XCTest
@testable import AetherMeshKit

/// Vectors shared with firmware (test_packetauth), Android (ControlAuthTest,
/// ChatCryptoTest) and tools/test_control_auth_vectors.py. If any of these
/// change, every platform must change together.
final class CryptoVectorTests: XCTestCase {
    // tools/test_control_auth_vectors.py fixture.
    static let v2CanonicalHex = "414d4346473201000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000"
    static let v2TagHex = "165a8fa5f809a08d3063ea46c78c64e4"
    static let v3TagHex = "0cd1d291935a725a4ea210f3ac3dddbf"

    // Generated independently with Python `cryptography` (see ChatCryptoTest.kt).
    static let chatVector = "v2:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaG8s/TOyrGKi8UPX219VjeO/Pd8l6u8iyX6dn8BvCzJWQzaVuWHc="
    static let chatKeyHex = "4cee2155e7bbb6926d7936117887d7890355408869830b869d05ba8995f76ba6"

    static func fixtureConfig() -> Aethermesh_NodeConfig {
        Aethermesh_NodeConfig.with {
            $0.nodeName = "Relay"
            $0.loraSf = 9
            $0.loraBw = 125
            $0.loraTxPower = 22
            $0.region = 0
            $0.nodeRole = 1
            $0.telemetryInterval = 60
            $0.screenTimeoutSecs = 30
            $0.positionPrecision = 100
        }
    }

    static let identity = ControlAuthIdentity(sessionId: 0x0102_0304_0506_0708, counter: 7)

    func testControlCanonicalMatchesPublishedVector() {
        let canonical = ControlAuth.canonical(
            senderId: 1, recipientId: 2, identity: Self.identity, config: Self.fixtureConfig()
        )
        XCTAssertEqual(canonical.hex, Self.v2CanonicalHex)
    }

    func testControlTagsMatchPublishedVectors() throws {
        let v2 = try ControlAuth.sign(
            senderId: 1, recipientId: 2, identity: Self.identity, config: Self.fixtureConfig(),
            password: "admin-key", authProtocol: 2
        )
        XCTAssertEqual(v2.hex, Self.v2TagHex)
        let v3 = try ControlAuth.sign(
            senderId: 1, recipientId: 2, identity: Self.identity, config: Self.fixtureConfig(),
            password: "admin-key", authProtocol: 3
        )
        XCTAssertEqual(v3.hex, Self.v3TagHex)
    }

    func testChatKeyDerivationMatchesPython() throws {
        let key = try ChatKeyDerivation.derive(passcode: "trail-key", salt: Data(0..<16))
        XCTAssertEqual(key.withUnsafeBytes { Data($0) }.hex, Self.chatKeyHex)
    }

    func testDecryptsIndependentlyProducedVector() {
        XCTAssertEqual(
            ChatCrypto.decrypt(Self.chatVector, passcode: "trail-key", chatContext: "CHANNEL_Trail"),
            "meet at the ridge ✓"
        )
    }

    func testEncryptWithFixedSaltAndIvReproducesVector() {
        let sealed = ChatCrypto.encrypt(
            "meet at the ridge ✓", passcode: "trail-key", chatContext: "CHANNEL_Trail",
            salt: Data(0..<16), iv: Data(16..<28)
        )
        XCTAssertEqual(sealed, Self.chatVector)
    }

    func testWrongKeyContextOrTamperingFailsClosed() {
        XCTAssertEqual(ChatCrypto.decrypt(Self.chatVector, passcode: "wrong", chatContext: "CHANNEL_Trail"),
                       ChatCrypto.errorBadContext)
        XCTAssertEqual(ChatCrypto.decrypt(Self.chatVector, passcode: "trail-key", chatContext: "CHANNEL_Other"),
                       ChatCrypto.errorBadContext)
        var bytes = [UInt8](Data(base64Encoded: String(Self.chatVector.dropFirst(3)))!)
        bytes[bytes.count - 1] ^= 0x01
        XCTAssertEqual(ChatCrypto.decrypt("v2:" + Data(bytes).base64EncodedString(), passcode: "trail-key",
                                          chatContext: "CHANNEL_Trail"),
                       ChatCrypto.errorBadContext)
        XCTAssertEqual(ChatCrypto.decrypt("v2:AAAA", passcode: "k"), ChatCrypto.errorInvalid)
    }

    func testRoundTripUsesFreshSaltAndIv() {
        let a = ChatCrypto.encrypt("same", passcode: "k", chatContext: "DM_1_2")
        let b = ChatCrypto.encrypt("same", passcode: "k", chatContext: "DM_1_2")
        XCTAssertNotNil(a)
        XCTAssertNotEqual(a, b)
        XCTAssertEqual(ChatCrypto.decrypt(a!, passcode: "k", chatContext: "DM_1_2"), "same")
    }

    func testLegacyV1GcmStillDecrypts() throws {
        // v1: AES-GCM keyed by SHA-256(passcode), iv12 || ciphertext || tag, no AAD.
        let key = SymmetricKey(data: SHA256.hash(data: Data("legacy".utf8)))
        let iv = Data(repeating: 7, count: 12)
        let sealed = try AES.GCM.seal(Data("old gcm".utf8), using: key, nonce: AES.GCM.Nonce(data: iv))
        let encoded = (iv + sealed.ciphertext + sealed.tag).base64EncodedString()
        XCTAssertEqual(ChatCrypto.decrypt(encoded, passcode: "legacy"), "old gcm")
    }
}

extension Data {
    var hex: String { map { String(format: "%02x", $0) }.joined() }
}
