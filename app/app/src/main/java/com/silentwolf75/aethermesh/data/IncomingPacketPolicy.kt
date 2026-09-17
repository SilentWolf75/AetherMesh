package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket

/**
 * Pre-dispatch inbound packet gates. Range-test PONGs are scored even if
 * recipient_id is 0. Loopback/relay frames with rx_rssi 0 must not overwrite
 * a known-good route. SQLite stays in [AetherMeshRepository].
 */
object IncomingPacketPolicy {
    fun acceptControl(packet: MeshPacket, trustedNodeId: Long, authenticated: Boolean): Boolean {
        val sender = unsignedNodeId(packet.senderId)
        val localResponse = when (packet.payloadCase) {
            MeshPacket.PayloadCase.AUTH_RESPONSE,
            MeshPacket.PayloadCase.DELIVERY_STATUS,
            MeshPacket.PayloadCase.OTA_STATUS,
            MeshPacket.PayloadCase.DIAGNOSTICS -> true
            MeshPacket.PayloadCase.CONFIG -> packet.config.reportOnly && packet.recipientId == 0
            else -> false
        }
        if (!localResponse) return true
        if (sender == 0L || packet.recipientId != 0) return false
        if (packet.payloadCase == MeshPacket.PayloadCase.AUTH_RESPONSE) {
            // Before the first successful auth the BLE ID may be provisional.
            // Updated firmware also rejects this payload at the radio boundary.
            return packet.rxRssi == 0f && packet.rxSnr == 0f &&
                (trustedNodeId == 0L || sender == trustedNodeId)
        }
        return authenticated && trustedNodeId != 0L && sender == trustedNodeId
    }

    fun unsignedNodeId(raw: Int): Long = raw.toLong() and 0xFFFFFFFFL

    fun isRangePong(isText: Boolean, content: String): Boolean =
        isText && RangeTestPolicy.isPongContent(content)

    fun shouldDropZeroIds(senderId: Long, recipientId: Long, isRangePong: Boolean): Boolean =
        (senderId == 0L || recipientId == 0L) && !isRangePong

    fun observeRoute(
        senderId: Long,
        prevHopId: Long,
        rxRssi: Float,
        rxSnr: Float,
        nowMs: Long
    ): RouteHopInfo? {
        if (prevHopId == 0L || rxRssi == 0f) return null
        return RouteHopInfo(
            targetId = senderId,
            nextHopId = prevHopId,
            hops = if (senderId == prevHopId) 1 else 2,
            lastRssi = rxRssi,
            lastSnr = rxSnr,
            timestamp = nowMs
        )
    }

    /**
     * TRACE_ROUTE reply → directory hop count. First hop is the next-hop
     * credit; last hop's RSSI/SNR is the far-end quality. Empty path is a no-op.
     */
    fun observeTracePath(
        targetId: Long,
        forward: List<TraceHop>,
        nowMs: Long
    ): RouteHopInfo? {
        if (forward.isEmpty()) return null
        return RouteHopInfo(
            targetId = targetId,
            nextHopId = forward.first().nodeId,
            hops = forward.size,
            lastRssi = forward.last().rssi.toFloat(),
            lastSnr = forward.last().snr,
            timestamp = nowMs
        )
    }
}
