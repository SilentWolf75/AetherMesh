package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.TextMessage

data class ChatSendPlan(
    val boundedChannel: String,
    val boundedContent: String,
    val chatIdentifier: String,
    val cryptoContext: String,
    val isChannelSend: Boolean,
    val persistChannel: String,
    val localStatus: String,
    val isEncrypted: Boolean
)

/**
 * Outbound chat packet rules. BLE write, key lookup, encrypt, and DB insert
 * stay in [AetherMeshRepository]. Never fall back to plaintext when a key is
 * expected.
 */
object ChatSendPolicy {
    const val BROADCAST = 0xFFFFFFFFL
    const val MAX_TEXT = 127
    const val MAX_ENCRYPTED = 76
    const val MAX_CHANNEL = 31
    const val HOP_LIMIT = 4
    val RETRY_STATUSES = setOf("FAILED", "QUEUED", "EXPIRED")

    fun isChannelSend(recipientId: Long): Boolean = recipientId == BROADCAST

    fun boundChannel(channel: String): String = channel.take(MAX_CHANNEL)

    fun boundContent(content: String, encrypted: Boolean): String =
        content.takeUtf8Bytes(if (encrypted) MAX_ENCRYPTED else MAX_TEXT)

    /** Keyring / mute / crypto label for a channel (name bounded to [MAX_CHANNEL]). */
    fun channelKey(channel: String): String = "CHANNEL_${boundChannel(channel)}"

    fun dmKey(peerId: Long): String = "DM_$peerId"

    fun chatIdentifier(recipientId: Long, channel: String): String =
        if (isChannelSend(recipientId)) channelKey(channel) else dmKey(recipientId)

    fun persistChannel(recipientId: Long, boundedChannel: String): String =
        if (isChannelSend(recipientId)) boundedChannel else ""

    fun localStatus(isChannel: Boolean): String = if (isChannel) "SENT" else "PENDING"

    /**
     * Every message asks to be acknowledged, channel sends included. A channel
     * message nobody confirms is indistinguishable from one that reached no
     * one, so this is not a user preference. The airtime is bounded on the
     * radio side instead: hearers stay quiet once a neighborhood outgrows the
     * ACK slot grid, and the sender still gets credit from nodes relaying it.
     */
    fun wantAck(): Boolean = true

    /** Window a channel send waits for receipts before the UI stops implying one is coming. */
    const val CHANNEL_RECEIPT_WINDOW_MS = 90_000L

    /**
     * True while a receipt could still plausibly arrive. At SF12 a message, a
     * relay copy and slotted receipts are each seconds on air, so the window is
     * generous; past it the message is reported as on air rather than pending.
     */
    fun channelReceiptPending(elapsedMs: Long): Boolean =
        elapsedMs in 0..CHANNEL_RECEIPT_WINDOW_MS

    fun missingKeyForRetry(wasEncrypted: Boolean, hasKey: Boolean): Boolean =
        wasEncrypted && !hasKey

    fun plan(
        localNodeId: Long,
        recipientId: Long,
        content: String,
        channel: String,
        hasPasscode: Boolean,
        existingWasEncrypted: Boolean
    ): ChatSendPlan? {
        if (missingKeyForRetry(existingWasEncrypted, hasPasscode)) return null
        val boundedChannel = boundChannel(channel)
        val isChannel = isChannelSend(recipientId)
        return ChatSendPlan(
            boundedChannel = boundedChannel,
            boundedContent = boundContent(content, hasPasscode),
            chatIdentifier = chatIdentifier(recipientId, boundedChannel),
            cryptoContext = ChatContext.authenticatedLabel(localNodeId, recipientId, boundedChannel),
            isChannelSend = isChannel,
            persistChannel = persistChannel(recipientId, boundedChannel),
            localStatus = localStatus(isChannel),
            isEncrypted = hasPasscode
        )
    }

    fun buildPacket(
        localNodeId: Long,
        recipientId: Long,
        packetId: Int,
        contentToSend: String,
        boundedChannel: String,
        isEncrypted: Boolean
    ): MeshPacket {
        val text = TextMessage.newBuilder()
            .setContent(contentToSend)
            .setChannel(persistChannel(recipientId, boundedChannel))
            .setIsEncrypted(isEncrypted)
            .build()
        return MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(recipientId.toInt())
            .setPacketId(packetId)
            .setHopLimit(HOP_LIMIT)
            .setWantAck(wantAck())
            .setPrevHopId(localNodeId.toInt())
            .setText(text)
            .build()
    }

    fun canRetry(
        recipientId: Long,
        channel: String,
        senderId: Long,
        localNodeId: Long,
        rangeTestActive: Boolean,
        status: String
    ): Boolean =
        !isChannelSend(recipientId) &&
            channel.isEmpty() &&
            MeshNodeId.same(senderId, localNodeId) &&
            !rangeTestActive &&
            status in RETRY_STATUSES
}
