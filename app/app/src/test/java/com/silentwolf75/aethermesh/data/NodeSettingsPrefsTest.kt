package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeSettingsPrefsTest {
    @Test
    fun prefsNameUsesMigrationPrefix() {
        assertEquals("node_settings_42", NodeSettingsPrefs.prefsName(42L))
        assertEquals(
            AppMigrationExportPolicy.NODE_SETTINGS_PREFIX + "99",
            NodeSettingsPrefs.prefsName(99L)
        )
    }

    @Test
    fun keyConstantsAreStable() {
        assertEquals("node_name", NodeSettingsPrefs.KEY_NODE_NAME)
        assertEquals("node_short_name", NodeSettingsPrefs.KEY_NODE_SHORT)
        assertEquals("power_save_mode", NodeSettingsPrefs.KEY_POWER_SAVE)
        assertEquals("lora_sf", NodeSettingsPrefs.KEY_LORA_SF)
        assertEquals("lora_bw", NodeSettingsPrefs.KEY_LORA_BW)
        assertEquals("region_configured", NodeSettingsPrefs.KEY_REGION_CONFIGURED)
        assertEquals("mesh_hop_limit", NodeSettingsPrefs.KEY_MESH_HOP_LIMIT)
        assertEquals("rebroadcast_txdelay_x100", NodeSettingsPrefs.KEY_REBROADCAST_TXDELAY)
        assertEquals("device_synced", NodeSettingsPrefs.KEY_DEVICE_SYNCED)
    }

    @Test
    fun clampHelpersMatchStoreDefaults() {
        assertEquals(125f, NodeSettingsPrefs.clampBw(0f))
        assertEquals(250f, NodeSettingsPrefs.clampBw(250f))
        assertEquals(22, NodeSettingsPrefs.clampTxPower(0))
        assertEquals(14, NodeSettingsPrefs.clampTxPower(14))
        assertEquals(60, NodeSettingsPrefs.clampTelemetrySecs(0))
        assertEquals(300, NodeSettingsPrefs.clampTelemetrySecs(300))
    }

    @Test
    fun clampSfKeepsValidAndFallsBack() {
        assertEquals(7, NodeSettingsPrefs.clampSf(7))
        assertEquals(12, NodeSettingsPrefs.clampSf(12))
        assertEquals(11, NodeSettingsPrefs.clampSf(6))
        assertEquals(11, NodeSettingsPrefs.clampSf(13))
        assertEquals(9, NodeSettingsPrefs.clampSf(0, default = 9))
    }
}
