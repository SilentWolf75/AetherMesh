package com.silentwolf75.aethermesh.data

import android.content.SharedPreferences
import org.json.JSONObject

/** Phone-side node Settings form snapshot for JSON backup/restore (not a BLE packet). */
data class NodeSettingsSnapshot(
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
    val fixedAltitude: Int
)

object NodeSettingsBackup {
    fun toJson(snapshot: NodeSettingsSnapshot): String =
        JSONObject().apply {
            put(NodeSettingsPrefs.KEY_NODE_NAME, snapshot.nodeName)
            put(NodeSettingsPrefs.KEY_NODE_SHORT, snapshot.nodeShortName)
            put(NodeSettingsPrefs.KEY_LORA_SF, snapshot.loraSf)
            put(NodeSettingsPrefs.KEY_LORA_BW, snapshot.loraBw.toDouble())
            put(NodeSettingsPrefs.KEY_LORA_TX_POWER, snapshot.loraTxPower)
            put(NodeSettingsPrefs.KEY_REGION, snapshot.region)
            put(NodeSettingsPrefs.KEY_NODE_ROLE, snapshot.nodeRole)
            put(NodeSettingsPrefs.KEY_TELEMETRY_INTERVAL, snapshot.telemetryInterval)
            put(NodeSettingsPrefs.KEY_SCREEN_TIMEOUT, snapshot.screenTimeout)
            put(NodeSettingsPrefs.KEY_POWER_SAVE, snapshot.powerSaveMode)
            put(NodeSettingsPrefs.KEY_POSITION_PRECISION, snapshot.positionPrecision)
            put(NodeSettingsPrefs.KEY_GPS_MODE, snapshot.gpsMode)
            put(NodeSettingsPrefs.KEY_GPS_DUTY_SECS, snapshot.gpsDutyIntervalSecs)
            put(NodeSettingsPrefs.KEY_FIXED_POSITION, snapshot.fixedPosition)
            put(NodeSettingsPrefs.KEY_FIXED_LAT, snapshot.fixedLatitude)
            put(NodeSettingsPrefs.KEY_FIXED_LON, snapshot.fixedLongitude)
            put(NodeSettingsPrefs.KEY_FIXED_ALT, snapshot.fixedAltitude)
        }.toString(2)

    fun fromJson(json: String, fallback: NodeSettingsSnapshot): NodeSettingsSnapshot {
        val obj = JSONObject(json)
        return NodeSettingsSnapshot(
            nodeName = obj.optString(NodeSettingsPrefs.KEY_NODE_NAME, fallback.nodeName),
            nodeShortName = obj.optString(NodeSettingsPrefs.KEY_NODE_SHORT, fallback.nodeShortName),
            loraSf = obj.optInt(NodeSettingsPrefs.KEY_LORA_SF, fallback.loraSf),
            loraBw = obj.optDouble(NodeSettingsPrefs.KEY_LORA_BW, fallback.loraBw.toDouble()).toFloat(),
            loraTxPower = obj.optInt(NodeSettingsPrefs.KEY_LORA_TX_POWER, fallback.loraTxPower),
            region = obj.optInt(NodeSettingsPrefs.KEY_REGION, fallback.region),
            nodeRole = obj.optInt(NodeSettingsPrefs.KEY_NODE_ROLE, fallback.nodeRole),
            telemetryInterval = obj.optInt(NodeSettingsPrefs.KEY_TELEMETRY_INTERVAL, fallback.telemetryInterval),
            screenTimeout = obj.optInt(NodeSettingsPrefs.KEY_SCREEN_TIMEOUT, fallback.screenTimeout),
            powerSaveMode = obj.optBoolean(NodeSettingsPrefs.KEY_POWER_SAVE, fallback.powerSaveMode),
            positionPrecision = obj.optInt(NodeSettingsPrefs.KEY_POSITION_PRECISION, fallback.positionPrecision),
            gpsMode = obj.optInt(NodeSettingsPrefs.KEY_GPS_MODE, fallback.gpsMode).coerceIn(0, 2),
            gpsDutyIntervalSecs = obj.optInt(
                NodeSettingsPrefs.KEY_GPS_DUTY_SECS,
                fallback.gpsDutyIntervalSecs
            ),
            fixedPosition = obj.optBoolean(NodeSettingsPrefs.KEY_FIXED_POSITION, fallback.fixedPosition),
            fixedLatitude = obj.optDouble(
                NodeSettingsPrefs.KEY_FIXED_LAT,
                fallback.fixedLatitude.toDouble()
            ).toFloat(),
            fixedLongitude = obj.optDouble(
                NodeSettingsPrefs.KEY_FIXED_LON,
                fallback.fixedLongitude.toDouble()
            ).toFloat(),
            fixedAltitude = obj.optInt(NodeSettingsPrefs.KEY_FIXED_ALT, fallback.fixedAltitude)
        )
    }

    fun writeToPrefs(prefs: SharedPreferences, snapshot: NodeSettingsSnapshot) {
        prefs.edit()
            .putString(NodeSettingsPrefs.KEY_NODE_NAME, snapshot.nodeName)
            .putString(NodeSettingsPrefs.KEY_NODE_SHORT, snapshot.nodeShortName)
            .putInt(NodeSettingsPrefs.KEY_LORA_SF, snapshot.loraSf)
            .putFloat(NodeSettingsPrefs.KEY_LORA_BW, snapshot.loraBw)
            .putInt(NodeSettingsPrefs.KEY_LORA_TX_POWER, snapshot.loraTxPower)
            .putInt(NodeSettingsPrefs.KEY_REGION, snapshot.region)
            .putInt(NodeSettingsPrefs.KEY_NODE_ROLE, snapshot.nodeRole)
            .putInt(NodeSettingsPrefs.KEY_TELEMETRY_INTERVAL, snapshot.telemetryInterval)
            .putInt(NodeSettingsPrefs.KEY_SCREEN_TIMEOUT, snapshot.screenTimeout)
            .putBoolean(NodeSettingsPrefs.KEY_POWER_SAVE, snapshot.powerSaveMode)
            .putInt(NodeSettingsPrefs.KEY_POSITION_PRECISION, snapshot.positionPrecision)
            .putInt(NodeSettingsPrefs.KEY_GPS_MODE, snapshot.gpsMode)
            .putInt(NodeSettingsPrefs.KEY_GPS_DUTY_SECS, snapshot.gpsDutyIntervalSecs)
            .putBoolean(NodeSettingsPrefs.KEY_FIXED_POSITION, snapshot.fixedPosition)
            .putFloat(NodeSettingsPrefs.KEY_FIXED_LAT, snapshot.fixedLatitude)
            .putFloat(NodeSettingsPrefs.KEY_FIXED_LON, snapshot.fixedLongitude)
            .putInt(NodeSettingsPrefs.KEY_FIXED_ALT, snapshot.fixedAltitude)
            .apply()
    }
}
