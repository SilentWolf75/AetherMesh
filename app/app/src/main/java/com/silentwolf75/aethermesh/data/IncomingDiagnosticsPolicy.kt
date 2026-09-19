package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshDiagnostics

data class MeshDiagnosticsSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val txPackets: Long = 0,
    val txFailures: Long = 0,
    val rxPackets: Long = 0,
    val relayedPackets: Long = 0,
    val retries: Long = 0,
    val ackedPackets: Long = 0,
    val ackTimeouts: Long = 0,
    val duplicatePackets: Long = 0,
    val cadBusyEvents: Long = 0,
    val queueDrops: Long = 0,
    val routeChanges: Long = 0,
    val activeRoutes: Int = 0,
    val rebroadcastQueueDepth: Int = 0,
    val pendingAckDepth: Int = 0,
    val airtimeMs: Long = 0,
    val uptimeSeconds: Long = 0,
    val protocolVersion: Int = 1,
    val rangePingsRx: Long = 0,
    val rangePongsQueued: Long = 0,
    val rangePongsSent: Long = 0,
    val rangePongTxFailures: Long = 0,
    val quietMode: Boolean = false,
    val directedRelays: Long = 0,
    val suppressRelays: Long = 0,
    val floodUnicasts: Long = 0,
    val rreqSent: Long = 0,
    val earlyRepairs: Long = 0,
    /** Share of the last minute the channel carried anyone's packets (0-100). */
    val channelUtilPercent: Int = 0,
    /** Our own transmissions over the last hour as a share of it (0-100). */
    val txDutyPercent: Int = 0,
    /** Hourly transmit limit in force; 100 means none. 0 = older firmware. */
    val dutyLimitPercent: Int = 0,
    val dutyCycleRefusals: Long = 0
)

/**
 * Inbound BLE DIAGNOSTICS. SQLite insert stays in [AetherMeshRepository].
 * Proto uint32 fields arrive as signed Java ints — widen without sign-extend.
 */
object IncomingDiagnosticsPolicy {
    fun unsigned32(raw: Int): Long = raw.toLong() and 0xFFFFFFFFL

    fun fromProto(
        value: MeshDiagnostics,
        nowMs: Long = System.currentTimeMillis()
    ): MeshDiagnosticsSnapshot = MeshDiagnosticsSnapshot(
        timestamp = nowMs,
        txPackets = unsigned32(value.txPackets),
        txFailures = unsigned32(value.txFailures),
        rxPackets = unsigned32(value.rxPackets),
        relayedPackets = unsigned32(value.relayedPackets),
        retries = unsigned32(value.retries),
        ackedPackets = unsigned32(value.ackedPackets),
        ackTimeouts = unsigned32(value.ackTimeouts),
        duplicatePackets = unsigned32(value.duplicatePackets),
        cadBusyEvents = unsigned32(value.cadBusyEvents),
        queueDrops = unsigned32(value.queueDrops),
        routeChanges = unsigned32(value.routeChanges),
        activeRoutes = value.activeRoutes,
        rebroadcastQueueDepth = value.rebroadcastQueueDepth,
        pendingAckDepth = value.pendingAckDepth,
        airtimeMs = unsigned32(value.airtimeMs),
        uptimeSeconds = unsigned32(value.uptimeSeconds),
        protocolVersion = value.protocolVersion,
        rangePingsRx = unsigned32(value.rangePingsRx),
        rangePongsQueued = unsigned32(value.rangePongsQueued),
        rangePongsSent = unsigned32(value.rangePongsSent),
        rangePongTxFailures = unsigned32(value.rangePongTxFailures),
        quietMode = value.quietMode,
        directedRelays = unsigned32(value.directedRelays),
        suppressRelays = unsigned32(value.suppressRelays),
        floodUnicasts = unsigned32(value.floodUnicasts),
        rreqSent = unsigned32(value.rreqSent),
        earlyRepairs = unsigned32(value.earlyRepairs),
        channelUtilPercent = value.channelUtilPercent.coerceIn(0, 100),
        txDutyPercent = value.txDutyPercent.coerceIn(0, 100),
        dutyLimitPercent = value.dutyLimitPercent.coerceIn(0, 100),
        dutyCycleRefusals = unsigned32(value.dutyCycleRefusals)
    )
}
