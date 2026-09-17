package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.NodeConfig

/**
 * Merge a live NodeConfig report into the remote Settings form snapshot.
 * Prefs write and Compose state stay in the UI / repository.
 */
object RemoteConfigHydratePolicy {
    fun merge(current: RemoteConfigSnapshot, cfg: NodeConfig): RemoteConfigSnapshot {
        return current.copy(
            name = cfg.nodeName.ifBlank { current.name },
            sf = if (cfg.loraSf in 7..12) cfg.loraSf else current.sf,
            bw = if (cfg.loraBw > 0f) cfg.loraBw else current.bw,
            txPower = if (cfg.loraTxPower != 0) cfg.loraTxPower else current.txPower,
            region = cfg.region,
            role = cfg.nodeRole.coerceIn(0, 1),
            telemetry = if (cfg.telemetryInterval > 0) cfg.telemetryInterval else current.telemetry,
            screen = cfg.screenTimeoutSecs,
            powerSave = cfg.powerSaveMode,
            posPrec = cfg.positionPrecision,
            gpsMode = cfg.gpsMode.coerceIn(0, 2),
            gpsDutySecs = NodeSettingsFormPolicy.snapGpsDutyIntervalSecs(cfg.gpsDutyIntervalSecs),
            fixed = cfg.fixedPosition,
            lat = cfg.fixedLatitude,
            lon = cfg.fixedLongitude,
            alt = cfg.fixedAltitude,
            hop = if (cfg.meshHopLimit in 1..HopRangePolicy.firmwareMax(cfg.maxHopLimit)) {
                cfg.meshHopLimit
            } else {
                NodeSettingsPrefs.DEFAULT_MESH_HOPS
            },
            txdelay = if (cfg.rebroadcastTxdelayX100 in 50..200) {
                cfg.rebroadcastTxdelayX100
            } else {
                NodeSettingsPrefs.DEFAULT_TXDELAY_X100
            }
        )
    }
}
