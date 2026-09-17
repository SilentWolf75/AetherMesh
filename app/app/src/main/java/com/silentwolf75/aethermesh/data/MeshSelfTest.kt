package com.silentwolf75.aethermesh.data

/**
 * Result of a short channel mesh self-test (Phase J).
 * Scores HEARD receipts when channel hearer receipts are on, plus RX delta.
 */
data class MeshSelfTestResult(
    val active: Boolean = false,
    val pingsSent: Int = 0,
    val pingsPlanned: Int = 0,
    val pingsHeard: Int = 0,
    val uniqueHearers: Int = 0,
    val rxDelta: Long = 0,
    val scorePercent: Int = 0,
    val statusLineEn: String = "",
    val statusLineEs: String = "",
    val finished: Boolean = false,
    val errorEn: String? = null,
    val errorEs: String? = null
)

sealed class MeshSelfTestStart {
    data class Run(val planned: Int, val settleMs: Long) : MeshSelfTestStart()
    data class Refused(val result: MeshSelfTestResult) : MeshSelfTestStart()
}

/**
 * Channel mesh self-test math and copy. BLE send, DB hearer counts, and the
 * coroutine stay in [AetherMeshRepository].
 */
object MeshSelfTestPolicy {
    const val BROADCAST_RECIPIENT = 0xFFFFFFFFL
    const val MIN_PINGS = 3
    const val MAX_PINGS = 10
    const val TAIL_WAIT_MS = 2_000L

    fun clampPingCount(pingCount: Int): Int = pingCount.coerceIn(MIN_PINGS, MAX_PINGS)

    fun settleMs(sf: Int): Long = when {
        sf >= 11 -> 8_000L
        sf >= 10 -> 6_000L
        else -> 4_000L
    }

    fun pingContent(nowMs: Long, index: Int): String =
        "MESHTEST_${nowMs % 100_000}_${index}"

    fun score(planned: Int, heardPings: Int, rxDelta: Long): Int = when {
        planned <= 0 -> 0
        heardPings > 0 -> (heardPings * 100) / planned
        rxDelta > 0 -> minOf(40, (rxDelta * 10).toInt())
        else -> 0
    }

    fun begin(
        pingCount: Int,
        connected: Boolean,
        gattReady: Boolean,
        authenticated: Boolean,
        rangeTestActive: Boolean,
        sf: Int
    ): MeshSelfTestStart {
        if (!connected || !gattReady || !authenticated) {
            return MeshSelfTestStart.Refused(
                MeshSelfTestResult(
                    finished = true,
                    errorEn = "Connect and unlock the node first.",
                    errorEs = "Conecta y desbloquea el nodo primero."
                )
            )
        }
        if (rangeTestActive) {
            return MeshSelfTestStart.Refused(
                MeshSelfTestResult(
                    finished = true,
                    errorEn = "Stop the range test before running mesh self-test.",
                    errorEs = "Detén la prueba de rango antes de la auto-prueba de malla."
                )
            )
        }
        val planned = clampPingCount(pingCount)
        return MeshSelfTestStart.Run(planned, settleMs(sf))
    }

    fun starting(planned: Int): MeshSelfTestResult = MeshSelfTestResult(
        active = true,
        pingsPlanned = planned,
        statusLineEn = "Sending $planned channel pings…",
        statusLineEs = "Enviando $planned pings de canal…"
    )

    fun progress(current: MeshSelfTestResult, sent: Int, planned: Int): MeshSelfTestResult =
        current.copy(
            pingsSent = sent,
            statusLineEn = "Sent $sent / $planned — waiting for hearers…",
            statusLineEs = "Enviados $sent / $planned — esperando oyentes…"
        )

    fun sendFailed(current: MeshSelfTestResult, result: SendMessageResult): MeshSelfTestResult =
        current.copy(
            active = false,
            finished = true,
            errorEn = "Could not send self-test ping ($result).",
            errorEs = "No se pudo enviar ping de auto-prueba ($result)."
        )

    fun finished(
        planned: Int,
        heardPings: Int,
        uniqueHearers: Int,
        rxDelta: Long
    ): MeshSelfTestResult {
        val scorePercent = score(planned, heardPings, rxDelta)
        return MeshSelfTestResult(
            active = false,
            pingsSent = planned,
            pingsPlanned = planned,
            pingsHeard = heardPings,
            uniqueHearers = uniqueHearers,
            rxDelta = rxDelta,
            scorePercent = scorePercent,
            statusLineEn =
                "Heard $heardPings/$planned · $uniqueHearers unique hearers · RX Δ$rxDelta · score $scorePercent%",
            statusLineEs =
                "Oídos $heardPings/$planned · $uniqueHearers oyentes · RX Δ$rxDelta · puntuación $scorePercent%",
            finished = true
        )
    }

    fun stopped(current: MeshSelfTestResult): MeshSelfTestResult = current.copy(
        active = false,
        finished = true,
        statusLineEn = "Self-test stopped.",
        statusLineEs = "Auto-prueba detenida."
    )

    fun failed(message: String?): MeshSelfTestResult = MeshSelfTestResult(
        finished = true,
        errorEn = "Self-test failed: $message",
        errorEs = "Auto-prueba falló: $message"
    )
}
