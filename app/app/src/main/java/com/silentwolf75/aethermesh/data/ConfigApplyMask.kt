package com.silentwolf75.aethermesh.data

/**
 * Sparse NodeConfig.apply_mask bits — must match firmware CFG_APPLY_* in main.cpp.
 * [diff] builds the mask from a live form vs the last known baseline.
 */
object ConfigApplyMask {
    const val NAME = 1 shl 0
    const val SF = 1 shl 1
    const val BW = 1 shl 2
    const val TX = 1 shl 3
    const val REGION = 1 shl 4
    const val ROLE = 1 shl 5
    const val TELEMETRY = 1 shl 6
    const val SCREEN = 1 shl 7
    const val POWER_SAVE = 1 shl 8
    const val POS_PREC = 1 shl 9
    const val GPS_MODE = 1 shl 10
    const val FIXED = 1 shl 11
    const val HOP = 1 shl 12
    const val TXDELAY = 1 shl 13

    fun diff(base: RemoteConfigSnapshot, next: RemoteConfigSnapshot): Int {
        var mask = 0
        if (next.name.trim() != base.name) mask = mask or NAME
        if (next.sf != base.sf) mask = mask or SF
        if (next.bw != base.bw) mask = mask or BW
        if (next.txPower != base.txPower) mask = mask or TX
        if (next.region != base.region) mask = mask or REGION
        if (next.role != base.role) mask = mask or ROLE
        if (next.telemetry != base.telemetry) mask = mask or TELEMETRY
        if (next.screen != base.screen) mask = mask or SCREEN
        if (next.powerSave != base.powerSave) mask = mask or POWER_SAVE
        if (next.posPrec != base.posPrec) mask = mask or POS_PREC
        if (next.gpsMode != base.gpsMode || next.gpsDutySecs != base.gpsDutySecs) {
            mask = mask or GPS_MODE
        }
        if (next.fixed != base.fixed || next.lat != base.lat ||
            next.lon != base.lon || next.alt != base.alt
        ) {
            mask = mask or FIXED
        }
        if (next.hop != base.hop) mask = mask or HOP
        if (next.txdelay != base.txdelay) mask = mask or TXDELAY
        return mask
    }
}

/** Phone-side remote Settings form snapshot (baseline or current draft). */
data class RemoteConfigSnapshot(
    val name: String,
    val sf: Int,
    val bw: Float,
    val txPower: Int,
    val region: Int,
    val role: Int,
    val telemetry: Int,
    val screen: Int,
    val powerSave: Boolean,
    val posPrec: Int,
    val gpsMode: Int,
    val gpsDutySecs: Int,
    val fixed: Boolean,
    val lat: Float,
    val lon: Float,
    val alt: Int,
    val hop: Int,
    val txdelay: Int
)
