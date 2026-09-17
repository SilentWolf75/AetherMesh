package com.silentwolf75.aethermesh.ble

/**
 * Chunk size and window for Heltec ESP32 BLE OTA.
 *
 * A node that advertises `READY.next_offset >= [CHUNK_FAST_CAP]` is running
 * firmware with the 512-byte RX ring, so the ceiling is what one ATT write
 * carries. Unknown or legacy nodes keep a conservative 128-byte chunk and
 * a 4-chunk window against a 256-byte RX-slot assumption.
 */
object OtaTransferPolicy {
    const val CHUNK_FAST_CAP = 224
    const val CHUNK_RELIABLE = 128
    const val WINDOW_FAST = 8
    const val WINDOW_LEGACY = 4
    const val PROTO_OVERHEAD = 56
    const val INTER_CHUNK_MS = 0L

    const val STATUS_PREPARING = "Preparing..."
    const val STATUS_RETRYING_START = "Retrying start..."
    const val STATUS_UPLOADING = "Uploading..."
    const val STATUS_VERIFYING = "Verifying..."
    const val STATUS_CANCELLED = "Update cancelled"
    const val STATUS_INTERRUPTED =
        "Update interrupted — Bluetooth dropped. Reconnect and retry the update."
    const val ERR_NEVER_READY = "Node never became ready"
    const val ERR_BLE_WRITE_REPEATED = "BLE write failed repeatedly"

    fun uploadingProgressStatus(offsetBytes: Int, totalBytes: Int): String =
        "Uploading... ${offsetBytes / 1024} / ${totalBytes / 1024} kB"

    fun failedStatus(detail: String?): String =
        "Update failed: ${detail ?: "error"}"

    fun bleWriteFailed(opName: String): String =
        "BLE write failed ($opName)"

    fun chunkSizeForLink(nodeHint: Int, negotiatedMtu: Int): Int {
        val attMax = (negotiatedMtu - 3).coerceAtLeast(20)
        val linkCap = (attMax - PROTO_OVERHEAD).coerceAtLeast(64)
        val wanted = if (nodeHint >= CHUNK_FAST_CAP) {
            nodeHint.coerceAtMost(linkCap)
        } else {
            CHUNK_RELIABLE.coerceAtMost(linkCap.coerceAtMost(256 - PROTO_OVERHEAD))
        }
        return wanted.coerceAtLeast(64)
    }

    fun windowForNode(nodeHint: Int): Int =
        if (nodeHint >= CHUNK_FAST_CAP) WINDOW_FAST else WINDOW_LEGACY

    fun versionsLookCompatible(reported: String, expected: String): Boolean {
        val a = reported.trim().lowercase()
        val b = expected.trim().lowercase()
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        if (a.contains(b) || b.contains(a)) return true
        val aBase = a.substringBefore('-').substringBefore('+')
        val bBase = b.substringBefore('-').substringBefore('+')
        return aBase.isNotEmpty() && aBase == bBase
    }

    fun successStatus(expected: String, dfu: Boolean): String {
        val label = expected.ifBlank { "new firmware" }
        return if (dfu) {
            "DFU success — installed $label. Node rebooting / reconnecting…"
        } else {
            "OTA success — installed $label. Node rebooting / reconnecting…"
        }
    }

    fun reconnectingStatus(expected: String, dfu: Boolean): String {
        val label = expected.ifBlank { "new firmware" }
        val kind = if (dfu) "DFU" else "OTA"
        return "$kind success — expected $label. Rebooting / reconnecting… (confirming version)"
    }

    fun successState(expected: String, status: String) = com.silentwolf75.aethermesh.data.OtaState(
        progress = 100,
        done = true,
        status = status,
        expectedVersion = expected
    )

    fun nodeErrorMessage(raw: String): String {
        val msg = raw.ifEmpty { "node reported error" }
        return if (msg.contains("Offset gap", ignoreCase = true)) {
            "$msg — this Heltec build needs one USB flash from the web flasher, then BLE OTA will work."
        } else {
            msg
        }
    }

    /** User-visible recovery guidance after a failed mid-OTA / DFU attempt. */
    fun rollbackGuidance(isRakNode: Boolean, spanish: Boolean): String =
        if (spanish) {
            if (isRakNode)
                "Si el DFU falló a medias: el bootloader suele conservar el firmware actual al agotar el tiempo. Si el nodo no vuelve a BLE, flashea el .uf2 por USB (arrastrar al disco RAK) o usa el flasher web."
            else
                "Si la OTA falló a medias: el nodo normalmente sigue con el firmware anterior (partición activa). Si no responde por BLE, recupera por USB con el flasher web y la imagen -usb.bin."
        } else {
            if (isRakNode)
                "If DFU failed mid-way: the bootloader usually keeps the current image when it times out. If the node never returns to BLE, flash the .uf2 over USB (drag onto the RAK drive) or use the web flasher."
            else
                "If OTA failed mid-way: the node normally keeps running the previous firmware (active partition). If it will not reconnect over BLE, recover over USB with the web flasher and the -usb.bin image."
        }
}
