package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.silentwolf75.aethermesh.proto.MeshPacket

class PhoneLocationShareTest {
    private val primaryPrecise = ChannelConfig(
        name = "Primary",
        psk = "",
        isPrimary = true,
        positionEnabled = true,
        preciseLocation = true,
        precisionMiles = 0f
    )

    @Test
    fun rejectsNullIslandAndOutOfRange() {
        assertFalse(PhoneLocationShare.isValidFix(0.0, 0.0))
        assertFalse(PhoneLocationShare.isValidFix(Double.NaN, -106.0))
        assertFalse(PhoneLocationShare.isValidFix(91.0, 0.0))
        assertTrue(PhoneLocationShare.isValidFix(35.0, -106.0))
        assertEquals(
            PhoneLocationDecision.SkipInvalid,
            PhoneLocationShare.decide(0.0, 0.0, 1L, primaryPrecise, 0L, 1_000L)
        )
    }

    @Test
    fun throttlesWithinOneMinute() {
        assertEquals(
            PhoneLocationDecision.SkipThrottled,
            PhoneLocationShare.decide(35.0, -106.0, 1L, primaryPrecise, 1_000L, 30_000L)
        )
        val send = PhoneLocationShare.decide(35.0, -106.0, 1L, primaryPrecise, 1_000L, 61_000L)
        assertTrue(send is PhoneLocationDecision.Send)
    }

    @Test
    fun skipsWhenPrimaryDisablesPosition() {
        val off = primaryPrecise.copy(positionEnabled = false)
        assertEquals(
            PhoneLocationDecision.SkipPositionDisabled,
            PhoneLocationShare.decide(35.0, -106.0, 1L, off, 0L, 1_000L)
        )
    }

    @Test
    fun secondaryChannelPrivacyOverridesPrecisePrimary() {
        val secondaryOff = ChannelConfig(
            name = "Private",
            psk = "",
            isPrimary = false,
            positionEnabled = false,
            preciseLocation = true
        )
        assertEquals(
            PhoneLocationDecision.SkipPositionDisabled,
            PhoneLocationShare.decide(
                35.0, -106.0, 1L, listOf(primaryPrecise, secondaryOff), 0L, 1_000L
            )
        )

        val secondaryBlur = ChannelConfig(
            name = "Blur",
            psk = "",
            isPrimary = false,
            positionEnabled = true,
            preciseLocation = false,
            precisionMiles = 2f
        )
        val send = PhoneLocationShare.decide(
            35.0, -106.0, 0xABCDL, listOf(primaryPrecise, secondaryBlur), 0L, 1_000L
        ) as PhoneLocationDecision.Send
        assertTrue(send.latitude != 35.0 || send.longitude != -106.0)
        assertEquals(3218, PhoneLocationShare.channelFloorMeters(listOf(primaryPrecise, secondaryBlur)))
    }

    @Test
    fun fuzzIsStablePerNodeAndChangesCoords() {
        val fuzzy = primaryPrecise.copy(preciseLocation = false, precisionMiles = 2f)
        val a = PhoneLocationShare.decide(35.0, -106.0, 0xABCDL, fuzzy, 0L, 1_000L) as PhoneLocationDecision.Send
        val b = PhoneLocationShare.decide(35.0, -106.0, 0xABCDL, fuzzy, 0L, 1_000L) as PhoneLocationDecision.Send
        assertEquals(a.latitude, b.latitude, 0.0)
        assertEquals(a.longitude, b.longitude, 0.0)
        assertTrue(a.latitude != 35.0 || a.longitude != -106.0)
        val other = PhoneLocationShare.decide(35.0, -106.0, 0x1234L, fuzzy, 0L, 1_000L) as PhoneLocationDecision.Send
        assertTrue(other.latitude != a.latitude || other.longitude != a.longitude)
    }

    @Test
    fun packetTargetsLocalNodeWithPhoneModel() {
        val packet = PhoneLocationShare.buildPacket(0x11L, 7, 35.0, -106.0)
        assertEquals(0x11, packet.senderId)
        assertEquals(0x11, packet.recipientId)
        assertEquals(1, packet.hopLimit)
        assertFalse(packet.wantAck)
        assertEquals(MeshPacket.PayloadCase.TELEMETRY, packet.payloadCase)
        assertEquals(PhoneLocationShare.NODE_MODEL, packet.telemetry.nodeModel)
        assertEquals(100, packet.telemetry.batteryLevel)
    }

    @Test
    fun channelFloorMetersIsZeroWhenPrecise() {
        assertEquals(0, PhoneLocationShare.channelFloorMeters(primaryPrecise))
        assertEquals(0, PhoneLocationShare.channelFloorMeters(null))
        val fuzzy = primaryPrecise.copy(preciseLocation = false, precisionMiles = 1f)
        assertEquals(1609, PhoneLocationShare.channelFloorMeters(fuzzy))
    }
}
