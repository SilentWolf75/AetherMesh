package com.silentwolf75.aethermesh.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class NodeNamePolicyTest {
    @Test
    fun preservesCustomShortNameWhenMeshAdvertisesLongName() {
        val chosen = NodeNamePolicy.choose(
            nodeId = 0xABCDEFL,
            existingName = "Base",
            existingShortName = "WOLF",
            existingIsCustom = true,
            advertisedName = "Base Camp"
        )
        assertEquals("Base Camp", chosen.longName)
        assertEquals("WOLF", chosen.shortName)
        assertTrue(chosen.isCustom)
    }

    @Test
    fun clipsShortNameToFourUppercase() {
        assertEquals("WOLF", NodeNamePolicy.clipShort(" wolfpack "))
        assertEquals("AB", NodeNamePolicy.clipShort("ab"))
        assertEquals("", NodeNamePolicy.clipShort("   "))
    }

    @Test
    fun skipsHydrateWhenDeviceSendsNoNames() {
        assertFalse(NodeNamePolicy.shouldHydrateNames("", ""))
        assertTrue(NodeNamePolicy.shouldHydrateNames("Camp", ""))
        assertTrue(NodeNamePolicy.shouldHydrateNames("", "WOLF"))
    }

    @Test
    fun deviceShortWinsAndPersistsOverPrefsAndDirectory() {
        val names = NodeNamePolicy.namesFromDevice(
            nodeId = 0xABCDEFL,
            deviceLongName = "Camp",
            deviceShortName = "wolfpack",
            prefsShort = "PREF",
            existingName = "Old",
            existingShort = "OLD1"
        )
        assertEquals("Camp", names.longName)
        assertEquals("WOLF", names.shortName)
        assertTrue(names.persistDeviceShort)
        assertTrue(names.writeDb)
    }

    @Test
    fun prefsShortFillsWhenDeviceOmitsShort() {
        val names = NodeNamePolicy.namesFromDevice(
            nodeId = 0xABCDEFL,
            deviceLongName = "Camp",
            deviceShortName = "  ",
            prefsShort = "pref",
            existingName = "Old",
            existingShort = "OLD1"
        )
        assertEquals("Camp", names.longName)
        assertEquals("PREF", names.shortName)
        assertFalse(names.persistDeviceShort)
        assertTrue(names.writeDb)
    }

    @Test
    fun directoryThenDerivedWhenDeviceAndPrefsHaveNoShort() {
        val fromRow = NodeNamePolicy.namesFromDevice(
            nodeId = 0xABCDEFL,
            deviceLongName = "",
            deviceShortName = "",
            prefsShort = null,
            existingName = "Base Camp",
            existingShort = "BASE"
        )
        assertEquals("Base Camp", fromRow.longName)
        assertEquals("BASE", fromRow.shortName)
        assertFalse(fromRow.persistDeviceShort)
        assertTrue(fromRow.writeDb)

        val derived = NodeNamePolicy.namesFromDevice(
            nodeId = 0xABCDEFL,
            deviceLongName = "Base Camp",
            deviceShortName = "",
            prefsShort = null,
            existingName = "",
            existingShort = ""
        )
        assertEquals("Base Camp", derived.longName)
        assertEquals(NodeNamePolicy.deriveShortName("Base Camp", 0xABCDEFL), derived.shortName)
        assertFalse(derived.persistDeviceShort)
        assertTrue(derived.writeDb)
    }

    @Test
    fun skipsDbWriteWhenLongNameStaysBlank() {
        val names = NodeNamePolicy.namesFromDevice(
            nodeId = 0x1L,
            deviceLongName = "",
            deviceShortName = "WOLF",
            prefsShort = null,
            existingName = "",
            existingShort = ""
        )
        assertEquals("WOLF", names.shortName)
        assertTrue(names.persistDeviceShort)
        assertFalse(names.writeDb)
    }

    @Test
    fun pendingFromPrefsAppliesStagedRenameAfterUnlock() {
        assertEquals(null, NodeNamePolicy.pendingFromPrefs(null, "WOLF", 0xABCDL))
        assertEquals(null, NodeNamePolicy.pendingFromPrefs("  ", "WOLF", 0xABCDL))

        val withShort = NodeNamePolicy.pendingFromPrefs("Base Camp", "wolfpack", 0xABCDL)!!
        assertEquals("Base Camp", withShort.longName)
        assertEquals("WOLF", withShort.shortName)

        val derived = NodeNamePolicy.pendingFromPrefs("Base Camp", null, 0xABCDL)!!
        assertEquals("Base Camp", derived.longName)
        assertEquals(NodeNamePolicy.deriveShortName("Base Camp", 0xABCDL), derived.shortName)
    }
}
