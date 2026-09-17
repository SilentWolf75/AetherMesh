package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaTransferPolicyTest {

    @Test
    fun legacyHintKeepsConservativeChunkAndWindow() {
        assertEquals(128, OtaTransferPolicy.chunkSizeForLink(128, 247))
        assertEquals(OtaTransferPolicy.WINDOW_LEGACY, OtaTransferPolicy.windowForNode(128))
    }

    @Test
    fun fastHintUsesAdvertisedChunkCappedByAttMtu() {
        val chunk = OtaTransferPolicy.chunkSizeForLink(224, 247)
        assertTrue("chunk $chunk should use the fast path", chunk > 128)
        assertTrue(chunk <= 224)
        assertEquals(OtaTransferPolicy.WINDOW_FAST, OtaTransferPolicy.windowForNode(224))
    }

    @Test
    fun tinyMtuStillProducesALegalChunk() {
        assertEquals(64, OtaTransferPolicy.chunkSizeForLink(224, 23))
    }

    @Test
    fun versionMatchIsLooseAroundSemverSuffixes() {
        assertTrue(OtaTransferPolicy.versionsLookCompatible("1.3.2", "1.3.2"))
        assertTrue(OtaTransferPolicy.versionsLookCompatible("1.3.2-cec1935", "1.3.2"))
        assertFalse(OtaTransferPolicy.versionsLookCompatible("1.3.1", "1.3.2"))
        assertFalse(OtaTransferPolicy.versionsLookCompatible("", "1.3.2"))
    }

    @Test
    fun offsetGapErrorPointsAtUsbRecovery() {
        val msg = OtaTransferPolicy.nodeErrorMessage("Offset gap at 4096")
        assertTrue(msg.contains("web flasher", ignoreCase = true))
    }

    @Test
    fun rollbackGuidanceDiffersForRakVsEsp() {
        val rak = OtaTransferPolicy.rollbackGuidance(isRakNode = true, spanish = false)
        val esp = OtaTransferPolicy.rollbackGuidance(isRakNode = false, spanish = false)
        assertTrue(rak.contains("DFU") || rak.contains(".uf2"))
        assertTrue(esp.contains("OTA") || esp.contains("-usb.bin"))
        assertTrue(
            OtaTransferPolicy.rollbackGuidance(true, true).contains("bootloader")
        )
    }

    @Test
    fun esp32UploadStatusHelpers() {
        assertEquals("Preparing...", OtaTransferPolicy.STATUS_PREPARING)
        assertEquals("Uploading... 1 / 4 kB", OtaTransferPolicy.uploadingProgressStatus(1024, 4096))
        assertEquals("Update failed: boom", OtaTransferPolicy.failedStatus("boom"))
        assertEquals("BLE write failed (BEGIN)", OtaTransferPolicy.bleWriteFailed("BEGIN"))
        assertTrue(
            OtaLocalizePolicy.localizeStatus(OtaTransferPolicy.STATUS_PREPARING, true)
                .contains("Preparando")
        )
        assertTrue(
            OtaLocalizePolicy.localizeStatus(OtaTransferPolicy.STATUS_INTERRUPTED, true)
                .contains("Bluetooth")
        )
    }
}
