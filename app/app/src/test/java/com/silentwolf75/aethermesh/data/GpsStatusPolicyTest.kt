package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsStatusPolicyTest {
    @Test
    fun storedCoordinatesAloneNeverShowAsLocked() {
        // The field case: a node whose module never saw a satellite showed LOCKED
        // because phone-shared coordinates were still stored.
        assertEquals(GpsBadge.SEARCHING, GpsStatusPolicy.badge(GpsStatusPolicy.STATE_SEARCHING, true, 0))
        assertEquals(GpsBadge.LAST_KNOWN, GpsStatusPolicy.badge(GpsStatusPolicy.STATE_UNKNOWN, true, 0))
    }

    @Test
    fun firmwareStateDrivesBadge() {
        assertEquals(GpsBadge.FIX, GpsStatusPolicy.badge(5, false, 0))
        assertEquals(GpsBadge.SLEEPING, GpsStatusPolicy.badge(3, true, 2))
        assertEquals(GpsBadge.OFF, GpsStatusPolicy.badge(2, false, 0))
        assertEquals(GpsBadge.NO_MODULE, GpsStatusPolicy.badge(1, false, 0))
    }

    @Test
    fun legacyFirmwareFallsBackToConfig() {
        assertEquals(GpsBadge.OFF, GpsStatusPolicy.badge(0, true, 1))
        assertEquals(GpsBadge.SLEEPING, GpsStatusPolicy.badge(0, false, 2))
        assertEquals(GpsBadge.NO_POSITION, GpsStatusPolicy.badge(0, false, 0))
    }

    @Test
    fun satellitesAndHdopText() {
        assertEquals("7 used · 11 in view", GpsStatusPolicy.satellitesText(5, 7, 11))
        assertEquals("0 used · 0 in view", GpsStatusPolicy.satellitesText(4, 0, 0))
        assertNull(GpsStatusPolicy.satellitesText(0, 0, 0))
        assertNull(GpsStatusPolicy.satellitesText(1, 0, 0))
        assertEquals("1.7", GpsStatusPolicy.hdopText(17))
        assertEquals("100.0", GpsStatusPolicy.hdopText(1000))
        assertNull(GpsStatusPolicy.hdopText(0))
    }

    @Test
    fun badgeLabelIncludesSatelliteCountOnFix() {
        assertEquals("LOCKED · 7 SATS", GpsStatusPolicy.badgeLabel(GpsBadge.FIX, 7, spanish = false))
        assertEquals("SEARCHING", GpsStatusPolicy.badgeLabel(GpsBadge.SEARCHING, 0, spanish = false))
    }

    @Test
    fun positionSourceText() {
        assertEquals(PositionSourceLabel.PHONE, GpsStatusPolicy.source(4, 2))
        assertEquals(PositionSourceLabel.UNKNOWN, GpsStatusPolicy.source(0, 2))
        assertEquals("Phone GPS", GpsStatusPolicy.sourceText(PositionSourceLabel.PHONE, 0, false))
        assertEquals("Onboard GPS", GpsStatusPolicy.sourceText(PositionSourceLabel.ONBOARD_GPS, 3, false))
        assertEquals("Onboard GPS · 12 min ago", GpsStatusPolicy.sourceText(PositionSourceLabel.ONBOARD_GPS, 720, false))
        assertNull(GpsStatusPolicy.sourceText(PositionSourceLabel.UNKNOWN, 0, false))
    }
}
