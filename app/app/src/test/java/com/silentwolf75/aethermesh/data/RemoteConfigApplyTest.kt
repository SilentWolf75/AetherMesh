package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigApplyTest {

    @Test
    fun refuseBlankPasswordOrEmptyMask() {
        val identity = ControlAuthIdentity(1L, 2)
        assertNull(
            RemoteConfigApply.buildReportRequest(1L, 2L, "", 3, 10, identity)
        )
        assertNull(
            RemoteConfigApply.buildApply(
                1L,
                sample(applyMask = 0),
                3,
                11,
                identity
            )
        )
    }

    @Test
    fun clampsDutyHopsAndTxdelay() {
        assertEquals(0, RemoteConfigApply.clampHops(0))
        assertEquals(8, RemoteConfigApply.clampHops(99))
        assertEquals(50, RemoteConfigApply.clampTxdelay(1))
        assertEquals(200, RemoteConfigApply.clampTxdelay(9_000))
        assertEquals(900, RemoteConfigApply.clampDutySecs(0))
        assertEquals(300, RemoteConfigApply.clampDutySecs(1))
    }

    @Test
    fun v3ApplySignsAndOmitsPassword() {
        val identity = ControlAuthIdentity(9L, 4)
        val packet = RemoteConfigApply.buildApply(
            0x11L,
            sample(applyMask = ConfigApplyMask.NAME or ConfigApplyMask.SF),
            3,
            77,
            identity
        )
        assertNotNull(packet)
        assertEquals(3, packet!!.protocolVersion)
        assertEquals(identity.sessionId, packet.sessionId)
        assertEquals(identity.counter, packet.authCounter)
        assertTrue(packet.authTag.size() == 16)
        assertEquals("", packet.config.configPassword)
        assertEquals(ConfigApplyMask.NAME or ConfigApplyMask.SF, packet.config.applyMask)
    }

    private fun sample(applyMask: Int) = RemoteConfigApplyRequest(
        nodeId = 0x22L,
        name = "Base",
        password = "secret",
        sf = 11,
        bw = 125f,
        txPower = 22,
        region = 1,
        role = 0,
        applyMask = applyMask
    )
}
