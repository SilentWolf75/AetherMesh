package com.silentwolf75.aethermesh.ble

import com.silentwolf75.aethermesh.data.FirmwareCatalog
import com.silentwolf75.aethermesh.data.BoardRegistry

/**
 * Infer ESP32 vs RAK OTA target and reject wrong payload types before flash.
 * Catalog board-id mismatch stays in [FirmwareCatalog.boardMismatchError].
 */
object OtaPayloadPolicy {
    const val ERR_EMPTY = "Empty file"
    const val ERR_USB_IMAGE =
        "USB images cannot be flashed over BLE OTA — use a -ota.bin or DFU .zip"
    const val ERR_UF2 = "UF2 is for USB drag-and-drop, not BLE OTA"
    const val ERR_RAK_BIN = "RAK nodes need a Nordic DFU .zip — not an ESP32 .bin"
    const val ERR_RAK_NOT_ZIP = "RAK updates need a .zip DFU package"
    const val ERR_RAK_TOO_SMALL = "File too small to be a DFU package"
    const val ERR_RAK_NOT_ZIP_BYTES = "Not a valid ZIP (DFU) package"
    const val ERR_ESP_ZIP =
        "Heltec/ESP32 updates need a .bin image — not a Nordic DFU .zip"
    const val ERR_ESP_TOO_SMALL = "Firmware image looks too small"
    const val ERR_ESP_NOT_BIN = "Does not look like an ESP32 .bin image"
    const val ERR_HELTEC_NOT_BIN = "Heltec updates need a .bin image (not .zip)"

    fun isRakTarget(vararg hints: String?): Boolean {
        hints.firstNotNullOfOrNull { BoardRegistry.forModel(it) }?.let {
            return it.updateFormat == "nordic-dfu"
        }
        val haystack = hints.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
            .lowercase()
        if (haystack.isEmpty()) return false
        return haystack.contains("rak") ||
            haystack.contains("wisblock") ||
            haystack.contains("nrf52") ||
            haystack.contains("nrf52840")
    }

    fun isEspTarget(vararg hints: String?): Boolean {
        hints.firstNotNullOfOrNull { BoardRegistry.forModel(it) }?.let {
            return it.updateFormat == "esp-bin"
        }
        val haystack = hints.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
            .lowercase()
        if (haystack.isEmpty()) return false
        return haystack.contains("heltec") ||
            haystack.contains("t-deck") ||
            haystack.contains("tdeck") ||
            haystack.contains("crowpanel") ||
            haystack.contains("esp32")
    }

    /**
     * Reject clearly wrong OTA payloads before flashing.
     * Heltec/ESP32: `.bin` only (never Nordic DFU `.zip` / UF2 / USB merge).
     * RAK/nRF52: `.zip` DFU package only.
     * @return error message, or null if OK.
     */
    fun validate(
        bytes: ByteArray,
        fileName: String,
        isRakNode: Boolean,
        expectedBoardId: String? = null
    ): String? {
        if (bytes.isEmpty()) return ERR_EMPTY
        val lower = fileName.lowercase()
        if (lower.contains("-usb") || lower.endsWith("-usb.bin")) {
            return ERR_USB_IMAGE
        }
        if (lower.endsWith(".uf2")) {
            return ERR_UF2
        }
        FirmwareCatalog.boardMismatchError(fileName, expectedBoardId)?.let { return it }
        return if (isRakNode) {
            when {
                lower.endsWith(".bin") -> ERR_RAK_BIN
                !lower.endsWith(".zip") -> ERR_RAK_NOT_ZIP
                bytes.size < 256 -> ERR_RAK_TOO_SMALL
                bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte() ->
                    ERR_RAK_NOT_ZIP_BYTES
                else -> null
            }
        } else {
            when {
                lower.endsWith(".zip") -> ERR_ESP_ZIP
                bytes.size < 1024 -> ERR_ESP_TOO_SMALL
                bytes[0] != 0xE9.toByte() && !lower.endsWith(".bin") -> ERR_ESP_NOT_BIN
                else -> null
            }
        }
    }
}
