package com.example.aethermesh.data

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
