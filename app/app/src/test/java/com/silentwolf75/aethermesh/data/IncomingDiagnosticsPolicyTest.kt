package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingDiagnosticsPolicyTest {
    @Test
    fun unsigned32DoesNotSignExtend() {
        assertEquals(0L, IncomingDiagnosticsPolicy.unsigned32(0))
        assertEquals(1L, IncomingDiagnosticsPolicy.unsigned32(1))
        assertEquals(4_294_967_295L, IncomingDiagnosticsPolicy.unsigned32(-1))
        assertEquals(2_147_483_648L, IncomingDiagnosticsPolicy.unsigned32(Int.MIN_VALUE))
    }

    @Test
    fun fromProtoCopiesCountersAndQuietFlag() {
        val proto = MeshDiagnostics.newBuilder()
            .setTxPackets(10)
            .setRxPackets(-1)
            .setActiveRoutes(3)
            .setRebroadcastQueueDepth(2)
            .setPendingAckDepth(1)
            .setQuietMode(true)
            .setRangePingsRx(7)
            .setDirectedRelays(4)
            .setProtocolVersion(3)
            .build()
        val snap = IncomingDiagnosticsPolicy.fromProto(proto, nowMs = 99L)
        assertEquals(99L, snap.timestamp)
        assertEquals(10L, snap.txPackets)
        assertEquals(4_294_967_295L, snap.rxPackets)
        assertEquals(3, snap.activeRoutes)
        assertEquals(2, snap.rebroadcastQueueDepth)
        assertEquals(1, snap.pendingAckDepth)
        assertTrue(snap.quietMode)
        assertEquals(7L, snap.rangePingsRx)
        assertEquals(4L, snap.directedRelays)
        assertEquals(3, snap.protocolVersion)
        assertFalse(IncomingDiagnosticsPolicy.fromProto(MeshDiagnostics.getDefaultInstance()).quietMode)
    }
}
