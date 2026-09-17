package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaLocalizePolicyTest {
    @Test
    fun englishPassthrough() {
        assertEquals(
            OtaStartPolicy.NOT_READY,
            OtaLocalizePolicy.localizeStatus(OtaStartPolicy.NOT_READY, false)
        )
        assertEquals("Empty file", OtaLocalizePolicy.localizePickError("Empty file", false))
    }

    @Test
    fun statusMapsCommonProgressAndSuccess() {
        assertEquals("Preparando…", OtaLocalizePolicy.localizeStatus("Preparing...", true))
        assertEquals(
            "Subiendo… 42%",
            OtaLocalizePolicy.localizeStatus("Uploading... 42%", true)
        )
        assertTrue(
            OtaLocalizePolicy.localizeStatus(
                "OTA success — installed 1.3.2. Node rebooting / reconnecting…",
                true
            ).contains("OTA correcta")
        )
        assertEquals(
            "Actualización cancelada",
            OtaLocalizePolicy.localizeStatus("Update cancelled", true)
        )
        assertEquals(
            "No conectado/autenticado",
            OtaLocalizePolicy.localizeStatus(OtaStartPolicy.NOT_READY, true)
        )
    }

    @Test
    fun pickErrorMapsBoardMismatch() {
        assertEquals("Archivo vacío", OtaLocalizePolicy.localizePickError("Empty file", true))
        val mapped = OtaLocalizePolicy.localizePickError(
            "Wrong board firmware: file looks like heltec, node is rak",
            true
        )
        assertTrue(mapped.startsWith("Firmware de placa incorrecto:"))
        assertTrue(mapped.contains("el archivo parece"))
        assertTrue(mapped.contains("el nodo es"))
    }
}
