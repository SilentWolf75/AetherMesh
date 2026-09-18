package com.silentwolf75.aethermesh.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chat payload crypto. v2 is AES-256-GCM with PBKDF2 salt + IV prepended.
 * Decrypt also accepts v1 GCM (SHA-256 key, IV prepended). There is no ECB path.
 * Callers must refuse to send when [encrypt] returns null — never fall back
 * to plaintext.
 */
object ChatCrypto {
    const val ERROR_INVALID = "[Decryption Error - Invalid Message]"
    const val ERROR_BAD_CONTEXT = "[Decryption Error - Bad Key or Context]"
    const val ERROR_BAD_KEY = "[Decryption Error - Bad Key]"

    fun encrypt(plainText: String, passcode: String, chatIdentifier: String = ""): String? {
        return try {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val keySpec = ChatKeyDerivation.derive(passcode, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            if (chatIdentifier.isNotEmpty()) {
                cipher.updateAAD(chatIdentifier.toByteArray(Charsets.UTF_8))
            }
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            "v2:" + Base64.encodeToString(salt + iv + encryptedBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decrypt(cipherText: String, passcode: String, chatIdentifier: String = ""): String {
        if (cipherText.startsWith("v2:")) {
            return try {
                val decoded = Base64.decode(cipherText.removePrefix("v2:"), Base64.NO_WRAP)
                if (decoded.size <= 44) return ERROR_INVALID
                val salt = decoded.copyOfRange(0, 16)
                val iv = decoded.copyOfRange(16, 28)
                val keySpec = ChatKeyDerivation.derive(passcode, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
                if (chatIdentifier.isNotEmpty()) {
                    cipher.updateAAD(chatIdentifier.toByteArray(Charsets.UTF_8))
                }
                String(cipher.doFinal(decoded, 28, decoded.size - 28), Charsets.UTF_8)
            } catch (_: Exception) {
                ERROR_BAD_CONTEXT
            }
        }

        val keySpec = deriveLegacyKey(passcode)
        try {
            val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
            if (decoded.size > 28) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, decoded, 0, 12))
                return String(cipher.doFinal(decoded, 12, decoded.size - 12), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            // fall through to the legacy format
        }
        // There is deliberately no AES-ECB fallback. ECB was only used to
        // encrypt on the project's first day and never shipped in a release,
        // and a fallback that tries ECB on anything GCM rejects would turn
        // roughly 1 in 256 corrupt messages into scrambled "plaintext" instead
        // of an error, since that is how often random bytes pass PKCS#5 padding.
        return ERROR_BAD_KEY
    }

    internal fun deriveLegacyKey(passcode: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(passcode.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }
}
