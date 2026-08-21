package com.example.aethermesh.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings

/** Mute / last-read prefs for channel + DM threads (Phase E). */
object ChatThreadPrefs {
    private const val PREFS = "aethermesh_prefs"
    private const val MUTE_PREFIX = "chat_mute_"
    private const val READ_PREFIX = "chat_last_read_"

    const val PREF_BLE_CONTROLLER_NICKNAME = "ble_controller_phone_nickname"
    const val PREF_BLE_CONTROLLER_NODE_ID = "ble_controller_node_id"

    fun channelKey(channel: String): String = "CHANNEL_$channel"
    fun dmKey(peerId: Long): String = "DM_$peerId"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isMuted(prefs: SharedPreferences, chatId: String): Boolean =
        prefs.getBoolean(MUTE_PREFIX + chatId, false)

    fun setMuted(prefs: SharedPreferences, chatId: String, muted: Boolean) {
        prefs.edit().putBoolean(MUTE_PREFIX + chatId, muted).apply()
    }

    fun lastReadTs(prefs: SharedPreferences, chatId: String): Long =
        prefs.getLong(READ_PREFIX + chatId, 0L)

    fun markRead(prefs: SharedPreferences, chatId: String, atTs: Long = System.currentTimeMillis()) {
        val prev = lastReadTs(prefs, chatId)
        if (atTs > prev) {
            prefs.edit().putLong(READ_PREFIX + chatId, atTs).apply()
        }
    }

    fun isUnreadPreview(
        prefs: SharedPreferences,
        chatId: String,
        preview: ChatInboxPreview?,
        localNodeId: Long
    ): Boolean {
        if (preview == null || preview.timestamp <= 0L) return false
        if (localNodeId != 0L && preview.senderId == localNodeId) return false
        return preview.timestamp > lastReadTs(prefs, chatId)
    }

    fun isChatPrefsKey(key: String?): Boolean =
        key != null && (key.startsWith(MUTE_PREFIX) || key.startsWith(READ_PREFIX))

    fun phoneNickname(context: Context): String {
        val fromSettings = try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        if (fromSettings != null) return fromSettings
        return Build.MODEL?.trim()?.takeIf { it.isNotEmpty() } ?: "Android"
    }

    fun recordBleController(context: Context, nodeId: Long) {
        prefs(context).edit()
            .putString(PREF_BLE_CONTROLLER_NICKNAME, phoneNickname(context))
            .putLong(PREF_BLE_CONTROLLER_NODE_ID, nodeId)
            .apply()
    }

    fun bleControllerNickname(prefs: SharedPreferences): String? =
        prefs.getString(PREF_BLE_CONTROLLER_NICKNAME, null)?.takeIf { it.isNotBlank() }
}
