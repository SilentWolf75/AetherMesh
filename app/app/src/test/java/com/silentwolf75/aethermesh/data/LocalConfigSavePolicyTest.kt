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
class LocalConfigSavePolicyTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun disabledFixedAllowsNullIsland() {
        assertTrue(LocalConfigSavePolicy.isAllowed(false, 0f, 0f))
        val parsed = LocalConfigSavePolicy.parse(false, "0", "0", "")
        assertTrue(parsed is LocalConfigFixed.Ready)
        assertEquals(0f, (parsed as LocalConfigFixed.Ready).latitude)
    }

    @Test
    fun enabledFixedRejectsNullIslandAndOutOfRange() {
        assertFalse(LocalConfigSavePolicy.isAllowed(true, 0f, 0f))
        assertFalse(LocalConfigSavePolicy.isAllowed(true, 91f, -106f))
        assertTrue(LocalConfigSavePolicy.parse(true, "0", "0", "10") is LocalConfigFixed.Invalid)
        val ok = LocalConfigSavePolicy.parse(true, "35.0", "-106.0", "1600") as LocalConfigFixed.Ready
        assertEquals(35f, ok.latitude)
        assertEquals(-106f, ok.longitude)
        assertEquals(1600, ok.altitude)
    }

    @Test
    fun copyLocksEnAndEs() {
        assertEquals(
            "Invalid fixed position — use real coordinates (not 0,0).",
            LocalConfigSavePolicy.invalidFixedMessage(false)
        )
        assertTrue(LocalConfigSavePolicy.sentMessage(true, false).contains("Battery Saver"))
        assertTrue(LocalConfigSavePolicy.sentMessage(false, true).contains("Configuración remota"))
        assertEquals("Failed to send configuration.", LocalConfigSavePolicy.failedMessage(false))
    }

    @Test
    fun persistWritesHopAndRegionConfigured() {
        val prefs = context.getSharedPreferences("node_settings_save", Context.MODE_PRIVATE)
        val snap = NodeSettingsSnapshot(
            nodeName = "Base",
            nodeShortName = "BASE",
            loraSf = 11,
            loraBw = 125f,
            loraTxPower = 22,
            region = 0,
            nodeRole = 0,
            telemetryInterval = 60,
            screenTimeout = 30,
            powerSaveMode = false,
            positionPrecision = 0,
            gpsMode = 0,
            gpsDutyIntervalSecs = 200,
            fixedPosition = false,
            fixedLatitude = 0f,
            fixedLongitude = 0f,
            fixedAltitude = 0
        )
        LocalConfigSavePolicy.persistPrefs(prefs, snap, meshHopLimit = 9, rebroadcastTxdelayX100 = 10)
        assertEquals("Base", prefs.getString("node_name", null))
        assertTrue(prefs.getBoolean("region_configured", false))
        assertEquals(8, prefs.getInt("mesh_hop_limit", -1))
        assertEquals(50, prefs.getInt("rebroadcast_txdelay_x100", -1))
        assertEquals(300, prefs.getInt("gps_duty_interval_secs", -1))
    }
}
