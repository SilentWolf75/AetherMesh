import Foundation
import XCTest

@testable import AetherMeshKit

final class NodeIdentityPolicyTests: XCTestCase {
    private func key(_ seed: UInt8) -> Data {
        Data((0..<32).map { seed &+ UInt8($0) })
    }

    /// Shared with the Android test and computed independently from the
    /// firmware's definition (SHA-256 over "AMID1-fp" || key, first 8 bytes).
    /// People read these aloud across devices, so all three must agree exactly.
    func testFingerprintMatchesTheCrossPlatformVector() {
        XCTAssertEqual(NodeIdentityPolicy.fingerprint(key(1)), "2587-706C-0147-0378")
    }

    func testFingerprintIsStableAndDistinct() {
        let first = NodeIdentityPolicy.fingerprint(key(1))
        XCTAssertEqual(first, NodeIdentityPolicy.fingerprint(key(1)))
        XCTAssertEqual(first.count, 19)
        XCTAssertNotEqual(first, NodeIdentityPolicy.fingerprint(key(2)))
    }

    func testDegenerateKeysAreNotIdentities() {
        XCTAssertEqual(NodeIdentityPolicy.fingerprint(Data(count: 32)), "")
        XCTAssertEqual(NodeIdentityPolicy.fingerprint(Data(repeating: 1, count: 31)), "")
        XCTAssertFalse(NodeIdentityPolicy.keyIsUsable(Data(count: 32)))
        XCTAssertTrue(NodeIdentityPolicy.keyIsUsable(key(9)))
    }

    func testVerdictsMapWithoutOptimism() {
        XCTAssertEqual(NodeIdentityPolicy.state(of: .firstUse), .learned)
        XCTAssertEqual(NodeIdentityPolicy.state(of: .known), .known)
        XCTAssertEqual(NodeIdentityPolicy.state(of: .rotated), .rotated)
        XCTAssertEqual(NodeIdentityPolicy.state(of: .conflict), .conflict)
        XCTAssertEqual(NodeIdentityPolicy.state(of: .unspecified), .unknown)
    }

    func testOnlySettledKeysAreSealedToAndTroubleIsSurfaced() {
        XCTAssertTrue(NodeIdentityPolicy.isSealable(.learned))
        XCTAssertTrue(NodeIdentityPolicy.isSealable(.known))
        XCTAssertFalse(NodeIdentityPolicy.isSealable(.rotated))
        XCTAssertFalse(NodeIdentityPolicy.isSealable(.conflict))
        XCTAssertTrue(NodeIdentityPolicy.needsAttention(.conflict))
        XCTAssertTrue(NodeIdentityPolicy.needsAttention(.rotated))
        XCTAssertFalse(NodeIdentityPolicy.needsAttention(.known))
        XCTAssertTrue(NodeIdentityPolicy.explanation(.conflict).contains("original key was kept"))
        XCTAssertTrue(NodeIdentityPolicy.explanation(.rotated).contains("reflashed"))
    }
}
