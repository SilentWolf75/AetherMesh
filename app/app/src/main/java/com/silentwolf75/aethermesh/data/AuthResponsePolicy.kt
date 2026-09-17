package com.silentwolf75.aethermesh.data

sealed class AuthResponseAction {
    /** Change-password reply. Must not unlock or lock the BLE session. */
    data class PasswordChanged(
        val success: Boolean,
        val savePendingPassword: Boolean
    ) : AuthResponseAction()

    data class Unlocked(
        val savePendingPassword: Boolean,
        /** Previous BLE placeholder id to migrate; 0 means none. */
        val migrateFromNodeId: Long
    ) : AuthResponseAction()

    /**
     * Firmware asked for auth without rejecting a typed password.
     * Do not wipe a saved password; always drop the local session.
     */
    data class Challenge(
        val needsInitialPassword: Boolean
    ) : AuthResponseAction()

    data class Rejected(
        val needsInitialPassword: Boolean,
        val forgetSavedPassword: Boolean
    ) : AuthResponseAction()
}

/**
 * AuthResponse branch for local BLE unlock / password change.
 * GATT, prefs, and packet send stay in [AetherMeshRepository].
 */
object AuthResponsePolicy {
    fun isLockChallenge(message: String): Boolean {
        val msg = message
        return msg.contains("Authentication required", ignoreCase = true) ||
            msg.contains("Password required", ignoreCase = true)
    }

    fun isIncorrectPassword(message: String): Boolean =
        message.contains("Incorrect", ignoreCase = true)

    fun decide(
        pendingPasswordChange: Boolean,
        success: Boolean,
        passwordNotSet: Boolean,
        message: String,
        hasPendingPassword: Boolean,
        oldNodeId: Long,
        senderId: Long
    ): AuthResponseAction {
        if (pendingPasswordChange) {
            // Unsolicited lock challenges (BLE reconnect / session desync) must
            // not look like a failed password change — that cleared the wait and
            // left a later unlock mis-classified as PasswordChanged(success).
            if (!success && isLockChallenge(message)) {
                return AuthResponseAction.Challenge(needsInitialPassword = passwordNotSet)
            }
            return AuthResponseAction.PasswordChanged(
                success = success,
                savePendingPassword = success && hasPendingPassword
            )
        }
        if (success) {
            val migrateFrom = if (oldNodeId != 0L && oldNodeId != senderId) oldNodeId else 0L
            return AuthResponseAction.Unlocked(
                savePendingPassword = hasPendingPassword,
                migrateFromNodeId = migrateFrom
            )
        }
        if (isLockChallenge(message)) {
            return AuthResponseAction.Challenge(needsInitialPassword = passwordNotSet)
        }
        return AuthResponseAction.Rejected(
            needsInitialPassword = passwordNotSet,
            forgetSavedPassword = !passwordNotSet && isIncorrectPassword(message)
        )
    }
}
