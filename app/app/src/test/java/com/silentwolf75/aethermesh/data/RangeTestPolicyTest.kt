package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.RangeTestControl

class RangeTestPolicyTest {

    @Test
    fun sf11NeedsTenSecondGaps() {
        assertEquals(10, RangeTestPolicy.minIntervalSeconds(11))
        assertEquals(10, RangeTestPolicy.clampInterval(5, 11))
        assertEquals(10, RangeTestPolicy.clampInterval(10, 11))
        assertEquals(30, RangeTestPolicy.clampInterval(60, 11))
    }

    @Test
    fun parsePongAcceptsLegacyAndDirectForms() {
        val legacy = RangeTestPolicy.parsePong("PONG_42")
        assertEquals(42, legacy?.pingId)
        assertNull(legacy?.targetRssi)

        val direct = RangeTestPolicy.parsePong("PONG_99_-87_8_D")
        assertEquals(99, direct?.pingId)
        assertEquals(-87f, direct?.targetRssi)
        assertEquals(2f, direct?.targetSnr)
    }

    @Test
    fun pendingStoreExpiresAndAllocatesDistinctIds() {
        val store = RangeTestPendingStore()
        val ping = PendingRangePing(20L, 1_000L, RangeTestPosition(0.0, 0.0, null, null))
        val id = store.allocatePingId { 1 }
        store.put(id, ping)
        assertEquals(2, id)
        assertTrue(store.expire(1_000L + RangeTestPolicy.PING_TIMEOUT_MS).contains(ping))
        assertTrue(store.expire(10_000_000L).isEmpty())
    }

    @Test
    fun pingTimeoutScalesWithSf() {
        assertEquals(15_000L, RangeTestPolicy.pingTimeoutMs(7))
        assertEquals(15_000L, RangeTestPolicy.pingTimeoutMs(9))
        assertTrue(RangeTestPolicy.pingTimeoutMs(11) > RangeTestPolicy.PING_TIMEOUT_MS)
        assertTrue(RangeTestPolicy.pingTimeoutMs(12) > RangeTestPolicy.pingTimeoutMs(11))
        val store = RangeTestPendingStore()
        val ping = PendingRangePing(20L, 1_000L, RangeTestPosition(0.0, 0.0, null, null))
        store.put(1, ping)
        assertTrue(
            store.expire(1_000L + RangeTestPolicy.pingTimeoutMs(12) - 1L, RangeTestPolicy.pingTimeoutMs(12))
                .isEmpty()
        )
        assertTrue(
            store.expire(1_000L + RangeTestPolicy.pingTimeoutMs(12), RangeTestPolicy.pingTimeoutMs(12))
                .contains(ping)
        )
    }

    @Test
    fun pongScoresOnPingIdEvenIfSenderFormDiffers() {
        val pending = PendingRangePing(0x12345678L, 1L, RangeTestPosition(1.0, 2.0, null, null))
        val inactive = RangeTestPolicy.decidePong(false, "PONG_9", pending, 0x12345678L)
        assertTrue(inactive is RangeScoreDecision.IgnoreInactive)

        val unmatched = RangeTestPolicy.decidePong(true, "PONG_9", null, 0x12345678L)
        assertTrue(unmatched is RangeScoreDecision.Unmatched)

        val suffix = RangeTestPolicy.decidePong(true, "PONG_9_-80_8_D", pending, 0x5678L)
            as RangeScoreDecision.Score
        assertEquals(9, suffix.packetId)
        assertFalse(suffix.senderMismatch)
        assertEquals(-80f, suffix.remoteRssi)
        assertEquals(2f, suffix.remoteSnr)

        val other = RangeTestPolicy.decidePong(true, "PONG_9", pending, 0xABCDL)
            as RangeScoreDecision.Score
        assertTrue(other.senderMismatch)
    }

