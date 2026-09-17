package com.silentwolf75.aethermesh.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded developer diagnostic log lines shown in Settings.
 * Ring mutation stays synchronized in [AetherMeshRepository].
 */
object DiagnosticLogPolicy {
    const val CAPACITY = 60

    fun formatLine(message: String, epochMs: Long): String {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMs))
        return "$stamp $message"
    }

    /** Append [message] and drop oldest entries so size never exceeds [CAPACITY]. */
    fun nextRing(current: Collection<String>, message: String, epochMs: Long): List<String> {
        val out = ArrayList<String>(current.size + 1)
        out.addAll(current)
        out.add(formatLine(message, epochMs))
        while (out.size > CAPACITY) out.removeAt(0)
        return out
    }
}
