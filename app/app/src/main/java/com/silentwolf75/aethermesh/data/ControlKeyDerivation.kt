package com.silentwolf75.aethermesh.data

/**
 * Derives the remote-config HMAC key (protocol v3 / AMCFG3).
 * Must match firmware PacketAuth control-key derivation byte-for-byte.
 */
object ControlKeyDerivation {
    /** Fixed 8-byte salt; not secret — uniqueness comes from the user password. */
    private val SALT = byteArrayOf(
        'A'.code.toByte(), 'M'.code.toByte(), 'C'.code.toByte(), 'T'.code.toByte(),
        'R'.code.toByte(), 'L'.code.toByte(), '1'.code.toByte(), 0
    )

    fun deriveKeyBytes(password: String): ByteArray =
        ChatKeyDerivation.derive(password, SALT, ChatKeyDerivation.ITERATIONS).encoded
}
