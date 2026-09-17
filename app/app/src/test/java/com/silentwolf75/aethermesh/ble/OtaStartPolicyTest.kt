package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaStartPolicyTest {
    @Test
    fun refusesSecondTransferAndUnauthedLink() {
        assertEquals(
            OtaStartDecision.AlreadyActive,
            OtaStartPolicy.begin(active = true, connected = true, authenticated = true)
        )
        val blocked = OtaStartPolicy.begin(false, connected = true, authenticated = false)
            as OtaStartDecision.Blocked
        assertEquals(OtaStartPolicy.NOT_READY, blocked.state.status)
        assertTrue(blocked.state.error)
        assertEquals(
            OtaStartDecision.Run,
            OtaStartPolicy.begin(false, connected = true, authenticated = true)
        )
    }

    @Test
    fun dfuNeedsADeviceAddress() {
        val missing = OtaStartPolicy.requireAddress(null) as OtaStartDecision.Blocked
        assertEquals(OtaStartPolicy.NO_ADDRESS, missing.state.status)
        assertEquals(OtaStartDecision.Run, OtaStartPolicy.requireAddress("AA:BB"))
    }
}
