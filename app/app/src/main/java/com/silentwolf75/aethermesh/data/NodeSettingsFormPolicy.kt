package com.silentwolf75.aethermesh.data

import android.content.SharedPreferences

data class NodeSettingsForm(
    val nodeName: String,
    val nodeShortName: String,
    val loraSf: Int,
    val loraBw: Float,
    val loraTxPower: Int,
    val region: Int,
    val nodeRole: Int,
    val telemetryInterval: Int,
    val screenTimeout: Int,
    val powerSaveMode: Boolean,
    val positionPrecision: Int,
    val gpsMode: Int,
    val gpsDutyIntervalSecs: Int,
    val fixedPosition: Boolean,
    val fixedLatitude: Float,
    val fixedLongitude: Float,
    val fixedAltitude: Int,
    val meshHopLimit: Int,
    val rebroadcastTxdelayX100: Int
) {
    val fixedLatInput: String = if (fixedLatitude != 0f) fixedLatitude.toString() else ""
    val fixedLonInput: String = if (fixedLongitude != 0f) fixedLongitude.toString() else ""
    val fixedAltInput: String = if (fixedAltitude != 0) fixedAltitude.toString() else ""
}

/**
 * Phone Settings form hydrate. BLE send stays in the repository. Skip reload
 * while a JSON import is pending Apply so device sync cannot clobber it.
 */
object NodeSettingsFormPolicy {
    fun shouldReload(
        nodeKey: Long,
        loadedForNode: Long,
        deviceConfigSyncEpoch: Int,
        lastDeviceConfigSyncEpoch: Int,
        importDirty: Boolean
    ): Boolean {
        if (importDirty || nodeKey == 0L) return false
        return nodeKey != loadedForNode || deviceConfigSyncEpoch != lastDeviceConfigSyncEpoch
    }

    fun advertisedLongName(raw: String?): String =
        raw?.replace("AetherMesh-", "")?.replace("Node ", "") ?: ""

    fun snapGpsDutyIntervalSecs(secs: Int): Int {
        val options = intArrayOf(300, 900, 1800, 3600)
        val clamped = when {
            secs <= 0 -> 900
            else -> secs.coerceIn(300, 3600)
        }
        return options.minBy { kotlin.math.abs(it - clamped) }
    }

    fun readFromPrefs(
        prefs: SharedPreferences,
        advertisedName: String? = null,
        advertisedShortName: String? = null
    ): NodeSettingsForm {
        val txdelay = prefs.getInt(
            NodeSettingsPrefs.KEY_REBROADCAST_TXDELAY,
            NodeSettingsPrefs.DEFAULT_TXDELAY_X100
        )
        return NodeSettingsForm(
            nodeName = prefs.getString(NodeSettingsPrefs.KEY_NODE_NAME, null)?.takeIf { it.isNotBlank() }
                ?: advertisedLongName(advertisedName),
            nodeShortName = prefs.getString(NodeSettingsPrefs.KEY_NODE_SHORT, null)?.takeIf { it.isNotBlank() }
                ?: advertisedShortName.orEmpty(),
            loraSf = NodeSettingsPrefs.readLoraSf(prefs),
            loraBw = prefs.getFloat(NodeSettingsPrefs.KEY_LORA_BW, NodeSettingsPrefs.DEFAULT_BW),
            loraTxPower = prefs.getInt(
                NodeSettingsPrefs.KEY_LORA_TX_POWER,
                NodeSettingsPrefs.DEFAULT_TX_POWER
            ),
            region = prefs.getInt(NodeSettingsPrefs.KEY_REGION, 0),
            nodeRole = prefs.getInt(NodeSettingsPrefs.KEY_NODE_ROLE, 0),
            telemetryInterval = prefs.getInt(
                NodeSettingsPrefs.KEY_TELEMETRY_INTERVAL,
                NodeSettingsPrefs.DEFAULT_TELEMETRY_SECS
            ),
            screenTimeout = prefs.getInt(
                NodeSettingsPrefs.KEY_SCREEN_TIMEOUT,
                NodeSettingsPrefs.DEFAULT_SCREEN_TIMEOUT
            ),
            powerSaveMode = prefs.getBoolean(NodeSettingsPrefs.KEY_POWER_SAVE, false),
            positionPrecision = prefs.getInt(NodeSettingsPrefs.KEY_POSITION_PRECISION, 0),
            gpsMode = prefs.getInt(NodeSettingsPrefs.KEY_GPS_MODE, 0).coerceIn(0, 2),
            gpsDutyIntervalSecs = snapGpsDutyIntervalSecs(
                prefs.getInt(NodeSettingsPrefs.KEY_GPS_DUTY_SECS, NodeSettingsPrefs.DEFAULT_GPS_DUTY_SECS)
            ),
            fixedPosition = prefs.getBoolean(NodeSettingsPrefs.KEY_FIXED_POSITION, false),
            fixedLatitude = prefs.getFloat(NodeSettingsPrefs.KEY_FIXED_LAT, 0f),
            fixedLongitude = prefs.getFloat(NodeSettingsPrefs.KEY_FIXED_LON, 0f),
            fixedAltitude = prefs.getInt(NodeSettingsPrefs.KEY_FIXED_ALT, 0),
            meshHopLimit = LocalNodeConfigApply.clampHops(
                prefs.getInt(NodeSettingsPrefs.KEY_MESH_HOP_LIMIT, NodeSettingsPrefs.DEFAULT_MESH_HOPS),
                NodeSettingsPrefs.readMaxHopLimit(prefs)
            ),
            rebroadcastTxdelayX100 = LocalNodeConfigApply.clampTxdelay(txdelay)
        )
    }
}
