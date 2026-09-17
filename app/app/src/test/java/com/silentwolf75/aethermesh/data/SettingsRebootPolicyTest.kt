package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRebootPolicyTest {
    @Test
    fun closeMatchesAutoAuthPostSettingsWait() {
        assertEquals(AutoAuthPolicy.WAIT_POST_SETTINGS_MS, SettingsRebootPolicy.CLOSE_AFTER_MS)
    }

    @Test
    fun reconnectIsAfterClose() {
        assertTrue(SettingsRebootPolicy.RECONNECT_AFTER_MS > SettingsRebootPolicy.CLOSE_AFTER_MS)
        assertEquals(5_000L, SettingsRebootPolicy.RECONNECT_AFTER_MS)
    }

    @Test
    fun queuedRetryStaggerIsPositive() {
        assertEquals(2_000L, SettingsRebootPolicy.QUEUED_RETRY_STAGGER_MS)
    }
}
