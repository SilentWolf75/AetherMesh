package com.silentwolf75.aethermesh.data

/**
 * BLE timing after local Settings apply (node MCU reboot). Close ahead of
 * reconnect; [CLOSE_AFTER_MS] matches [AutoAuthPolicy.WAIT_POST_SETTINGS_MS]
 * so the first AuthRequest is not racing a half-dead GATT.
 */
object SettingsRebootPolicy {
    const val CLOSE_AFTER_MS = AutoAuthPolicy.WAIT_POST_SETTINGS_MS
    const val RECONNECT_AFTER_MS = 5_000L

    /** Gap between store-and-forward retry BLE writes for one peer. */
    const val QUEUED_RETRY_STAGGER_MS = 2_000L
}
