package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import com.silentwolf75.aethermesh.proto.NodeConfig
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
class LocalNodeConfigApplyTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun localHopsNeverDropToZero() {
        assertEquals(1, LocalNodeConfigApply.clampHops(0))
        assertEquals(8, LocalNodeConfigApply.clampHops(99))
        assertEquals(4, LocalNodeConfigApply.clampHops(4))
    }

    @Test
    fun localTxdelayDefaultsToOneHundred() {
        assertEquals(100, LocalNodeConfigApply.clampTxdelay(0))
        assertEquals(50, LocalNodeConfigApply.clampTxdelay(1))
        assertEquals(200, LocalNodeConfigApply.clampTxdelay(9_000))
    }

    @Test
    fun applyPacketTargetsRecipientZero() {
        val packet = LocalNodeConfigApply.build(
            0x11L,
            42,
            LocalNodeConfigRequest(name = "Base", shortName = "base-station", sf = 11, bw = 125f, txPower = 22, region = 1, role = 0, meshHopLimit = 99)
        )
        assertEquals(0, packet.recipientId)
        assertEquals(0x11, packet.senderId)
        assertEquals(42, packet.packetId)
        assertFalse(packet.wantAck)
        assertEquals(8, packet.config.meshHopLimit)
        assertEquals("BASE", packet.config.nodeShortName)
    }

    @Test
    fun hydrateWritesClampedDeviceValues() {
        val prefs = context.getSharedPreferences("node_settings_test", Context.MODE_PRIVATE)
        NodeSettingsStore.writeFromDevice(
            prefs,
            NodeConfig.newBuilder()
                .setNodeName("Relay")
                .setNodeShortName("rel1")
                .setLoraSf(3)
                .setLoraBw(0f)
                .setGpsDutyIntervalSecs(1)
                .setMeshHopLimit(0)
                .setRebroadcastTxdelayX100(0)
                .setGpsMode(9)
                .build()
        )
        assertEquals("Relay", prefs.getString("node_name", null))
        assertEquals("REL1", prefs.getString("node_short_name", null))
        assertEquals(11, prefs.getInt("lora_sf", -1))
        assertEquals(125f, prefs.getFloat("lora_bw", -1f))
        assertEquals(300, prefs.getInt("gps_duty_interval_secs", -1))
        assertEquals(4, prefs.getInt("mesh_hop_limit", -1))
        assertEquals(100, prefs.getInt("rebroadcast_txdelay_x100", -1))
        assertEquals(2, prefs.getInt("gps_mode", -1))
        assertTrue(prefs.getBoolean("device_synced", false))
    }
}
