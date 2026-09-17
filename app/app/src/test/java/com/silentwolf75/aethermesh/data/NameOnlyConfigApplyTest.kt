package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameOnlyConfigApplyTest {
    private val identity = ControlAuthIdentity(9L, 4)

    @Test
    fun localRenameUsesRecipientZeroAndNameOnlyFlag() {
        val packet = NameOnlyConfigApply.build(
            localNodeId = 0x1111L,
            nodeId = 0x1111L,
            name = "Heltec-Alpha",
            shortName = "hltc",
            adminPassword = "",
            authProtocol = 0,
            packetId = 7,
            isLocal = true,
            identity = identity
        )
        assertNotNull(packet)
        assertEquals(0, packet!!.recipientId)
        assertEquals(1, packet.hopLimit)
        assertFalse(packet.wantAck)
        assertEquals("Heltec-Alpha", packet.config.nodeName)
        assertEquals("HLTC", packet.config.nodeShortName)
        assertTrue(packet.config.applyNameOnly)
        assertEquals(0, packet.protocolVersion)
        assertEquals("", packet.config.configPassword)
    }

    @Test
    fun remoteRenameAttachesV3AuthAndTargetRecipient() {
        val packet = NameOnlyConfigApply.build(
            localNodeId = 0x1111L,
            nodeId = 0xABCDL,
            name = "Remote Node",
            shortName = "",
            adminPassword = "secret",
            authProtocol = 3,
            packetId = 8,
            isLocal = false,
            identity = identity
        )
        assertNotNull(packet)
        assertEquals(0xABCD, packet!!.recipientId)
        assertEquals(4, packet.hopLimit)
        assertTrue(packet.wantAck)
        assertEquals("REMO", packet.config.nodeShortName)
        assertTrue(packet.config.applyNameOnly)
        assertEquals(3, packet.protocolVersion)
        assertEquals(identity.sessionId, packet.sessionId)
        assertEquals(identity.counter, packet.authCounter)
        assertEquals(16, packet.authTag.size())
        assertEquals("", packet.config.configPassword)
    }

    @Test
    fun remoteLegacyPutsPasswordInConfig() {
        val packet = NameOnlyConfigApply.build(
            localNodeId = 1L,
            nodeId = 2L,
            name = "Peer",
            shortName = "PEER",
            adminPassword = "secret",
            authProtocol = 0,
            packetId = 9,
            isLocal = false,
            identity = identity
        )
        assertNotNull(packet)
        assertEquals("secret", packet!!.config.configPassword)
        assertEquals(0, packet.protocolVersion)
        assertEquals(0, packet.authTag.size())
    }

    @Test
    fun remoteRenameWithoutPasswordReturnsNull() {
        assertNull(
            NameOnlyConfigApply.build(
                localNodeId = 1L,
                nodeId = 2L,
                name = "Nope",
                shortName = "NOPE",
                adminPassword = "",
                authProtocol = 3,
                packetId = 1,
                isLocal = false,
                identity = identity
            )
        )
    }

    @Test
    fun blankNameStillBuildsLocalPacket() {
        val packet = NameOnlyConfigApply.build(
            localNodeId = 1L,
            nodeId = 1L,
            name = "   ",
            shortName = "AB",
            adminPassword = "",
            authProtocol = 0,
            packetId = 1,
            isLocal = true,
            identity = identity
        )
        assertNotNull(packet)
        assertEquals("", packet!!.config.nodeName)
        assertEquals("AB", packet.config.nodeShortName)
    }

    @Test
    fun clipsLongNameToSixteen() {
        val packet = NameOnlyConfigApply.build(
            localNodeId = 1L,
            nodeId = 1L,
            name = "12345678901234567890",
            shortName = "abcd",
            adminPassword = "",
            authProtocol = 0,
            packetId = 1,
            isLocal = true,
            identity = identity
        )
        assertEquals("1234567890123456", packet!!.config.nodeName)
        assertEquals("ABCD", packet.config.nodeShortName)
    }
}
