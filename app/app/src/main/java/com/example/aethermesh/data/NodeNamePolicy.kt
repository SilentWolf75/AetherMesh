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
        // Keep a user-chosen short name across telemetry / device hydrates.
        // Long name still follows the mesh-advertised value when present.
        val derivedShort = longName.replace("AetherMesh-", "").replace("Node ", "")
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .take(4)
            .uppercase()
            .ifEmpty { String.format("%04X", (nodeId and 0xFFFF).toInt()) }
        val shortName = if (existingIsCustom && existingShortName.isNotBlank()) {
            existingShortName.take(4).uppercase()
        } else {
            derivedShort
        }
        return CanonicalNodeName(
            longName = longName,
            shortName = shortName,
            // Stick once the user (or Settings apply) chose a short name.
            isCustom = existingIsCustom ||
                (existingShortName.isNotBlank() && existingShortName.take(4).uppercase() != derivedShort)
        )
    }
}
