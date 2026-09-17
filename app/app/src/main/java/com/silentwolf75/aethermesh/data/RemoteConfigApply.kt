package com.silentwolf75.aethermesh.data

import com.google.protobuf.ByteString
import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.NodeConfig

data class RemoteConfigApplyRequest(
    val nodeId: Long,
    val name: String,
    val password: String,
    val sf: Int,
    val bw: Float,
    val txPower: Int,
    val region: Int,
    val role: Int,
    val telemetryInterval: Int = 60,
    val screenTimeout: Int = 30,
    val powerSaveMode: Boolean = false,
    val positionPrecision: Int = 0,
    val gpsMode: Int = 0,
    val gpsDutyIntervalSecs: Int = 900,
    val fixedPosition: Boolean = false,
    val fixedLatitude: Float = 0f,
    val fixedLongitude: Float = 0f,
    val fixedAltitude: Int = 0,
    val meshHopLimit: Int = 0,
    val rebroadcastTxdelayX100: Int = 0,
    val applyMask: Int,
    /** Target node's firmware ceiling ([NodeSettingsPrefs.readMaxHopLimit]). */
    val maxHopLimit: Int = HopRangePolicy.LEGACY_MAX
)

object RemoteConfigApply {
    fun clampHops(meshHopLimit: Int, maxHopLimit: Int = HopRangePolicy.LEGACY_MAX): Int = when {
        meshHopLimit <= 0 -> 0
        else -> HopRangePolicy.clamp(meshHopLimit, maxHopLimit)
    }

    fun clampTxdelay(rebroadcastTxdelayX100: Int): Int = when {
        rebroadcastTxdelayX100 <= 0 -> 0
        else -> rebroadcastTxdelayX100.coerceIn(50, 200)
    }

    fun clampDutySecs(gpsDutyIntervalSecs: Int): Int = when {
        gpsDutyIntervalSecs <= 0 -> 900
        else -> gpsDutyIntervalSecs.coerceIn(300, 3600)
    }

    fun attachAuth(
        packetBuilder: MeshPacket.Builder,
        localNodeId: Long,
        nodeId: Long,
        config: NodeConfig,
        password: String,
        authProtocol: Int,
        identity: ControlAuthIdentity = ControlAuthSession.next()
    ) {
        if (authProtocol < 2) return
        val tag = ControlAuth.sign(
            localNodeId, nodeId, identity, config, password,
            authProtocol = authProtocol
        )
        packetBuilder
            .setProtocolVersion(authProtocol)
            .setSessionId(identity.sessionId)
            .setAuthCounter(identity.counter)
            .setAuthTag(ByteString.copyFrom(tag))
    }

    fun buildReportRequest(
        localNodeId: Long,
        nodeId: Long,
        password: String,
        authProtocol: Int,
        packetId: Int,
        identity: ControlAuthIdentity = ControlAuthSession.next()
    ): MeshPacket? {
        if (password.isBlank()) return null
        val config = NodeConfig.newBuilder()
            .setRequestReport(true)
            .setConfigPassword(if (authProtocol >= 2) "" else password)
            .setApplyMask(0)
            .build()
        val packetBuilder = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(nodeId.toInt())
            .setPacketId(packetId)
            .setHopLimit(4)
            .setWantAck(true)
            .setPrevHopId(localNodeId.toInt())
            .setConfig(config)
        attachAuth(packetBuilder, localNodeId, nodeId, config, password, authProtocol, identity)
        return packetBuilder.build()
    }

    fun buildApply(
        localNodeId: Long,
        request: RemoteConfigApplyRequest,
        authProtocol: Int,
        packetId: Int,
        identity: ControlAuthIdentity = ControlAuthSession.next()
    ): MeshPacket? {
        if (request.password.isBlank() || request.applyMask == 0) return null
        val hops = clampHops(request.meshHopLimit, request.maxHopLimit)
        val txdelay = clampTxdelay(request.rebroadcastTxdelayX100)
        val dutySecs = clampDutySecs(request.gpsDutyIntervalSecs)
        val config = NodeConfig.newBuilder()
            .setNodeName(request.name)
            .setConfigPassword(if (authProtocol >= 2) "" else request.password)
            .setLoraSf(request.sf)
            .setLoraBw(request.bw)
            .setLoraTxPower(request.txPower)
            .setRegion(request.region)
            .setNodeRole(request.role)
            .setTelemetryInterval(request.telemetryInterval)
            .setScreenTimeoutSecs(request.screenTimeout)
            .setPowerSaveMode(request.powerSaveMode)
            .setPositionPrecision(request.positionPrecision)
            .setGpsMode(request.gpsMode.coerceIn(0, 2))
            .setGpsDutyIntervalSecs(dutySecs)
            .setFixedPosition(request.fixedPosition)
            .setFixedLatitude(request.fixedLatitude)
            .setFixedLongitude(request.fixedLongitude)
            .setFixedAltitude(request.fixedAltitude)
            .setMeshHopLimit(hops)
            .setRebroadcastTxdelayX100(txdelay)
            .setRequestReport(false)
            .setApplyMask(request.applyMask)
            .build()
        val packetBuilder = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(request.nodeId.toInt())
            .setPacketId(packetId)
            .setHopLimit(4)
            .setWantAck(true)
            .setPrevHopId(localNodeId.toInt())
            .setConfig(config)
        attachAuth(
            packetBuilder, localNodeId, request.nodeId, config,
            request.password, authProtocol, identity
        )
        return packetBuilder.build()
    }
}
