package com.example.aethermesh.data

import com.example.aethermesh.proto.NodeConfig
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ControlAuthIdentity(val sessionId: Long, val counter: Int)

object ControlAuthSession {
    private val session = SecureRandom().nextLong().let { if (it == 0L) 1L else it }
    private val counter = AtomicInteger(SecureRandom().nextInt(Int.MAX_VALUE - 1) + 1)

    fun next(): ControlAuthIdentity = ControlAuthIdentity(
        session,
        counter.updateAndGet { if (it == Int.MAX_VALUE) 1 else it + 1 }
    )
}

object ControlAuth {
    private val domain = "AMCFG2".toByteArray(Charsets.US_ASCII)

    fun sign(
        senderId: Long,
        recipientId: Long,
        identity: ControlAuthIdentity,
        config: NodeConfig,
        password: String
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical(senderId, recipientId, identity, config)).copyOf(16)
    }

    fun canonical(
        senderId: Long,
        recipientId: Long,
        identity: ControlAuthIdentity,
        config: NodeConfig
    ): ByteArray {
        val output = ByteArrayOutputStream(96)
        output.write(domain)
        output.putU32(senderId)
        output.putU32(recipientId)
        output.putU64(identity.sessionId)
        output.putU32(identity.counter.toLong())
        val name = config.nodeName.takeUtf8Bytes(16).toByteArray(Charsets.UTF_8)
        output.write(name.size)
        output.write(name)
        output.putU32(config.loraSf.toLong())
        output.putU32(config.loraBw.toRawBits().toLong())
        output.putU32(config.loraTxPower.toLong())
        output.putU32(config.region.toLong())
        output.putU32(config.nodeRole.toLong())
        output.putU32(config.telemetryInterval.toLong())
        output.putU32(config.screenTimeoutSecs.toLong())
        output.write(if (config.powerSaveMode) 1 else 0)
        output.putU32(config.positionPrecision.toLong())
        output.putU32(config.gpsMode.toLong())
        output.write(if (config.fixedPosition) 1 else 0)
        output.putU32(config.fixedLatitude.toRawBits().toLong())
        output.putU32(config.fixedLongitude.toRawBits().toLong())
        output.putU32(config.fixedAltitude.toLong())
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.putU32(value: Long) {
        repeat(4) { write((value ushr (it * 8)).toInt() and 0xFF) }
    }

    private fun ByteArrayOutputStream.putU64(value: Long) {
        repeat(8) { write((value ushr (it * 8)).toInt() and 0xFF) }
    }
}
