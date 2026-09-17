package com.silentwolf75.aethermesh.ble

/**
 * Nordic DFU scan/timing and English status lines. Nordic DFU library calls
 * stay in [com.silentwolf75.aethermesh.data.AetherMeshRepository]; Spanish
 * mapping stays in [OtaLocalizePolicy].
 */
object DfuSessionPolicy {
    const val LEGACY_SERVICE_UUID = "00001530-1212-EFDE-1523-785FEABCD123"
    const val SECURE_SERVICE_UUID = "0000FE59-0000-1000-8000-00805F9B34FB"

    const val ENTER_ACK_TIMEOUT_MS = 10_000L
    const val BOOTLOADER_SETTLE_MS = 3_000L
    const val SCAN_TIMEOUT_MS = 15_000L
    const val SERVICE_ENGAGE_WATCHDOG_MS = 45_000L

    const val STATUS_REBOOTING = "Rebooting node into DFU bootloader..."
    const val STATUS_SEARCHING = "Searching for DFU bootloader..."
    const val STATUS_STARTING_TRANSFER = "Starting DFU transfer..."
    const val STATUS_CONNECTING = "DFU: connecting to bootloader..."
    const val STATUS_PROCESS_STARTING = "DFU: starting transfer..."
    const val STATUS_VALIDATING = "DFU: validating firmware..."
    const val STATUS_CANCELLED = "DFU cancelled"
    const val ERR_NOT_ADVERTISING =
        "DFU bootloader not advertising (node may need a newer bootloader)"
    const val ERR_NEVER_ENGAGED = "DFU service never engaged the bootloader"

    fun uploadingStatus(percent: Int, partHint: String): String =
        "DFU uploading... $percent%$partHint"

    fun completeStatus(expected: String): String {
        val label = expected.ifBlank { "new firmware" }
        return "DFU complete — node rebooting with $label"
    }

    fun failedStatus(detail: String?): String =
        "DFU failed: ${detail ?: "error"}"

    fun failedStatus(error: Int, message: String?): String =
        "DFU failed: ${message ?: "error $error"}"
}
