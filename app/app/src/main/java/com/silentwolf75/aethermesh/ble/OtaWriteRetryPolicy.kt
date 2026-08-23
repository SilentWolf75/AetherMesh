package com.silentwolf75.aethermesh.ble

/**
 * Backoff between OTA chunk-write retries.
 *
 * The node erases a 4 KB flash sector every 4 KB written, and an ESP32 flash
 * erase runs with the instruction cache disabled, so the BLE stack stops being
 * serviced for as long as the erase takes. A stalled write is therefore normal
 * mid-transfer and recoverable — provided the app waits long enough.
 *
 * The original schedule retried every 50 ms for 8 attempts, spending its whole
 * budget in 400 ms against stalls measured at ~3 s. A later schedule spanned
 * ~11.7 s and still lost a transfer that had run cleanly for four minutes
 * before stalling longer than that.
 *
 * A full 1 MB image takes roughly 17 minutes over this link, and the node keeps
 * its received offset so a stalled window can simply resume. Abandoning minutes
 * of progress to save under a minute of patience is the wrong trade, so the
 * budget now spans ~56 s.
 */
internal object OtaWriteRetryPolicy {
    const val MAX_ATTEMPTS = 14
    private const val BASE_DELAY_MS = 250L
    private const val MAX_DELAY_MS = 5_000L

    /** Delay before retry [attempt] (1-based). Doubles, then holds at the cap. */
    fun delayMs(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 30)
        val scaled = if (exponent >= 5) MAX_DELAY_MS else BASE_DELAY_MS shl exponent
        return scaled.coerceAtMost(MAX_DELAY_MS)
    }

    /** Total time spent retrying before giving up. */
    fun totalBudgetMs(): Long = (1..MAX_ATTEMPTS).sumOf { delayMs(it) }
}
