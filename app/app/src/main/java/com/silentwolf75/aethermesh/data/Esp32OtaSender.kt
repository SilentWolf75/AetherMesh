package com.silentwolf75.aethermesh.data

import android.util.Log
import com.silentwolf75.aethermesh.ble.OtaTransferPolicy
import com.silentwolf75.aethermesh.ble.OtaWriteRetryPolicy
import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.OtaControl
import com.silentwolf75.aethermesh.proto.OtaData
import com.silentwolf75.aethermesh.proto.OtaStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.security.MessageDigest

/**
 * ESP32-S3 BLE OTA windowed sender. Nordic DFU stays in [AetherMeshRepository].
 */
class Esp32OtaSender(
    private val inbox: OtaStatusInbox,
    private val send: (ByteArray, timeoutMs: Long, withResponse: Boolean) -> Boolean,
    private val negotiatedMtu: () -> Int,
    private val isConnected: () -> Boolean
) {
    companion object {
        private const val TAG = "Esp32OtaSender"
    }

    suspend fun upload(
        firmware: ByteArray,
        nodeId: Long,
        expectedVersion: String,
        onState: (OtaState) -> Unit,
        onSuccess: () -> Unit,
        onDiagnostic: (String) -> Unit,
        requestHighPriority: () -> Unit,
        resumeAfter: () -> Unit
    ) {
        try {
            onState(OtaState(active = true, status = OtaTransferPolicy.STATUS_PREPARING, expectedVersion = expectedVersion))
            requestHighPriority()

            val md5 = MessageDigest.getInstance("MD5").digest(firmware)
                .joinToString("") { "%02x".format(it) }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(firmware)
                .joinToString("") { "%02x".format(it) }

            inbox.drain()

            var chunkHint = -1
            for (attempt in 1..2) {
                sendControl(nodeId, OtaControl.Op.BEGIN, firmware.size, md5, sha256)
                try {
                    chunkHint = inbox.awaitState(OtaStatus.State.READY, 25_000, "start acknowledgment")
                    break
                } catch (e: Exception) {
                    if (attempt == 2) throw e
                    onState(
                        OtaState(
                            active = true,
                            status = OtaTransferPolicy.STATUS_RETRYING_START,
                            expectedVersion = expectedVersion
                        )
                    )
                    delay(1500)
                    inbox.drain()
                }
            }
            if (chunkHint < 0) throw Exception(OtaTransferPolicy.ERR_NEVER_READY)

            val chunkSize = OtaTransferPolicy.chunkSizeForLink(chunkHint, negotiatedMtu())
            val window = OtaTransferPolicy.windowForNode(chunkHint)
            Log.d(TAG, "OTA profile: chunk=$chunkSize window=$window exclusive+unconfirmed (hint $chunkHint, mtu=${negotiatedMtu()})")

            onState(OtaState(active = true, status = OtaTransferPolicy.STATUS_UPLOADING, expectedVersion = expectedVersion))
            var offset = 0
            while (offset < firmware.size) {
                var windowEndOffset = offset
                for (w in 0 until window) {
                    if (windowEndOffset >= firmware.size) break
                    val len = minOf(chunkSize, firmware.size - windowEndOffset)
                    val chunk = OtaData.newBuilder()
                        .setOffset(windowEndOffset)
                        .setData(com.google.protobuf.ByteString.copyFrom(firmware, windowEndOffset, len))
                    val pkt = MeshPacket.newBuilder()
                        .setSenderId(nodeId.toInt())
                        .setRecipientId(nodeId.toInt())
                        .setHopLimit(1)
                        .setOtaData(chunk)
                        .build()
                        .toByteArray()
                    var tries = 0
                    while (!send(pkt, 3000L, false)) {
                        if (++tries > OtaWriteRetryPolicy.MAX_ATTEMPTS) {
                            throw Exception(OtaTransferPolicy.ERR_BLE_WRITE_REPEATED)
                        }
                        delay(OtaWriteRetryPolicy.delayMs(tries))
                    }
                    windowEndOffset += len
                    if (w < window - 1 && windowEndOffset < firmware.size) {
                        delay(OtaTransferPolicy.INTER_CHUNK_MS)
                    }
                }

                var acked = inbox.awaitProgress(30_000)
                if (acked < offset) {
                    Log.w(TAG, "OTA node behind ($acked < $offset); resyncing")
                    offset = acked
                    continue
                }
                while (acked < windowEndOffset) {
                    acked = inbox.awaitProgress(30_000)
                    if (acked < offset) {
                        offset = acked
                        break
                    }
                    if (acked > windowEndOffset) {
                        throw Exception("Node acked $acked past window end $windowEndOffset")
                    }
                }
                if (acked < windowEndOffset) continue
                offset = windowEndOffset
                onState(
                    OtaState(
                        active = true,
                        progress = (offset.toLong() * 100 / firmware.size).toInt(),
                        status = OtaTransferPolicy.uploadingProgressStatus(offset, firmware.size),
                        expectedVersion = expectedVersion
                    )
                )
            }

            onState(
                OtaState(
                    active = true,
                    progress = 100,
                    status = OtaTransferPolicy.STATUS_VERIFYING,
                    expectedVersion = expectedVersion
                )
            )
            sendControl(nodeId, OtaControl.Op.END, 0, "")
            inbox.awaitState(OtaStatus.State.SUCCESS, 20_000, "verification")

            onSuccess()
            onState(
                OtaTransferPolicy.successState(
                    expectedVersion,
                    OtaTransferPolicy.successStatus(expectedVersion, dfu = false)
                )
            )
            delay(4_000)
            try {
                resumeAfter()
            } catch (e: Exception) {
                Log.w(TAG, "Post-OTA reconnect schedule failed: ${e.message}")
            }
            onState(
                OtaTransferPolicy.successState(
                    expectedVersion,
                    OtaTransferPolicy.reconnectingStatus(expectedVersion, dfu = false)
                )
            )
        } catch (e: CancellationException) {
            try {
                sendControl(nodeId, OtaControl.Op.ABORT, 0, "")
            } catch (_: Exception) {}
            onState(OtaState(error = true, status = OtaTransferPolicy.STATUS_CANCELLED))
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OTA failed: ${e.message}")
            onDiagnostic("OTA failed: ${e.message}")
            try {
                sendControl(nodeId, OtaControl.Op.ABORT, 0, "")
            } catch (_: Exception) {}
            onState(
                OtaState(
                    error = true,
                    status = if (!isConnected())
                        OtaTransferPolicy.STATUS_INTERRUPTED
                    else
                        OtaTransferPolicy.failedStatus(e.message)
                )
            )
        }
    }

    fun sendControl(
        nodeId: Long,
        op: OtaControl.Op,
        size: Int,
        md5: String,
        sha256: String = ""
    ) {
        val pkt = OtaControlPackets.build(nodeId, op, size, md5, sha256)
        if (!send(pkt, 1000L, false)) {
            throw Exception(OtaTransferPolicy.bleWriteFailed(op.name))
        }
    }
}
