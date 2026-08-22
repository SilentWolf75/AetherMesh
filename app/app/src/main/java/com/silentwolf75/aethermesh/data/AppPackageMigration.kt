package com.silentwolf75.aethermesh.data

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-time export/import when [LEGACY_APPLICATION_ID] and the Play-ready package
 * are both installed — Android treats them as separate apps (no in-place upgrade).
 */
object AppPackageMigration {
    const val LEGACY_APPLICATION_ID = "com.example.aethermesh"
    const val MIGRATION_FORMAT = "aethermesh_app_migration"
    const val MIGRATION_VERSION = 1

    fun isLegacyPackageInstalled(context: Context): Boolean =
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(LEGACY_APPLICATION_ID, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun exportJson(repository: AetherMeshRepository, context: Context): String {
        val root = JSONObject()
        root.put("format", MIGRATION_FORMAT)
        root.put("version", MIGRATION_VERSION)
        root.put("exported_at", System.currentTimeMillis())
        root.put("source_package", context.packageName)

        root.put("messages", JSONArray().apply {
            repository.getAllChatMessages().forEach { msg ->
                put(JSONObject().apply {
                    put("sender_id", msg.senderId)
                    put("recipient_id", msg.recipientId)
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                    put("channel", msg.channel)
                    put("packet_id", msg.packetId)
                    put("status", msg.status)
                    put("is_encrypted", msg.isEncrypted)
                    put("heard_count", msg.heardCount)
                })
            }
        })

        root.put("nodes", JSONArray().apply {
            repository.dbHelper.getNodes().forEach { node ->
                put(JSONObject().apply {
                    put("node_id", node.nodeId)
                    put("name", node.name)
                    put("short_name", node.shortName)
                    put("battery", node.battery)
                    put("latitude", node.latitude.toDouble())
                    put("longitude", node.longitude.toDouble())
                    put("last_active", node.lastActive)
                    put("model", node.model)
                    put("uptime_seconds", node.uptimeSeconds)
                    put("firmware_version", node.firmwareVersion)
                    put("is_charging", node.isCharging)
                    put("rssi", node.rssi.toDouble())
                    put("snr", node.snr.toDouble())
                    put("voltage", node.voltage.toDouble())
                    put("position_precision", node.positionPrecision)
                    put("protocol_version", node.protocolVersion)
                    put("lora_sf", node.loraSf)
                    put("region", node.region)
                    put("last_position_at", node.lastPositionAt)
                })
            }
        })

        root.put("channels", JSONArray().apply {
            repository.getChannelsList().forEach { ch ->
                put(JSONObject().apply {
                    put("name", ch.name)
                    put("psk", ch.psk)
                    put("uplink", ch.uplinkEnabled)
                    put("downlink", ch.downlinkEnabled)
                    put("position", ch.positionEnabled)
                    put("precise", ch.preciseLocation)
                    put("precision_miles", ch.precisionMiles.toDouble())
                    put("primary", ch.isPrimary)
                })
            }
        })

        root.put("chat_keys", JSONObject().apply {
            repository.exportChatKeysForMigration().forEach { (id, key) -> put(id, key) }
        })

        root.put("secure_secrets", JSONObject().apply {
            repository.exportSecureSecretsForMigration().forEach { (key, value) -> put(key, value) }
        })

        root.put("app_prefs", JSONObject().apply {
            repository.exportAppPrefsForMigration().forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Float -> put(key, value.toDouble())
                    is Boolean -> put(key, value)
                }
            }
        })

