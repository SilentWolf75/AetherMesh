package com.silentwolf75.aethermesh.data

/**
 * How long the phone waits for a node reply (DM ACK or remote-config RPC).
 * Scaled with LoRa SF to match firmware [ackRetryBaseMs] / [ACK_MAX_RETRIES]
 * so SF11/12 meshes are not marked FAILED while the node is still RETRYING.
 * GPS poll and DFU watchdog keep their own fixed budgets.
 */
object MeshReplyPolicy {
    /** Floor for SF7–10 (historical chat/remote-config wait). */
    const val TIMEOUT_MS = 45_000L
    const val ACK_POLL_MS = 5_000L

    /** Matches firmware `ACK_MAX_RETRIES` (fast retries before STORED). */
    const val ACK_FAST_RETRIES = 3

    /** Firmware `meshmath::ackRetryBaseMs` mirror. */
    fun ackRetryBaseMs(sf: Int): Long = when {
        sf >= 12 -> 6_000L
        sf >= 11 -> 4_500L
        sf >= 10 -> 3_500L
        sf >= 9 -> 2_800L
        else -> 2_200L
    }

    /**
     * Phone wait for a DM ACK or remote-config reply at [sf].
     * Fast-retry window uses multipliers 1+2+4 (=7× base) like firmware
     * `ackRetryDelayMs`, plus margin for airtime / route penalty / jitter.
     * Never shorter than [TIMEOUT_MS].
     */
    fun timeoutMs(sf: Int = NodeSettingsPrefs.DEFAULT_SF): Long {
        val s = sf.coerceIn(7, 12)
        val fastWindow = ackRetryBaseMs(s) * 7L
        val margin = when {
            s >= 12 -> 28_000L
            s >= 11 -> 22_000L
            else -> 15_000L
        }
        return maxOf(TIMEOUT_MS, fastWindow + margin)
    }

    fun ackCutoff(nowMs: Long, sf: Int = NodeSettingsPrefs.DEFAULT_SF): Long =
        nowMs - timeoutMs(sf)

    fun remoteTimedOut(spanish: Boolean): String =
        if (spanish) "Tiempo agotado — sin respuesta del nodo."
        else "Timed out — no response from node."
}
