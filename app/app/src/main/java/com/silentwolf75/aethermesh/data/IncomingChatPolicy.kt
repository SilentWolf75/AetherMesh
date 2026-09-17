package com.silentwolf75.aethermesh.data

data class IncomingChatPlan(
    val chatIdentifier: String,
    val cryptoContext: String,
    val channelForRow: String,
    val isBroadcast: Boolean
)

data class IncomingChatContent(
    val content: String,
    /** Undecryptable ciphertext to retry when a key is saved; null once readable. */
    val pendingCipherText: String?
)

sealed class IncomingTextKind {
    object IgnorePing : IncomingTextKind()
    object IgnoreUnaddressed : IncomingTextKind()
    data class Chat(val plan: IncomingChatPlan) : IncomingTextKind()
}

/**
 * Inbound TEXT routing. Catch-up unicasts keep the channel name but restamp
 * recipient — decrypt with the original channel AAD, not a DM pair.
 * BLE, key lookup, and DB insert stay in [AetherMeshRepository].
 */
object IncomingChatPolicy {
    const val DEFAULT_CHANNEL = "General"
    const val ERROR_NO_KEY = "[Encrypted Message - No Key Configured]"

    fun isControlPing(content: String): Boolean = content.startsWith("PING_")

    fun isChannelFrame(recipientId: Long, channel: String): Boolean =
        recipientId == ChatSendPolicy.BROADCAST || channel.isNotEmpty()

    fun isDirectMessageForUs(recipientId: Long, channel: String, localNodeId: Long): Boolean =
        isChannelFrame(recipientId, channel) || localNodeId == 0L || MeshNodeId.same(recipientId, localNodeId)

    fun classify(
        content: String,
        senderId: Long,
        recipientId: Long,
        channel: String,
        localNodeId: Long = 0L
    ): IncomingTextKind {
        if (isControlPing(content)) return IncomingTextKind.IgnorePing
        if (!isDirectMessageForUs(recipientId, channel, localNodeId)) {
            return IncomingTextKind.IgnoreUnaddressed
        }
        return IncomingTextKind.Chat(plan(senderId, recipientId, channel))
    }

    fun plan(senderId: Long, recipientId: Long, channel: String): IncomingChatPlan {
        val named = channel.ifEmpty { DEFAULT_CHANNEL }
        val channelFrame = isChannelFrame(recipientId, channel)
        val chatIdentifier = if (channelFrame) {
            ChatSendPolicy.channelKey(named)
        } else {
            ChatSendPolicy.dmKey(senderId)
        }
        val cryptoRecipient = if (channelFrame) ChatSendPolicy.BROADCAST else recipientId
        val cryptoContext = ChatContext.authenticatedLabel(senderId, cryptoRecipient, named)
        val channelForRow = when {
            recipientId == ChatSendPolicy.BROADCAST -> named
            channel.isNotEmpty() -> channel
            else -> ""
        }
        return IncomingChatPlan(
            chatIdentifier = chatIdentifier,
            cryptoContext = cryptoContext,
            channelForRow = channelForRow,
            isBroadcast = channelForRow.isNotEmpty() || recipientId == ChatSendPolicy.BROADCAST
        )
    }

    /**
     * Row content for an inbound frame. When an encrypted frame cannot be read
     * (no key yet, wrong key), the ciphertext is kept alongside the placeholder
     * so saving the right key later can recover it instead of losing it.
     */
    fun resolveContent(
        encrypted: Boolean,
        hasKey: Boolean,
        raw: String,
        decrypted: String
    ): IncomingChatContent = when {
        !encrypted -> IncomingChatContent(raw, pendingCipherText = null)
        !hasKey -> IncomingChatContent(ERROR_NO_KEY, pendingCipherText = raw)
        isDecryptFailure(decrypted) -> IncomingChatContent(decrypted, pendingCipherText = raw)
        else -> IncomingChatContent(decrypted, pendingCipherText = null)
    }

    fun isDecryptFailure(content: String): Boolean =
        content == ChatCrypto.ERROR_INVALID ||
            content == ChatCrypto.ERROR_BAD_CONTEXT ||
            content == ChatCrypto.ERROR_BAD_KEY

    /** Spanish UI mapping for stored English crypto/placeholder sentinels. */
    fun localizePlaceholder(content: String, spanish: Boolean): String {
        if (!spanish) return content
        return when (content) {
            ERROR_NO_KEY -> "[Mensaje cifrado — sin clave configurada]"
            ChatCrypto.ERROR_INVALID -> "[Error de descifrado — mensaje inválido]"
            ChatCrypto.ERROR_BAD_CONTEXT -> "[Error de descifrado — clave o contexto incorrecto]"
            ChatCrypto.ERROR_BAD_KEY -> "[Error de descifrado — clave incorrecta]"
            else -> content
        }
    }
}
