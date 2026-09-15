package com.silentwolf75.aethermesh.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorageUnavailable(cause: Throwable) : Exception("Secure storage is unavailable", cause)

/** Opening secrets is fail-closed. Never create or write a plaintext fallback. */
object SecurePreferences {
    fun open(context: Context): SharedPreferences = open(context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, SecurePrefsNames.ENCRYPTED, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    internal fun open(context: Context, createEncrypted: () -> SharedPreferences): SharedPreferences = try {
        val destination = createEncrypted()
        migrate(context.getSharedPreferences(SecurePrefsNames.FALLBACK, Context.MODE_PRIVATE), destination) { true }
        migrate(context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE), destination) {
            it.startsWith("node_pwd_") || it == "ecdh_private_key"
        }
        destination
    } catch (e: Exception) {
        throw SecureStorageUnavailable(e)
    }

    internal fun migrate(
        source: SharedPreferences,
        destination: SharedPreferences,
        include: (String) -> Boolean
    ) {
        val values = source.all.filter { (key, value) -> include(key) && value is String }
        if (values.isEmpty()) return
        val editor = destination.edit()
        values.forEach { (key, value) ->
            if (!destination.contains(key)) editor.putString(key, value as String)
        }
        check(editor.commit()) { "Could not persist encrypted secrets" }
        // A failed cleanup leaves a recoverable source. It must never erase the only copy.
        val cleanup = source.edit()
        values.keys.forEach(cleanup::remove)
        check(cleanup.commit()) { "Could not remove migrated plaintext secrets" }
    }
}
