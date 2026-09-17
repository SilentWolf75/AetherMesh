package com.silentwolf75.aethermesh.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployConfirmCopyTest {
    @Test
    fun englishSummaryIncludesRoleGpsAndTx() {
        val text = DeployConfirmCopy.summary(
            spanish = false,
            role = 1,
            gpsMode = 2,
            dutySecs = 900,
            powerSave = true,
            telemetrySecs = 300,
            txPower = 22
        )
        assertTrue(text.contains("Role: Router"))
        assertTrue(text.contains("GPS duty 15m"))
        assertTrue(text.contains("Battery saver: yes"))
        assertTrue(text.contains("Telemetry: 300s"))
        assertTrue(text.contains("TX 22 dBm"))
    }

    @Test
    fun repeaterSpanishOmitsTxOutsideRange() {
        val text = DeployConfirmCopy.summary(
            spanish = true,
            role = 2,
            gpsMode = 1,
            dutySecs = 900,
            powerSave = true,
            telemetrySecs = 300,
            txPower = 0
        )
        assertEquals("Repetidor", DeployConfirmCopy.roleLabel(2, true))
        assertTrue(text.contains("Rol: Repetidor"))
        assertTrue(text.contains("GPS apagado"))
        assertTrue(!text.contains("TX"))
    }
}
