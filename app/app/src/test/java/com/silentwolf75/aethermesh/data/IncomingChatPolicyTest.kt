package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingChatPolicyTest {
    @Test
    fun rangePingNeverBecomesChat() {
        assertEquals(
            IncomingTextKind.IgnorePing,
            IncomingChatPolicy.classify("PING_42_D", 1L, 2L, "")
        )
        assertFalse(IncomingChatPolicy.isControlPing("hello"))
    }

    @Test
    fun broadcastUsesChannelKeyAndAad() {
        val plan = IncomingChatPolicy.plan(0x11L, ChatSendPolicy.BROADCAST, "Trail")
        assertEquals("CHANNEL_Trail", plan.chatIdentifier)
        assertEquals(
            ChatContext.authenticatedLabel(0x11L, ChatSendPolicy.BROADCAST, "Trail"),
            plan.cryptoContext
        )
        assertEquals("Trail", plan.channelForRow)
        assertTrue(plan.isBroadcast)
    }

    @Test
    fun catchUpUnicastKeepsChannelAadNotDmPair() {
        val plan = IncomingChatPolicy.plan(0xAAL, 0xBBL, "General")
        assertTrue(IncomingChatPolicy.isChannelFrame(0xBBL, "General"))
        assertEquals("CHANNEL_General", plan.chatIdentifier)
        assertEquals(
            ChatContext.authenticatedLabel(0xAAL, ChatSendPolicy.BROADCAST, "General"),
            plan.cryptoContext
        )
        assertEquals("General", plan.channelForRow)
        assertTrue(plan.isBroadcast)
        assertFalse(plan.cryptoContext.startsWith("DM_"))
    }

    @Test
    fun dmUsesSenderThreadAndEmptyChannelRow() {
        val plan = IncomingChatPolicy.plan(0x22L, 0x11L, "")
        assertEquals("DM_34", plan.chatIdentifier)
        assertEquals(
            ChatContext.authenticatedLabel(0x22L, 0x11L, IncomingChatPolicy.DEFAULT_CHANNEL),
            plan.cryptoContext
        )
        assertEquals("", plan.channelForRow)
        assertFalse(plan.isBroadcast)
    }

    @Test
    fun emptyBroadcastChannelFallsBackToGeneral() {
        val plan = IncomingChatPolicy.plan(1L, ChatSendPolicy.BROADCAST, "")
        assertEquals("CHANNEL_General", plan.chatIdentifier)
        assertEquals(IncomingChatPolicy.DEFAULT_CHANNEL, plan.channelForRow)
    }

    @Test
    fun encryptedWithoutKeyNeverShowsCiphertext() {
        assertEquals("hi", IncomingChatPolicy.resolveContent(false, false, "hi", "unused").content)
        assertEquals(
            IncomingChatPolicy.ERROR_NO_KEY,
            IncomingChatPolicy.resolveContent(true, false, "v2:cipher", "plain").content
        )
        assertEquals("plain", IncomingChatPolicy.resolveContent(true, true, "v2:cipher", "plain").content)
    }

    @Test
    fun undecryptableFramesKeepCiphertextForLaterKey() {
        assertEquals(
            IncomingChatContent(IncomingChatPolicy.ERROR_NO_KEY, pendingCipherText = "v2:cipher"),
            IncomingChatPolicy.resolveContent(true, false, "v2:cipher", "")
        )
        assertEquals(
            IncomingChatContent(ChatCrypto.ERROR_BAD_CONTEXT, pendingCipherText = "v2:cipher"),
            IncomingChatPolicy.resolveContent(true, true, "v2:cipher", ChatCrypto.ERROR_BAD_CONTEXT)
        )
        assertEquals(
            IncomingChatContent(ChatCrypto.ERROR_BAD_KEY, pendingCipherText = "legacy"),
            IncomingChatPolicy.resolveContent(true, true, "legacy", ChatCrypto.ERROR_BAD_KEY)
        )
    }

    @Test
    fun readableFramesDoNotRetainCiphertext() {
        assertEquals(null, IncomingChatPolicy.resolveContent(false, false, "hi", "").pendingCipherText)
        assertEquals(null, IncomingChatPolicy.resolveContent(true, true, "v2:cipher", "plain").pendingCipherText)
    }

    @Test
    fun decryptFailureSentinelsAreRecognized() {
        assertTrue(IncomingChatPolicy.isDecryptFailure(ChatCrypto.ERROR_INVALID))
        assertTrue(IncomingChatPolicy.isDecryptFailure(ChatCrypto.ERROR_BAD_CONTEXT))
        assertTrue(IncomingChatPolicy.isDecryptFailure(ChatCrypto.ERROR_BAD_KEY))
        assertFalse(IncomingChatPolicy.isDecryptFailure("hello"))
        assertFalse(IncomingChatPolicy.isDecryptFailure(IncomingChatPolicy.ERROR_NO_KEY))
    }

    @Test
    fun channelKeyIsBoundedLikeSendPolicy() {
        val longName = "TrailCrewNameThatIsWayTooLongForTheWire"
        val plan = IncomingChatPolicy.plan(1L, ChatSendPolicy.BROADCAST, longName)
        assertEquals(ChatSendPolicy.channelKey(longName), plan.chatIdentifier)
    }

    @Test
    fun localizePlaceholderMapsSentinelsInSpanish() {
        assertEquals(
            IncomingChatPolicy.ERROR_NO_KEY,
            IncomingChatPolicy.localizePlaceholder(IncomingChatPolicy.ERROR_NO_KEY, false)
        )
        assertEquals(
            "[Mensaje cifrado — sin clave configurada]",
            IncomingChatPolicy.localizePlaceholder(IncomingChatPolicy.ERROR_NO_KEY, true)
        )
        assertTrue(
            IncomingChatPolicy.localizePlaceholder(ChatCrypto.ERROR_BAD_KEY, true)
                .contains("clave incorrecta")
        )
    }

    @Test
    fun foreignDirectMessageIsIgnored() {
        assertEquals(
            IncomingTextKind.IgnoreUnaddressed,
            IncomingChatPolicy.classify("hello", 0x11L, 0x22L, "", localNodeId = 0x33L)
        )
    }

    @Test
    fun addressedDirectMessageIsAccepted() {
        val result = IncomingChatPolicy.classify("hello", 0x11L, 0x22L, "", localNodeId = 0x22L)
        assertTrue(result is IncomingTextKind.Chat)
        val chat = result as IncomingTextKind.Chat
        assertEquals("DM_17", chat.plan.chatIdentifier)
    }

    @Test
    fun addressedDirectMessageAcceptedWith16BitAnd32BitIdMatch() {
        // Connected node ID might be 16-bit 0x1234 while wire recipient is 32-bit 0xAABB1234
        val result16 = IncomingChatPolicy.classify("hello", 0x11L, 0xAABB1234L, "", localNodeId = 0x1234L)
        assertTrue(result16 is IncomingTextKind.Chat)

        // Or connected node ID is 32-bit 0xAABB1234 while wire recipient is 16-bit 0x1234
        val result32 = IncomingChatPolicy.classify("hello", 0x11L, 0x1234L, "", localNodeId = 0xAABB1234L)
        assertTrue(result32 is IncomingTextKind.Chat)
    }

    @Test
    fun broadcastAndCatchupAlwaysAcceptedRegardlessOfLocalNodeId() {
        val broadcastResult = IncomingChatPolicy.classify(
            "announcement",
            0x11L,
            ChatSendPolicy.BROADCAST,
            "General",
            localNodeId = 0x33L
        )
        assertTrue(broadcastResult is IncomingTextKind.Chat)

        val catchupResult = IncomingChatPolicy.classify(
            "catchup message",
            0x11L,
            0x22L,
            "General",
            localNodeId = 0x33L
        )
        assertTrue(catchupResult is IncomingTextKind.Chat)
    }
}
