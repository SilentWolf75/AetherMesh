package com.silentwolf75.aethermesh.data

/**
 * Transmit power range for the settings slider, in dBm at the antenna.
 *
 * Mirrors firmware/src/TxPower.h: boards with an external amplifier reach
 * further than the radio chip's 22 dBm, and the region caps everything
 * (US915 30 dBm, EU868 27 dBm). Older firmware treated the number as the
 * chip setting; it migrates saved values on first boot.
 */
object TxPowerPolicy {
    const val MIN_DBM = 10
    const val CHIP_MAX_DBM = 22

    /** Highest output of the board, from the model name its telemetry reports. */
    fun boardMaxDbm(model: String?): Int {
        val m = model?.trim()?.lowercase().orEmpty()
        return when {
            m == "heltec v4" -> 28
            m == "rak 1w" || m.contains("rak3401") -> 29
            else -> CHIP_MAX_DBM
        }
    }

    /** Regulatory ceiling; region 1 is EU868, anything else US915. */
    fun regionMaxDbm(region: Int): Int = if (region == 1) 27 else 30

    fun maxDbm(model: String?, region: Int): Int =
        minOf(boardMaxDbm(model), regionMaxDbm(region))

    fun clamp(value: Int, model: String?, region: Int): Int =
        value.coerceIn(MIN_DBM, maxDbm(model, region))
}
