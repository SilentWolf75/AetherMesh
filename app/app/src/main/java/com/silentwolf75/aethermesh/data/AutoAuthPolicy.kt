package com.silentwolf75.aethermesh.data

sealed class AutoAuthExhausted {
    object Idle : AutoAuthExhausted()
    object ForceRefresh : AutoAuthExhausted()
    object PromptUnlock : AutoAuthExhausted()
}

/**
 * BLE auto-unlock timings. GATT write and AuthResponse wait stay in
 * [AetherMeshRepository]. Write success is never treated as unlock.
 */
object AutoAuthPolicy {
    const val INITIAL_POST_SETTINGS_MS = 700L
    const val INITIAL_NORMAL_MS = 400L
    const val GATT_READY_POLL_MS = 100L
    const val GATT_READY_POLLS = 12
    const val GATT_GAP_MS = 200L
    const val WAIT_WRITE_FAIL_MS = 500L
    const val WAIT_POST_SETTINGS_MS = 1_200L
    const val WAIT_NORMAL_MS = 900L
    const val FORCE_REFRESH_MS = 1_000L
    const val CHALLENGE_THROTTLE_MS = 1_200L
    const val CHALLENGE_DELAY_MS = 120L

    fun initialDelayMs(postSettings: Boolean): Long =
        if (postSettings) INITIAL_POST_SETTINGS_MS else INITIAL_NORMAL_MS

    fun attemptCount(hasPassword: Boolean, postSettings: Boolean): Int = when {
        hasPassword && postSettings -> 8
        hasPassword -> 6
        else -> 3
    }

    fun responseWaitMs(wrote: Boolean, postSettings: Boolean): Long = when {
        !wrote -> WAIT_WRITE_FAIL_MS
        postSettings -> WAIT_POST_SETTINGS_MS
        else -> WAIT_NORMAL_MS
    }

    fun shouldStop(connected: Boolean, authenticated: Boolean): Boolean =
        !connected || authenticated

    fun onExhausted(
        connected: Boolean,
        authenticated: Boolean,
        postSettings: Boolean,
        refreshUsed: Boolean
    ): AutoAuthExhausted {
        if (!connected || authenticated) return AutoAuthExhausted.Idle
        if (postSettings && !refreshUsed) return AutoAuthExhausted.ForceRefresh
        return AutoAuthExhausted.PromptUnlock
    }

    fun shouldShowUnlockPrompt(authenticationRequired: Boolean?): Boolean =
        authenticationRequired == null

    fun shouldResubmitChallenge(
        nowMs: Long,
        lastResubmitMs: Long,
        connected: Boolean,
        gattReady: Boolean,
        hasSavedPassword: Boolean
    ): Boolean {
        if (!connected || !gattReady || !hasSavedPassword) return false
        return nowMs - lastResubmitMs >= CHALLENGE_THROTTLE_MS
    }
}
