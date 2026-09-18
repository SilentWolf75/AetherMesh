package com.silentwolf75.aethermesh.data

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unlocking the node without sending its password over Bluetooth.
 *
 * The node answers an empty AuthRequest with a one-time challenge; the app
 * replies with HMAC-SHA256(key = password, message = "AMAUTH1" || challenge).
 * Must match firmware/src/AuthProof.cpp and the iOS AuthProof; all three
 * share the same test vectors.
 */
object AuthProof {
    const val CHALLENGE_BYTES = 16
    private val LABEL = "AMAUTH1".toByteArray(Charsets.US_ASCII)

    fun compute(password: String, challenge: ByteArray): ByteArray {
        require(password.isNotEmpty()) { "password must not be empty" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(LABEL)
        mac.update(challenge)
        return mac.doFinal()
    }

    /** A challenge the app can answer; older firmware sends none. */
    fun isUsableChallenge(challenge: ByteArray?): Boolean =
        challenge != null && challenge.size == CHALLENGE_BYTES
}
