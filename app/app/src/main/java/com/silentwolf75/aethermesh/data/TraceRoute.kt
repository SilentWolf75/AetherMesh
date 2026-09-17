package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.TraceRoute

data class TraceHop(
    val nodeId: Long,
    val rssi: Int,
    val snr: Float
)

data class TraceRouteState(
    val visible: Boolean = false,
    val showDialog: Boolean = false,
    val active: Boolean = false,
    val targetId: Long = 0L,
    val traceId: Int = 0,
    val forward: List<TraceHop> = emptyList(),
    val returning: List<TraceHop> = emptyList(),
    val forwardTruncated: Boolean = false,
    val returnTruncated: Boolean = false,
    val error: String? = null,
    val startedAtMs: Long = 0L,
    val finishedAtMs: Long = 0L
) {
    val durationSeconds: Float?
        get() {
            if (startedAtMs <= 0L || finishedAtMs < startedAtMs) return null
            return (finishedAtMs - startedAtMs) / 1000f
        }
}

object TraceRoutePolicy {
    /** Compact extended-range hop layout: uint32 LE node id + int8 SNR quarter dB. */
    const val COMPACT_HOP_BYTES = 5

    /** Decodes firmware TraceHops.h paths. Compact hops carry no RSSI (reported as 0). */
    fun decodeCompactHops(bytes: com.google.protobuf.ByteString): List<TraceHop> =
        (0 until bytes.size() / COMPACT_HOP_BYTES).map { index ->
            val offset = index * COMPACT_HOP_BYTES
            val nodeId = (0 until 4).fold(0L) { acc, shift ->
                acc or ((bytes.byteAt(offset + shift).toLong() and 0xFFL) shl (8 * shift))
            }
            TraceHop(nodeId = nodeId, rssi = 0, snr = bytes.byteAt(offset + 4) / 4f)
        }

    /** Floor for SF7–9 (historical traceroute wait). */
    const val TIMEOUT_MS = 30_000L
    const val HOP_LIMIT = 7
    const val ERR_TIMEOUT = "No route response received"
    const val ERR_CANCELLED = "Cancelled"
    const val ERR_DISCONNECTED = "Disconnected"

    /** Multi-hop RTT grows with SF; keep SF7–9 at [TIMEOUT_MS]. */
    fun timeoutMs(sf: Int = NodeSettingsPrefs.DEFAULT_SF): Long = when (sf.coerceIn(7, 12)) {
        12 -> 75_000L
        11 -> 55_000L
        10 -> 40_000L
        else -> TIMEOUT_MS
    }

    fun canStart(
        connected: Boolean,
        authenticated: Boolean,
        localNodeId: Long,
        targetId: Long
    ): Boolean = connected && authenticated && localNodeId != 0L &&
        targetId != 0L && targetId != localNodeId

    fun localizeError(error: String?, spanish: Boolean): String = when (error) {
        null -> if (spanish) "Traza fallida" else "Trace failed"
        ERR_TIMEOUT -> if (spanish)
            "No se recibió respuesta de ruta"
        else
            ERR_TIMEOUT
        ERR_CANCELLED -> if (spanish) "Trazado cancelado" else "Traceroute cancelled"
        ERR_DISCONNECTED -> if (spanish)
            "Desconectado — traza cancelada"
        else
            "Disconnected — traceroute cancelled"
        else -> error
    }

    fun buildRequest(localNodeId: Long, targetId: Long, traceId: Int): MeshPacket {
        val trace = TraceRoute.newBuilder()
            .setType(TraceRoute.Type.REQUEST)
            .setTraceId(traceId)
            .setOriginId(localNodeId.toInt())
            .setTargetId(targetId.toInt())
            .build()
        return MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(targetId.toInt())
            .setPacketId(traceId)
            .setHopLimit(HOP_LIMIT)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setTraceRoute(trace)
            .build()
    }

    fun starting(targetId: Long, traceId: Int, nowMs: Long) = TraceRouteState(
        visible = true,
        showDialog = true,
        active = true,
        targetId = targetId,
        traceId = traceId,
        startedAtMs = nowMs
    )

    fun matchesResponse(
        current: TraceRouteState,
        packet: MeshPacket,
        localNodeId: Long
    ): Boolean {
        if (!current.active) return false
        if (packet.payloadCase != MeshPacket.PayloadCase.TRACE_ROUTE) return false
        val trace = packet.traceRoute
        val responseTarget = trace.targetId.toLong() and 0xFFFFFFFFL
        if (current.targetId != 0L && responseTarget != 0L && responseTarget != current.targetId) {
            return false
        }
        return trace.type == TraceRoute.Type.RESPONSE &&
            trace.traceId == current.traceId &&
            (trace.originId.toLong() and 0xFFFFFFFFL) == localNodeId
    }

    fun applyResponse(
        current: TraceRouteState,
        packet: MeshPacket,
        localNodeId: Long,
        nowMs: Long
    ): TraceRouteState? {
        if (!matchesResponse(current, packet, localNodeId)) return null
        val trace = packet.traceRoute
        // Extended-range traces use the compact path; an older relay on such a
        // path may still append to the repeated fields, so keep both.
        val forward = decodeCompactHops(trace.forwardHops) + trace.forwardNodeIdsList.mapIndexed { index, id ->
            TraceHop(
                nodeId = id.toLong() and 0xFFFFFFFFL,
                rssi = trace.forwardRssiList.getOrElse(index) { 0 },
                snr = trace.forwardSnrQuarterDbList.getOrElse(index) { 0 } / 4f
            )
        }
        val returning = (decodeCompactHops(trace.returnHops) + trace.returnNodeIdsList.mapIndexed { index, id ->
            TraceHop(
                nodeId = id.toLong() and 0xFFFFFFFFL,
                rssi = trace.returnRssiList.getOrElse(index) { 0 },
                snr = trace.returnSnrQuarterDbList.getOrElse(index) { 0 } / 4f
            )
        }).toMutableList()
        if (returning.lastOrNull()?.nodeId != localNodeId) {
            returning += TraceHop(localNodeId, packet.rxRssi.toInt(), packet.rxSnr)
        }
        return current.copy(
            active = false,
            showDialog = true,
            forward = forward,
            returning = returning,
            forwardTruncated = trace.forwardTruncated,
            returnTruncated = trace.returnTruncated,
            error = null,
            finishedAtMs = nowMs
        )
    }

    fun timedOut(current: TraceRouteState, traceId: Int, nowMs: Long): TraceRouteState? {
        if (!current.active || current.traceId != traceId) return null
        return current.copy(
            active = false,
            showDialog = true,
            error = ERR_TIMEOUT,
            finishedAtMs = nowMs
        )
    }

    fun cancelled(current: TraceRouteState, nowMs: Long): TraceRouteState? {
        if (!current.active) return null
        return current.copy(
            active = false,
            showDialog = true,
            error = ERR_CANCELLED,
            finishedAtMs = nowMs
        )
    }

    fun disconnected(current: TraceRouteState, nowMs: Long): TraceRouteState {
        if (!current.active) return current
        return current.copy(
            active = false,
            showDialog = true,
            error = ERR_DISCONNECTED,
            finishedAtMs = nowMs
        )
    }
}
