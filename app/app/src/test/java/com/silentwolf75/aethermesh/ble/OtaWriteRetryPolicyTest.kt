package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the OTA write-retry schedule. The failure this replaced was not a crash
 * but a budget that was too small to notice: 8 retries at a flat 50 ms gave up
 * 400 ms into a stall that lasted ~3 s, so any transfer unlucky enough to hit a
 * flash sector erase died.
 */
class OtaWriteRetryPolicyTest {

    @Test
    fun backoffDoublesThenHoldsAtCap() {
        assertEquals(250L, OtaWriteRetryPolicy.delayMs(1))
        assertEquals(500L, OtaWriteRetryPolicy.delayMs(2))
        assertEquals(1000L, OtaWriteRetryPolicy.delayMs(3))
        assertEquals(2000L, OtaWriteRetryPolicy.delayMs(4))
        assertEquals(4000L, OtaWriteRetryPolicy.delayMs(5))
        // Held at the cap rather than growing without bound.
        assertEquals(5000L, OtaWriteRetryPolicy.delayMs(6))
        assertEquals(5000L, OtaWriteRetryPolicy.delayMs(14))
    }

    @Test
    fun scheduleIsMonotonicAndBounded() {
        for (attempt in 2..OtaWriteRetryPolicy.MAX_ATTEMPTS) {
            assertTrue(
                "delay must not shrink as attempts grow",
                OtaWriteRetryPolicy.delayMs(attempt) >= OtaWriteRetryPolicy.delayMs(attempt - 1)
            )
            assertTrue(
                "delay must stay within the cap",
                OtaWriteRetryPolicy.delayMs(attempt) <= 5000L
            )
        }
    }

    @Test
    fun totalBudgetOutlastsAnObservedNodeStall() {
        // The stall seen in the field was ~3s (write callback lost at 3000ms).
        // The budget must clear that with real margin, or the fix is cosmetic.
        val budget = OtaWriteRetryPolicy.totalBudgetMs()
        assertTrue("budget $budget ms must exceed a 3s stall", budget > 3_000L)
        // A transfer that had run cleanly for 4 minutes was lost to a stall
        // longer than the previous ~11.7s budget.
        assertTrue("budget $budget ms must outlast a >13s stall", budget > 20_000L)
        // The old flat-50ms schedule spent 400 ms total; anything near that is a
        // regression back to the original bug.
        assertTrue("budget must not regress toward the old 400ms", budget > 5_000L)
    }

    @Test
    fun attemptIndexIsClampedRatherThanOverflowing() {
        // Guards against `250L shl (attempt - 1)` overflowing into a negative or
        // absurd delay if the attempt count is ever raised.
        assertEquals(5000L, OtaWriteRetryPolicy.delayMs(64))
        assertEquals(250L, OtaWriteRetryPolicy.delayMs(0))
        assertTrue(OtaWriteRetryPolicy.delayMs(1000) in 1..5000L)
    }
}
