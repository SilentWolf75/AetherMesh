package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaVerifyPolicyTest {
    @Test
    fun ignoresOtherNodesAndClearedVerify() {
        assertEquals(
            OtaVerifyDecision.Ignore,
            OtaVerifyPolicy.decide(0L, 1L, "1.3.2", "1.3.1", "1.3.2")
        )
        assertEquals(
            OtaVerifyDecision.Ignore,
            OtaVerifyPolicy.decide(1L, 2L, "1.3.2", "1.3.1", "1.3.2")
        )
    }

    @Test
    fun stillOnPreIsRollbackUnlessExpectedAlsoMatches() {
        val rollback = OtaVerifyPolicy.decide(9L, 9L, "1.3.2", "1.3.1", "1.3.1")
            as OtaVerifyDecision.Finish
        assertTrue(rollback.state.suspectRollback)
        assertTrue(rollback.state.error)
        assertEquals(
            "Update may not have applied — still on 1.3.1",
            rollback.state.status
        )
        val confirmedSame = OtaVerifyPolicy.decide(9L, 9L, "1.3.1", "1.3.1", "1.3.1")
        assertTrue(confirmedSame is OtaVerifyDecision.Finish)
        assertTrue(!(confirmedSame as OtaVerifyDecision.Finish).state.suspectRollback)
    }

    @Test
    fun expectedMatchOrLeftPreConfirms() {
        val matched = OtaVerifyPolicy.decide(9L, 9L, "1.3.2", "1.3.1", "1.3.2-cec")
            as OtaVerifyDecision.Finish
        assertTrue(matched.state.status.contains("now running 1.3.2-cec"))
        assertTrue(matched.state.status.contains("expected 1.3.2"))
        val leftPre = OtaVerifyPolicy.decide(9L, 9L, "", "1.3.1", "1.3.2")
            as OtaVerifyDecision.Finish
        assertEquals(
            "Update confirmed — now running 1.3.2",
            leftPre.state.status
        )
    }

    @Test
    fun waitsWhenTelemetryCannotDecide() {
        assertEquals(
            OtaVerifyDecision.Wait,
            OtaVerifyPolicy.decide(9L, 9L, "1.3.2", "", "1.2.0")
        )
    }
}
