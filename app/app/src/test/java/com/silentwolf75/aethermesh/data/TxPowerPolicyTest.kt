package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TxPowerPolicyTest {

    @Test
    fun plainBoardsStopAtTheChipLimit() {
        assertEquals(22, TxPowerPolicy.maxDbm("RAK4631", 0))
        assertEquals(22, TxPowerPolicy.maxDbm("Heltec V3", 1))
        assertEquals(22, TxPowerPolicy.maxDbm(null, 0))
    }

    @Test
    fun amplifiedBoardsReachTheirRatedOutput() {
        assertEquals(28, TxPowerPolicy.maxDbm("Heltec V4", 0))
        assertEquals(29, TxPowerPolicy.maxDbm("RAK 1W", 0))
    }

    @Test
    fun euRegionCapsAt27() {
        assertEquals(27, TxPowerPolicy.maxDbm("Heltec V4", 1))
        assertEquals(27, TxPowerPolicy.maxDbm("RAK 1W", 1))
    }

    @Test
    fun clampKeepsValuesInRange() {
        assertEquals(27, TxPowerPolicy.clamp(30, "Heltec V4", 1))
        assertEquals(10, TxPowerPolicy.clamp(2, "RAK4631", 0))
        assertEquals(20, TxPowerPolicy.clamp(20, "RAK4631", 0))
    }
}
