package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxStatusPolicyTest {

    private fun sampleMsg(senderId: Long, status: String): ChatMessage = ChatMessage(
        id = 1L,
        senderId = senderId,
        recipientId = 0x22L,
        content = "test",
        timestamp = 1000L,
        channel = "",
        status = status
    )

    @Test
    fun emptyMessagesProducesNoOutbox() {
        val summary = OutboxStatusPolicy.summarize(
            messages = emptyList(),
            localNodeId = 0x11L,
            isConnected = true,
            isAuthenticated = true
        )
        assertFalse(summary.hasUnsent)
        assertEquals(0, summary.pendingCount)
        assertNull(summary.label(false))
    }

    @Test
    fun pendingMessagesShowSendingWhenConnected() {
        val messages = listOf(
            sampleMsg(0x11L, DeliveryStatusPolicy.PENDING),
            sampleMsg(0x22L, DeliveryStatusPolicy.PENDING) // Inbound from someone else
        )
        val summary = OutboxStatusPolicy.summarize(
            messages = messages,
            localNodeId = 0x11L,
            isConnected = true,
            isAuthenticated = true
        )
        assertTrue(summary.hasUnsent)
        assertEquals(1, summary.pendingCount)
        assertEquals("1 message(s) sending • waiting for ACK...", summary.label(false))
        assertEquals("1 mensaje(s) enviando • esperando confirmación...", summary.label(true))
    }

    @Test
    fun queuedMessagesShowStoreAndForward() {
        val messages = listOf(
            sampleMsg(0x11L, DeliveryStatusPolicy.QUEUED)
        )
        val summary = OutboxStatusPolicy.summarize(
            messages = messages,
            localNodeId = 0x11L,
            isConnected = true,
            isAuthenticated = true
        )
        assertTrue(summary.hasUnsent)
        assertEquals(1, summary.queuedCount)
        assertTrue(summary.label(false)?.contains("store-and-forward") == true)
    }

    @Test
    fun disconnectedShowsReconnectNotice() {
        val messages = listOf(
            sampleMsg(0x11L, DeliveryStatusPolicy.PENDING)
        )
        val summary = OutboxStatusPolicy.summarize(
            messages = messages,
            localNodeId = 0x11L,
            isConnected = false,
            isAuthenticated = false
        )
        assertTrue(summary.hasUnsent)
        assertEquals("1 message(s) will send once reconnected", summary.label(false))
        assertEquals("1 mensaje(s) se enviará(n) al reconectar", summary.label(true))
    }

    @Test
    fun outboxMatches16BitAnd32BitSender() {
        // Message was sent when connectedNodeId was 0xAABB1234L, but current connectedNodeId is 0x1234L
        val messages = listOf(
            sampleMsg(0xAABB1234L, DeliveryStatusPolicy.PENDING)
        )
        val summary = OutboxStatusPolicy.summarize(
            messages = messages,
            localNodeId = 0x1234L,
            isConnected = true,
            isAuthenticated = true
        )
        assertTrue(summary.hasUnsent)
        assertEquals(1, summary.pendingCount)
    }
}
