package com.silentwolf75.aethermesh.ble

import com.silentwolf75.aethermesh.data.OtaState

sealed class OtaStartDecision {
    object AlreadyActive : OtaStartDecision()
    data class Blocked(val state: OtaState) : OtaStartDecision()
    object Run : OtaStartDecision()
}

/**
 * Shared start gate for Heltec BLE OTA and RAK DFU. Transfer and Nordic DFU
 * stay in the repository.
 */
object OtaStartPolicy {
    const val NOT_READY = "Not connected/authenticated"
    const val NO_ADDRESS = "No device address"

    fun begin(
        active: Boolean,
        connected: Boolean,
        authenticated: Boolean
    ): OtaStartDecision {
        if (active) return OtaStartDecision.AlreadyActive
        if (!connected || !authenticated) {
            return OtaStartDecision.Blocked(OtaState(error = true, status = NOT_READY))
        }
        return OtaStartDecision.Run
    }

    fun requireAddress(mac: String?): OtaStartDecision =
        if (mac.isNullOrBlank()) {
            OtaStartDecision.Blocked(OtaState(error = true, status = NO_ADDRESS))
        } else {
            OtaStartDecision.Run
        }
}
