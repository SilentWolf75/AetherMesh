package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.RangeTestControl
import com.silentwolf75.aethermesh.proto.TextMessage
import java.util.concurrent.ConcurrentHashMap

data class RangeTestPosition(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float?,
    val gpsAccuracyM: Float?
)

data class PendingRangePing(
    val targetId: Long,
    val sentAtMs: Long,
    val position: RangeTestPosition
)

data class RangePongFields(
    val pingId: Int?,
    val targetRssi: Float?,
    val targetSnr: Float?
)

sealed class RangeScoreDecision {
    object IgnoreInactive : RangeScoreDecision()
    object Unmatched : RangeScoreDecision()
    object Ignore : RangeScoreDecision()
    data class Score(
        val packetId: Int,
        val pending: PendingRangePing,
        val senderMismatch: Boolean,
        val remoteRssi: Float?,
        val remoteSnr: Float?
    ) : RangeScoreDecision()
}

sealed class RangeTestStart {
    object SelfTarget : RangeTestStart()
    data class Run(val intervalSeconds: Int) : RangeTestStart()
}

/**
 * Direct range-test rules. BLE write, GPS listener, and SQLite logs stay in
 * [AetherMeshRepository]. Pings are hop-1, no ACK — scored via PONG.
 */
object RangeTestPolicy {
    /** Floor for SF7–9 ping/PONG wait (hop-1, no mesh ACK). */
    const val PING_TIMEOUT_MS = 15_000L
    const val MAX_PING_ID = 9_999_999
    const val PHONE_FIX_MAX_AGE_MS = 30_000L
    const val FAIL_RSSI = -140f
    const val FAIL_SNR = -20f
    const val FAIL_TIMEOUT = "timeout"
    const val FAIL_BLE = "ble_send_fail"
    const val FAIL_STOPPED = "test_stopped"
    const val FAIL_AUTH = "auth_blocked"
    const val FAIL_SELF = "self_target"
    const val CONTROL_HOP_LIMIT = 0
    const val PING_HOP_LIMIT = 1

    /** SF11+ airtime for ping+PONG needs a longer phone wait than [PING_TIMEOUT_MS]. */
    fun pingTimeoutMs(sf: Int = NodeSettingsPrefs.DEFAULT_SF): Long = when (sf.coerceIn(7, 12)) {
        12 -> 30_000L
        11 -> 22_000L
        10 -> 18_000L
        else -> PING_TIMEOUT_MS
    }

    fun failureShort(reason: String?, spanish: Boolean): String = when (reason) {
        FAIL_BLE -> if (spanish) "fallo BLE" else "BLE fail"
        FAIL_AUTH -> if (spanish) "auth" else "auth"
        FAIL_STOPPED -> if (spanish) "detenido" else "stopped"
        FAIL_SELF -> if (spanish) "mismo nodo" else "self"
        else -> if (spanish) "timeout" else "timeout"
    }

    fun failureLabel(reason: String?, spanish: Boolean): String = when (reason) {
        FAIL_BLE -> if (spanish)
            "Fallo al escribir por BLE — revisa el enlace."
        else
            "BLE write failed — check the phone↔node link."
        FAIL_AUTH -> if (spanish)
            "Bloqueado: autentica el dispositivo."
        else
            "Blocked — unlock/authenticate the device."
        FAIL_STOPPED -> if (spanish)
            "Prueba detenida."
        else
            "Test stopped."
        FAIL_SELF -> if (spanish)
            "Ese es el nodo conectado por BLE — conéctate a otro nodo para probar este."
        else
            "That's the BLE-connected node — connect to a different node to range-test this one."
        else -> if (spanish)
            "Sin respuesta (timeout)."
        else
            "No reply (timeout)."
    }

    fun minIntervalSeconds(sf: Int): Int = when {
        sf >= 11 -> 10
        sf >= 10 -> 8
        else -> 5
    }

    fun clampInterval(requested: Int, sf: Int): Int =
        requested.coerceIn(minIntervalSeconds(sf), 30)

    fun begin(
        localNodeId: Long,
        targetId: Long,
        requestedInterval: Int,
        sf: Int
    ): RangeTestStart {
        if (MeshNodeId.same(localNodeId, targetId)) return RangeTestStart.SelfTarget
        return RangeTestStart.Run(clampInterval(requestedInterval, sf))
    }

    /** Dialogs for other nodes may load history; a live test keeps its target. */
    fun sessionTargetAfterLoad(active: Boolean, currentTarget: Long, requested: Long): Long =
        if (active) currentTarget else requested

    fun pingContent(packetId: Int): String = "PING_${packetId}_D"

    fun isPongContent(content: String): Boolean = content.startsWith("PONG_")

    fun parsePong(content: String): RangePongFields? {
        if (!isPongContent(content)) return null
        val fields = content.removePrefix("PONG_").split('_')
        return RangePongFields(
            pingId = fields.getOrNull(0)?.toIntOrNull(),
            targetRssi = fields.getOrNull(1)?.toFloatOrNull(),
            targetSnr = fields.getOrNull(2)?.toFloatOrNull()?.div(4f)
        )
    }

