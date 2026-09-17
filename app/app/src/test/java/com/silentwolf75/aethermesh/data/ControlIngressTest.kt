package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.AuthResponse
import com.silentwolf75.aethermesh.proto.DeliveryStatus
import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.NodeConfig
import com.silentwolf75.aethermesh.proto.OtaStatus
import org.junit.Assert.*
import org.junit.Test

class ControlIngressTest {
    private fun auth(sender: Int) = MeshPacket.newBuilder().setSenderId(sender)
        .setAuthResponse(AuthResponse.newBuilder().setSuccess(true)).build()

    @Test fun onlyBootstrapCanResolveAProvisionalNodeIdentity() {
        assertTrue(IncomingPacketPolicy.acceptControl(auth(0x12345678), 0, false))
        assertFalse(IncomingPacketPolicy.acceptControl(auth(0), 0, false))
        assertFalse(IncomingPacketPolicy.acceptControl(auth(99), 10, true))
        assertTrue(IncomingPacketPolicy.acceptControl(auth(10), 10, true))
        assertFalse(IncomingPacketPolicy.acceptControl(
            auth(99).toBuilder().setRxRssi(-70f).build(), 0, false))
        assertFalse(IncomingPacketPolicy.acceptControl(
            auth(10).toBuilder().setRecipientId(42).build(), 10, true))
    }

    @Test fun deliveryAndOtaRequireTheAuthenticatedLocalNode() {
        val delivery = MeshPacket.newBuilder().setSenderId(10)
            .setDeliveryStatus(DeliveryStatus.newBuilder().setState(DeliveryStatus.State.DELIVERED))
            .setRxRssi(-75f).build() // local delivery event legitimately includes ACK signal
        val ota = MeshPacket.newBuilder().setSenderId(10)
            .setOtaStatus(OtaStatus.newBuilder().setState(OtaStatus.State.SUCCESS)).build()
        for (packet in listOf(delivery, ota)) {
            assertTrue(IncomingPacketPolicy.acceptControl(packet, 10, true))
            assertFalse(IncomingPacketPolicy.acceptControl(packet, 10, false))
            assertFalse(IncomingPacketPolicy.acceptControl(packet, 11, true))
            assertFalse(IncomingPacketPolicy.acceptControl(
                packet.toBuilder().setRecipientId(99).build(), 10, true))
        }
    }

    @Test fun radioLikeAuthWithSignalIsRejectedEvenDuringBootstrap() {
        assertFalse(
            IncomingPacketPolicy.acceptControl(
                auth(0x12345678).toBuilder().setRxRssi(-80f).setRxSnr(5f).build(),
                0,
                false
            )
        )
    }

    @Test fun localConfigCannotClaimAnotherNodesIdentity() {
        val report = MeshPacket.newBuilder().setSenderId(10)
            .setConfig(NodeConfig.newBuilder().setReportOnly(true)).build()
        assertTrue(IncomingPacketPolicy.acceptControl(report, 10, true))
        assertFalse(IncomingPacketPolicy.acceptControl(report, 11, true))
        // A remote report uses a mesh recipient, not the local BLE client.
        assertTrue(IncomingPacketPolicy.acceptControl(
            report.toBuilder().setRecipientId(11).build(), 11, true))
    }
}
