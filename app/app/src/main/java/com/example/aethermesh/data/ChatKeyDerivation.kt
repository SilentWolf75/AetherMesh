package com.example.aethermesh.data

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ChatKeyDerivation {
    const val ITERATIONS = 120_000

    fun derive(passcode: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        require(salt.size >= 4) { "Chat key salt is too short" }
        require(iterations > 0) { "PBKDF2 iterations must be positive" }
        // Implement one 32-byte PBKDF2 block directly with HMAC-SHA256 so the
        // result is identical on Android 7+; the named PBKDF2/SHA256 provider
        // is not available on every API level supported by the app.
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passcode.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val firstInput = salt + byteArrayOf(0, 0, 0, 1)
        var block = mac.doFinal(firstInput)
        val result = block.copyOf()
        repeat(iterations - 1) {
            block = mac.doFinal(block)
            for (index in result.indices) result[index] = (result[index].toInt() xor block[index].toInt()).toByte()
        }
        return SecretKeySpec(result, "AES")
    }
}
