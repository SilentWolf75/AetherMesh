package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMessageAlertPolicyTest {
    @Test
    fun suppressesWhenDisabledMutedOrForeground() {
        assertTrue(IncomingMessageAlertPolicy.shouldNotify(true, muted = false, activityVisible = false))
        assertFalse(IncomingMessageAlertPolicy.shouldNotify(false, muted = false, activityVisible = false))
        assertFalse(IncomingMessageAlertPolicy.shouldNotify(true, muted = true, activityVisible = false))
        assertFalse(IncomingMessageAlertPolicy.shouldNotify(true, muted = false, activityVisible = true))
    }

    @Test
    fun broadcastTitleIncludesChannel() {
        assertEquals(
            "Wolf @ General",
            IncomingMessageAlertPolicy.title("Wolf", "General", isBroadcast = true)
        )
        assertEquals(
            "Wolf",
            IncomingMessageAlertPolicy.title("Wolf", "General", isBroadcast = false)
        )
    }

    @Test
    fun notifyIdIsStablePerThread() {
        val a = IncomingMessageAlertPolicy.notifyId("CHANNEL_General")
        val b = IncomingMessageAlertPolicy.notifyId("CHANNEL_General")
        val dm = IncomingMessageAlertPolicy.notifyId("DM_34")
        assertEquals(a, b)
        assertTrue(a != dm)
    }

    @Test
    fun copyLocksEnAndEs() {
        assertEquals("Messages", IncomingMessageAlertPolicy.channelName(false))
        assertEquals("Mensajes de AetherMesh", IncomingMessageAlertPolicy.summaryTitle(true))
        assertEquals("Nodo ABCD", IncomingMessageAlertPolicy.fallbackName(0xABCDL, true))
    }
}