        root.put("node_settings", JSONObject().apply {
            repository.exportNodeSettingsForMigration().forEach { (prefName, entries) ->
                put(prefName, JSONObject().apply {
                    entries.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Int -> put(key, value)
                            is Long -> put(key, value)
                            is Float -> put(key, value.toDouble())
                            is Boolean -> put(key, value)
                        }
                    }
                })
            }
        })

        return root.toString(2)
    }

    fun importJson(repository: AetherMeshRepository, context: Context, json: String): ImportResult {
        val root = JSONObject(json)
        if (root.optString("format") != MIGRATION_FORMAT) {
            return ImportResult(false, "Not an AetherMesh migration file")
        }
        if (root.optInt("version", 0) != MIGRATION_VERSION) {
            return ImportResult(false, "Unsupported migration version")
        }

        var messages = 0
        var nodes = 0
        var channels = 0
        var secrets = 0

        root.optJSONArray("messages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (repository.dbHelper.importMessageForMigration(
                        senderId = o.getLong("sender_id"),
                        recipientId = o.getLong("recipient_id"),
                        content = o.getString("content"),
                        timestamp = o.getLong("timestamp"),
                        channel = o.optString("channel", ""),
                        packetId = o.optInt("packet_id", 0),
                        status = o.optString("status", "SENT"),
                        isEncrypted = o.optBoolean("is_encrypted", false),
                        heardCount = o.optInt("heard_count", 0)
                    )
                ) messages++
            }
        }

        root.optJSONArray("nodes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                repository.dbHelper.importNodeForMigration(
                    nodeId = o.getLong("node_id"),
                    name = o.optString("name", ""),
                    shortName = o.optString("short_name", ""),
                    battery = o.optInt("battery", 0),
                    latitude = o.optDouble("latitude", 0.0).toFloat(),
                    longitude = o.optDouble("longitude", 0.0).toFloat(),
                    lastActive = o.optLong("last_active", 0L),
                    model = o.optString("model", ""),
                    uptimeSeconds = o.optLong("uptime_seconds", 0L),
                    firmwareVersion = o.optString("firmware_version", ""),
                    isCharging = o.optBoolean("is_charging", false),
                    rssi = o.optDouble("rssi", 0.0).toFloat(),
                    snr = o.optDouble("snr", 0.0).toFloat(),
                    voltage = o.optDouble("voltage", 0.0).toFloat(),
                    positionPrecision = o.optInt("position_precision", 0),
                    protocolVersion = o.optInt("protocol_version", 1),
                    loraSf = o.optInt("lora_sf", 0),
                    region = o.optInt("region", -1),
                    lastPositionAt = o.optLong("last_position_at", 0L)
                )
                nodes++
            }
        }

        root.optJSONArray("channels")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                repository.dbHelper.insertChannel(
                    ChannelConfig(
                        name = o.getString("name"),
                        psk = o.optString("psk", ""),
                        uplinkEnabled = o.optBoolean("uplink", true),
                        downlinkEnabled = o.optBoolean("downlink", true),
                        positionEnabled = o.optBoolean("position", true),
                        preciseLocation = o.optBoolean("precise", true),
                        precisionMiles = o.optDouble("precision_miles", 0.0).toFloat(),
                        isPrimary = o.optBoolean("primary", false)
                    )
                )
                channels++
            }
        }

        root.optJSONObject("chat_keys")?.let { keys ->
            keys.keys().forEach { id ->
                repository.dbHelper.saveChatKey(id, keys.getString(id))
            }
        }

        root.optJSONObject("secure_secrets")?.let { sec ->
            val editor = repository.securePrefsForMigration().edit()
            sec.keys().forEach { key ->
                editor.putString(key, sec.getString(key))
                secrets++
            }
            editor.apply()
        }

        root.optJSONObject("app_prefs")?.let { prefs ->
            val editor = repository.appPrefs().edit()
            prefs.keys().forEach { key ->
                when (val value = prefs.get(key)) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is Boolean -> editor.putBoolean(key, value)
                }
            }
            editor.apply()
        }

        root.optJSONObject("node_settings")?.let { all ->
            all.keys().forEach { prefName ->
                val entries = all.getJSONObject(prefName)
                val editor = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit()
                entries.keys().forEach { key ->
                    when (val value = entries.get(key)) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is Boolean -> editor.putBoolean(key, value)
                    }
                }
                editor.apply()
            }
        }

        repository.refreshData()
        return ImportResult(
            true,
            "Imported $messages messages, $nodes nodes, $channels channels, $secrets secrets"
        )
    }

    data class ImportResult(val success: Boolean, val summary: String)
}
