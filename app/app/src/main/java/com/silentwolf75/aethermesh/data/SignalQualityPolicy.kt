package com.silentwolf75.aethermesh.data

enum class SignalBand { NONE, WEAK, FAIR, GOOD, STRONG }

/**
 * LoRa RSSI / SNR → field chrome (bars, band color). Thresholds match the
 * node-list SignalBars the app already showed.
 */
object SignalQualityPolicy {
    fun barsFromRssi(rssi: Float): Int = when {
        rssi >= -70f -> 4
        rssi >= -85f -> 3
        rssi >= -100f -> 2
        rssi > -115f -> 1
        else -> 0
    }

    fun bandFromRssi(rssi: Float): SignalBand = when (barsFromRssi(rssi)) {
        4 -> SignalBand.STRONG
        3 -> SignalBand.GOOD
        2 -> SignalBand.FAIR
        1 -> SignalBand.WEAK
        else -> SignalBand.NONE
    }

    fun bandFromSnr(snr: Float): SignalBand = when {
        snr == 0f -> SignalBand.NONE
        snr >= 0f -> SignalBand.STRONG
        snr >= -7.5f -> SignalBand.GOOD
        snr >= -12.5f -> SignalBand.FAIR
        else -> SignalBand.WEAK
    }

    /** Map typical LoRa SNR (−20…+10 dB) onto a 0–1 meter fill. */
    fun snrFillFraction(snr: Float): Float {
        if (snr == 0f) return 0f
        return ((snr + 20f) / 30f).coerceIn(0f, 1f)
    }
}
