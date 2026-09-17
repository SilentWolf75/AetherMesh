package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.DeliveryStatus

sealed class DeliveryStatusAction {
    object Ignore : DeliveryStatusAction()
    data class Heard(val fromNodeId: Long, val recordHearer: Boolean) : DeliveryStatusAction()
    /** DM ACK only — channel rows stay on HEARD, not DELIVERED. */
    object DeliveredIfDirect : DeliveryStatusAction()
    data class SetStatus(val status: String) : DeliveryStatusAction()
}

/**
 * Firmware DeliveryStatus → app bubble status. BLE and SQLite stay in
 * [AetherMeshRepository]. Firmware STORED is shown as QUEUED.
 */
object DeliveryStatusPolicy {
    const val HEARD = "HEARD"
    const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"
    const val PENDING = "PENDING"
    const val QUEUED = "QUEUED"
    const val EXPIRED = "EXPIRED"

    fun fromNodeId(raw: Int): Long = raw.toLong() and 0xFFFFFFFFL

    /**
     * Mesh ACK (payload ACK, not DeliveryStatus). Channel bubbles stay on
     * HEARD; DMs become DELIVERED. SQLite writes stay in the repository.
     */
    fun onMeshAck(isChannelMessage: Boolean, fromNodeId: Long): DeliveryStatusAction =
        if (isChannelMessage) DeliveryStatusAction.Heard(
            fromNodeId = fromNodeId,
            recordHearer = true
        ) else DeliveryStatusAction.DeliveredIfDirect

    fun decide(state: DeliveryStatus.State, fromNodeId: Long): DeliveryStatusAction =
        when (state) {
            DeliveryStatus.State.HEARD -> DeliveryStatusAction.Heard(
                fromNodeId = fromNodeId,
                recordHearer = fromNodeId != 0L
            )
            DeliveryStatus.State.DELIVERED -> DeliveryStatusAction.DeliveredIfDirect
            DeliveryStatus.State.FAILED -> DeliveryStatusAction.SetStatus(FAILED)
            DeliveryStatus.State.RETRYING -> DeliveryStatusAction.SetStatus(PENDING)
            DeliveryStatus.State.QUEUED, DeliveryStatus.State.STORED ->
                DeliveryStatusAction.SetStatus(QUEUED)
            DeliveryStatus.State.EXPIRED -> DeliveryStatusAction.SetStatus(EXPIRED)
            DeliveryStatus.State.UNKNOWN, DeliveryStatus.State.UNRECOGNIZED ->
                DeliveryStatusAction.Ignore
        }
}
