package com.silentwolf75.aethermesh.data

import android.util.Base64
import java.security.KeyPairGenerator
import java.security.MessageDigest

data class EcdhKeyPair(val publicKey: String, val privateKey: String) {
    fun asPair(): Pair<String, String> = Pair(publicKey, privateKey)
}

/** EC P-256 keypair as Base64 SPKI / PKCS#8. Persist stays in [AetherMeshRepository]. */
object EcdhKeyPolicy {
    const val ERROR_PUBLIC = "ErrorGeneratingPublicKey"
    const val ERROR_PRIVATE = "ErrorGeneratingPrivateKey"
    const val PREF_PUBLIC = "ecdh_public_key"
    /** Must stay aligned with [AppMigrationExportPolicy.ECDH_PRIVATE_KEY]. */
    const val PREF_PRIVATE = "ecdh_private_key"

    fun generate(): EcdhKeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val pair = keyGen.generateKeyPair()
        return EcdhKeyPair(
            publicKey = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP),
            privateKey = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
        )
    }

    fun generateOrError(): EcdhKeyPair = try {
        generate()
    } catch (_: Exception) {
        EcdhKeyPair(ERROR_PUBLIC, ERROR_PRIVATE)
    }

    fun existingPair(publicKey: String?, privateKey: String?): Pair<String, String>? {
        if (publicKey.isNullOrBlank() || privateKey.isNullOrBlank()) return null
        if (publicKey == ERROR_PUBLIC || privateKey == ERROR_PRIVATE) return null
        return Pair(publicKey, privateKey)
    }

    fun exportText(publicKey: String, privateKey: String): String =
        "AetherMesh Security Keys:\nPublic: $publicKey\nPrivate: $privateKey"

    fun isError(pair: EcdhKeyPair): Boolean =
        pair.publicKey == ERROR_PUBLIC || pair.privateKey == ERROR_PRIVATE

    /** 8-hex fingerprint of one public key (read aloud to confirm identity). */
    fun fingerprint(publicKey: String): String {
        val raw = decodeKey(publicKey) ?: return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        return digest.take(4).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    /**
     * Order-independent 6-digit SAS check code for two public keys. Empty if
     * either key is missing or not valid Base64.
     */
    fun verificationCode(ourPublicKey: String, theirPublicKey: String): String {
        val a = decodeKey(ourPublicKey) ?: return ""
        val b = decodeKey(theirPublicKey) ?: return ""
        val first: ByteArray
        val second: ByteArray
        if (unsignedCompare(a, b) <= 0) {
            first = a
            second = b
        } else {
            first = b
            second = a
        }
        val md = MessageDigest.getInstance("SHA-256")
        md.update(first)
        md.update(second)
        val digest = md.digest()
        var n = 0L
        for (i in 0 until 4) {
            n = (n shl 8) or (digest[i].toLong() and 0xFF)
        }
        return "%06d".format(n % 1_000_000L)
    }

    private fun decodeKey(value: String): ByteArray? {
        if (value.isBlank() || value == ERROR_PUBLIC || value == ERROR_PRIVATE) return null
        return try {
            val decoded = Base64.decode(value.trim(), Base64.NO_WRAP)
            if (decoded.isEmpty()) null else decoded
        } catch (_: Exception) {
            null
        }
    }

    private fun unsignedCompare(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
