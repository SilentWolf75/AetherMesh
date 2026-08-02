package com.example.aethermesh.data

/**
 * Sparse NodeConfig.apply_mask bits — must match firmware CFG_APPLY_* in main.cpp.
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
}
