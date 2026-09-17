package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingTelemetryPolicyTest {
    private val precise = ChannelConfig(
        name = "Primary",
        psk = "",
        isPrimary = true,
        positionEnabled = true,
        preciseLocation = true,
        precisionMiles = 0f
    )

    @Test
    fun precisePrimaryLeavesCoordsAlone() {
        val coords = IncomingTelemetryPolicy.displayCoords(35f, -106f, 0xABCDL, precise)
        assertEquals(35f, coords.latitude)
        assertEquals(-106f, coords.longitude)
        assertFalse(coords.fuzzed)
        assertFalse(PhoneLocationShare.shouldFuzz(null))
    }

    @Test
    fun coarsePrimaryUsesStablePhoneFuzz() {
        val fuzzy = precise.copy(preciseLocation = false, precisionMiles = 2f)
        val a = IncomingTelemetryPolicy.displayCoords(35f, -106f, 0xABCDL, fuzzy)
        val b = IncomingTelemetryPolicy.displayCoords(35f, -106f, 0xABCDL, fuzzy)
        assertTrue(a.fuzzed)
        assertEquals(a.latitude, b.latitude)
        assertEquals(a.longitude, b.longitude)
        val expected = PhoneLocationShare.fuzzMeters(35.0, -106.0, 0xABCDL, 3218)
        assertEquals(expected.first.toFloat(), a.latitude)
        assertEquals(expected.second.toFloat(), a.longitude)
    }

    @Test
    fun secondaryBlurFloorAppliesEvenWhenPrimaryIsPrecise() {
        val secondary = precise.copy(
            name = "Crew",
            isPrimary = false,
            preciseLocation = false,
            precisionMiles = 1f
        )
        val coords = IncomingTelemetryPolicy.displayCoords(
            35f, -106f, 0xABCDL, listOf(precise, secondary)
        )
        assertTrue(coords.fuzzed)
        assertEquals(1609, IncomingTelemetryPolicy.channelFloorMeters(listOf(precise, secondary)))
    }

    @Test
    fun regionIsTrustedOnlyWhenSpreadingFactorPresent() {
        assertEquals(1, IncomingTelemetryPolicy.trustedRegion(11, 1))
        assertEquals(0, IncomingTelemetryPolicy.trustedRegion(7, 0))
        assertEquals(-1, IncomingTelemetryPolicy.trustedRegion(0, 0))
        assertEquals(-1, IncomingTelemetryPolicy.trustedRegion(13, 2))
    }

    @Test
    fun protocolVersionNeverGoesBelowOne() {
        assertEquals(1, IncomingTelemetryPolicy.trustedProtocolVersion(0))
        assertEquals(3, IncomingTelemetryPolicy.trustedProtocolVersion(3))
    }

    @Test
    fun channelFloorMetersMatchesPhoneShare() {
        assertEquals(0, IncomingTelemetryPolicy.channelFloorMeters(precise))
        val fuzzy = precise.copy(preciseLocation = false, precisionMiles = 2f)
        assertEquals(PhoneLocationShare.channelFloorMeters(fuzzy), IncomingTelemetryPolicy.channelFloorMeters(fuzzy))
        assertTrue(IncomingTelemetryPolicy.channelFloorMeters(fuzzy) > 3000)
    }

    @Test
    fun uptimeKeepsUnsigned32() {
        assertEquals(0L, IncomingTelemetryPolicy.unsigned32(0))
        assertEquals(60L, IncomingTelemetryPolicy.unsigned32(60))
        assertEquals(0xFFFFFFFFL, IncomingTelemetryPolicy.unsigned32(-1))
    }
}
