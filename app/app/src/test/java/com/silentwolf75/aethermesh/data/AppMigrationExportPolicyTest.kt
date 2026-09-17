package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMigrationExportPolicyTest {
    @Test
    fun secretKeysAreExcludedFromAppPrefsExport() {
        assertTrue(AppMigrationExportPolicy.isSecretPrefKey("node_pwd_aa:bb"))
        assertTrue(AppMigrationExportPolicy.isSecretPrefKey("node_pwd_id_123"))
        assertTrue(AppMigrationExportPolicy.isSecretPrefKey("ecdh_private_key"))
        assertFalse(AppMigrationExportPolicy.isSecretPrefKey("app_theme"))
        assertFalse(AppMigrationExportPolicy.includeAppPrefKey("node_pwd_x"))
        assertTrue(AppMigrationExportPolicy.includeAppPrefKey("enable_phone_gps_sharing"))
    }

    @Test
    fun filterAppPrefsDropsSecretsOnly() {
        val filtered = AppMigrationExportPolicy.filterAppPrefs(
            mapOf(
                "app_theme" to "Dark",
                "node_pwd_aa" to "secret",
                "ecdh_private_key" to "pk",
                "bg_alerts_enabled" to true
            )
        )
        assertEquals(setOf("app_theme", "bg_alerts_enabled"), filtered.keys)
        assertEquals("Dark", filtered["app_theme"])
        assertEquals(true, filtered["bg_alerts_enabled"])
    }

    @Test
    fun stringSecretsKeepsOnlyStrings() {
        assertEquals(
            mapOf("a" to "1", "c" to "3"),
            AppMigrationExportPolicy.stringSecrets(
                mapOf("a" to "1", "b" to 2, "c" to "3", "d" to true)
            )
        )
    }

    @Test
    fun nodeSettingsPrefNameRecognizesXmlFiles() {
        assertEquals(
            "node_settings_42",
            AppMigrationExportPolicy.nodeSettingsPrefName("node_settings_42.xml")
        )
        assertEquals(
            "node_settings_1",
            AppMigrationExportPolicy.nodeSettingsPrefName("node_settings_1")
        )
        assertNull(AppMigrationExportPolicy.nodeSettingsPrefName("aethermesh_prefs.xml"))
        assertNull(AppMigrationExportPolicy.nodeSettingsPrefName("other.xml"))
    }
}