    @Test
    fun ackRequiresExactTargetIdAndDropsZeroRemoteSignal() {
        val pending = PendingRangePing(0x11L, 1L, RangeTestPosition(0.0, 0.0, null, null))
        assertTrue(
            RangeTestPolicy.decideAck(true, 4, pending, senderId = 0x22L, 0f, 0f)
                is RangeScoreDecision.Ignore
        )
        val score = RangeTestPolicy.decideAck(true, 4, pending, 0x11L, -70f, 0f)
            as RangeScoreDecision.Score
        assertEquals(4, score.packetId)
        assertEquals(-70f, score.remoteRssi)
        assertNull(score.remoteSnr)
        assertFalse(score.senderMismatch)
    }

    @Test
    fun phoneFixMustBeNewerThanThirtySeconds() {
        assertTrue(RangeTestPolicy.phoneFixFresh(1_000L, 1_000L + 29_999L))
        assertFalse(RangeTestPolicy.phoneFixFresh(1_000L, 1_000L + 30_000L))
    }

    @Test
    fun beginRefusesSelfAndClampsInterval() {
        assertTrue(RangeTestPolicy.begin(0x11L, 0x11L, 5, 11) is RangeTestStart.SelfTarget)
        val run = RangeTestPolicy.begin(0x11L, 0x22L, 5, 11) as RangeTestStart.Run
        assertEquals(10, run.intervalSeconds)
    }

    @Test
    fun liveLoadKeepsCurrentTarget() {
        assertEquals(20L, RangeTestPolicy.sessionTargetAfterLoad(true, 20L, 99L))
        assertEquals(99L, RangeTestPolicy.sessionTargetAfterLoad(false, 20L, 99L))
    }

    @Test
    fun pickPositionPrefersFreshPhoneFix() {
        val phone = RangeTestPolicy.pickPosition(
            phoneLat = 35.0, phoneLon = -106.0, phoneTimeMs = 1_000L,
            phoneSpeed = 1.5f, phoneAccuracy = 8f, nowMs = 2_000L,
            nodeLat = 1.0, nodeLon = 2.0
        )
        assertEquals(35.0, phone.latitude, 0.0)
        assertEquals(-106.0, phone.longitude, 0.0)
        assertEquals(1.5f, phone.speedMps)
        val stale = RangeTestPolicy.pickPosition(
            phoneLat = 35.0, phoneLon = -106.0, phoneTimeMs = 1_000L,
            phoneSpeed = 1.5f, phoneAccuracy = 8f, nowMs = 1_000L + 30_000L,
            nodeLat = 1.0, nodeLon = 2.0
        )
        assertEquals(1.0, stale.latitude, 0.0)
        assertEquals(2.0, stale.longitude, 0.0)
        assertNull(stale.speedMps)
    }

    @Test
    fun controlStaysLocalAndPingIsDirectNoAck() {
        val control = RangeTestPolicy.buildControl(0x11L, 7, RangeTestControl.Op.START)
        assertEquals(0x11, control.senderId)
        assertEquals(0x11, control.recipientId)
        assertEquals(0, control.hopLimit)
        assertFalse(control.wantAck)
        assertEquals(MeshPacket.PayloadCase.RANGE_TEST_CONTROL, control.payloadCase)
        assertEquals(RangeTestControl.Op.START, control.rangeTestControl.op)

        val ping = RangeTestPolicy.buildPing(0x11L, 0x22L, 42)
        assertEquals(0x22, ping.recipientId)
        assertEquals(1, ping.hopLimit)
        assertFalse(ping.wantAck)
        assertEquals("PING_42_D", ping.text.content)
        assertEquals(MeshPacket.PayloadCase.TEXT, ping.payloadCase)
    }

    @Test
    fun failureCopyLocksEnAndEs() {
        assertEquals("BLE fail", RangeTestPolicy.failureShort(RangeTestPolicy.FAIL_BLE, false))
        assertEquals("fallo BLE", RangeTestPolicy.failureShort(RangeTestPolicy.FAIL_BLE, true))
        assertTrue(
            RangeTestPolicy.failureLabel(RangeTestPolicy.FAIL_SELF, false)
                .contains("BLE-connected")
        )
        assertTrue(
            RangeTestPolicy.failureLabel(null, true).contains("timeout")
        )
    }
}
