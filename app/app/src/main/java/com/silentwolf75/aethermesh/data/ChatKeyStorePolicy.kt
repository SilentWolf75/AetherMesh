package com.silentwolf75.aethermesh.data

import android.content.SharedPreferences
import java.security.MessageDigest

data class ChatKeyLookup(
    val value: String?,
    val migrated: Boolean = false
)

/**
 * Encrypted-prefs names for chat PSKs. Values live in secure storage; the
 * SQLite table is a one-time plaintext fallback. BLE/send stays in the
 * repository.
 */
object ChatKeyStorePolicy {
    const val DM_PREFIX = "chat_key_dm_"
    const val CHANNEL_PREFIX = "chat_key_channel_"

    fun prefKey(chatIdentifier: String): String {
        val kind = if (chatIdentifier.startsWith("CHANNEL_")) "channel" else "dm"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(chatIdentifier.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "chat_key_${kind}_$digest"
    }

    fun isDmPrefKey(key: String): Boolean = key.startsWith(DM_PREFIX)

    fun lookup(
        prefs: SharedPreferences,
        chatIdentifier: String,
        legacy: () -> String?
    ): ChatKeyLookup {
        val key = prefKey(chatIdentifier)
        prefs.getString(key, null)?.let { return ChatKeyLookup(it) }
        val fromLegacy = legacy()?.takeIf { it.isNotEmpty() } ?: return ChatKeyLookup(null)
        check(prefs.edit().putString(key, fromLegacy).commit()) { "Could not migrate chat key" }
        return ChatKeyLookup(fromLegacy, migrated = true)
    }

    fun save(prefs: SharedPreferences, chatIdentifier: String, key: String) {
        val prefKey = prefKey(chatIdentifier)
        if (key.isBlank()) {
            prefs.edit().remove(prefKey).apply()
        } else {
            prefs.edit().putString(prefKey, key).apply()
        }
    }

    fun delete(prefs: SharedPreferences, chatIdentifier: String) {
        prefs.edit().remove(prefKey(chatIdentifier)).apply()
    }

    fun dmPrefKeys(allKeys: Set<String>): List<String> =
        allKeys.filter(::isDmPrefKey)
}
