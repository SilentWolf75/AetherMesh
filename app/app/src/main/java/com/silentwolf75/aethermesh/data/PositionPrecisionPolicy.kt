package com.silentwolf75.aethermesh.data

/**
 * Position privacy blur radius choices (meters). 0 = broadcast precise GPS.
 */
object PositionPrecisionPolicy {
    val STEPS = listOf(0, 100, 250, 500, 1000, 2000, 5000, 10000)

    fun format(meters: Int, imperial: Boolean, spanish: Boolean): String {
        if (meters <= 0) return if (spanish) "Precisa" else "Precise"
        return if (imperial) {
            if (meters < 400) "±${(meters * 3.28084 / 10).toInt() * 10} ft"
            else "±%.1f mi".format(meters / 1609.34)
        } else {
            if (meters < 1000) "±$meters m"
            else "±%.1f km".format(meters / 1000.0)
        }
    }

    fun nearestStep(meters: Int): Int =
        STEPS.minBy { kotlin.math.abs(it - meters.coerceAtLeast(0)) }
}
