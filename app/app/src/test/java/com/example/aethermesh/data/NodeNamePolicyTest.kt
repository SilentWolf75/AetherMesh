package com.example.aethermesh.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class NodeNamePolicyTest {
    @Test
    fun customNameAlwaysWinsOverAdvertisedDefault() {
        val result = NodeNamePolicy.choose(0x1234, "Wolf Base", "WOLF", true, "AetherMesh-1234")
        assertEquals("Wolf Base", result.longName)
        assertEquals("WOLF", result.shortName)
        assertTrue(result.isCustom)
    }

    @Test
    fun advertisedNameReplacesGeneratedDefaultAndRegeneratesShortName() {
        val result = NodeNamePolicy.choose(0x1234, "Node 00001234", "1234", false, "Trail-Relay")
        assertEquals("Trail-Relay", result.longName)
        assertEquals("TRAI", result.shortName)
        assertFalse(result.isCustom)
    }

    @Test
    fun missingNamesGetStableFullWidthDefault() {
        val result = NodeNamePolicy.choose(0xFEDCBA98L, "", "", false, "")
        assertEquals("Node FEDCBA98", result.longName)
        assertEquals("FEDC", result.shortName)
    }

    @Test
    fun advertisedNameRespectsFirmwareUtf8ByteLimit() {
        val result = NodeNamePolicy.choose(0x1234, "", "", false, "abcdefghijklmno\uD83D\uDE00")
        assertEquals("abcdefghijklmno", result.longName)
        assertTrue(result.longName.toByteArray(Charsets.UTF_8).size <= 16)
    }
}
