package com.silentwolf75.aethermesh.data

import java.security.MessageDigest

/**
 * Derives the remote-config HMAC key (protocol v3 / AMCFG3).
 * Must match firmware PacketAuth control-key derivation byte-for-byte.
 *
 * Firmware caches in `packetauth::setControlPassword()`; the app caches the last
 * derived key so repeated remote renames/config do not re-run 120k PBKDF2
 * iterations. The cache is keyed by a digest rather than the password itself, so
 * no admin password is retained in the heap for the life of the process.
 *
 * The first derivation after a cache miss still costs roughly 60-150 ms, so call
 * it off the main thread when wiring new UI paths.
 */
object ControlKeyDerivation {
    /** Fixed 8-byte salt; not secret — uniqueness comes from the user password. */
    private val SALT = byteArrayOf(
        'A'.code.toByte(), 'M'.code.toByte(), 'C'.code.toByte(), 'T'.code.toByte(),
        'R'.code.toByte(), 'L'.code.toByte(), '1'.code.toByte(), 0
    )

    /** Domain separator so the cache key is not a bare password hash. */
    private val FINGERPRINT_DOMAIN = "AMCTRL-KEYCACHE-1".toByteArray(Charsets.US_ASCII)

    private val lock = Any()
    private var cachedFingerprint: ByteArray? = null
    private var cachedKey: ByteArray? = null
    private var derivations = 0L

    fun deriveKeyBytes(password: String): ByteArray {
        val fingerprint = fingerprint(password)
        synchronized(lock) {
            val key = cachedKey
            val known = cachedFingerprint
            if (key != null && known != null && MessageDigest.isEqual(known, fingerprint)) {
                return key.copyOf()
            }
        }
        val derived = ChatKeyDerivation.derive(password, SALT, ChatKeyDerivation.ITERATIONS).encoded
        synchronized(lock) {
            cachedKey?.fill(0)
            cachedFingerprint = fingerprint
            cachedKey = derived
            derivations++
        }
        return derived.copyOf()
    }

    /** Drop the cached key. Called whenever a stored admin password changes. */
    fun clearCache() {
        synchronized(lock) {
            cachedKey?.fill(0)
            cachedKey = null
            cachedFingerprint = null
        }
    }

    /**
     * How many times the PBKDF2 path has actually run. Exists so tests can prove
     * the cache is doing work — asserting two calls return equal bytes passes
     * just as well with no cache at all.
     */
    fun derivationCount(): Long = synchronized(lock) { derivations }

    private fun fingerprint(password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(FINGERPRINT_DOMAIN)
        digest.update(SALT)
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }
}
