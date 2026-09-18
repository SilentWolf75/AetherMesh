package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class BootReconnectPolicyTest {

    @Test
    fun savedNodeWithPermissionStartsTheService() {
        assertEquals(
            BootReconnectPolicy.Decision.START,
            BootReconnectPolicy.decide("C8:F0:9E:12:34:56", hasBluetoothPermission = true)
        )
    }

    @Test
    fun nothingPairedMeansNothingToDo() {
        assertEquals(BootReconnectPolicy.Decision.NO_NODE, BootReconnectPolicy.decide(null, true))
        assertEquals(BootReconnectPolicy.Decision.NO_NODE, BootReconnectPolicy.decide("", true))
    }

    @Test
    fun malformedAddressIsIgnored() {
        assertEquals(BootReconnectPolicy.Decision.NO_NODE, BootReconnectPolicy.decide("not-a-mac", true))
    }

    @Test
    fun revokedPermissionDoesNotStartASilentRetryLoop() {
        assertEquals(
            BootReconnectPolicy.Decision.NO_PERMISSION,
            BootReconnectPolicy.decide("C8:F0:9E:12:34:56", hasBluetoothPermission = false)
        )
    }
}
