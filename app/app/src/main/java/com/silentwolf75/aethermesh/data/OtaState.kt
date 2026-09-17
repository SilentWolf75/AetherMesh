package com.silentwolf75.aethermesh.data

data class OtaState(
    val active: Boolean = false,
    val progress: Int = 0,
    val status: String = "",
    val error: Boolean = false,
    val done: Boolean = false,
    /** Catalog / package label remembered for success + post-reconnect verify. */
    val expectedVersion: String = "",
    /** True when transfer finished but telemetry still matches pre-flash FW. */
    val suspectRollback: Boolean = false
)
