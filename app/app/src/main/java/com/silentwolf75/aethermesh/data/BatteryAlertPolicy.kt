package com.silentwolf75.aethermesh.data

sealed class BatteryAlertAction {
    object Ignore : BatteryAlertAction()
    object Rearm : BatteryAlertAction()
    data class Notify(val threshold: Int, val critical: Boolean) : BatteryAlertAction()
}

/**
 * Per-node battery notification gates. Android notify stays in
 * [AetherMeshRepository]. First crossing only; charge/recover re-arms.
 */
object BatteryAlertPolicy {
    const val CRITICAL = 10
    const val LOW = 20
    const val RECOVER = 25
    const val CHANNEL_ID = "aethermesh_battery"
    const val GROUP = "aethermesh_battery"

    fun decide(
        nodeId: Long,
        level: Int,
        isCharging: Boolean,
        already: Int
    ): BatteryAlertAction {
        if (nodeId == 0L) return BatteryAlertAction.Ignore
        if (isCharging || level >= RECOVER) return BatteryAlertAction.Rearm
        val threshold = when {
            level <= CRITICAL -> CRITICAL
            level <= LOW -> LOW
            else -> return BatteryAlertAction.Ignore
        }
        if (already != 0 && threshold >= already) return BatteryAlertAction.Ignore
        return BatteryAlertAction.Notify(threshold, threshold == CRITICAL)
    }

    fun fallbackName(nodeId: Long, spanish: Boolean): String =
        (if (spanish) "Nodo %04X" else "Node %04X").format(nodeId and 0xFFFF)

    fun title(name: String, critical: Boolean, spanish: Boolean): String = when {
        critical && spanish -> "⚠ $name batería crítica"
        critical -> "⚠ $name battery critical"
        spanish -> "$name batería baja"
        else -> "$name battery low"
    }

    fun body(level: Int, critical: Boolean, spanish: Boolean): String = when {
        critical && spanish -> "$level% restante — cárgalo ahora"
        critical -> "$level% remaining — charge it now"
        spanish -> "$level% restante"
        else -> "$level% remaining"
    }

    fun channelName(spanish: Boolean): String =
        if (spanish) "Alertas de batería" else "Battery Alerts"

    fun channelDescription(spanish: Boolean): String =
        if (spanish) "Avisa cuando la batería de un nodo de la malla está baja"
        else "Warns when a mesh node's battery runs low"

    fun summaryTitle(spanish: Boolean): String =
        if (spanish) "Alertas de batería" else "Battery alerts"

    fun summaryText(spanish: Boolean): String =
        if (spanish) "Alertas de nodos de la malla" else "Mesh node battery alerts"

    fun inboxSummary(spanish: Boolean): String =
        if (spanish) "Batería de la malla" else "Mesh battery"

    fun notifyId(nodeId: Long): Int = "batt$nodeId".hashCode()

    fun summaryNotifyId(): Int = "aethermesh_battery_summary".hashCode()
}
