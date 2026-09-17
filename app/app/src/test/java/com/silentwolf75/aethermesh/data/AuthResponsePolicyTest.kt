package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthResponsePolicyTest {
    @Test
    fun passwordChangeNeverGoesThroughUnlockOrLock() {
        val ok = AuthResponsePolicy.decide(
            pendingPasswordChange = true,
            success = true,
            passwordNotSet = false,
            message = "ok",
            hasPendingPassword = true,
            oldNodeId = 1L,
            senderId = 2L
        )
        assertEquals(AuthResponseAction.PasswordChanged(success = true, savePendingPassword = true), ok)

        val fail = AuthResponsePolicy.decide(
            pendingPasswordChange = true,
            success = false,
            passwordNotSet = false,
            message = "Incorrect current password",
            hasPendingPassword = true,
            oldNodeId = 1L,
            senderId = 2L
        )
        assertEquals(AuthResponseAction.PasswordChanged(success = false, savePendingPassword = false), fail)
    }

    @Test
    fun lockChallengeDuringPasswordChangeIsNotAFailedChange() {
        val challenge = AuthResponsePolicy.decide(
            pendingPasswordChange = true,
            success = false,
            passwordNotSet = false,
            message = "Authentication required",
            hasPendingPassword = true,
            oldNodeId = 1L,
            senderId = 1L
        )
        assertEquals(AuthResponseAction.Challenge(needsInitialPassword = false), challenge)

        val initial = AuthResponsePolicy.decide(
            pendingPasswordChange = true,
            success = false,
            passwordNotSet = true,
            message = "Password required",
            hasPendingPassword = false,
            oldNodeId = 0L,
            senderId = 1L
        )
        assertEquals(AuthResponseAction.Challenge(needsInitialPassword = true), initial)
    }

    @Test
    fun unlockMigratesPlaceholderAndSavesPassword() {
        val action = AuthResponsePolicy.decide(
            pendingPasswordChange = false,
            success = true,
            passwordNotSet = false,
            message = "",
            hasPendingPassword = true,
            oldNodeId = 0xABCL,
            senderId = 0x12345678L
        ) as AuthResponseAction.Unlocked
        assertTrue(action.savePendingPassword)
        assertEquals(0xABCL, action.migrateFromNodeId)
    }

    @Test
    fun unlockSkipsMigrateWhenIdsMatchOrPlaceholderZero() {
        val same = AuthResponsePolicy.decide(
            false, true, false, "", true, 0x11L, 0x11L
        ) as AuthResponseAction.Unlocked
        assertEquals(0L, same.migrateFromNodeId)

        val none = AuthResponsePolicy.decide(
            false, true, false, "", false, 0L, 0x11L
        ) as AuthResponseAction.Unlocked
        assertFalse(none.savePendingPassword)
        assertEquals(0L, none.migrateFromNodeId)
    }

    @Test
    fun lockChallengeDoesNotForgetSavedPassword() {
        val required = AuthResponsePolicy.decide(
            pendingPasswordChange = false,
            success = false,
            passwordNotSet = false,
            message = "Authentication required",
            hasPendingPassword = true,
            oldNodeId = 1L,
            senderId = 1L
        )
        assertEquals(AuthResponseAction.Challenge(needsInitialPassword = false), required)

        val initial = AuthResponsePolicy.decide(
            false, false, true, "Password required", false, 0L, 1L
        )
        assertEquals(AuthResponseAction.Challenge(needsInitialPassword = true), initial)
    }

    @Test
    fun wrongPasswordForgetsSavedAndBlankFirmwareNeedsInitial() {
        val wrong = AuthResponsePolicy.decide(
            false, false, false, "Incorrect password", true, 0L, 1L
        ) as AuthResponseAction.Rejected
        assertFalse(wrong.needsInitialPassword)
        assertTrue(wrong.forgetSavedPassword)

        val blank = AuthResponsePolicy.decide(
            false, false, true, "not a challenge", false, 0L, 1L
        ) as AuthResponseAction.Rejected
        assertTrue(blank.needsInitialPassword)
        assertFalse(blank.forgetSavedPassword)
    }
}
