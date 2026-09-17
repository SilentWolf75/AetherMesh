package com.silentwolf75.aethermesh.data

import com.google.protobuf.ByteString
import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.NodeConfig
import com.silentwolf75.aethermesh.proto.TraceRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HopRangePolicyTest {
    @Test
    fun firmwareMaxFollowsReportedCapability() {
        assertEquals(8, HopRangePolicy.firmwareMax(0))   // legacy firmware reports nothing
        assertEquals(8, HopRangePolicy.firmwareMax(12))  // unknown partial value stays safe
        assertEquals(16, HopRangePolicy.firmwareMax(16))
        assertEquals(16, HopRangePolicy.firmwareMax(64))
    }

    @Test
    fun clampNeverExceedsTheNodesCeiling() {
        assertEquals(8, HopRangePolicy.clamp(12))
        assertEquals(8, LocalNodeConfigApply.clampHops(12, maxHopLimit = 8))
        assertEquals(12, LocalNodeConfigApply.clampHops(12, maxHopLimit = 16))
        assertEquals(16, LocalNodeConfigApply.clampHops(40, maxHopLimit = 16))
        assertEquals(1, LocalNodeConfigApply.clampHops(0, maxHopLimit = 16))
        assertEquals(0, RemoteConfigApply.clampHops(0, maxHopLimit = 16))
        assertEquals(14, RemoteConfigApply.clampHops(14, maxHopLimit = 16))
    }

    @Test
    fun onlyLimitsAboveEightNeedAnUpgradedMesh() {
        assertFalse(HopRangePolicy.needsUpgradedMesh(8))
        assertTrue(HopRangePolicy.needsUpgradedMesh(9))
    }

    @Test
    fun localApplySendsExtendedLimitOnlyToCapableNode() {
        val legacy = LocalNodeConfigApply.build(
            1L, 5, LocalNodeConfigRequest(
                name = "N", shortName = "N", sf = 11, bw = 125f, txPower = 20, region = 0, role = 1,
                meshHopLimit = 12
            )
        )
        assertEquals(8, legacy.config.meshHopLimit)
        val extended = LocalNodeConfigApply.build(
            1L, 5, LocalNodeConfigRequest(
                name = "N", shortName = "N", sf = 11, bw = 125f, txPower = 20, region = 0, role = 1,
                meshHopLimit = 12, maxHopLimit = 16
            )
        )
        assertEquals(12, extended.config.meshHopLimit)
    }

    @Test
    fun hydrateKeepsExtendedLimitFromCapableNode() {
        val current = RemoteConfigSnapshot(
            name = "N", sf = 11, bw = 125f, txPower = 22, region = 0, role = 0, telemetry = 60,
            screen = 30, powerSave = false, posPrec = 0, gpsMode = 0, gpsDutySecs = 900,
            fixed = false, lat = 0f, lon = 0f, alt = 0, hop = 4, txdelay = 100
        )
        val legacyReport = NodeConfig.newBuilder().setMeshHopLimit(12).build()
        assertEquals(4, RemoteConfigHydratePolicy.merge(current, legacyReport).hop)
        val extendedReport = NodeConfig.newBuilder().setMeshHopLimit(12).setMaxHopLimit(16).build()
        assertEquals(12, RemoteConfigHydratePolicy.merge(current, extendedReport).hop)
    }

    @Test
    fun compactTraceHopsMatchFirmwareLayout() {
        // Same bytes as firmware test_tracehops: 0xC504A6B0 @ 6.75 dB, 0x14D3228C @ -12.25 dB.
        val bytes = ByteString.copyFrom(
            byteArrayOf(0xB0.toByte(), 0xA6.toByte(), 0x04, 0xC5.toByte(), 27,
                0x8C.toByte(), 0x22, 0xD3.toByte(), 0x14, (-49).toByte())
        )
        val hops = TraceRoutePolicy.decodeCompactHops(bytes)
        assertEquals(2, hops.size)
        assertEquals(0xC504A6B0L, hops[0].nodeId)
        assertEquals(6.75f, hops[0].snr)
        assertEquals(0x14D3228CL, hops[1].nodeId)
        assertEquals(-12.25f, hops[1].snr)
    }

    @Test
    fun extendedTraceResponseUsesCompactPaths() {
        val current = TraceRoutePolicy.starting(0x14D3228CL, 7, 1_000L)
        val forward = ByteString.copyFrom(byteArrayOf(0x8C.toByte(), 0x22, 0xD3.toByte(), 0x14, 8))
        val back = ByteString.copyFrom(byteArrayOf(0x02, 0, 0, 0, 12))
        val packet = MeshPacket.newBuilder()
            .setRxRssi(-80f)
            .setRxSnr(3.5f)
            .setTraceRoute(
                TraceRoute.newBuilder()
                    .setType(TraceRoute.Type.RESPONSE)
                    .setTraceId(7)
                    .setOriginId(10)
                    .setTargetId(0x14D3228C)
                    .setForwardHops(forward)
                    .setReturnHops(back)
            )
            .build()
        val applied = TraceRoutePolicy.applyResponse(current, packet, 10L, 2_000L)
        assertNotNull(applied)
        assertEquals(listOf(0x14D3228CL), applied!!.forward.map { it.nodeId })
        assertEquals(2f, applied.forward[0].snr)
        assertEquals(listOf(2L, 10L), applied.returning.map { it.nodeId })
        assertEquals(3f, applied.returning[0].snr)
    }
}
