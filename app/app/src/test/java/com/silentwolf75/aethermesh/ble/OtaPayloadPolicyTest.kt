package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaPayloadPolicyTest {
    @Test
    fun rakAndEspHintsAreDistinct() {
        assertTrue(OtaPayloadPolicy.isRakTarget("RAK4631", "WisBlock"))
        assertTrue(OtaPayloadPolicy.isRakTarget("nRF52840"))
        assertTrue(OtaPayloadPolicy.isRakTarget("T1000-E"))
        assertTrue(OtaPayloadPolicy.isRakTarget("SenseCAP Card Tracker T1000-E"))
        assertTrue(OtaPayloadPolicy.isRakTarget("T-Echo"))
        assertTrue(OtaPayloadPolicy.isRakTarget("LILYGO T-Echo"))
        assertFalse(OtaPayloadPolicy.isRakTarget("Heltec V4"))
        assertTrue(OtaPayloadPolicy.isEspTarget("Heltec V4", "esp32"))
        assertTrue(OtaPayloadPolicy.isEspTarget("LilyGo T-Deck"))
        assertFalse(OtaPayloadPolicy.isEspTarget("RAK4631"))
        assertFalse(OtaPayloadPolicy.isEspTarget("T1000-E"))
        assertFalse(OtaPayloadPolicy.isEspTarget("T-Echo"))
        assertFalse(OtaPayloadPolicy.isRakTarget())
        assertFalse(OtaPayloadPolicy.isEspTarget(null, "  "))
    }

    @Test
    fun rejectsEmptyUsbAndUf2() {
        assertEquals(OtaPayloadPolicy.ERR_EMPTY, OtaPayloadPolicy.validate(ByteArray(0), "x.bin", false))
        assertEquals(
            OtaPayloadPolicy.ERR_USB_IMAGE,
            OtaPayloadPolicy.validate(ByteArray(2048) { 0xE9.toByte() }, "app-usb.bin", false)
        )
        assertEquals(
            OtaPayloadPolicy.ERR_UF2,
            OtaPayloadPolicy.validate(ByteArray(2048), "firmware.uf2", false)
        )
    }

    @Test
    fun rakRequiresZipWithPkHeader() {
        assertEquals(
            OtaPayloadPolicy.ERR_RAK_BIN,
            OtaPayloadPolicy.validate(ByteArray(2048) { 0xE9.toByte() }, "fw.bin", true)
        )
        assertEquals(
            OtaPayloadPolicy.ERR_RAK_TOO_SMALL,
            OtaPayloadPolicy.validate(ByteArray(128), "fw.zip", true)
        )
        val badZip = ByteArray(512) { 0 }
        assertEquals(
            OtaPayloadPolicy.ERR_RAK_NOT_ZIP_BYTES,
            OtaPayloadPolicy.validate(badZip, "fw.zip", true)
        )
        val goodZip = ByteArray(512).also {
            it[0] = 'P'.code.toByte()
            it[1] = 'K'.code.toByte()
        }
        assertNull(OtaPayloadPolicy.validate(goodZip, "fw.zip", true))
    }

    @Test
    fun espRejectsZipAndTinyImages() {
        assertEquals(
            OtaPayloadPolicy.ERR_ESP_ZIP,
            OtaPayloadPolicy.validate(ByteArray(2048), "fw.zip", false)
        )
        assertEquals(
            OtaPayloadPolicy.ERR_ESP_TOO_SMALL,
            OtaPayloadPolicy.validate(ByteArray(100), "fw.bin", false)
        )
        val espImage = ByteArray(2048).also { it[0] = 0xE9.toByte() }
        assertNull(OtaPayloadPolicy.validate(espImage, "app-ota.bin", false))
        // Named .bin without magic is still accepted (many builds are valid).
        assertNull(OtaPayloadPolicy.validate(ByteArray(2048), "app.bin", false))
    }

    @Test
    fun boardMismatchUsesCatalog() {
        val err = OtaPayloadPolicy.validate(
            ByteArray(2048).also { it[0] = 0xE9.toByte() },
            "aethermesh-heltec-v4-ota.bin",
            isRakNode = false,
            expectedBoardId = "rak4631"
        )
        assertTrue(err!!.startsWith("Wrong board firmware:"))
    }

    @Test
    fun spanishPickErrorsMatchConstants() {
        assertEquals(
            "Archivo vacío",
            OtaLocalizePolicy.localizePickError(OtaPayloadPolicy.ERR_EMPTY, true)
        )
        assertTrue(
            OtaLocalizePolicy.localizePickError(OtaPayloadPolicy.ERR_RAK_BIN, true)
                .contains("RAK")
        )
    }
}
