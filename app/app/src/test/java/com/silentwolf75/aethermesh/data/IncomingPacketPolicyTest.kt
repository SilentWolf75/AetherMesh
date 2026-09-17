package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingPacketPolicyTest {
    @Test
    fun rangePongBypassesZeroIdDrop() {
        assertTrue(IncomingPacketPolicy.isRangePong(true, "PONG_42_-87_8_D"))
        assertFalse(IncomingPacketPolicy.isRangePong(false, "PONG_42"))
        assertFalse(IncomingPacketPolicy.isRangePong(true, "hello"))
        assertFalse(
            IncomingPacketPolicy.shouldDropZeroIds(0x11L, 0L, isRangePong = true)
        )
        assertTrue(
            IncomingPacketPolicy.shouldDropZeroIds(0x11L, 0L, isRangePong = false)
        )
        assertTrue(
            IncomingPacketPolicy.shouldDropZeroIds(0L, 0x11L, isRangePong = false)
        )
        assertFalse(
            IncomingPacketPolicy.shouldDropZeroIds(0x11L, 0x22L, isRangePong = false)
        )
    }

    @Test
    fun loopbackRssiZeroDoesNotObserveRoute() {
        assertNull(
            IncomingPacketPolicy.observeRoute(0x11L, 0x22L, rxRssi = 0f, rxSnr = 4f, nowMs = 1L)
        )
        assertNull(
            IncomingPacketPolicy.observeRoute(0x11L, 0L, rxRssi = -80f, rxSnr = 4f, nowMs = 1L)
        )
    }

    @Test
    fun directHopIsOneAndRelayedIsTwo() {
        val direct = IncomingPacketPolicy.observeRoute(0x11L, 0x11L, -70f, 8f, 9L)
        assertNotNull(direct)
        assertEquals(1, direct!!.hops)
        assertEquals(0x11L, direct.nextHopId)
        assertEquals(-70f, direct.lastRssi)

        val relayed = IncomingPacketPolicy.observeRoute(0xAAL, 0xBBL, -90f, 2f, 9L)!!
        assertEquals(2, relayed.hops)
        assertEquals(0xBBL, relayed.nextHopId)
        assertEquals(0xAAL, relayed.targetId)
    }

    @Test
    fun unsignedNodeIdKeeps32Bits() {
        assertEquals(0xFFFFFFFFL, IncomingPacketPolicy.unsignedNodeId(-1))
        assertEquals(0L, IncomingPacketPolicy.unsignedNodeId(0))
    }

    @Test
    fun emptyTracePathDoesNotObserve() {
        assertNull(IncomingPacketPolicy.observeTracePath(0xAAL, emptyList(), 1L))
    }

    @Test
    fun tracePathCreditsFirstHopAndFarEndSignal() {
        val direct = IncomingPacketPolicy.observeTracePath(
            targetId = 0xAAL,
            forward = listOf(TraceHop(nodeId = 0xAAL, rssi = -72, snr = 6.5f)),
            nowMs = 9L
        )!!
        assertEquals(0xAAL, direct.targetId)
        assertEquals(0xAAL, direct.nextHopId)
        assertEquals(1, direct.hops)
        assertEquals(-72f, direct.lastRssi)
        assertEquals(6.5f, direct.lastSnr)
        assertEquals(9L, direct.timestamp)

        val relayed = IncomingPacketPolicy.observeTracePath(
            targetId = 0xCCL,
            forward = listOf(
                TraceHop(nodeId = 0xBBL, rssi = -80, snr = 3f),
                TraceHop(nodeId = 0xCCL, rssi = -95, snr = -2f)
            ),
            nowMs = 9L
        )!!
        assertEquals(0xCCL, relayed.targetId)
        assertEquals(0xBBL, relayed.nextHopId)
        assertEquals(2, relayed.hops)
        assertEquals(-95f, relayed.lastRssi)
        assertEquals(-2f, relayed.lastSnr)
    }
}
