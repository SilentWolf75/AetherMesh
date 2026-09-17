package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NodeSettingsFormPolicyTest {
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setup() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("node_settings_form", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun reloadSkipsZeroNodeImportDirtyAndUnchangedEpoch() {
        assertFalse(NodeSettingsFormPolicy.shouldReload(0L, 0L, 1, 0, false))
        assertFalse(NodeSettingsFormPolicy.shouldReload(9L, 9L, 1, 1, importDirty = true))
        assertFalse(NodeSettingsFormPolicy.shouldReload(9L, 9L, 3, 3, false))
        assertTrue(NodeSettingsFormPolicy.shouldReload(9L, 8L, 1, 1, false))
        assertTrue(NodeSettingsFormPolicy.shouldReload(9L, 9L, 4, 3, false))
    }

    @Test
    fun advertisedNameStripsBlePrefix() {
        assertEquals("Wolf", NodeSettingsFormPolicy.advertisedLongName("AetherMesh-Wolf"))
        assertEquals("Alpha", NodeSettingsFormPolicy.advertisedLongName("Node Alpha"))
        assertEquals("", NodeSettingsFormPolicy.advertisedLongName(null))
    }

    @Test
    fun dutySnapsToChipValues() {
        assertEquals(900, NodeSettingsFormPolicy.snapGpsDutyIntervalSecs(0))
        assertEquals(300, NodeSettingsFormPolicy.snapGpsDutyIntervalSecs(400))
        assertEquals(900, NodeSettingsFormPolicy.snapGpsDutyIntervalSecs(1000))
        assertEquals(1800, NodeSettingsFormPolicy.snapGpsDutyIntervalSecs(2000))
        assertEquals(3600, NodeSettingsFormPolicy.snapGpsDutyIntervalSecs(5000))
    }

    @Test
    fun readFromPrefsClampsAndFallsBackToAdvertised() {
        prefs.edit()
            .putInt("mesh_hop_limit", 9)
            .putInt("rebroadcast_txdelay_x100", 0)
            .putInt("gps_mode", 9)
            .putInt("gps_duty_interval_secs", 400)
            .putFloat("fixed_latitude", 0f)
            .putInt("fixed_altitude", 0)
            .commit()
        val form = NodeSettingsFormPolicy.readFromPrefs(
            prefs,
            advertisedName = "AetherMesh-Trail",
            advertisedShortName = "TRL"
        )
        assertEquals("Trail", form.nodeName)
        assertEquals("TRL", form.nodeShortName)
        assertEquals(8, form.meshHopLimit)
        assertEquals(100, form.rebroadcastTxdelayX100)
        assertEquals(2, form.gpsMode)
        assertEquals(300, form.gpsDutyIntervalSecs)
        assertEquals("", form.fixedLatInput)
        assertEquals("", form.fixedAltInput)
    }
}
