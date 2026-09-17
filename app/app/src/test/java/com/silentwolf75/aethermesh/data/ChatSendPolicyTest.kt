package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.silentwolf75.aethermesh.proto.MeshPacket

class ChatSendPolicyTest {
    @Test
    fun channelAndDmKeysMatchChatIdentifier() {
        val longName = "TrailCrewNameThatIsWayTooLongForTheWire"
        assertEquals(
            ChatSendPolicy.chatIdentifier(ChatSendPolicy.BROADCAST, longName),
            ChatSendPolicy.channelKey(longName)
        )
        assertEquals("CHANNEL_General", ChatSendPolicy.channelKey("General"))
        assertEquals("DM_34", ChatSendPolicy.dmKey(0x22L))
        assertEquals(ChatSendPolicy.channelKey("General"), ChatThreadPrefs.channelKey("General"))
        assertEquals(ChatSendPolicy.dmKey(99L), ChatThreadPrefs.dmKey(99L))
    }

    @Test
    fun channelPlanUsesBroadcastIdentityAndSentStatus() {
        val plan = ChatSendPolicy.plan(
            localNodeId = 0x11L,
            recipientId = ChatSendPolicy.BROADCAST,
            content = "hello",
            channel = "TrailCrewNameThatIsWayTooLongForTheWire",
            hasPasscode = false,
            existingWasEncrypted = false
        )
        assertNotNull(plan)
        assertTrue(plan!!.isChannelSend)
        assertEquals(31, plan.boundedChannel.length)
        assertEquals("CHANNEL_${plan.boundedChannel}", plan.chatIdentifier)
        assertEquals("SENT", plan.localStatus)
        assertEquals(plan.boundedChannel, plan.persistChannel)
        assertFalse(plan.isEncrypted)
    }

    @Test
    fun dmPlanWantsPendingAndEmptyChannel() {
        val plan = ChatSendPolicy.plan(
            localNodeId = 0x11L,
            recipientId = 0x22L,
            content = "dm",
            channel = "ignored",
            hasPasscode = true,
            existingWasEncrypted = true
        )!!
        assertFalse(plan.isChannelSend)
        assertEquals("DM_34", plan.chatIdentifier)
        assertEquals("PENDING", plan.localStatus)
        assertEquals("", plan.persistChannel)
        assertTrue(plan.isEncrypted)
        assertEquals(
            ChatContext.authenticatedLabel(0x11L, 0x22L, plan.boundedChannel),
            plan.cryptoContext
        )
    }

    @Test
    fun retryWithoutKeyIsRefused() {
        assertNull(
            ChatSendPolicy.plan(1L, 2L, "x", "", hasPasscode = false, existingWasEncrypted = true)
        )
    }

    @Test
    fun encryptedContentIsCappedShorterThanPlain() {
        val long = "a".repeat(200)
        assertEquals(127, ChatSendPolicy.boundContent(long, encrypted = false).length)
        assertEquals(76, ChatSendPolicy.boundContent(long, encrypted = true).length)
    }

    @Test
    fun everyPacketAsksToBeAcknowledged() {
        // Receipts are no longer a preference: a channel message nobody confirms
        // is indistinguishable from one that reached no one. Hearers bound the
        // airtime themselves once a neighborhood outgrows the ACK slot grid.
        val channel = ChatSendPolicy.buildPacket(0x11L, ChatSendPolicy.BROADCAST, 9, "hi", "General", false)
        assertEquals(ChatSendPolicy.HOP_LIMIT, channel.hopLimit)
        assertTrue(channel.wantAck)
        assertEquals("General", channel.text.channel)
        val dm = ChatSendPolicy.buildPacket(0x11L, 0x22L, 3, "hi", "General", true)
        assertTrue(dm.wantAck)
        assertEquals("", dm.text.channel)
        assertTrue(dm.text.isEncrypted)
        assertEquals(MeshPacket.PayloadCase.TEXT, dm.payloadCase)
    }

    @Test
    fun retryOnlyDirectFailedFromLocal() {
        assertTrue(ChatSendPolicy.canRetry(0x22L, "", 0x11L, 0x11L, false, "FAILED"))
        assertFalse(ChatSendPolicy.canRetry(ChatSendPolicy.BROADCAST, "", 0x11L, 0x11L, false, "FAILED"))
        assertFalse(ChatSendPolicy.canRetry(0x22L, "General", 0x11L, 0x11L, false, "FAILED"))
        assertFalse(ChatSendPolicy.canRetry(0x22L, "", 0x99L, 0x11L, false, "FAILED"))
        assertFalse(ChatSendPolicy.canRetry(0x22L, "", 0x11L, 0x11L, true, "FAILED"))
        assertFalse(ChatSendPolicy.canRetry(0x22L, "", 0x11L, 0x11L, false, "SENT"))
    }
}
