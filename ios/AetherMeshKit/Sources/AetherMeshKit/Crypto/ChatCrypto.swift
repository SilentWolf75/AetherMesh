import Crypto
import CryptoExtras
import Foundation

/// PBKDF2-HMAC-SHA256, one 32-byte block. Identical output to Android
/// `ChatKeyDerivation` and firmware `PacketAuth` (standard PBKDF2 with dkLen 32).
public enum ChatKeyDerivation {
    public static let iterations = 120_000

    public static func derive(passcode: String, salt: Data, iterations: Int = iterations) throws -> SymmetricKey {
        try KDF.Insecure.PBKDF2.deriveKey(
            from: Data(passcode.utf8),
            salt: salt,
            using: .sha256,
            outputByteCount: 32,
            // 120k matches every deployed node and app; the library's 210k floor
            // would produce keys nothing else in the mesh can use.
            unsafeUncheckedRounds: iterations
        )
    }
}

/// Chat payload crypto, wire-compatible with Android `ChatCrypto`.
///
/// v2 (only format this app writes): `"v2:" + base64(salt16 || iv12 || ciphertext || tag16)`,
/// AES-256-GCM, key from [ChatKeyDerivation] with the per-message salt, and the
/// chat context label as AAD. Decrypt also accepts v1 GCM (SHA-256 of the
/// passcode, iv12 prefix). Legacy AES-ECB is deliberately not supported: it has
/// no integrity check, so a forged message could not be told from a real one.
public enum ChatCrypto {
    public static let errorInvalid = "[Decryption Error - Invalid Message]"
    public static let errorBadContext = "[Decryption Error - Bad Key or Context]"
    public static let errorBadKey = "[Decryption Error - Bad Key]"

    static let v2Prefix = "v2:"
    static let saltLength = 16
    static let ivLength = 12
    static let tagLength = 16

    /// Returns nil on failure. Callers must refuse to send rather than fall back to plaintext.
    public static func encrypt(_ plainText: String, passcode: String, chatContext: String = "") -> String? {
        encrypt(
            plainText,
            passcode: passcode,
            chatContext: chatContext,
            salt: randomBytes(saltLength),
            iv: randomBytes(ivLength)
        )
    }

    static func encrypt(_ plainText: String, passcode: String, chatContext: String, salt: Data, iv: Data) -> String? {
        do {
            let key = try ChatKeyDerivation.derive(passcode: passcode, salt: salt)
            let nonce = try AES.GCM.Nonce(data: iv)
            let sealed = try AES.GCM.seal(
                Data(plainText.utf8),
                using: key,
                nonce: nonce,
                authenticating: Data(chatContext.utf8)
            )
            var payload = Data()
            payload.append(salt)
            payload.append(iv)
            payload.append(sealed.ciphertext)
            payload.append(sealed.tag)
            return v2Prefix + payload.base64EncodedString()
        } catch {
            return nil
        }
    }

    /// Plaintext, or one of the `error*` sentinels (the same strings Android stores).
    public static func decrypt(_ cipherText: String, passcode: String, chatContext: String = "") -> String {
        if cipherText.hasPrefix(v2Prefix) {
            // Android rejects anything not longer than salt + iv + tag (44 bytes).
            guard let decoded = Data(base64Encoded: String(cipherText.dropFirst(v2Prefix.count))),
                  decoded.count > saltLength + ivLength + tagLength
            else { return errorInvalid }
            let bytes = [UInt8](decoded)
            let salt = Data(bytes[0..<saltLength])
            let iv = Data(bytes[saltLength..<(saltLength + ivLength)])
            let body = Data(bytes[(saltLength + ivLength)...])
            guard body.count > tagLength else { return errorInvalid }
            do {
                let key = try ChatKeyDerivation.derive(passcode: passcode, salt: salt)
                let box = try AES.GCM.SealedBox(
                    nonce: AES.GCM.Nonce(data: iv),
                    ciphertext: body.prefix(body.count - tagLength),
                    tag: body.suffix(tagLength)
                )
                let plain = try AES.GCM.open(box, using: key, authenticating: Data(chatContext.utf8))
                return String(decoding: plain, as: UTF8.self)
            } catch {
                return errorBadContext
            }
        }

        // v1: AES-GCM keyed by SHA-256(passcode), iv12 || ciphertext || tag16, no AAD.
        guard let decoded = Data(base64Encoded: cipherText), decoded.count > ivLength + tagLength else {
            return errorBadKey
        }
        do {
            let key = SymmetricKey(data: SHA256.hash(data: Data(passcode.utf8)))
            let bytes = [UInt8](decoded)
            let body = Data(bytes[ivLength...])
            let box = try AES.GCM.SealedBox(
                nonce: AES.GCM.Nonce(data: Data(bytes[0..<ivLength])),
                ciphertext: body.prefix(body.count - tagLength),
                tag: body.suffix(tagLength)
            )
            return String(decoding: try AES.GCM.open(box, using: key), as: UTF8.self)
        } catch {
            return errorBadKey
        }
    }

    public static func isDecryptFailure(_ content: String) -> Bool {
        content == errorInvalid || content == errorBadContext || content == errorBadKey
    }

    static func randomBytes(_ count: Int) -> Data {
        var generator = SystemRandomNumberGenerator()
        return Data((0..<count).map { _ in UInt8.random(in: .min ... .max, using: &generator) })
    }
}
