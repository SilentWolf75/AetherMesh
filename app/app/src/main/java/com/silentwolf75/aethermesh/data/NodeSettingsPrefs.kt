package com.silentwolf75.aethermesh.data

import android.content.SharedPreferences

/**
 * Per-node `node_settings_<id>` SharedPreferences keys and defaults. File open
 * stays at the call site; values match [NodeSettingsStore.writeFromDevice].
 */
object NodeSettingsPrefs {
    const val KEY_NODE_NAME = "node_name"
    const val KEY_NODE_SHORT = "node_short_name"
    const val KEY_LORA_SF = "lora_sf"
    const val KEY_LORA_BW = "lora_bw"
    const val KEY_LORA_TX_POWER = "lora_tx_power"
    const val KEY_REGION = "region"
    const val KEY_NODE_ROLE = "node_role"
    const val KEY_TELEMETRY_INTERVAL = "telemetry_interval"
    const val KEY_SCREEN_TIMEOUT = "screen_timeout"
    const val KEY_POWER_SAVE = "power_save_mode"
    const val KEY_POSITION_PRECISION = "position_precision"
    const val KEY_GPS_MODE = "gps_mode"
    const val KEY_GPS_DUTY_SECS = "gps_duty_interval_secs"
    const val KEY_FIXED_POSITION = "fixed_position"
    const val KEY_FIXED_LAT = "fixed_latitude"
    const val KEY_FIXED_LON = "fixed_longitude"
    const val KEY_FIXED_ALT = "fixed_altitude"
    const val KEY_REGION_CONFIGURED = "region_configured"
    const val KEY_MESH_HOP_LIMIT = "mesh_hop_limit"
    /** Firmware-reported NodeConfig.max_hop_limit; 0 = legacy firmware (8). */
    const val KEY_MAX_HOP_LIMIT = "max_hop_limit"
    const val KEY_REBROADCAST_TXDELAY = "rebroadcast_txdelay_x100"
    const val KEY_DEVICE_SYNCED = "device_synced"

    const val DEFAULT_SF = 11
    const val DEFAULT_BW = 125f
    const val DEFAULT_TX_POWER = 22
    const val DEFAULT_TELEMETRY_SECS = 60
    const val DEFAULT_SCREEN_TIMEOUT = 30
    const val DEFAULT_GPS_DUTY_SECS = 900
    const val DEFAULT_MESH_HOPS = 4
    const val DEFAULT_TXDELAY_X100 = 100

    fun readMaxHopLimit(prefs: SharedPreferences): Int =
        HopRangePolicy.firmwareMax(prefs.getInt(KEY_MAX_HOP_LIMIT, 0))

    fun prefsName(nodeId: Long): String =
        "${AppMigrationExportPolicy.NODE_SETTINGS_PREFIX}$nodeId"

    fun readLoraSf(prefs: SharedPreferences, default: Int = DEFAULT_SF): Int = try {
        prefs.getInt(KEY_LORA_SF, default)
    } catch (_: Exception) {
        default
    }

    fun clampSf(sf: Int, default: Int = DEFAULT_SF): Int =
        if (sf in 7..12) sf else default

    fun clampBw(bw: Float, default: Float = DEFAULT_BW): Float =
        if (bw > 0f) bw else default

    fun clampTxPower(tx: Int, default: Int = DEFAULT_TX_POWER): Int =
        if (tx != 0) tx else default

    fun clampTelemetrySecs(secs: Int, default: Int = DEFAULT_TELEMETRY_SECS): Int =
        if (secs > 0) secs else default
}
