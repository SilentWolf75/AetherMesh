package com.silentwolf75.aethermesh.data

import android.content.SharedPreferences
import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.NodeConfig

data class LocalNodeConfigRequest(
    val name: String,
    val shortName: String,
    val sf: Int,
    val bw: Float,
    val txPower: Int,
    val region: Int,
    val role: Int,
    val telemetryInterval: Int = 60,
    val screenTimeout: Int = 30,
    val powerSaveMode: Boolean = false,
    val positionPrecision: Int = 0,
    val gpsMode: Int = 0,
    val gpsDutyIntervalSecs: Int = 900,
    val fixedPosition: Boolean = false,
    val fixedLatitude: Float = 0f,
    val fixedLongitude: Float = 0f,
    val fixedAltitude: Int = 0,
    val meshHopLimit: Int = 4,
    val rebroadcastTxdelayX100: Int = 100,
    /** Connected node's firmware ceiling ([NodeSettingsPrefs.readMaxHopLimit]). */
    val maxHopLimit: Int = HopRangePolicy.LEGACY_MAX
)

/** Local BLE Settings apply (recipient 0). Clamps differ from [RemoteConfigApply]. */
object LocalNodeConfigApply {
    fun clampHops(meshHopLimit: Int, maxHopLimit: Int = HopRangePolicy.LEGACY_MAX): Int =
        HopRangePolicy.clamp(meshHopLimit, maxHopLimit)

    fun clampTxdelay(rebroadcastTxdelayX100: Int): Int = when {
        rebroadcastTxdelayX100 <= 0 -> 100
        else -> rebroadcastTxdelayX100.coerceIn(50, 200)
    }

    fun build(localNodeId: Long, packetId: Int, request: LocalNodeConfigRequest): MeshPacket {
        val hops = clampHops(request.meshHopLimit, request.maxHopLimit)
        val txdelay = clampTxdelay(request.rebroadcastTxdelayX100)
        val dutySecs = RemoteConfigApply.clampDutySecs(request.gpsDutyIntervalSecs)
        val clippedShort = NodeNamePolicy.clipShort(request.shortName)
        val config = NodeConfig.newBuilder()
            .setNodeName(request.name)
            .setNodeShortName(clippedShort)
            .setLoraSf(request.sf)
            .setLoraBw(request.bw)
            .setLoraTxPower(request.txPower)
            .setRegion(request.region)
            .setNodeRole(request.role)
            .setTelemetryInterval(request.telemetryInterval)
            .setScreenTimeoutSecs(request.screenTimeout)
            .setPowerSaveMode(request.powerSaveMode)
            .setPositionPrecision(request.positionPrecision)
            .setGpsMode(request.gpsMode.coerceIn(0, 2))
            .setGpsDutyIntervalSecs(dutySecs)
            .setFixedPosition(request.fixedPosition)
            .setFixedLatitude(request.fixedLatitude)
            .setFixedLongitude(request.fixedLongitude)
            .setFixedAltitude(request.fixedAltitude)
            .setMeshHopLimit(hops)
            .setRebroadcastTxdelayX100(txdelay)
            .build()
        return MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(0)
            .setPacketId(packetId)
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setConfig(config)
            .build()
    }
}

object NodeSettingsStore {
    fun writeFromDevice(prefs: SharedPreferences, config: NodeConfig) {
        prefs.edit()
            .putString(NodeSettingsPrefs.KEY_NODE_NAME, config.nodeName)
            .apply {
                val short = NodeNamePolicy.clipShort(config.nodeShortName)
                if (short.isNotEmpty()) putString(NodeSettingsPrefs.KEY_NODE_SHORT, short)
            }
            .putInt(NodeSettingsPrefs.KEY_LORA_SF, NodeSettingsPrefs.clampSf(config.loraSf))
            .putFloat(NodeSettingsPrefs.KEY_LORA_BW, NodeSettingsPrefs.clampBw(config.loraBw))
            .putInt(NodeSettingsPrefs.KEY_LORA_TX_POWER, NodeSettingsPrefs.clampTxPower(config.loraTxPower))
            .putInt(NodeSettingsPrefs.KEY_REGION, config.region)
            .putInt(NodeSettingsPrefs.KEY_NODE_ROLE, config.nodeRole)
            .putInt(
                NodeSettingsPrefs.KEY_TELEMETRY_INTERVAL,
                NodeSettingsPrefs.clampTelemetrySecs(config.telemetryInterval)
            )
            .putInt(NodeSettingsPrefs.KEY_SCREEN_TIMEOUT, config.screenTimeoutSecs)
            .putBoolean(NodeSettingsPrefs.KEY_POWER_SAVE, config.powerSaveMode)
            .putInt(NodeSettingsPrefs.KEY_POSITION_PRECISION, config.positionPrecision)
            .putInt(NodeSettingsPrefs.KEY_GPS_MODE, config.gpsMode.coerceIn(0, 2))
            .putInt(
                NodeSettingsPrefs.KEY_GPS_DUTY_SECS,
                RemoteConfigApply.clampDutySecs(config.gpsDutyIntervalSecs)
            )
            .putBoolean(NodeSettingsPrefs.KEY_FIXED_POSITION, config.fixedPosition)
            .putFloat(NodeSettingsPrefs.KEY_FIXED_LAT, config.fixedLatitude)
            .putFloat(NodeSettingsPrefs.KEY_FIXED_LON, config.fixedLongitude)
            .putInt(NodeSettingsPrefs.KEY_FIXED_ALT, config.fixedAltitude)
            .putBoolean(NodeSettingsPrefs.KEY_REGION_CONFIGURED, config.regionConfigured)
            .putInt(
                NodeSettingsPrefs.KEY_MESH_HOP_LIMIT,
                if (config.meshHopLimit in 1..HopRangePolicy.firmwareMax(config.maxHopLimit)) {
                    config.meshHopLimit
                } else {
                    NodeSettingsPrefs.DEFAULT_MESH_HOPS
                }
            )
            .putInt(NodeSettingsPrefs.KEY_MAX_HOP_LIMIT, config.maxHopLimit)
            .putInt(
                NodeSettingsPrefs.KEY_REBROADCAST_TXDELAY,
                LocalNodeConfigApply.clampTxdelay(config.rebroadcastTxdelayX100)
            )
            .putBoolean(NodeSettingsPrefs.KEY_DEVICE_SYNCED, true)
            .apply()
    }
}
