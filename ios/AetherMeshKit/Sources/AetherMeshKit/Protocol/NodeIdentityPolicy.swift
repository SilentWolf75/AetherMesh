import Crypto
import Foundation

/// Identity keys as the phone sees them — the Swift twin of the Android
/// `NodeIdentityPolicy`.
///
/// The phone never verifies a signature. Its own radio does, over the
/// authenticated BLE link, and reports a verdict; this turns that verdict into
/// something a person can act on and computes the fingerprint every device
/// shows, so a node's serial log, the Android app and this app all read the
/// same four groups aloud.
public enum NodeIdentityPolicy {
    /// Matches the firmware's fingerprint domain so the three agree exactly.
    static let fingerprintDomain = Data("AMID1-fp".utf8)
    static let keyBytes = 32

    public enum State: String, Sendable, Equatable {
        /// No announcement heard yet; direct messages to this node are not sealed.
        case unknown
        /// First key seen for this node; stored and in use.
        case learned
        /// Matches the key already on file.
        case known
        /// Replaced by a properly signed newer key. Legitimate, but worth seeing.
        case rotated
        /// A different key claimed this node's id. The stored key was kept.
        case conflict
    }

    public static func state(of trust: Aethermesh_NodeIdentity.Trust) -> State {
        switch trust {
        case .firstUse: return .learned
        case .known: return .known
        case .rotated: return .rotated
        case .conflict: return .conflict
        // An older node, or a verdict this build does not know, is never
        // optimistically treated as verified.
        default: return .unknown
        }
    }

    public static func isSealable(_ state: State) -> Bool {
        state == .learned || state == .known
    }

    /// A rotation is included: it is indistinguishable from someone else
    /// reflashing that node.
    public static func needsAttention(_ state: State) -> Bool {
        state == .conflict || state == .rotated
    }

    /// An all-zero X25519 key makes every shared secret zero, so it is never an
    /// identity.
    public static func keyIsUsable(_ key: Data) -> Bool {
        key.count == keyBytes && key.contains { $0 != 0 }
    }

    /// "A1B2-C3D4-E5F6-7890" over the signing key.
    public static func fingerprint(_ ed25519Public: Data) -> String {
        guard keyIsUsable(ed25519Public) else { return "" }
        var hasher = SHA256()
        hasher.update(data: fingerprintDomain)
        hasher.update(data: ed25519Public)
        let digest = Array(hasher.finalize().prefix(8))
        var out = ""
        for (index, byte) in digest.enumerated() {
            out += String(format: "%02X", byte)
            if index % 2 == 1 && index != 7 { out += "-" }
        }
        return out
    }

    public static func label(_ state: State) -> String {
        switch state {
        case .unknown: return "No key yet"
        case .learned, .known: return "Key verified"
        case .rotated: return "Key changed"
        case .conflict: return "KEY CONFLICT"
        }
    }

    public static func explanation(_ state: State) -> String {
        switch state {
        case .unknown:
            return "This node has not announced a key yet. Direct messages to it are not sealed."
        case .learned, .known:
            return "Direct messages to this node are sealed to its key. Compare the fingerprint in person to be sure of who it is."
        case .rotated:
            return "This node announced a new key. That is normal after reinstalling firmware, but it looks the same if someone else reflashed the node. Check the fingerprint before sending anything sensitive."
        case .conflict:
            return "A different key claimed this node's id. The original key was kept and nothing is being sealed to it. Verify the fingerprint in person before trusting it."
        }
    }
}
