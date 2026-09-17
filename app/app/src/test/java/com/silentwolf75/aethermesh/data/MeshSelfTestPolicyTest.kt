package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshSelfTestPolicyTest {
    @Test
    fun clampsPingCount() {
        assertEquals(3, MeshSelfTestPolicy.clampPingCount(1))
        assertEquals(5, MeshSelfTestPolicy.clampPingCount(5))
        assertEquals(10, MeshSelfTestPolicy.clampPingCount(99))
    }

    @Test
    fun settleTracksSpreadingFactor() {
        assertEquals(8_000L, MeshSelfTestPolicy.settleMs(12))
        assertEquals(8_000L, MeshSelfTestPolicy.settleMs(11))
        assertEquals(6_000L, MeshSelfTestPolicy.settleMs(10))
        assertEquals(4_000L, MeshSelfTestPolicy.settleMs(9))
    }

    @Test
    fun pingContentUsesWrappedTimestamp() {
        assertEquals("MESHTEST_1234_3", MeshSelfTestPolicy.pingContent(101_234L, 3))
    }

    @Test
    fun scorePrefersHeardThenRxDelta() {
        assertEquals(0, MeshSelfTestPolicy.score(0, 1, 9))
        assertEquals(60, MeshSelfTestPolicy.score(5, 3, 99))
        assertEquals(30, MeshSelfTestPolicy.score(5, 0, 3))
        assertEquals(40, MeshSelfTestPolicy.score(5, 0, 9))
        assertEquals(0, MeshSelfTestPolicy.score(5, 0, 0))
    }

    @Test
    fun beginRefusesUntilUnlockedAndIdle() {
        val locked = MeshSelfTestPolicy.begin(5, connected = true, gattReady = true, authenticated = false, rangeTestActive = false, sf = 11)
        assertTrue(locked is MeshSelfTestStart.Refused)
        assertEquals(
            "Connect and unlock the node first.",
            (locked as MeshSelfTestStart.Refused).result.errorEn
        )
        val busy = MeshSelfTestPolicy.begin(5, true, true, true, rangeTestActive = true, sf = 11)
        assertEquals(
            "Stop the range test before running mesh self-test.",
            (busy as MeshSelfTestStart.Refused).result.errorEn
        )
        val run = MeshSelfTestPolicy.begin(2, true, true, true, false, sf = 11) as MeshSelfTestStart.Run
        assertEquals(3, run.planned)
        assertEquals(8_000L, run.settleMs)
    }

    @Test
    fun finishedSummaryIncludesScore() {
        val result = MeshSelfTestPolicy.finished(5, 2, 3, 4)
        assertEquals(40, result.scorePercent)
        assertEquals(2, result.pingsHeard)
        assertTrue(result.statusLineEn.contains("score 40%"))
        assertTrue(result.statusLineEs.contains("puntuación 40%"))
        assertTrue(result.finished)
        assertTrue(!result.active)
    }
}
