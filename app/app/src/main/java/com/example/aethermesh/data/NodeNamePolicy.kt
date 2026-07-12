package com.example.aethermesh.data

data class CanonicalNodeName(val longName: String, val shortName: String, val isCustom: Boolean)

object NodeNamePolicy {
    fun choose(
        nodeId: Long,
        existingName: String,
        existingShortName: String,
        existingIsCustom: Boolean,
        advertisedName: String
    ): CanonicalNodeName {
        val advertised = advertisedName.trim().takeUtf8Bytes(16)
        val defaultName = "Node ${String.format("%08X", nodeId)}"
        // Mesh-advertised name wins so a fresh phone install learns names from
        // the nodes themselves. Phone-only renames only stick until telemetry
        // arrives (or until the rename is pushed onto the node).
        val longName = when {
            advertised.isNotBlank() -> advertised
            existingName.isNotBlank() -> existingName
            else -> defaultName
        }
        val shortName = if (advertised.isBlank() && existingIsCustom && existingShortName.isNotBlank()) {
            existingShortName
        } else {
            longName.replace("AetherMesh-", "").replace("Node ", "")
                .replace(Regex("[^a-zA-Z0-9]"), "")
                .take(4)
                .uppercase()
                .ifEmpty { String.format("%04X", (nodeId and 0xFFFF).toInt()) }
        }
        return CanonicalNodeName(
            longName = longName,
            shortName = shortName,
            isCustom = existingIsCustom && advertised.isBlank()
        )
    }
}
