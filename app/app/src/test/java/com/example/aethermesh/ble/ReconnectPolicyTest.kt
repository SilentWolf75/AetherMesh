package com.example.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun delayGrowsAndCapsAtThirtySeconds() {
        val delays = (0..8).map { ReconnectPolicy.delayMs(it, 0) }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L), delays.take(5))
        assertTrue(delays.drop(4).all { it == 30_000L })
    }

    @Test
    fun deterministicJitterNeverExceedsCap() {
        assertEquals(2_499L, ReconnectPolicy.delayMs(0, 499))
        assertEquals(30_000L, ReconnectPolicy.delayMs(8, Int.MAX_VALUE))
    }
}
