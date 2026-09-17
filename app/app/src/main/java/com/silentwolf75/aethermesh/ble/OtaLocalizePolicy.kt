package com.silentwolf75.aethermesh.ble

/**
 * Spanish UI mapping for OTA/DFU status lines and firmware-pick errors.
 * English strings stay authoritative in senders / catalog validation.
 */
object OtaLocalizePolicy {
    fun localizeStatus(status: String, spanish: Boolean): String {
        if (!spanish || status.isBlank()) return status
        return when {
            status == OtaStartPolicy.NOT_READY -> "No conectado/autenticado"
            status == OtaStartPolicy.NO_ADDRESS -> "Sin dirección del dispositivo"
            status == OtaTransferPolicy.STATUS_PREPARING -> "Preparando…"
            status == OtaTransferPolicy.STATUS_RETRYING_START -> "Reintentando inicio…"
            status == OtaTransferPolicy.STATUS_UPLOADING -> "Subiendo…"
            status.startsWith(OtaTransferPolicy.STATUS_UPLOADING) ->
                status.replace(OtaTransferPolicy.STATUS_UPLOADING, "Subiendo…")
            status == OtaTransferPolicy.STATUS_VERIFYING -> "Verificando…"
            status.startsWith("OTA success") || status.startsWith("DFU success") ||
                status.startsWith("Update verified") || status.startsWith("Update confirmed") -> {
                status
                    .replace("OTA success", "OTA correcta")
                    .replace("DFU success", "DFU correcto")
                    .replace("Update verified", "Actualización verificada")
                    .replace("Update confirmed", "Actualización confirmada")
                    .replace("installed", "instalado")
                    .replace("expected", "esperado")
                    .replace(
                        "Node rebooting / reconnecting…",
                        "Nodo reiniciando / reconectando…"
                    )
                    .replace(
                        "Rebooting / reconnecting… (confirming version)",
                        "Reiniciando / reconectando… (confirmando versión)"
                    )
                    .replace("now running", "ahora ejecuta")
                    .replace("(confirming version)", "(confirmando versión)")
            }
            status.startsWith("Update may not have applied") ->
                status.replace(
                    "Update may not have applied — still on",
                    "Es posible que la actualización no se aplicara — sigue en"
                )
            status == OtaTransferPolicy.STATUS_CANCELLED -> "Actualización cancelada"
            status.startsWith("Update failed:") ->
                status.replace("Update failed:", "Falló la actualización:")
            status.startsWith("Update interrupted") ->
                status.replace(
                    OtaTransferPolicy.STATUS_INTERRUPTED,
                    "Actualización interrumpida — se perdió Bluetooth. Reconecta e inténtalo de nuevo."
                )
            status.startsWith("DFU: connecting") -> "DFU: conectando al bootloader…"
            status.startsWith("DFU: starting") -> "DFU: iniciando transferencia…"
            status.startsWith("DFU: validating") -> "DFU: validando firmware…"
            status.startsWith("DFU uploading...") ->
                status.replace("DFU uploading...", "DFU subiendo…")
            status.startsWith("DFU complete") ->
                "DFU completo — el nodo reinicia con el nuevo firmware"
            status == DfuSessionPolicy.STATUS_CANCELLED -> "DFU cancelado"
            status.startsWith("DFU failed:") -> status.replace("DFU failed:", "DFU falló:")
            status.startsWith("Rebooting node into DFU") ->
                "Reiniciando nodo en bootloader DFU…"
            status.startsWith("Searching for DFU") -> "Buscando bootloader DFU…"
            status.startsWith("Starting DFU transfer") -> "Iniciando transferencia DFU…"
            status == "No device address" -> "Sin dirección del dispositivo"
            else -> status
        }
    }

    fun localizePickError(error: String, spanish: Boolean): String {
        if (!spanish) return error
        return when {
            error == OtaPayloadPolicy.ERR_EMPTY -> "Archivo vacío"
            error == OtaPayloadPolicy.ERR_RAK_NOT_ZIP ->
                "Las actualizaciones RAK requieren un paquete DFU .zip"
            error == OtaPayloadPolicy.ERR_RAK_BIN ->
                "Los nodos RAK necesitan un .zip DFU de Nordic — no un .bin de ESP32"
            error == OtaPayloadPolicy.ERR_RAK_TOO_SMALL ->
                "El archivo es demasiado pequeño para ser un paquete DFU"
            error == OtaPayloadPolicy.ERR_RAK_NOT_ZIP_BYTES ->
                "No es un paquete ZIP (DFU) válido"
            error == OtaPayloadPolicy.ERR_HELTEC_NOT_BIN ||
                error == OtaPayloadPolicy.ERR_ESP_ZIP ->
                "Las actualizaciones Heltec/ESP32 requieren una imagen .bin — no un .zip DFU de Nordic"
            error == OtaPayloadPolicy.ERR_ESP_TOO_SMALL ->
                "La imagen de firmware parece demasiado pequeña"
            error == OtaPayloadPolicy.ERR_ESP_NOT_BIN ->
                "No parece una imagen .bin de ESP32"
            error == OtaPayloadPolicy.ERR_USB_IMAGE ->
                "Las imágenes USB no se pueden flashear por BLE OTA — usa un -ota.bin o .zip DFU"
            error == OtaPayloadPolicy.ERR_UF2 ->
                "UF2 es para arrastrar por USB, no para BLE OTA"
            error.startsWith("Wrong board firmware:") -> {
                val rest = error.removePrefix("Wrong board firmware:")
                "Firmware de placa incorrecto:$rest"
                    .replace("file looks like", "el archivo parece")
                    .replace("node is", "el nodo es")
            }
            else -> error
        }
    }
}
