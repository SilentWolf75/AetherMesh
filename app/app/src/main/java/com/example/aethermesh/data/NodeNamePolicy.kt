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
        val longName = when {
            existingIsCustom && existingName.isNotBlank() -> existingName
            advertised.isNotBlank() -> advertised
            existingName.isNotBlank() -> existingName
            else -> defaultName
        }
        val preserveShortName = existingShortName.isNotBlank() &&
            (existingIsCustom || advertised.isBlank())
        val shortName = if (preserveShortName) {
            existingShortName
        } else {
            longName.replace("AetherMesh-", "").replace("Node ", "")
                .replace(Regex("[^a-zA-Z0-9]"), "")
                .take(4)
                .uppercase()
                .ifEmpty { String.format("%04X", (nodeId and 0xFFFF).toInt()) }
        }
        return CanonicalNodeName(longName, shortName, existingIsCustom)
    }
}