    fun phoneFixFresh(fixTimeMs: Long, nowMs: Long): Boolean =
        nowMs - fixTimeMs < PHONE_FIX_MAX_AGE_MS

    fun pickPosition(
        phoneLat: Double?,
        phoneLon: Double?,
        phoneTimeMs: Long,
        phoneSpeed: Float?,
        phoneAccuracy: Float?,
        nowMs: Long,
        nodeLat: Double,
        nodeLon: Double
    ): RangeTestPosition {
        if (phoneLat != null && phoneLon != null && phoneFixFresh(phoneTimeMs, nowMs)) {
            return RangeTestPosition(phoneLat, phoneLon, phoneSpeed, phoneAccuracy)
        }
        return RangeTestPosition(nodeLat, nodeLon, null, null)
    }

    fun buildControl(localNodeId: Long, packetId: Int, op: RangeTestControl.Op): MeshPacket {
        val control = RangeTestControl.newBuilder().setOp(op).build()
        return MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(localNodeId.toInt())
            .setPacketId(packetId)
            .setHopLimit(CONTROL_HOP_LIMIT)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setRangeTestControl(control)
            .build()
    }

    fun buildPing(localNodeId: Long, targetId: Long, packetId: Int): MeshPacket {
        val text = TextMessage.newBuilder()
            .setContent(pingContent(packetId))
            .setChannel("")
            .setIsEncrypted(false)
            .build()
        return MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(targetId.toInt())
            .setPacketId(packetId)
            .setHopLimit(PING_HOP_LIMIT)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setText(text)
            .build()
    }

    /**
     * Ping-id is authoritative. Sender may differ by 16-bit vs 32-bit form;
     * still score on mismatch.
     */
    fun decidePong(
        active: Boolean,
        content: String,
        pending: PendingRangePing?,
        senderId: Long
    ): RangeScoreDecision {
        if (!active) return RangeScoreDecision.IgnoreInactive
        val parsed = parsePong(content) ?: return RangeScoreDecision.Unmatched
        val pingId = parsed.pingId ?: return RangeScoreDecision.Unmatched
        if (pending == null) return RangeScoreDecision.Unmatched
        return RangeScoreDecision.Score(
            packetId = pingId,
            pending = pending,
            senderMismatch = !MeshNodeId.same(pending.targetId, senderId),
            remoteRssi = parsed.targetRssi,
            remoteSnr = parsed.targetSnr
        )
    }

    /** ACK match requires the exact 32-bit target id, unlike PONG. */
    fun decideAck(
        active: Boolean,
        ackedId: Int,
        pending: PendingRangePing?,
        senderId: Long,
        ackedRxRssi: Float,
        ackedRxSnr: Float
    ): RangeScoreDecision {
        if (!active || pending == null || pending.targetId != senderId) {
            return RangeScoreDecision.Ignore
        }
        return RangeScoreDecision.Score(
            packetId = ackedId,
            pending = pending,
            senderMismatch = false,
            remoteRssi = ackedRxRssi.takeIf { it != 0f },
            remoteSnr = ackedRxSnr.takeIf { it != 0f }
        )
    }
}

/** Outstanding PING identities for one range-test session. */
class RangeTestPendingStore {
    private val pending = ConcurrentHashMap<Int, PendingRangePing>()

    fun clear() = pending.clear()

    fun contains(packetId: Int): Boolean = pending.containsKey(packetId)

    operator fun get(packetId: Int): PendingRangePing? = pending[packetId]

    val keys: Set<Int>
        get() = pending.keys.toSet()

    fun put(packetId: Int, ping: PendingRangePing) {
        pending[packetId] = ping
    }

    fun remove(packetId: Int, expected: PendingRangePing): Boolean =
        pending.remove(packetId, expected)

    fun entries(): List<Map.Entry<Int, PendingRangePing>> = pending.entries.toList()

    fun allocatePingId(nextId: () -> Int): Int {
        var generated: Int
        do {
            generated = (nextId() % RangeTestPolicy.MAX_PING_ID) + 1
        } while (pending.containsKey(generated))
        return generated
    }

    fun expire(nowMs: Long, timeoutMs: Long = RangeTestPolicy.PING_TIMEOUT_MS): List<PendingRangePing> {
        val cutoff = nowMs - timeoutMs
        val expired = mutableListOf<PendingRangePing>()
        pending.entries
            .filter { it.value.sentAtMs <= cutoff }
            .forEach { entry ->
                if (pending.remove(entry.key, entry.value)) expired.add(entry.value)
            }
        return expired
    }

    fun drainAll(): List<PendingRangePing> {
        val drained = mutableListOf<PendingRangePing>()
        entries().forEach { entry ->
            if (pending.remove(entry.key, entry.value)) drained.add(entry.value)
        }
        return drained
    }

    fun removeForTarget(targetId: Long) {
        pending.entries
            .filter { it.value.targetId == targetId }
            .forEach { pending.remove(it.key, it.value) }
    }
}
