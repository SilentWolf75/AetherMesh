package com.silentwolf75.aethermesh.data

/**
 * What leaves the phone in an app-package migration JSON. Keep node passwords
 * and the ECDH private key out of the plaintext `app_prefs` map — those ride
 * in `secure_secrets` / encrypted prefs instead.
 */
object AppMigrationExportPolicy {
    const val NODE_PWD_PREFIX = "node_pwd_"
    const val ECDH_PRIVATE_KEY = "ecdh_private_key"
    const val NODE_SETTINGS_PREFIX = "node_settings_"

    /** Keys that must migrate into encrypted prefs, never the open app prefs export. */
    fun isSecretPrefKey(key: String): Boolean =
        key.startsWith(NODE_PWD_PREFIX) || key == ECDH_PRIVATE_KEY

    fun includeAppPrefKey(key: String): Boolean = !isSecretPrefKey(key)

    fun filterAppPrefs(all: Map<String, *>): Map<String, Any?> =
        all.filterKeys(::includeAppPrefKey)

    fun stringSecrets(all: Map<String, *>): Map<String, String> =
        all.mapNotNull { (key, value) ->
            if (value is String) key to value else null
        }.toMap()

    fun nodeSettingsPrefName(fileName: String): String? {
        val name = fileName.removeSuffix(".xml")
        return name.takeIf { it.startsWith(NODE_SETTINGS_PREFIX) }
    }
}
