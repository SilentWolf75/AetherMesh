package com.example.aethermesh.data

object ChatContext {
    fun authenticatedLabel(senderId: Long, recipientId: Long, channel: String): String {
        if (recipientId == 0xFFFFFFFFL) return "CHANNEL_${channel.take(31)}"
        val first = minOf(senderId, recipientId)
        val second = maxOf(senderId, recipientId)
        return "DM_${first.toString(16)}_${second.toString(16)}"
    }
}
