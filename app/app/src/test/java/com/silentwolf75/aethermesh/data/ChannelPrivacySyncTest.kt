package com.silentwolf75.aethermesh.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ChannelPrivacySyncTest {
    private val desired = ChannelPrivacy(true, 1609)

    private class Radio {
        var current = true
        var ota = false
        var report: ChannelPrivacy? = null
        var sends = 0
        val states = mutableListOf<ChannelPrivacyStatus>()
        suspend fun sync(desired: ChannelPrivacy, wait: suspend () -> Unit = {}) =
            ChannelPrivacySync.apply(desired, { current }, { ota }, { report },
                { sends++ }, { states.add(it) }, wait)
    }

    @Test fun lostReportsExhaustRetryBudgetWithoutClaimingSuccess() = runBlocking {
        val radio = Radio()
        radio.sync(desired)
        assertEquals(3, radio.sends)
        assertEquals(ChannelPrivacyStatus.FAILED, radio.states.last())
        assertFalse(radio.states.contains(ChannelPrivacyStatus.CONFIRMED))
    }

    @Test fun lostFirstConfirmationRecoversOnRetry() = runBlocking {
        val radio = Radio()
        radio.sync(desired) { if (radio.sends == 2) radio.report = desired }
        assertEquals(2, radio.sends)
        assertEquals(ChannelPrivacyStatus.CONFIRMED, radio.states.last())
    }

    @Test fun failedPersistenceReportDoesNotConfirm() = runBlocking {
        val radio = Radio()
        radio.sync(desired) { radio.report = ChannelPrivacy() }
        assertEquals(3, radio.sends)
        assertEquals(ChannelPrivacyStatus.FAILED, radio.states.last())
    }

    @Test fun disconnectOrDeviceSwitchDuringWaitCannotConfirmOldSession() = runBlocking {
        val radio = Radio()
        radio.sync(desired) { radio.current = false; radio.report = desired }
        assertEquals(1, radio.sends)
        assertFalse(radio.states.contains(ChannelPrivacyStatus.CONFIRMED))
    }

    @Test fun reconnectUsesPersistedReportAndReappliesIfRebootLostIt() = runBlocking {
        val radio = Radio()
        radio.report = desired
        radio.sync(desired)
        assertEquals(0, radio.sends)
        assertEquals(ChannelPrivacyStatus.CONFIRMED, radio.states.last())
        radio.report = ChannelPrivacy()
        radio.sync(desired) { radio.report = desired }
        assertEquals(1, radio.sends)
        assertEquals(ChannelPrivacyStatus.CONFIRMED, radio.states.last())
    }

    @Test fun cancellationWhileWaitingCannotPublishConfirmation() = runBlocking {
        val radio = Radio()
        val waiting = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val job = launch {
            radio.sync(desired) { waiting.complete(Unit); resume.await() }
        }
        waiting.await()
        job.cancelAndJoin()
        radio.report = desired
        resume.complete(Unit)
        assertEquals(1, radio.sends)
        assertFalse(radio.states.contains(ChannelPrivacyStatus.CONFIRMED))
    }

    @Test fun updateInProgressPausesRetries() = runBlocking {
        val radio = Radio()
        radio.sync(desired) { radio.ota = true }
        assertEquals(1, radio.sends)
        assertEquals(ChannelPrivacyStatus.OTA_BUSY, radio.states.last())
    }
}
