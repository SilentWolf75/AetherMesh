package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.TraceRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceRoutePolicyTest {

    @Test
    fun refusesSelfAndDisconnected() {
        assertFalse(TraceRoutePolicy.canStart(true, true, 10L, 10L))
        assertFalse(TraceRoutePolicy.canStart(false, true, 10L, 20L))
        assertFalse(TraceRoutePolicy.canStart(true, false, 10L, 20L))
        assertTrue(TraceRoutePolicy.canStart(true, true, 10L, 20L))
    }

    @Test
    fun requestUsesHopLimitSevenAndNoAck() {
        val packet = TraceRoutePolicy.buildRequest(10L, 20L, 99)
        assertEquals(99, packet.packetId)
        assertEquals(20, packet.recipientId)
        assertEquals(TraceRoutePolicy.HOP_LIMIT, packet.hopLimit)
        assertFalse(packet.wantAck)
        assertEquals(TraceRoute.Type.REQUEST, packet.traceRoute.type)
        assertEquals(99, packet.traceRoute.traceId)
    }

    @Test
    fun responseAppendsLocalHopAndConvertsQuarterDb() {
        val current = TraceRoutePolicy.starting(20L, 7, 1_000L)
        val packet = MeshPacket.newBuilder()
            .setRxRssi(-80f)
            .setRxSnr(3.5f)
            .setTraceRoute(
                TraceRoute.newBuilder()
                    .setType(TraceRoute.Type.RESPONSE)
                    .setTraceId(7)
                    .setOriginId(10)
                    .addForwardNodeIds(20)
                    .addForwardRssi(-90)
                    .addForwardSnrQuarterDb(8)
            )
            .build()
        val applied = TraceRoutePolicy.applyResponse(current, packet, 10L, 2_000L)
        assertNotNull(applied)
        assertFalse(applied!!.active)
        assertEquals(1, applied.forward.size)
        assertEquals(2f, applied.forward[0].snr)
        assertEquals(10L, applied.returning.last().nodeId)
        assertEquals(-80, applied.returning.last().rssi)
        assertNull(TraceRoutePolicy.applyResponse(current, packet, 99L, 2_000L))
    }

    @Test
    fun timeoutOnlyFinishesMatchingInFlightTrace() {
        val current = TraceRoutePolicy.starting(20L, 7, 1_000L)
        assertNull(TraceRoutePolicy.timedOut(current, 8, 40_000L))
        val timed = TraceRoutePolicy.timedOut(current, 7, 40_000L)
        assertEquals(TraceRoutePolicy.ERR_TIMEOUT, timed?.error)
        assertFalse(timed!!.active)
    }

    @Test
    fun timeoutScalesWithSf() {
        assertEquals(30_000L, TraceRoutePolicy.timeoutMs(7))
        assertEquals(30_000L, TraceRoutePolicy.timeoutMs(9))
        assertTrue(TraceRoutePolicy.timeoutMs(11) > TraceRoutePolicy.TIMEOUT_MS)
        assertTrue(TraceRoutePolicy.timeoutMs(12) > TraceRoutePolicy.timeoutMs(11))
    }

    @Test
    fun rejectsResponseWhenNotActiveOrWrongTarget() {
        val finished = TraceRoutePolicy.starting(20L, 7, 1_000L).copy(active = false)
        val packet = MeshPacket.newBuilder()
            .setTraceRoute(
                TraceRoute.newBuilder()
                    .setType(TraceRoute.Type.RESPONSE)
                    .setTraceId(7)
                    .setOriginId(10)
                    .setTargetId(20)
            )
            .build()
        assertFalse(TraceRoutePolicy.matchesResponse(finished, packet, 10L))
        assertNull(TraceRoutePolicy.applyResponse(finished, packet, 10L, 2_000L))

        val active = TraceRoutePolicy.starting(20L, 7, 1_000L)
        val wrongTarget = packet.toBuilder()
            .setTraceRoute(packet.traceRoute.toBuilder().setTargetId(99))
            .build()
        assertFalse(TraceRoutePolicy.matchesResponse(active, wrongTarget, 10L))
        assertTrue(TraceRoutePolicy.matchesResponse(active, packet, 10L))
    }

    @Test
    fun localizeErrorLocksEnAndEs() {
        assertEquals(
            TraceRoutePolicy.ERR_TIMEOUT,
            TraceRoutePolicy.localizeError(TraceRoutePolicy.ERR_TIMEOUT, false)
        )
        assertEquals(
            "No se recibió respuesta de ruta",
            TraceRoutePolicy.localizeError(TraceRoutePolicy.ERR_TIMEOUT, true)
        )
        assertEquals("Traceroute cancelled", TraceRoutePolicy.localizeError(TraceRoutePolicy.ERR_CANCELLED, false))
        assertEquals("Traza fallida", TraceRoutePolicy.localizeError(null, true))
    }
}
