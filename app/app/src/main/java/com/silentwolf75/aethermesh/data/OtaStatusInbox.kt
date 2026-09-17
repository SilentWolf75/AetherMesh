package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.ble.OtaTransferPolicy
import com.silentwolf75.aethermesh.proto.OtaStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** Fan-in for node OtaStatus packets while a BLE OTA / DFU transfer is running. */
class OtaStatusInbox {
    private val channel = Channel<OtaStatus>(Channel.BUFFERED)

    fun offer(status: OtaStatus) {
        channel.trySend(status)
    }

    fun drain() {
        while (channel.tryReceive().isSuccess) { /* drop stale acks */ }
    }

    suspend fun awaitState(
        wanted: OtaStatus.State,
        timeoutMs: Long,
        what: String
    ): Int {
        val result = withTimeoutOrNull(timeoutMs) {
            var value = -1
            var got = false
            while (!got) {
                val st = channel.receive()
                when (st.state) {
                    wanted -> {
                        value = st.nextOffset
                        got = true
                    }
                    OtaStatus.State.ERROR ->
                        throw Exception(OtaTransferPolicy.nodeErrorMessage(st.message))
                    else -> { /* keep waiting */ }
                }
            }
            value
        }
        return result ?: throw Exception("Timed out waiting for $what")
    }

    suspend fun awaitProgress(timeoutMs: Long): Int {
        val result = withTimeoutOrNull(timeoutMs) {
            var acked = -1
            while (acked < 0) {
                val st = channel.receive()
                when (st.state) {
                    OtaStatus.State.IN_PROGRESS -> acked = st.nextOffset
                    OtaStatus.State.ERROR ->
                        throw Exception(OtaTransferPolicy.nodeErrorMessage(st.message))
                    else -> { /* keep waiting */ }
                }
            }
            acked
        }
        return result ?: throw Exception("Timed out waiting for chunk ack")
    }
}
