package com.silentwolf75.aethermesh.data

/**
 * How busy the channel is and how much of the hourly transmit allowance the
 * node has used, as the connected node reports them in its diagnostics.
 */
object ChannelUsePolicy {
    enum class Level { CALM, BUSY, CONGESTED }

    /** Above this the node holds back telemetry and announcements (firmware MeshMath.h). */
    const val ROUTINE_TRAFFIC_LIMIT = 25

    fun channelLevel(percent: Int): Level = when {
        percent <= ROUTINE_TRAFFIC_LIMIT -> Level.CALM
        percent <= 50 -> Level.BUSY
        else -> Level.CONGESTED
    }

    /** "4%", or "4% of 10%" where a regional hourly limit applies. */
    fun airtimeLabel(txPercent: Int, limitPercent: Int): String =
        if (limitPercent in 1..99) "$txPercent% of $limitPercent%" else "$txPercent%"

    fun airtimeLevel(txPercent: Int, limitPercent: Int): Level {
        val limit = if (limitPercent in 1..99) limitPercent else 100
        return when {
            txPercent * 100 < limit * 60 -> Level.CALM
            txPercent < limit -> Level.BUSY
            else -> Level.CONGESTED
        }
    }

    /** Firmware before these fields reports a limit of 0; show nothing then. */
    fun isReported(limitPercent: Int): Boolean = limitPercent > 0
}
