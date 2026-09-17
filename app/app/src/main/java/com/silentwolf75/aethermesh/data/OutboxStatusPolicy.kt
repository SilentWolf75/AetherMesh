package com.silentwolf75.aethermesh.data

data class OutboxSummary(
    val pendingCount: Int,
    val queuedCount: Int,
    val failedCount: Int,
    val hasUnsent: Boolean,
    val textEn: String?,
    val textEs: String?
) {
    fun label(spanish: Boolean): String? = if (spanish) textEs else textEn
}

/**
 * Evaluates pending outbox state and delivery status for display in chat views.
 */
object OutboxStatusPolicy {

    fun summarize(
        messages: List<ChatMessage>,
        localNodeId: Long,
        isConnected: Boolean,
        isAuthenticated: Boolean
    ): OutboxSummary {
        var pending = 0
        var queued = 0
        var failed = 0

        for (msg in messages) {
            if (localNodeId != 0L && !MeshNodeId.same(msg.senderId, localNodeId)) continue
            when (msg.status) {
                DeliveryStatusPolicy.PENDING -> pending++
                DeliveryStatusPolicy.QUEUED -> queued++
                DeliveryStatusPolicy.FAILED, DeliveryStatusPolicy.EXPIRED -> failed++
            }
        }

        val hasUnsent = pending > 0 || queued > 0 || failed > 0

        if (!isConnected || !isAuthenticated) {
            val en = when {
                pending > 0 -> "$pending message(s) will send once reconnected"
                hasUnsent -> "${pending + queued + failed} message(s) unsent • Reconnect to retry"
                else -> null
            }
            val es = when {
                pending > 0 -> "$pending mensaje(s) se enviará(n) al reconectar"
                hasUnsent -> "${pending + queued + failed} mensaje(s) no enviados • Reconecta para reintentar"
                else -> null
            }
            return OutboxSummary(pending, queued, failed, hasUnsent, en, es)
        }

        if (queued > 0) {
            val en = "$queued message(s) queued in mesh store-and-forward"
            val es = "$queued mensaje(s) en cola de reenvío de la malla"
            return OutboxSummary(pending, queued, failed, true, en, es)
        }

        if (pending > 0) {
            val en = "$pending message(s) sending • waiting for ACK..."
            val es = "$pending mensaje(s) enviando • esperando confirmación..."
            return OutboxSummary(pending, queued, failed, true, en, es)
        }

        if (failed > 0) {
            val en = "$failed message(s) failed delivery • tap bubble to retry"
            val es = "$failed mensaje(s) no entregados • toca la burbuja para reintentar"
            return OutboxSummary(pending, queued, failed, true, en, es)
        }

        return OutboxSummary(0, 0, 0, false, null, null)
    }
}
