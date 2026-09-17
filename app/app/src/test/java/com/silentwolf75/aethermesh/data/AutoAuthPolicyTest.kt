package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAuthPolicyTest {
    @Test
    fun postSettingsWaitsLongerAndRetriesMore() {
        assertEquals(700L, AutoAuthPolicy.initialDelayMs(true))
        assertEquals(400L, AutoAuthPolicy.initialDelayMs(false))
        assertEquals(8, AutoAuthPolicy.attemptCount(hasPassword = true, postSettings = true))
        assertEquals(6, AutoAuthPolicy.attemptCount(hasPassword = true, postSettings = false))
        assertEquals(3, AutoAuthPolicy.attemptCount(hasPassword = false, postSettings = true))
        assertEquals(1_200L, AutoAuthPolicy.responseWaitMs(wrote = true, postSettings = true))
        assertEquals(900L, AutoAuthPolicy.responseWaitMs(wrote = true, postSettings = false))
        assertEquals(500L, AutoAuthPolicy.responseWaitMs(wrote = false, postSettings = true))
    }

    @Test
    fun stopWhenDisconnectedOrAlreadyUnlocked() {
        assertTrue(AutoAuthPolicy.shouldStop(connected = false, authenticated = false))
        assertTrue(AutoAuthPolicy.shouldStop(connected = true, authenticated = true))
        assertFalse(AutoAuthPolicy.shouldStop(connected = true, authenticated = false))
    }

    @Test
    fun zombiePostSettingsForcesOneGattRefreshThenUnlockPrompt() {
        assertEquals(
            AutoAuthExhausted.ForceRefresh,
            AutoAuthPolicy.onExhausted(true, false, postSettings = true, refreshUsed = false)
        )
        assertEquals(
            AutoAuthExhausted.PromptUnlock,
            AutoAuthPolicy.onExhausted(true, false, postSettings = true, refreshUsed = true)
        )
        assertEquals(
            AutoAuthExhausted.PromptUnlock,
            AutoAuthPolicy.onExhausted(true, false, postSettings = false, refreshUsed = false)
        )
        assertEquals(
            AutoAuthExhausted.Idle,
            AutoAuthPolicy.onExhausted(true, true, postSettings = true, refreshUsed = false)
        )
        assertTrue(AutoAuthPolicy.shouldShowUnlockPrompt(null))
        assertFalse(AutoAuthPolicy.shouldShowUnlockPrompt(true))
    }

    @Test
    fun challengeResubmitIsThrottled() {
        assertFalse(
            AutoAuthPolicy.shouldResubmitChallenge(1_000L, 0L, connected = true, gattReady = false, hasSavedPassword = true)
        )
        assertTrue(
            AutoAuthPolicy.shouldResubmitChallenge(1_200L, 0L, connected = true, gattReady = true, hasSavedPassword = true)
        )
        assertFalse(
            AutoAuthPolicy.shouldResubmitChallenge(1_199L, 0L, connected = true, gattReady = true, hasSavedPassword = true)
        )
        assertFalse(
            AutoAuthPolicy.shouldResubmitChallenge(5_000L, 0L, connected = true, gattReady = true, hasSavedPassword = false)
        )
    }
}
