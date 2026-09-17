package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleConnectPolicyTest {
    @Test
    fun postRebootConnectNeverBeatsClosePlusPad() {
        assertEquals(
            5_000L,
            BleConnectPolicy.postRebootConnectDelay(closeAfterMs = 1_200L, reconnectAfterMs = 5_000L)
        )
        assertEquals(
            1_700L,
            BleConnectPolicy.postRebootConnectDelay(closeAfterMs = 1_200L, reconnectAfterMs = 1_000L)
        )
        assertEquals(
            BleConnectPolicy.POST_REBOOT_RECONNECT_PAD_MS,
            BleConnectPolicy.postRebootConnectDelay(closeAfterMs = 0L, reconnectAfterMs = 0L)
        )
    }

    @Test
    fun forceRefreshHasFloor() {
        assertEquals(200L, BleConnectPolicy.forceRefreshDelay(0L))
        assertEquals(200L, BleConnectPolicy.forceRefreshDelay(199L))
        assertEquals(1_000L, BleConnectPolicy.forceRefreshDelay(1_000L))
    }

    @Test
    fun watchdogOutlivesAutoConnectDelay() {
        assertTrue(BleConnectPolicy.CONNECT_WATCHDOG_MS > BleConnectPolicy.AUTO_CONNECT_DELAY_MS)
        assertEquals(12, BleConnectPolicy.MAX_RECONNECT_ATTEMPTS)
        assertEquals(256, BleConnectPolicy.REQUESTED_MTU)
    }

    @Test
    fun scanLabels() {
        assertEquals("AetherMesh Node", BleConnectPolicy.scanDisplayName(null))
        assertEquals("AetherMesh-ABCD", BleConnectPolicy.scanDisplayName("AetherMesh-ABCD"))
        assertTrue(BleConnectPolicy.matchesAdvertName("AetherMesh-1"))
        assertTrue(!BleConnectPolicy.matchesAdvertName("Other"))
        assertEquals("paired_mac", BleConnectPolicy.PREF_PAIRED_MAC)
    }
}
