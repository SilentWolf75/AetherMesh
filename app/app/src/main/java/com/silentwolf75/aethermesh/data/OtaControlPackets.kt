package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.OtaControl

object OtaControlPackets {
    fun build(
        nodeId: Long,
        op: OtaControl.Op,
        size: Int,
        md5: String,
        sha256: String = ""
    ): ByteArray {
        val ctl = OtaControl.newBuilder()
            .setOp(op)
            .setTotalSize(size)
            .setMd5(md5)
            .setSha256(sha256)
        return MeshPacket.newBuilder()
            .setSenderId(nodeId.toInt())
            .setRecipientId(nodeId.toInt())
            .setHopLimit(1)
            .setOtaControl(ctl)
            .build()
            .toByteArray()
    }
}
