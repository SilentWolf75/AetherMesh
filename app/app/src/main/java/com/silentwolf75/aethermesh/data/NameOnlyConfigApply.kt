package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.NodeConfig

/**
 * Node-rename packet builder. Local BLE uses recipient_id=0, hop 1, no ACK.
 * Remote uses the target node id and requires a password. Packet construction
 * only — BLE send and DB persist stay in [AetherMeshRepository].
 */
object NameOnlyConfigApply {
    const val MAX_LONG_NAME = 16
    const val MAX_SHORT_NAME = 4

    fun clipLongName(name: String): String = name.trim().take(MAX_LONG_NAME)

    fun clipShortName(shortName: String, longName: String, nodeId: Long): String =
        NodeNamePolicy.clipShort(shortName).ifEmpty {
            NodeNamePolicy.deriveShortName(longName, nodeId)
        }

    fun build(
        localNodeId: Long,
        nodeId: Long,
        name: String,
        shortName: String,
        adminPassword: String,
        authProtocol: Int,
        packetId: Int,
        isLocal: Boolean,
        identity: ControlAuthIdentity = ControlAuthSession.next()
    ): MeshPacket? {
        val clipped = clipLongName(name)
        if (!isLocal && adminPassword.isBlank()) return null
        val clippedShort = clipShortName(shortName, clipped, nodeId)
        val configBuilder = NodeConfig.newBuilder()
            .setNodeName(clipped)
            .setNodeShortName(clippedShort)
            .setApplyNameOnly(true)
        if (!isLocal && authProtocol == 0) {
            configBuilder.setConfigPassword(adminPassword)
        }
        val config = configBuilder.build()
        val packetBuilder = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(if (isLocal) 0 else nodeId.toInt())
            .setPacketId(packetId)
            .setHopLimit(if (isLocal) 1 else 4)
            .setWantAck(!isLocal)
            .setPrevHopId(localNodeId.toInt())
            .setConfig(config)
        if (!isLocal && authProtocol >= 2) {
            RemoteConfigApply.attachAuth(
                packetBuilder, localNodeId, nodeId, config, adminPassword, authProtocol, identity
            )
        }
        return packetBuilder.build()
    }
}
