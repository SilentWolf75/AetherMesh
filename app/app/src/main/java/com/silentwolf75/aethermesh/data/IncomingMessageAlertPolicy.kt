package com.silentwolf75.aethermesh.data

/**
 * Incoming chat notification gates and copy. Android notify stays in
 * [AetherMeshRepository]. Mute still stores the message; only the system
 * notification is skipped.
 */
object IncomingMessageAlertPolicy {
    const val CHANNEL_ID = "aethermesh_messages"
    const val GROUP = "aethermesh_messages"

    fun shouldNotify(
        bgAlertsEnabled: Boolean,
        muted: Boolean,
        activityVisible: Boolean
    ): Boolean = bgAlertsEnabled && !muted && !activityVisible

    fun title(senderName: String, channel: String, isBroadcast: Boolean): String =
        if (isBroadcast) "$senderName @ $channel" else senderName

    fun fallbackName(senderId: Long, spanish: Boolean): String =
        BatteryAlertPolicy.fallbackName(senderId, spanish)

    fun channelName(spanish: Boolean): String =
        if (spanish) "Mensajes" else "Messages"

    fun channelDescription(spanish: Boolean): String =
        if (spanish) "Mensajes entrantes de la malla" else "Incoming mesh chat messages"

    fun summaryTitle(spanish: Boolean): String =
        if (spanish) "Mensajes de AetherMesh" else "AetherMesh messages"

    fun summaryText(spanish: Boolean): String =
        if (spanish) "Nuevos mensajes de la malla" else "New mesh messages"

    fun inboxSummary(spanish: Boolean): String =
        if (spanish) "Chat de la malla" else "Mesh chat"

    fun notifyId(chatIdentifier: String): Int = chatIdentifier.hashCode()

    fun summaryNotifyId(): Int = "aethermesh_messages_summary".hashCode()
}
