package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeTimePolicyTest {
    private val now = 1_000_000_000L

    @Test
    fun lastHeardBuckets() {
        assertEquals("never", RelativeTimePolicy.lastHeard(0, false, now))
        assertEquals("nunca", RelativeTimePolicy.lastHeard(0, true, now))
        assertEquals("just now", RelativeTimePolicy.lastHeard(now - 10_000, false, now))
        assertEquals("5m ago", RelativeTimePolicy.lastHeard(now - 5 * 60_000, false, now))
        assertEquals("2h ago", RelativeTimePolicy.lastHeard(now - 2 * 3_600_000, false, now))
        assertEquals("3d ago", RelativeTimePolicy.lastHeard(now - 3 * RelativeTimePolicy.DAY_MS, false, now))
        assertEquals("hace 5m", RelativeTimePolicy.lastHeard(now - 5 * 60_000, true, now))
    }

    @Test
    fun relativeAgeCapsAtHours() {
        assertEquals("—", RelativeTimePolicy.relativeAge(0, false, now))
        assertEquals("just now", RelativeTimePolicy.relativeAge(now - 1_000, false, now))
        assertEquals("4h ago", RelativeTimePolicy.relativeAge(now - 4 * 3_600_000, false, now))
    }

    @Test
    fun uptimeAndDaysLabels() {
        assertEquals("45s", RelativeTimePolicy.uptime(45, false))
        assertEquals("1m 30s", RelativeTimePolicy.uptime(90, false))
        assertEquals("1h 1m", RelativeTimePolicy.uptime(3661, false))
        assertEquals("1d 1h", RelativeTimePolicy.uptime(90_000, false))
        assertEquals(0, RelativeTimePolicy.daysSinceHeard(now - 3_600_000, now))
        assertEquals("1 day since heard", RelativeTimePolicy.daysSinceHeardLabel(now - RelativeTimePolicy.DAY_MS, false, now))
        assertEquals("2 días sin oír", RelativeTimePolicy.daysSinceHeardLabel(now - 2 * RelativeTimePolicy.DAY_MS, true, now))
    }

    @Test
    fun gpsLabelsAndDutyClamp() {
        assertEquals("No GPS lock yet", RelativeTimePolicy.gpsLockAge(0, false, now))
        assertEquals("GPS just now", RelativeTimePolicy.gpsLockAge(now - 1_000, false, now))
        assertEquals("GPS: always on", RelativeTimePolicy.gpsDutyStatus(0, 900, false))
        assertEquals("GPS: duty (15 min)", RelativeTimePolicy.gpsDutyStatus(2, 900, false))
        assertNull(RelativeTimePolicy.gpsDutyStatus(null, 900, false))
        assertEquals(900, RelativeTimePolicy.clampDutySecs(0))
        assertEquals(300, RelativeTimePolicy.clampDutySecs(60))
        assertEquals(3600, RelativeTimePolicy.clampDutySecs(99_000))
    }
}
