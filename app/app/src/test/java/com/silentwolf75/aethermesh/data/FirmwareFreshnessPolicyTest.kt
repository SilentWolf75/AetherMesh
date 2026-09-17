package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareFreshnessPolicyTest {
    @Test
    fun zeroNodeDoesNotAwait() {
        assertNull(FirmwareFreshnessPolicy.awaiting(0L))
        val start = FirmwareFreshnessPolicy.awaiting(0x11L)!!
        assertTrue(start.awaitingFreshTelemetry)
        assertEquals(0x11L, start.connectedNodeId)
    }

    @Test
    fun blankTelemetryDoesNotClearAwaiting() {
        val awaiting = FirmwareFreshness(true, 0x11L)
        assertFalse(FirmwareFreshnessPolicy.acceptReported("  "))
        assertEquals(awaiting, FirmwareFreshnessPolicy.onReported(awaiting, 0x11L, "  "))
        val cleared = FirmwareFreshnessPolicy.onReported(awaiting, 0x11L, "1.3.2")
        assertFalse(cleared.awaitingFreshTelemetry)
        assertEquals(0x11L, cleared.connectedNodeId)
    }

    @Test
    fun otherNodeDoesNotClearAwaiting() {
        val awaiting = FirmwareFreshness(true, 0x11L)
        val same = FirmwareFreshnessPolicy.onReported(awaiting, 0x22L, "1.3.2")
        assertTrue(same.awaitingFreshTelemetry)
        assertEquals(0x11L, same.connectedNodeId)
    }

    @Test
    fun checkingUses16BitOr32BitNodeId() {
        val awaiting = FirmwareFreshness(true, 0x12345678L)
        assertTrue(FirmwareFreshnessPolicy.isChecking(awaiting, 0x12345678L))
        assertTrue(FirmwareFreshnessPolicy.isChecking(awaiting, 0x5678L))
        assertFalse(FirmwareFreshnessPolicy.isChecking(awaiting, 0xABCDL))
        assertFalse(FirmwareFreshnessPolicy.isChecking(FirmwareFreshness(), 0x11L))
        assertTrue(FirmwareFreshnessPolicy.isChecking(FirmwareFreshness(true, 0L), null))
    }

    @Test
    fun expectedLabelTrims() {
        assertEquals("1.3.2", FirmwareFreshnessPolicy.expectedLabel(" 1.3.2 "))
        assertEquals("", FirmwareFreshnessPolicy.expectedLabel(null))
    }

    @Test
    fun preFlashPrefersMemoryThenDirectoryThenEmpty() {
        assertEquals("", FirmwareFreshnessPolicy.pickPreFlashVersion(0L, "1.3.2", "1.2.0"))
        assertEquals(
            "1.3.2",
            FirmwareFreshnessPolicy.pickPreFlashVersion(0x11L, " 1.3.2 ", "1.2.0")
        )
        assertEquals(
            "1.2.0",
            FirmwareFreshnessPolicy.pickPreFlashVersion(0x11L, "  ", " 1.2.0 ")
        )
        assertEquals("", FirmwareFreshnessPolicy.pickPreFlashVersion(0x11L, null, null))
        assertEquals("", FirmwareFreshnessPolicy.pickPreFlashVersion(0x11L, "", "  "))
    }

    @Test
    fun installedLabelGenderDiffersFromBareVersionUnknown() {
        assertEquals(
            "Installed: unknown",
            FirmwareFreshnessPolicy.formatInstalledLabel(null, false, false)
        )
        assertEquals(
            "Instalado: desconocido",
            FirmwareFreshnessPolicy.formatInstalledLabel(null, false, true)
        )
        assertEquals(
            "desconocida",
            FirmwareFreshnessPolicy.formatVersionValue(null, false, true)
        )
        assertEquals(
            "Installed: checking…",
            FirmwareFreshnessPolicy.formatInstalledLabel("1.3.2", true, false)
        )
        assertEquals(
            "Installed: 1.3.2",
            FirmwareFreshnessPolicy.formatInstalledLabel("1.3.2", false, false)
        )
    }
}
