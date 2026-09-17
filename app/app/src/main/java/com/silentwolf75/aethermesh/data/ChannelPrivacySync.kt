package com.silentwolf75.aethermesh.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Transport success is not confirmation: only the radio's persisted report is. */
object ChannelPrivacySync {
    suspend fun apply(
        desired: ChannelPrivacy,
        sessionCurrent: () -> Boolean,
        otaActive: () -> Boolean,
        reported: () -> ChannelPrivacy?,
        send: suspend () -> Unit,
        status: (ChannelPrivacyStatus) -> Unit,
        waitForReport: suspend () -> Unit = { delay(3000) }
    ) {
        repeat(3) {
            currentCoroutineContext().ensureActive()
            if (!sessionCurrent()) return
            if (otaActive()) {
                status(ChannelPrivacyStatus.OTA_BUSY)
                return
            }
            if (reported() == desired) {
                status(ChannelPrivacyStatus.CONFIRMED)
                return
            }
            status(ChannelPrivacyStatus.APPLYING)
            send()
            waitForReport()
            // Recheck after suspension: a report from another connection cannot confirm this one.
            currentCoroutineContext().ensureActive()
            if (!sessionCurrent()) return
            if (reported() == desired) {
                status(ChannelPrivacyStatus.CONFIRMED)
                return
            }
        }
        status(ChannelPrivacyStatus.FAILED)
    }
}
