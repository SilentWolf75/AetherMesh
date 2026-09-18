package com.silentwolf75.aethermesh.ble

/**
 * Whether a phone restart (or an app update) should bring the mesh service
 * back up so the app reconnects to its node without being opened.
 *
 * The permission check matters because the failure it avoids is silent: with
 * Bluetooth permission revoked, a service started at boot would retry forever
 * behind a notification that only says "Disconnected", with nobody watching.
 */
object BootReconnectPolicy {
    enum class Decision {
        /** Start the service; the saved node will be reconnected. */
        START,
        /** No node has ever been paired, so there is nothing to reconnect to. */
        NO_NODE,
        /** A node is saved but Bluetooth permission is missing. */
        NO_PERMISSION
    }

    private val MAC = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

    /**
     * @param savedMac the paired node; a deliberate disconnect clears it, so a
     *   user who disconnected is not reconnected behind their back.
     */
    fun decide(savedMac: String?, hasBluetoothPermission: Boolean): Decision = when {
        savedMac.isNullOrBlank() || !MAC.matches(savedMac) -> Decision.NO_NODE
        !hasBluetoothPermission -> Decision.NO_PERMISSION
        else -> Decision.START
    }
}
