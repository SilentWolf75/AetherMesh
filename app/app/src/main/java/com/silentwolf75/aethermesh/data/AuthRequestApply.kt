package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.AuthRequest
import com.silentwolf75.aethermesh.proto.MeshPacket

/** Local BLE AuthRequest packets (recipient 0). Send stays in [AetherMeshRepository]. */
object AuthRequestApply {
    fun canChangePassword(currentPassword: String, newPassword: String): Boolean =
        currentPassword.trim().isNotEmpty() && newPassword.trim().isNotEmpty()

    fun buildUnlock(localNodeId: Long, packetId: Int, password: String): MeshPacket =
        envelope(
            localNodeId,
            packetId,
            AuthRequest.newBuilder()
                .setPassword(password)
                .setIsChangePassword(false)
                .build()
        )

    /** Unlock by proof: the password itself stays on the phone. */
    fun buildProof(localNodeId: Long, packetId: Int, proof: ByteArray): MeshPacket =
        envelope(
            localNodeId,
            packetId,
            AuthRequest.newBuilder()
                .setProof(com.google.protobuf.ByteString.copyFrom(proof))
                .setIsChangePassword(false)
                .build()
        )

    fun buildChangePassword(
        localNodeId: Long,
        packetId: Int,
        currentPassword: String,
        newPassword: String
    ): MeshPacket =
        envelope(
            localNodeId,
            packetId,
            AuthRequest.newBuilder()
                .setPassword(currentPassword)
                .setIsChangePassword(true)
                .setNewPassword(newPassword)
                .build()
        )

    private fun envelope(localNodeId: Long, packetId: Int, auth: AuthRequest): MeshPacket =
        MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(0)
            .setPacketId(packetId)
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setAuthRequest(auth)
            .build()
}
