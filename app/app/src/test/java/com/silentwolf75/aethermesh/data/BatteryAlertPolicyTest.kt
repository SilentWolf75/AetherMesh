package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryAlertPolicyTest {
    @Test
    fun ignoresZeroNodeAndHealthyLevel() {
        assertEquals(
            BatteryAlertAction.Ignore,
            BatteryAlertPolicy.decide(0L, 5, false, 0)
        )
        assertEquals(
            BatteryAlertAction.Ignore,
            BatteryAlertPolicy.decide(9L, 21, false, 0)
        )
    }

    @Test
    fun chargingOrRecoverRearms() {
        assertEquals(
            BatteryAlertAction.Rearm,
            BatteryAlertPolicy.decide(9L, 5, true, BatteryAlertPolicy.CRITICAL)
        )
        assertEquals(
            BatteryAlertAction.Rearm,
            BatteryAlertPolicy.decide(9L, 25, false, BatteryAlertPolicy.LOW)
        )
    }

    @Test
    fun firstCrossingThenEscalateNotRepeat() {
        val low = BatteryAlertPolicy.decide(9L, 20, false, 0) as BatteryAlertAction.Notify
        assertEquals(BatteryAlertPolicy.LOW, low.threshold)
        assertTrue(!low.critical)
        assertEquals(
            BatteryAlertAction.Ignore,
            BatteryAlertPolicy.decide(9L, 15, false, BatteryAlertPolicy.LOW)
        )
        val crit = BatteryAlertPolicy.decide(9L, 10, false, BatteryAlertPolicy.LOW)
            as BatteryAlertAction.Notify
        assertTrue(crit.critical)
        assertEquals(
            BatteryAlertAction.Ignore,
            BatteryAlertPolicy.decide(9L, 8, false, BatteryAlertPolicy.CRITICAL)
        )
    }

    @Test
    fun copyLocksEnAndEs() {
        assertEquals("⚠ Wolf battery critical", BatteryAlertPolicy.title("Wolf", true, false))
        assertEquals("Wolf batería baja", BatteryAlertPolicy.title("Wolf", false, true))
        assertEquals("8% remaining — charge it now", BatteryAlertPolicy.body(8, true, false))
        assertEquals("Nodo ABCD", BatteryAlertPolicy.fallbackName(0xABCDL, true))
    }
}
