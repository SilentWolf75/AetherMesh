package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshReplyPolicyTest {
    @Test
    fun lowSfKeepsHistoricalFloor() {
        assertEquals(45_000L, MeshReplyPolicy.TIMEOUT_MS)
        assertEquals(5_000L, MeshReplyPolicy.ACK_POLL_MS)
        assertEquals(45_000L, MeshReplyPolicy.timeoutMs(7))
        assertEquals(45_000L, MeshReplyPolicy.timeoutMs(9))
        assertEquals(45_000L, MeshReplyPolicy.timeoutMs(10))
        assertEquals(55_000L, MeshReplyPolicy.ackCutoff(100_000L, 7))
    }

    @Test
    fun highSfExtendsPastFirmwareFastRetryWindow() {
        // Firmware SF12: base 6s × (1+2+4) = 42s before STORED; phone must wait longer.
        val sf12 = MeshReplyPolicy.timeoutMs(12)
        assertTrue(sf12 > MeshReplyPolicy.TIMEOUT_MS)
        assertTrue(sf12 >= MeshReplyPolicy.ackRetryBaseMs(12) * 7)
        assertEquals(100_000L - sf12, MeshReplyPolicy.ackCutoff(100_000L, 12))

        val sf11 = MeshReplyPolicy.timeoutMs(11)
        assertTrue(sf11 > MeshReplyPolicy.TIMEOUT_MS)
        assertTrue(sf11 < sf12)
        assertTrue(sf11 >= MeshReplyPolicy.ackRetryBaseMs(11) * 7)
    }

    @Test
    fun timeoutIsNonDecreasingWithSf() {
        var prev = 0L
        for (sf in 7..12) {
            val t = MeshReplyPolicy.timeoutMs(sf)
            assertTrue("SF$sf=$t should be >= SF${sf - 1}=$prev", t >= prev)
            prev = t
        }
    }

    @Test
    fun remoteTimeoutCopy() {
        assertEquals(
            "Timed out — no response from node.",
            MeshReplyPolicy.remoteTimedOut(false)
        )
        assertEquals(
            "Tiempo agotado — sin respuesta del nodo.",
            MeshReplyPolicy.remoteTimedOut(true)
        )
    }
}
