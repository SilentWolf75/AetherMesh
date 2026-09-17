package com.silentwolf75.aethermesh.data

import java.util.Locale

enum class VoltageTrend { UNKNOWN, RISING, FALLING, FLAT }

/**
 * Battery voltage trend + low-voltage safe-mode hint for node details / list.
 */
object VoltageTrendPolicy {
    const val LOW_VOLTAGE_SAFE_ENTER_V = 3.50f
    const val DELTA_THRESHOLD_V = 0.04f
    const val SAMPLE_WINDOW = 12

    fun isLowVoltageSafeHint(voltage: Float, isCharging: Boolean): Boolean =
        voltage > 0f && voltage < LOW_VOLTAGE_SAFE_ENTER_V && !isCharging

    fun trend(history: List<TelemetrySample>): VoltageTrend {
        val samples = history.filter { it.voltage > 0f }
        if (samples.size < 2) return VoltageTrend.UNKNOWN
        val newest = samples.takeLast(minOf(SAMPLE_WINDOW, samples.size))
        val delta = newest.last().voltage - newest.first().voltage
        return when {
            delta >= DELTA_THRESHOLD_V -> VoltageTrend.RISING
            delta <= -DELTA_THRESHOLD_V -> VoltageTrend.FALLING
            else -> VoltageTrend.FLAT
        }
    }

    fun format(history: List<TelemetrySample>, spanish: Boolean): String? {
        val samples = history.filter { it.voltage > 0f }
        if (samples.size < 2) return null
        val newest = samples.takeLast(minOf(SAMPLE_WINDOW, samples.size))
        val first = newest.first().voltage
        val last = newest.last().voltage
        val delta = last - first
        val arrow = when {
            delta >= DELTA_THRESHOLD_V -> "↑"
            delta <= -DELTA_THRESHOLD_V -> "↓"
            else -> "→"
        }
        val label = when {
            delta >= DELTA_THRESHOLD_V -> if (spanish) "subiendo" else "rising"
            delta <= -DELTA_THRESHOLD_V -> if (spanish) "bajando" else "falling"
            else -> if (spanish) "estable" else "flat"
        }
        val lastStr = String.format(Locale.US, "%.2f", last)
        val deltaStr = String.format(Locale.US, "%+.2f", delta)
        return "$arrow $lastStr V ($label $deltaStr V)"
    }
}
