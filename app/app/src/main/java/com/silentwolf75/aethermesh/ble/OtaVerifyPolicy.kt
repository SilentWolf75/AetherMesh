package com.silentwolf75.aethermesh.ble

import com.silentwolf75.aethermesh.data.OtaState

sealed class OtaVerifyDecision {
    object Ignore : OtaVerifyDecision()
    object Wait : OtaVerifyDecision()
    data class Finish(val state: OtaState) : OtaVerifyDecision()
}

/**
 * Post-flash telemetry vs remembered pre/expected versions. Transfer chunking
 * stays in [OtaTransferPolicy]; BLE/state updates stay in the repository.
 */
object OtaVerifyPolicy {
    fun decide(
        verifyNodeId: Long,
        nodeId: Long,
        expected: String,
        pre: String,
        reported: String
    ): OtaVerifyDecision {
        if (verifyNodeId == 0L || nodeId != verifyNodeId) return OtaVerifyDecision.Ignore
        val stillOnPre = pre.isNotBlank() && reported.equals(pre, ignoreCase = true)
        val matchesExpected = expected.isNotBlank() &&
            OtaTransferPolicy.versionsLookCompatible(reported, expected)
        return when {
            stillOnPre && (expected.isBlank() || !matchesExpected) ->
                OtaVerifyDecision.Finish(rollbackState(reported, expected))
            matchesExpected || (pre.isNotBlank() && !stillOnPre) ->
                OtaVerifyDecision.Finish(confirmedState(reported, expected))
            else -> OtaVerifyDecision.Wait
        }
    }

    fun rollbackStatus(reported: String): String =
        "Update may not have applied — still on $reported"

    fun confirmedStatus(reported: String, expected: String): String {
        val extra = if (expected.isNotBlank() && reported != expected) {
            val label = expected.ifBlank { reported }
            " (expected $label)"
        } else {
            ""
        }
        return "Update confirmed — now running $reported$extra"
    }

    fun rollbackState(reported: String, expected: String): OtaState = OtaState(
        progress = 100,
        done = true,
        error = true,
        suspectRollback = true,
        expectedVersion = expected,
        status = rollbackStatus(reported)
    )

    fun confirmedState(reported: String, expected: String): OtaState = OtaState(
        progress = 100,
        done = true,
        expectedVersion = expected,
        status = confirmedStatus(reported, expected)
    )
}
