package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHealthPolicyTest {
    @Test
    fun android12NeedsBleScanAndConnect() {
        val ok = PermissionHealthPolicy.evaluate(
            sdkInt = 31,
            bleScanGranted = true,
            bleConnectGranted = true,
            fineLocationGranted = true,
            coarseLocationGranted = true,
            bgAlertsEnabled = false,
            notificationsGranted = false
        )
        assertFalse(ok.hasAnyIssue)

        val missingConnect = PermissionHealthPolicy.evaluate(
            sdkInt = 31,
            bleScanGranted = true,
            bleConnectGranted = false,
            fineLocationGranted = true,
            coarseLocationGranted = true,
            bgAlertsEnabled = false,
            notificationsGranted = false
        )
        assertTrue(missingConnect.missingBle)
        assertFalse(missingConnect.missingLocation)
    }

    @Test
    fun preAndroid12BleUsesFineLocation() {
        val missing = PermissionHealthPolicy.evaluate(
            sdkInt = 30,
            bleScanGranted = false,
            bleConnectGranted = false,
            fineLocationGranted = false,
            coarseLocationGranted = true,
            bgAlertsEnabled = false,
            notificationsGranted = false
        )
        assertTrue(missing.missingBle)
        assertFalse(missing.missingLocation)
    }

    @Test
    fun notificationsOnlyWhenBgAlertsAndApi33Ungranted() {
        val flagged = PermissionHealthPolicy.evaluate(
            sdkInt = 33,
            bleScanGranted = true,
            bleConnectGranted = true,
            fineLocationGranted = true,
            coarseLocationGranted = true,
            bgAlertsEnabled = true,
            notificationsGranted = false
        )
        assertTrue(flagged.missingNotifications)

        val ignored = PermissionHealthPolicy.evaluate(
            sdkInt = 33,
            bleScanGranted = true,
            bleConnectGranted = true,
            fineLocationGranted = true,
            coarseLocationGranted = true,
            bgAlertsEnabled = false,
            notificationsGranted = false
        )
        assertFalse(ignored.missingNotifications)

        val pre33 = PermissionHealthPolicy.evaluate(
            sdkInt = 32,
            bleScanGranted = true,
            bleConnectGranted = true,
            fineLocationGranted = true,
            coarseLocationGranted = true,
            bgAlertsEnabled = true,
            notificationsGranted = false
        )
        assertFalse(pre33.missingNotifications)
    }

    @Test
    fun copyLocksEnAndEs() {
        val health = PermissionHealth(
            missingBle = true,
            missingLocation = true,
            missingNotifications = true
        )
        assertEquals("Missing: Bluetooth, location, notifications.", PermissionHealthPolicy.summary(health, false))
        assertEquals("Faltan: Bluetooth, ubicación, notificaciones.", PermissionHealthPolicy.summary(health, true))
        assertTrue(PermissionHealthPolicy.why(health, false).contains("Bluetooth links"))
        assertEquals("Ajustes", PermissionHealthPolicy.settingsLabel(true))
    }
}
