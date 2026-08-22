package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.NodeConfig
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class ControlAuthTest {
    private val config = NodeConfig.newBuilder()
        .setNodeName("Relay")
        .setLoraSf(9)
        .setLoraBw(125f)
        .setLoraTxPower(22)
        .setRegion(0)
        .setNodeRole(1)
        .setTelemetryInterval(60)
        .setScreenTimeoutSecs(30)
        .setPowerSaveMode(false)
        .setPositionPrecision(100)
        .setGpsMode(0)
        .build()

    @Test
    fun tagIsDeterministicAndBoundToCounterAndRecipient() {
        val identity = ControlAuthIdentity(0x0102030405060708L, 7)
        val first = ControlAuth.sign(1, 2, identity, config, "admin-key", authProtocol = 2)
        val same = ControlAuth.sign(1, 2, identity, config, "admin-key", authProtocol = 2)
        val changed = ControlAuth.sign(1, 3, identity.copy(counter = 8), config, "admin-key", authProtocol = 2)
        assertEquals(16, first.size)
        assertEquals(first.toList(), same.toList())
        assertFalse(first.contentEquals(changed))
    }

    @Test
    fun canonicalFormatMatchesPublishedV2Vector() {
        val identity = ControlAuthIdentity(0x0102030405060708L, 7)
        val canonical = ControlAuth.canonical(1, 2, identity, config)
        val expectedCanonical = "414d4346473201000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000"
        val expectedTag = "165a8fa5f809a08d3063ea46c78c64e4"
        assertEquals(expectedCanonical, canonical.toHex())
        assertEquals(expectedTag, ControlAuth.sign(1, 2, identity, config, "admin-key", authProtocol = 2).toHex())
        assertTrue(canonical.size < 256)
    }

    /**
     * Cross-language golden vector for protocol v3 (AMCFG3 + PBKDF2).
     * Must match tools/test_control_auth_vectors.py and firmware/test/test_packetauth.
     */
    @Test
    fun canonicalFormatMatchesPublishedV3Vector() {
        val identity = ControlAuthIdentity(0x0102030405060708L, 7)
        val domainV3 = "AMCFG3".toByteArray(Charsets.US_ASCII)
        val canonical = ControlAuth.canonical(1, 2, identity, config, domainV3)
        val expectedCanonical = "414d4346473301000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000"
        val expectedTag = "0cd1d291935a725a4ea210f3ac3dddbf"
        assertEquals(expectedCanonical, canonical.toHex())
        assertEquals(expectedTag, ControlAuth.sign(1, 2, identity, config, "admin-key", authProtocol = 3).toHex())
    }

    @Test
    fun v3UsesDifferentDomainAndKey() {
        val identity = ControlAuthIdentity(0x0102030405060708L, 7)
        val v2 = ControlAuth.sign(1, 2, identity, config, "admin-key", authProtocol = 2)
        val v3 = ControlAuth.sign(1, 2, identity, config, "admin-key", authProtocol = 3)
        assertFalse(v2.contentEquals(v3))
        assertEquals("0cd1d291935a725a4ea210f3ac3dddbf", v3.toHex())
    }

    @Test
    fun remoteControlAuthPolicyMatchesPeerCapability() {
        assertEquals(0, RemoteControlAuthPolicy.authProtocolForPeer(1))
        assertEquals(2, RemoteControlAuthPolicy.authProtocolForPeer(2))
        assertEquals(3, RemoteControlAuthPolicy.authProtocolForPeer(3))
        assertEquals(3, RemoteControlAuthPolicy.authProtocolForPeer(4))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
