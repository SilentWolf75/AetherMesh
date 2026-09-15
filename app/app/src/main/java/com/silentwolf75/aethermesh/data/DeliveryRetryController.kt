package com.silentwolf75.aethermesh.data

data class OutboundAttempt(val messageId: Long, val payload: ByteArray)

interface DeliveryRetryStore {
    fun candidates(peerId: Long, senderId: Long, now: Long): List<OutboundAttempt>
    fun claim(messageId: Long, senderId: Long, now: Long, manual: Boolean = false): ByteArray?
    fun handoffFailed(messageId: Long)
}

/** The same controller drives manual retries and telemetry-triggered retries. */
class DeliveryRetryController(
    private val store: DeliveryRetryStore,
    private val ready: () -> Boolean,
    private val senderId: () -> Long,
    private val send: (ByteArray) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val COOLDOWN_MS = 300_000L
        const val LIFETIME_MS = 1_800_000L
        const val MAX_ATTEMPTS = 3
    }

    fun candidates(peerId: Long): List<OutboundAttempt> =
        if (ready()) store.candidates(peerId, senderId(), clock()) else emptyList()

    fun retry(messageId: Long, manual: Boolean = false): Boolean {
        if (!ready()) return false
        val payload = store.claim(messageId, senderId(), clock(), manual) ?: return false
        val accepted = try { send(payload) } catch (e: Exception) { false }
        if (!accepted) store.handoffFailed(messageId)
        return accepted
    }
}
