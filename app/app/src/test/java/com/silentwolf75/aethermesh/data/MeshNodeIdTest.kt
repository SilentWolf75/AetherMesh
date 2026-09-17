package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshNodeIdTest {
    @Test
    fun zeroIsNeverANode() {
        assertFalse(MeshNodeId.same(0L, 0L))
        assertFalse(MeshNodeId.same(0L, 0xABCDL))
        assertFalse(MeshNodeId.same(0xABCDL, 0L))
    }

    @Test
    fun fullThirtyTwoBitMatch() {
        assertTrue(MeshNodeId.same(0xAABBCCDDL, 0xAABBCCDDL))
        assertFalse(MeshNodeId.same(0xAABBCCDDL, 0xAABBCCEEL))
    }

    @Test
    fun bleNameSixteenBitSuffixMatches() {
        assertTrue(MeshNodeId.same(0xCCDDL, 0xAABBCCDDL))
        assertTrue(MeshNodeId.same(0xAABBCCDDL, 0xCCDDL))
        assertFalse(MeshNodeId.same(0xCCEEL, 0xAABBCCDDL))
    }

    @Test
    fun masksToThirtyTwoBits() {
        val high = 0x11AABBCCDDL
        assertTrue(MeshNodeId.same(high, 0xAABBCCDDL))
    }

    @Test
    fun fromMacLittleEndianFirstFourOctets() {
        // 90:70:69:9a → b0=0x90, b1=0x70, b2=0x69, b3=0x9a → 0x9A697090
        assertEquals(0x9A697090L, MeshNodeId.fromMac("90:70:69:9a:93:4c"))
        assertEquals(0L, MeshNodeId.fromMac("aa:bb"))
        assertEquals(0L, MeshNodeId.fromMac("not-a-mac"))
        assertEquals(0L, MeshNodeId.fromMac(""))
    }
}
