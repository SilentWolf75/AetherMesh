package com.silentwolf75.aethermesh.data

/**
 * Mesh hop limit range. Firmware with extended range accepts up to 16 hops and
 * says so in NodeConfig.max_hop_limit; older firmware reports 0 and drops any
 * packet above 8 hops, so a limit above 8 only works once every node that may
 * relay the traffic has been upgraded.
 */
object HopRangePolicy {
    const val LEGACY_MAX = 8
    const val EXTENDED_MAX = 16

    /** Highest hop limit a node's firmware accepts, from its reported max_hop_limit. */
    fun firmwareMax(reportedMaxHopLimit: Int): Int =
        if (reportedMaxHopLimit >= EXTENDED_MAX) EXTENDED_MAX else LEGACY_MAX

    fun clamp(meshHopLimit: Int, maxHopLimit: Int = LEGACY_MAX): Int =
        meshHopLimit.coerceIn(1, maxHopLimit.coerceIn(LEGACY_MAX, EXTENDED_MAX))

    /** True when this limit is only safe on a mesh where every relay is upgraded. */
    fun needsUpgradedMesh(meshHopLimit: Int): Boolean = meshHopLimit > LEGACY_MAX
}
