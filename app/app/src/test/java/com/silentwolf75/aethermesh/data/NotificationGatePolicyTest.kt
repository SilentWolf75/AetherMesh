package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGatePolicyTest {
    @Test
    fun canPostBeforeApi33IgnoresPermission() {
        assertTrue(NotificationGatePolicy.canPost(32, permissionGranted = false))
        assertTrue(NotificationGatePolicy.canPost(32, permissionGranted = true))
    }

    @Test
    fun canPostApi33RequiresGrant() {
        assertFalse(NotificationGatePolicy.canPost(33, permissionGranted = false))
        assertTrue(NotificationGatePolicy.canPost(33, permissionGranted = true))
        assertFalse(NotificationGatePolicy.canPost(34, permissionGranted = false))
    }

    @Test
    fun batteryNeedsBgAlertsAndPermission() {
        assertTrue(
            NotificationGatePolicy.mayNotifyBattery(true, 33, permissionGranted = true)
        )
        assertFalse(
            NotificationGatePolicy.mayNotifyBattery(false, 33, permissionGranted = true)
        )
        assertFalse(
            NotificationGatePolicy.mayNotifyBattery(true, 33, permissionGranted = false)
        )
        assertTrue(
            NotificationGatePolicy.mayNotifyBattery(true, 31, permissionGranted = false)
        )
    }

    @Test
    fun messageCombinesIncomingGatesAndPermission() {
        assertTrue(
            NotificationGatePolicy.mayNotifyMessage(
                bgAlertsEnabled = true,
                muted = false,
                activityVisible = false,
                sdkInt = 33,
                permissionGranted = true
            )
        )
        assertFalse(
            NotificationGatePolicy.mayNotifyMessage(
                bgAlertsEnabled = true,
                muted = true,
                activityVisible = false,
                sdkInt = 33,
                permissionGranted = true
            )
        )
        assertFalse(
            NotificationGatePolicy.mayNotifyMessage(
                bgAlertsEnabled = true,
                muted = false,
                activityVisible = true,
                sdkInt = 33,
                permissionGranted = true
            )
        )
        assertFalse(
            NotificationGatePolicy.mayNotifyMessage(
                bgAlertsEnabled = true,
                muted = false,
                activityVisible = false,
                sdkInt = 33,
                permissionGranted = false
            )
        )
    }
}
