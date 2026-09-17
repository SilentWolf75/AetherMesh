package com.silentwolf75.aethermesh.ble

/**
 * GATT connect / switch / MTU handshake timings. Backoff math stays in
 * [ReconnectPolicy]; post-settings close/reconnect stays in
 * [com.silentwolf75.aethermesh.data.SettingsRebootPolicy].
 */
object BleConnectPolicy {
    const val PREF_PAIRED_MAC = "paired_mac"
    const val FALLBACK_NODE_LABEL = "AetherMesh Node"
    const val ADVERT_NAME_PREFIX = "AetherMesh-"

    const val MAX_RECONNECT_ATTEMPTS = 12
    const val AUTO_CONNECT_DELAY_MS = 1_500L
    const val DEVICE_SWITCH_SETTLE_MS = 500L
    const val CONNECT_WATCHDOG_MS = 15_000L
    const val REQUESTED_MTU = 256
    const val MTU_FALLBACK_DISCOVER_MS = 800L
    const val FORCE_REFRESH_MIN_MS = 200L
    const val POST_REBOOT_RECONNECT_PAD_MS = 500L

    fun postRebootConnectDelay(closeAfterMs: Long, reconnectAfterMs: Long): Long =
        reconnectAfterMs.coerceAtLeast(closeAfterMs + POST_REBOOT_RECONNECT_PAD_MS)

    fun forceRefreshDelay(requestedMs: Long): Long =
        requestedMs.coerceAtLeast(FORCE_REFRESH_MIN_MS)

    fun scanDisplayName(advertisedName: String?): String =
        advertisedName ?: FALLBACK_NODE_LABEL

    fun matchesAdvertName(deviceName: String?): Boolean =
        deviceName?.startsWith(ADVERT_NAME_PREFIX) == true
}
