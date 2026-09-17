package com.silentwolf75.aethermesh.data

/**
 * Shared gate for system notifications (API 33+ POST_NOTIFICATIONS + Settings
 * Background Alerts). Actual notify stays in [AetherMeshRepository].
 */
object NotificationGatePolicy {
    const val POST_NOTIFICATIONS_SDK = 33

    fun canPost(sdkInt: Int, permissionGranted: Boolean): Boolean =
        sdkInt < POST_NOTIFICATIONS_SDK || permissionGranted

    /** Battery alerts: bg toggle + permission only (no mute / foreground). */
    fun mayNotifyBattery(
        bgAlertsEnabled: Boolean,
        sdkInt: Int,
        permissionGranted: Boolean
    ): Boolean = bgAlertsEnabled && canPost(sdkInt, permissionGranted)

    /** Chat alerts: IncomingMessageAlertPolicy gates plus permission. */
    fun mayNotifyMessage(
        bgAlertsEnabled: Boolean,
        muted: Boolean,
        activityVisible: Boolean,
        sdkInt: Int,
        permissionGranted: Boolean
    ): Boolean =
        IncomingMessageAlertPolicy.shouldNotify(bgAlertsEnabled, muted, activityVisible) &&
            canPost(sdkInt, permissionGranted)
}
