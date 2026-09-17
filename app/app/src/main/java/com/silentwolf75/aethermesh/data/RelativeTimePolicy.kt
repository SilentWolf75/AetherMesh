package com.silentwolf75.aethermesh.data

/**
 * Relative time / uptime / GPS duty labels used across Nodes, Map, Chat, Details.
 * UI wrappers in MeshUiFormatters pass [System.currentTimeMillis]; tests inject [nowMs].
 */
object RelativeTimePolicy {
    const val DAY_MS = 86_400_000L
    const val DEFAULT_DUTY_SECS = 900
    const val MIN_DUTY_SECS = 300
    const val MAX_DUTY_SECS = 3600

    fun lastHeard(lastActive: Long, spanish: Boolean, nowMs: Long): String {
        if (lastActive <= 0L) return if (spanish) "nunca" else "never"
        val elapsedSeconds = ((nowMs - lastActive).coerceAtLeast(0L)) / 1000L
        return when {
            elapsedSeconds < 60L -> if (spanish) "ahora" else "just now"
            elapsedSeconds < 3600L -> {
                val m = elapsedSeconds / 60L
                if (spanish) "hace ${m}m" else "${m}m ago"
            }
            elapsedSeconds < 86_400L -> {
                val h = elapsedSeconds / 3600L
                if (spanish) "hace ${h}h" else "${h}h ago"
            }
            else -> {
                val d = elapsedSeconds / 86_400L
                if (spanish) "hace ${d}d" else "${d}d ago"
            }
        }
    }

    /** Compact age for diagnostics tiles (same vocabulary as [lastHeard], caps at hours). */
    fun relativeAge(timestampMs: Long, spanish: Boolean, nowMs: Long): String {
        if (timestampMs <= 0L) return "—"
        val elapsedSeconds = ((nowMs - timestampMs).coerceAtLeast(0L)) / 1000L
        return when {
            elapsedSeconds < 60L -> if (spanish) "ahora" else "just now"
            elapsedSeconds < 3600L -> {
                val m = elapsedSeconds / 60L
                if (spanish) "hace ${m}m" else "${m}m ago"
            }
            else -> {
                val h = (elapsedSeconds / 3600L).coerceAtLeast(1L)
                if (spanish) "hace ${h}h" else "${h}h ago"
            }
        }
    }

    fun uptime(seconds: Long, spanish: Boolean): String {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            d > 0 -> if (spanish) "${d} días ${h} h" else "${d}d ${h}h"
            h > 0 -> if (spanish) "${h} h ${m} m" else "${h}h ${m}m"
            m > 0 -> if (spanish) "${m} m ${s} s" else "${m}m ${s}s"
            else -> if (spanish) "${s} s" else "${s}s"
        }
    }

    fun daysSinceHeard(lastActive: Long, nowMs: Long): Long {
        if (lastActive <= 0L) return 0L
        return (nowMs - lastActive).coerceAtLeast(0L) / DAY_MS
    }

    fun daysSinceHeardLabel(lastActive: Long, spanish: Boolean, nowMs: Long): String? {
        val d = daysSinceHeard(lastActive, nowMs)
        if (d < 1L) return null
        return if (spanish) {
            if (d == 1L) "1 día sin oír" else "$d días sin oír"
        } else {
            if (d == 1L) "1 day since heard" else "$d days since heard"
        }
    }

    fun gpsLockAge(lastPositionAt: Long, spanish: Boolean, nowMs: Long): String {
        if (lastPositionAt <= 0L) {
            return if (spanish) "Sin fijación GPS" else "No GPS lock yet"
        }
        val age = lastHeard(lastPositionAt, spanish, nowMs)
        return if (spanish) "GPS: $age" else "GPS $age"
    }

    /** gps_mode: 0 on, 1 off, 2 duty. Returns null when prefs unknown. */
    fun gpsDutyStatus(gpsMode: Int?, dutyIntervalSecs: Int, spanish: Boolean): String? {
        if (gpsMode == null || gpsMode !in 0..2) return null
        val mins = ((clampDutySecs(dutyIntervalSecs) + 59) / 60)
        return when (gpsMode) {
            0 -> if (spanish) "GPS: siempre encendido" else "GPS: always on"
            1 -> if (spanish) "GPS: apagado" else "GPS: off"
            else -> if (spanish) "GPS: periódico (${mins} min)" else "GPS: duty (${mins} min)"
        }
    }

    fun clampDutySecs(raw: Int): Int = when {
        raw <= 0 -> DEFAULT_DUTY_SECS
        else -> raw.coerceIn(MIN_DUTY_SECS, MAX_DUTY_SECS)
    }
}
