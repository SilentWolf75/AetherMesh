package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DfuSessionPolicyTest {
    @Test
    fun timeoutsAreOrdered() {
        assertTrue(DfuSessionPolicy.ENTER_ACK_TIMEOUT_MS < DfuSessionPolicy.SCAN_TIMEOUT_MS)
        assertTrue(DfuSessionPolicy.BOOTLOADER_SETTLE_MS < DfuSessionPolicy.SCAN_TIMEOUT_MS)
        assertTrue(DfuSessionPolicy.SCAN_TIMEOUT_MS < DfuSessionPolicy.SERVICE_ENGAGE_WATCHDOG_MS)
    }

    @Test
    fun statusStringsMatchLocalizePrefixes() {
        assertTrue(DfuSessionPolicy.STATUS_CONNECTING.startsWith("DFU: connecting"))
        assertTrue(DfuSessionPolicy.STATUS_SEARCHING.startsWith("Searching for DFU"))
        assertTrue(DfuSessionPolicy.STATUS_REBOOTING.startsWith("Rebooting node into DFU"))
        assertEquals(
            "DFU uploading... 42%",
            DfuSessionPolicy.uploadingStatus(42, "")
        )
        assertEquals("DFU failed: boom", DfuSessionPolicy.failedStatus("boom"))
        assertEquals("DFU failed: error 5", DfuSessionPolicy.failedStatus(5, null))
    }
}
