package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NodeSettingsBackupTest {
    private lateinit var context: Context

    private val sample = NodeSettingsSnapshot(
        nodeName = "Wolf Base",
        nodeShortName = "WOLF",
        loraSf = 11,
        loraBw = 125f,
        loraTxPower = 22,
        region = 1,
        nodeRole = 1,
        telemetryInterval = 300,
        screenTimeout = 10,
        powerSaveMode = true,
        positionPrecision = 50,
        gpsMode = 2,
        gpsDutyIntervalSecs = 900,
        fixedPosition = true,
        fixedLatitude = 35f,
        fixedLongitude = -106f,
        fixedAltitude = 1600
    )

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun jsonRoundTripPreservesFields() {
        val restored = NodeSettingsBackup.fromJson(NodeSettingsBackup.toJson(sample), sample.copy(nodeName = "fallback"))
        assertEquals(sample, restored)
        assertTrue(NodeSettingsBackup.toJson(sample).contains("\"node_name\":"))
    }

    @Test
    fun missingKeysUseFallbackAndGpsModeIsClamped() {
        val fallback = sample.copy(nodeName = "Keep", gpsMode = 0)
        val restored = NodeSettingsBackup.fromJson("""{"gps_mode":9}""", fallback)
        assertEquals("Keep", restored.nodeName)
        assertEquals(2, restored.gpsMode)
        assertEquals(125f, restored.loraBw)
    }

    @Test
    fun writeToPrefsStoresSnapshot() {
        val prefs = context.getSharedPreferences("node_settings_test", Context.MODE_PRIVATE)
        NodeSettingsBackup.writeToPrefs(prefs, sample)
        assertEquals("Wolf Base", prefs.getString(NodeSettingsPrefs.KEY_NODE_NAME, null))
        assertEquals("WOLF", prefs.getString(NodeSettingsPrefs.KEY_NODE_SHORT, null))
        assertEquals(11, prefs.getInt(NodeSettingsPrefs.KEY_LORA_SF, -1))
        assertEquals(125f, prefs.getFloat(NodeSettingsPrefs.KEY_LORA_BW, -1f))
        assertEquals(2, prefs.getInt(NodeSettingsPrefs.KEY_GPS_MODE, -1))
        assertTrue(prefs.getBoolean(NodeSettingsPrefs.KEY_POWER_SAVE, false))
    }
}
