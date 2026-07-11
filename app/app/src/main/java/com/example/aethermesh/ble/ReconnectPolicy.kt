package com.example.aethermesh.ble

internal object ReconnectPolicy {
    private const val BASE_DELAY_MS = 2_000L
    private const val MAX_DELAY_MS = 30_000L

    fun delayMs(attempt: Int, entropy: Int): Long {
        val exponent = attempt.coerceIn(0, 4)
        val base = (BASE_DELAY_MS shl exponent).coerceAtMost(MAX_DELAY_MS)
        val jitterWindow = (base / 4).coerceAtLeast(1L)
        val jitter = (entropy.toLong() and 0x7FFFFFFF) % jitterWindow
        return (base + jitter).coerceAtMost(MAX_DELAY_MS)
    }
}
