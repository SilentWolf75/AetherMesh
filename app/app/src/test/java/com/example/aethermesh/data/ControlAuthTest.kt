package com.example.aethermesh.data

import com.example.aethermesh.proto.NodeConfig
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
        val first = ControlAuth.sign(1, 2, identity, config, "admin-key")
        val same = ControlAuth.sign(1, 2, identity, config, "admin-key")
        val changed = ControlAuth.sign(1, 3, identity.copy(counter = 8), config, "admin-key")
        assertEquals(16, first.size)
        assertEquals(first.toList(), same.toList())
        assertFalse(first.contentEquals(changed))
    }

    @Test
    fun canonicalFormatMatchesPublishedV2Vector() {
        val identity = ControlAuthIdentity(0x0102030405060708L, 7)
        val canonical = ControlAuth.canonical(1, 2, identity, config)
        val expectedCanonical = "414d4346473201000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000"
        val expectedTag = "5214b8958033640dd29ffc57a5e891ad"
        assertEquals(expectedCanonical, canonical.toHex())
        assertEquals(expectedTag, ControlAuth.sign(1, 2, identity, config, "admin-key").toHex())
        assertTrue(canonical.size < 128)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
