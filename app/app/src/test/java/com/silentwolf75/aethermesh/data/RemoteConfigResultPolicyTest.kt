package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.ConfigResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigResultPolicyTest {
    @Test
    fun matchesPendingRequiresExactNonZeroIds() {
        assertFalse(RemoteConfigResultPolicy.matchesPendingRequest(0, 5))
        assertFalse(RemoteConfigResultPolicy.matchesPendingRequest(5, 0))
        assertFalse(RemoteConfigResultPolicy.matchesPendingRequest(0, 0))
        assertTrue(RemoteConfigResultPolicy.matchesPendingRequest(7, 7))
        assertFalse(RemoteConfigResultPolicy.matchesPendingRequest(7, 8))
    }

    @Test
    fun acceptLiveReportRequiresAwaitingAndExactNode() {
        assertFalse(RemoteConfigResultPolicy.acceptLiveReport(false, 10L, 10L))
        assertFalse(RemoteConfigResultPolicy.acceptLiveReport(true, 10L, 0L))
        assertFalse(RemoteConfigResultPolicy.acceptLiveReport(true, 11L, 10L))
        assertTrue(RemoteConfigResultPolicy.acceptLiveReport(true, 10L, 10L))
    }

    @Test
    fun decideIgnoresUnsolicitedWhenNotWaiting() {
        val action = RemoteConfigResultPolicy.decide(
            pendingPacketId = 0,
            requestPacketId = 42,
            status = ConfigResult.Status.APPLIED,
            message = "",
            spanish = false
        )
        assertEquals(RemoteConfigResultAction.IgnoreStale, action)
    }

    @Test
    fun persistOnlyOnAppliedStatuses() {
        assertTrue(RemoteConfigResultPolicy.shouldPersistBaseline(ConfigResult.Status.APPLIED))
        assertTrue(RemoteConfigResultPolicy.shouldPersistBaseline(ConfigResult.Status.APPLIED_REBOOTING))
        assertFalse(RemoteConfigResultPolicy.shouldPersistBaseline(ConfigResult.Status.AUTH_FAILED))
        assertFalse(RemoteConfigResultPolicy.shouldPersistBaseline(ConfigResult.Status.REPORT_OK))
    }

    @Test
    fun decideIgnoresStaleRequestIds() {
        val action = RemoteConfigResultPolicy.decide(
            pendingPacketId = 10,
            requestPacketId = 11,
            status = ConfigResult.Status.APPLIED,
            message = "",
            spanish = false
        )
        assertEquals(RemoteConfigResultAction.IgnoreStale, action)
    }

    @Test
    fun decideAppliedPersistsAndShowsFeedback() {
        val action = RemoteConfigResultPolicy.decide(
            pendingPacketId = 10,
            requestPacketId = 10,
            status = ConfigResult.Status.APPLIED_REBOOTING,
            message = "",
            spanish = false
        ) as RemoteConfigResultAction.Handle
        assertTrue(action.persistBaseline)
        assertTrue(action.showFeedback)
        assertEquals("Applied — node rebooting.", action.statusText)
    }

    @Test
    fun decideAuthFailedShowsFeedbackWithoutPersist() {
        val action = RemoteConfigResultPolicy.decide(
            pendingPacketId = 1,
            requestPacketId = 1,
            status = ConfigResult.Status.AUTH_FAILED,
            message = "",
            spanish = true
        ) as RemoteConfigResultAction.Handle
        assertFalse(action.persistBaseline)
        assertTrue(action.showFeedback)
        assertEquals("Autenticación fallida.", action.statusText)
    }

    @Test
    fun copyLocksEnAndEs() {
        assertEquals("Live settings loaded.", RemoteConfigResultPolicy.liveSettingsLoaded(false))
        assertEquals("Ajustes cargados del nodo.", RemoteConfigResultPolicy.liveSettingsLoaded(true))
        assertEquals(
            "Disconnected — request cancelled.",
            RemoteConfigResultPolicy.disconnectCancelled(false)
        )
        assertEquals(
            "Repeater role rejected (no BLE).",
            RemoteConfigResultPolicy.statusText(
                ConfigResult.Status.REJECTED_ROLE2, "", false
            )
        )
        assertEquals("Password required", RemoteConfigResultPolicy.passwordRequired(false))
        assertEquals("Sin cambios que aplicar.", RemoteConfigResultPolicy.noChanges(true))
        assertEquals(
            "Load live settings from the node first.",
            RemoteConfigResultPolicy.loadBaselineFirst(false)
        )
    }
}
