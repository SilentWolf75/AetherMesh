import Crypto
import Foundation

/// Unlocking the node without sending its password over Bluetooth.
///
/// The node answers an empty AuthRequest with a one-time challenge; the app
/// replies with HMAC-SHA256(key: password, message: "AMAUTH1" || challenge).
/// Matches firmware/src/AuthProof.cpp and Android `AuthProof`; all three share
/// the same test vectors.
public enum AuthProof {
    public static let challengeBytes = 16
    private static let label = Data("AMAUTH1".utf8)

    public static func compute(password: String, challenge: Data) -> Data {
        let key = SymmetricKey(data: Data(password.utf8))
        var message = label
        message.append(challenge)
        return Data(HMAC<SHA256>.authenticationCode(for: message, using: key))
    }

    /// A challenge the app can answer; older firmware sends none.
    public static func isUsable(_ challenge: Data) -> Bool {
        challenge.count == challengeBytes
    }
}
