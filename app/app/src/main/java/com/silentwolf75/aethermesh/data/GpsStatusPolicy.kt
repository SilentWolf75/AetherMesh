package com.silentwolf75.aethermesh.data

/** What the GPS status badge should say for a node. */
enum class GpsBadge {
    /** Firmware reports a live onboard fix. */
    FIX,
    /** GPS powered, no current fix. */
    SEARCHING,
    /** Duty-cycle GPS powered down between fixes. */
    SLEEPING,
    OFF,
    NO_MODULE,
    /** Older firmware: coordinates exist but nothing says they are current. */
    LAST_KNOWN,
    /** Older firmware, no coordinates. */
    NO_POSITION
}

/** Where a node's reported coordinates came from (Telemetry.position_source). */
enum class PositionSourceLabel { UNKNOWN, NONE, ONBOARD_GPS, PHONE, FIXED }

/**
 * GPS status display, from the GNSS snapshot newer firmware adds to telemetry.
 * Before this, "LOCKED" meant only that the app had some coordinates stored,
 * including ones borrowed from the phone hours earlier.
 */
object GpsStatusPolicy {
    // Telemetry.GpsState / PositionSource wire values.
    const val STATE_UNKNOWN = 0
    const val STATE_ABSENT = 1
    const val STATE_OFF = 2
    const val STATE_SLEEPING = 3
    const val STATE_SEARCHING = 4
    const val STATE_FIX = 5

    fun badge(gpsState: Int, hasPosition: Boolean, configuredGpsMode: Int): GpsBadge = when (gpsState) {
        STATE_FIX -> GpsBadge.FIX
        STATE_SEARCHING -> GpsBadge.SEARCHING
        STATE_SLEEPING -> GpsBadge.SLEEPING
        STATE_OFF -> GpsBadge.OFF
        STATE_ABSENT -> GpsBadge.NO_MODULE
        // Older firmware: fall back to config, and never claim a lock.
        else -> when {
            configuredGpsMode == 1 -> GpsBadge.OFF
            hasPosition -> GpsBadge.LAST_KNOWN
            configuredGpsMode == 2 -> GpsBadge.SLEEPING
            else -> GpsBadge.NO_POSITION
        }
    }

    fun source(gpsState: Int, positionSource: Int): PositionSourceLabel {
        if (gpsState == STATE_UNKNOWN) return PositionSourceLabel.UNKNOWN
        return when (positionSource) {
            1 -> PositionSourceLabel.ONBOARD_GPS
            2 -> PositionSourceLabel.PHONE
            3 -> PositionSourceLabel.FIXED
            else -> PositionSourceLabel.NONE
        }
    }

    /** "7 used · 11 in view", or null when the firmware does not report satellites. */
    fun satellitesText(gpsState: Int, used: Int, inView: Int, spanish: Boolean = false): String? {
        if (gpsState == STATE_UNKNOWN || gpsState == STATE_ABSENT) return null
        return if (spanish) "$used en uso · $inView visibles" else "$used used · $inView in view"
    }

    /** HDOP with one decimal, or null when unknown. */
    fun hdopText(hdopX10: Int): String? =
        if (hdopX10 <= 0) null else "%d.%d".format(hdopX10 / 10, hdopX10 % 10)

    fun badgeLabel(badge: GpsBadge, satellitesUsed: Int, spanish: Boolean): String = when (badge) {
        GpsBadge.FIX -> if (spanish) "FIJADO · $satellitesUsed SAT" else "LOCKED · $satellitesUsed SATS"
        GpsBadge.SEARCHING -> if (spanish) "BUSCANDO" else "SEARCHING"
        GpsBadge.SLEEPING -> if (spanish) "EN REPOSO" else "PERIODIC SLEEP"
        GpsBadge.OFF -> if (spanish) "GPS APAGADO" else "GPS OFF"
        GpsBadge.NO_MODULE -> if (spanish) "SIN MÓDULO GPS" else "NO GPS MODULE"
        GpsBadge.LAST_KNOWN -> if (spanish) "ÚLTIMA POSICIÓN" else "LAST KNOWN"
        GpsBadge.NO_POSITION -> if (spanish) "ESPERANDO FIJACIÓN" else "WAITING FOR LOCK"
    }

    fun sourceText(source: PositionSourceLabel, fixAgeSecs: Int, spanish: Boolean): String? = when (source) {
        PositionSourceLabel.UNKNOWN -> null
        PositionSourceLabel.NONE -> if (spanish) "Ninguna" else "None"
        PositionSourceLabel.ONBOARD_GPS -> {
            val base = if (spanish) "GPS del nodo" else "Onboard GPS"
            if (fixAgeSecs > 10) "$base · ${ageText(fixAgeSecs, spanish)}" else base
        }
        PositionSourceLabel.PHONE -> if (spanish) "GPS del teléfono" else "Phone GPS"
        PositionSourceLabel.FIXED -> if (spanish) "Posición fija" else "Fixed position"
    }

    private fun ageText(seconds: Int, spanish: Boolean): String {
        val (value, unit) = when {
            seconds < 60 -> seconds to "s"
            seconds < 3600 -> seconds / 60 to "min"
            else -> seconds / 3600 to "h"
        }
        return if (spanish) "hace $value $unit" else "$value $unit ago"
    }
}
