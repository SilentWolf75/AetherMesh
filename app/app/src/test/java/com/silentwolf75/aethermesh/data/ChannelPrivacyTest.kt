package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.NodeConfig
import org.junit.Assert.*
import org.junit.Test

class ChannelPrivacyTest {
    private val exact = ChannelConfig(name = "General", psk = "", isPrimary = true)
    @Test fun mostRestrictiveChannelGovernsSharedTelemetry() {
        val blurred = exact.copy(name = "Private", preciseLocation = false, precisionMiles = 2f)
        assertEquals(ChannelPrivacy(false, 3218), ChannelPrivacy.fromChannels(listOf(exact, blurred)))
        val disabled = blurred.copy(positionEnabled = false)
        assertEquals(ChannelPrivacy(true, 3218), ChannelPrivacy.fromChannels(listOf(exact, disabled)))
        assertEquals(ChannelPrivacy(), ChannelPrivacy.fromChannels(listOf(exact)))
    }
    @Test fun invalidBlurDoesNotSilentlyEnablePrecisePosition() {
        for (radius in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertTrue(ChannelPrivacy.fromChannels(listOf(
                exact.copy(preciseLocation = false, precisionMiles = radius))).radiusM > 0)
        }
    }
    @Test fun privacyPacketIsLocalAndReportsMustMatchPersistedSettings() {
        val desired = ChannelPrivacy(true, 1609)
        val packet = MeshPacket.parseFrom(desired.packet(123))
        assertEquals(MeshPacket.PayloadCase.POSITION_PRIVACY, packet.payloadCase)
        assertEquals(0, packet.recipientId)
        assertEquals(0, packet.hopLimit)
        assertFalse(packet.wantAck)
        val report = NodeConfig.newBuilder().setReportOnly(true)
            .setPositionPrivacySupported(true).setChannelPositionDisabled(true)
            .setChannelPrecisionM(1609).build()
        assertEquals(desired, ChannelPrivacy.fromReport(report))
        assertNotEquals(desired, ChannelPrivacy.fromReport(report.toBuilder()
            .setChannelPositionDisabled(false).build()))
    }

    @Test fun statusLabelsAreReadableInEnglishAndSpanish() {
        for (status in ChannelPrivacyStatus.entries) {
            val en = status.label(false)
            val es = status.label(true)
            assertTrue(en.isNotBlank())
            assertTrue(es.isNotBlank())
            assertFalse("English label leaked '?': $en", en.contains('?'))
            assertFalse("Spanish label leaked '?': $es", es.contains('?'))
        }
        assertTrue(ChannelPrivacyStatus.DISCONNECTED.label(true).contains("ubicación"))
        assertTrue(ChannelPrivacyStatus.CONFIRMED.label(true).contains("más"))
        assertTrue(ChannelPrivacyStatus.CHECKING.label(true).contains("…"))
    }
}
