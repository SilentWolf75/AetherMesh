package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.silentwolf75.aethermesh.proto.DeliveryStatus

class DeliveryStatusPolicyTest {
    @Test
    fun storedBecomesQueuedAndRetryingBecomesPending() {
        assertEquals(
            DeliveryStatusAction.SetStatus(DeliveryStatusPolicy.QUEUED),
            DeliveryStatusPolicy.decide(DeliveryStatus.State.STORED, 0L)
        )
        assertEquals(
            DeliveryStatusAction.SetStatus(DeliveryStatusPolicy.QUEUED),
            DeliveryStatusPolicy.decide(DeliveryStatus.State.QUEUED, 0L)
        )
        assertEquals(
            DeliveryStatusAction.SetStatus(DeliveryStatusPolicy.PENDING),
            DeliveryStatusPolicy.decide(DeliveryStatus.State.RETRYING, 0L)
        )
        assertEquals(
            DeliveryStatusAction.SetStatus(DeliveryStatusPolicy.FAILED),
            DeliveryStatusPolicy.decide(DeliveryStatus.State.FAILED, 0L)
        )
        assertEquals(
            DeliveryStatusAction.SetStatus(DeliveryStatusPolicy.EXPIRED),
            DeliveryStatusPolicy.decide(DeliveryStatus.State.EXPIRED, 0L)
        )
    }

    @Test
    fun deliveredNeverRewritesChannelBubbles() {
        assertEquals(
            DeliveryStatusAction.DeliveredIfDirect,
            DeliveryStatusPolicy.decide(DeliveryStatus.State.DELIVERED, 0x11L)
        )
    }

    @Test
    fun heardRecordsHearerOnlyWhenFromNodeIsReal() {
        val named = DeliveryStatusPolicy.decide(DeliveryStatus.State.HEARD, 0x22L)
            as DeliveryStatusAction.Heard
        assertTrue(named.recordHearer)
        assertEquals(0x22L, named.fromNodeId)

        val anonymous = DeliveryStatusPolicy.decide(DeliveryStatus.State.HEARD, 0L)
            as DeliveryStatusAction.Heard
        assertFalse(anonymous.recordHearer)
    }

    @Test
    fun unknownAndUnrecognizedAreIgnored() {
        assertEquals(
            DeliveryStatusAction.Ignore,
            DeliveryStatusPolicy.decide(DeliveryStatus.State.UNKNOWN, 0L)
        )
        assertEquals(
            DeliveryStatusAction.Ignore,
            DeliveryStatusPolicy.decide(DeliveryStatus.State.UNRECOGNIZED, 0L)
        )
    }

    @Test
    fun fromNodeIdKeepsUnsigned32() {
        assertEquals(0xFFFFFFFFL, DeliveryStatusPolicy.fromNodeId(-1))
        assertEquals(0L, DeliveryStatusPolicy.fromNodeId(0))
    }

    @Test
    fun meshAckMarksDirectDeliveredAndChannelHeard() {
        val dm = DeliveryStatusPolicy.onMeshAck(isChannelMessage = false, fromNodeId = 0xABCDEFL)
        assertEquals(DeliveryStatusAction.DeliveredIfDirect, dm)

        val channel = DeliveryStatusPolicy.onMeshAck(isChannelMessage = true, fromNodeId = 0x11L)
            as DeliveryStatusAction.Heard
        assertTrue(channel.recordHearer)
        assertEquals(0x11L, channel.fromNodeId)
    }
}
