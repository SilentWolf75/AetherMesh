package com.silentwolf75.aethermesh.data

data class FirmwareFreshness(
    val awaitingFreshTelemetry: Boolean = false,
    val connectedNodeId: Long = 0L
)

/**
 * After auth / OTA, Installed and Node Details must not show a stale cached
 * firmware string until Telemetry.firmware_version arrives. BLE and SQLite
 * stay in [AetherMeshRepository].
 */
object FirmwareFreshnessPolicy {
    fun awaiting(nodeId: Long): FirmwareFreshness? =
        if (nodeId == 0L) null else FirmwareFreshness(true, nodeId)

    fun expectedLabel(versionLabel: String?): String = versionLabel?.trim().orEmpty()

    fun acceptReported(reported: String): Boolean = reported.trim().isNotEmpty()

    /**
     * Pre-flash firmware string for rollback detection. In-memory directory
     * wins; SQLite fills a blank. Node 0 has no version. Lookups stay in
     * [AetherMeshRepository].
     */
    fun pickPreFlashVersion(nodeId: Long, memoryVersion: String?, dbVersion: String?): String {
        if (nodeId == 0L) return ""
        return expectedLabel(memoryVersion).ifEmpty { expectedLabel(dbVersion) }
    }

    fun onReported(
        current: FirmwareFreshness,
        nodeId: Long,
        reported: String
    ): FirmwareFreshness {
        if (!acceptReported(reported)) return current
        if (!current.awaitingFreshTelemetry) return current
        if (current.connectedNodeId != 0L && current.connectedNodeId != nodeId) return current
        return FirmwareFreshness(awaitingFreshTelemetry = false, connectedNodeId = nodeId)
    }

    fun isChecking(freshness: FirmwareFreshness, nodeId: Long?): Boolean {
        if (!freshness.awaitingFreshTelemetry) return false
        if (freshness.connectedNodeId == 0L) return true
        if (nodeId == null || nodeId == 0L) return false
        return MeshNodeId.same(freshness.connectedNodeId, nodeId)
    }

    /** Installed firmware line for Node Details / Firmware Update / Connection. */
    fun formatInstalledLabel(
        cachedVersion: String?,
        awaitingFresh: Boolean,
        spanish: Boolean
    ): String {
        val prefix = if (spanish) "Instalado: " else "Installed: "
        return when {
            awaitingFresh -> prefix + if (spanish) "comprobando…" else "checking…"
            !cachedVersion.isNullOrBlank() -> prefix + cachedVersion
            else -> prefix + if (spanish) "desconocido" else "unknown"
        }
    }

    fun formatVersionValue(
        cachedVersion: String?,
        awaitingFresh: Boolean,
        spanish: Boolean
    ): String = when {
        awaitingFresh -> if (spanish) "comprobando…" else "checking…"
        !cachedVersion.isNullOrBlank() -> cachedVersion
        else -> if (spanish) "desconocida" else "unknown"
    }
}
