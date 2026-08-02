package com.example.aethermesh.data

import junit.framework.TestCase.assertEquals
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
}
