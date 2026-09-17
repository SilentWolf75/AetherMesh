package com.silentwolf75.aethermesh.data

import android.content.SharedPreferences

/**
 * BLE node admin password keys in encrypted prefs. Canonical MAC keys are
 * trimmed+uppercase; lookup still accepts the raw legacy key. Empty passwords
 * are never stored. SharedPreferences I/O only — cache invalidation stays in
 * [AetherMeshRepository].
 */
object SavedPasswordPolicy {
    fun normalizeMac(macAddress: String): String = macAddress.trim().uppercase()

    fun keyForMac(macAddress: String): String = "node_pwd_${normalizeMac(macAddress)}"

    fun keyForNodeId(nodeId: Long): String = "node_pwd_id_$nodeId"

    fun legacyKey(macAddress: String): String = "node_pwd_$macAddress"

    fun lookup(prefs: SharedPreferences, macAddress: String, nodeId: Long): String? {
        val byMac = prefs.getString(keyForMac(macAddress), null)
        if (!byMac.isNullOrEmpty()) return byMac
        val legacy = prefs.getString(legacyKey(macAddress), null)
        if (!legacy.isNullOrEmpty()) return legacy
        if (nodeId != 0L) {
            val byId = prefs.getString(keyForNodeId(nodeId), null)
            if (!byId.isNullOrEmpty()) return byId
        }
        return null
    }

    /** @return false when [password] is empty and nothing was written. */
    fun save(
        prefs: SharedPreferences,
        macAddress: String?,
        nodeId: Long,
        password: String
    ): Boolean {
        if (password.isEmpty()) return false
        val editor = prefs.edit()
        if (!macAddress.isNullOrBlank()) {
            editor.putString(keyForMac(macAddress), password)
        }
        if (nodeId != 0L) {
            editor.putString(keyForNodeId(nodeId), password)
        }
        editor.apply()
        return true
    }

    fun clear(prefs: SharedPreferences, macAddress: String?, nodeId: Long) {
        val editor = prefs.edit()
        keysToClear(macAddress, nodeId).forEach(editor::remove)
        editor.apply()
    }

    fun keysToClear(macAddress: String?, nodeId: Long): List<String> {
        val keys = mutableListOf<String>()
        if (!macAddress.isNullOrBlank()) {
            val mac = normalizeMac(macAddress)
            keys += keyForMac(mac)
            keys += legacyKey(macAddress)
            keys += legacyKey(mac)
        }
        if (nodeId != 0L) keys += keyForNodeId(nodeId)
        return keys
    }
}
