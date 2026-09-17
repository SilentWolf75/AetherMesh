package com.silentwolf75.aethermesh.data

import android.app.Application
import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.OtaStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Esp32OtaSenderTest {

    @Test
    fun tinyImageSucceedsWhenNodeAcksEachControlAndWindow() = runTest {
        val inbox = OtaStatusInbox()
        val sender = Esp32OtaSender(
            inbox = inbox,
            send = { bytes, _, _ ->
                val pkt = MeshPacket.parseFrom(bytes)
                when (pkt.payloadCase) {
                    MeshPacket.PayloadCase.OTA_CONTROL -> when (pkt.otaControl.op) {
                        com.silentwolf75.aethermesh.proto.OtaControl.Op.BEGIN ->
                            inbox.offer(
                                OtaStatus.newBuilder()
                                    .setState(OtaStatus.State.READY)
                                    .setNextOffset(224)
                                    .build()
                            )
                        com.silentwolf75.aethermesh.proto.OtaControl.Op.END ->
                            inbox.offer(
                                OtaStatus.newBuilder()
                                    .setState(OtaStatus.State.SUCCESS)
                                    .build()
                            )
                        else -> {}
                    }
                    MeshPacket.PayloadCase.OTA_DATA -> inbox.offer(
                        OtaStatus.newBuilder()
                            .setState(OtaStatus.State.IN_PROGRESS)
                            .setNextOffset(pkt.otaData.offset + pkt.otaData.data.size())
                            .build()
                    )
                    else -> {}
                }
                true
            },
            negotiatedMtu = { 247 },
            isConnected = { true }
        )
        val states = mutableListOf<OtaState>()
        var success = false
        sender.upload(
            firmware = ByteArray(80) { 1 },
            nodeId = 0xABC,
            expectedVersion = "1.3.2",
            onState = { states.add(it) },
            onSuccess = { success = true },
            onDiagnostic = {},
            requestHighPriority = {},
            resumeAfter = {}
        )
        assertTrue(success)
        assertTrue(states.any { it.done && !it.error })
        assertFalse(states.last().error)
    }

    @Test
    fun writeFailureSurfacesAsErrorWithoutSuccess() = runTest {
        val inbox = OtaStatusInbox()
        val sender = Esp32OtaSender(
            inbox = inbox,
            send = { _, _, _ -> false },
            negotiatedMtu = { 247 },
            isConnected = { true }
        )
        val states = mutableListOf<OtaState>()
        var success = false
        sender.upload(
            firmware = ByteArray(16),
            nodeId = 1L,
            expectedVersion = "",
            onState = { states.add(it) },
            onSuccess = { success = true },
            onDiagnostic = {},
            requestHighPriority = {},
            resumeAfter = {}
        )
        assertFalse(success)
        assertTrue(states.last().error)
    }
}
