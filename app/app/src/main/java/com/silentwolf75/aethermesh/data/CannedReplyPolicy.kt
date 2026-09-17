package com.silentwolf75.aethermesh.data

/**
 * Short emergency canned replies for the chat composer (Meshtastic BaseUI
 * lesson). Insert into the draft — never auto-send. Keep every body within
 * [ChatSendPolicy.MAX_ENCRYPTED] UTF-8 bytes so encrypted DMs can use them.
 */
data class CannedReply(
    val id: String,
    val labelEn: String,
    val labelEs: String,
    val bodyEn: String,
    val bodyEs: String
)

object CannedReplyPolicy {
    val REPLIES: List<CannedReply> = listOf(
        CannedReply(
            id = "ok",
            labelEn = "OK",
            labelEs = "OK",
            bodyEn = "OK",
            bodyEs = "OK"
        ),
        CannedReply(
            id = "need_help",
            labelEn = "Need help",
            labelEs = "Ayuda",
            bodyEn = "Need help",
            bodyEs = "Necesito ayuda"
        ),
        CannedReply(
            id = "on_my_way",
            labelEn = "On my way",
            labelEs = "En camino",
            bodyEn = "On my way",
            bodyEs = "Voy en camino"
        ),
        CannedReply(
            id = "standing_by",
            labelEn = "Standing by",
            labelEs = "En espera",
            bodyEn = "Standing by",
            bodyEs = "En espera"
        ),
        CannedReply(
            id = "low_battery",
            labelEn = "Low battery",
            labelEs = "Batería baja",
            bodyEn = "Low battery",
            bodyEs = "Batería baja"
        ),
        CannedReply(
            id = "all_clear",
            labelEn = "All clear",
            labelEs = "Todo bien",
            bodyEn = "All clear",
            bodyEs = "Todo en orden"
        )
    )

    fun label(reply: CannedReply, spanish: Boolean): String =
        if (spanish) reply.labelEs else reply.labelEn

    fun body(reply: CannedReply, spanish: Boolean): String =
        if (spanish) reply.bodyEs else reply.bodyEn

    /** Replace the draft when empty; otherwise leave the typed text alone. */
    fun applyToDraft(currentDraft: String, reply: CannedReply, spanish: Boolean): String {
        if (currentDraft.isNotBlank()) return currentDraft
        return body(reply, spanish)
    }

    fun fitsEncryptedBudget(reply: CannedReply): Boolean {
        val en = body(reply, spanish = false).toByteArray(Charsets.UTF_8).size
        val es = body(reply, spanish = true).toByteArray(Charsets.UTF_8).size
        return en <= ChatSendPolicy.MAX_ENCRYPTED && es <= ChatSendPolicy.MAX_ENCRYPTED
    }

    fun allFitEncryptedBudget(): Boolean = REPLIES.all { fitsEncryptedBudget(it) }
}
