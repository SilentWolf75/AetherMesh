package com.silentwolf75.aethermesh.data

data class CanonicalNodeName(val longName: String, val shortName: String, val isCustom: Boolean)

data class DeviceNameHydrate(
    val longName: String,
    val shortName: String,
    val persistDeviceShort: Boolean,
    val writeDb: Boolean
)

data class PendingNodeNameApply(val longName: String, val shortName: String)

object NodeNamePolicy {
    const val SHORT_LEN = 4

    fun clipShort(raw: String): String = raw.trim().take(SHORT_LEN).uppercase()

    fun shouldHydrateNames(deviceLongName: String, deviceShortName: String): Boolean =
        deviceLongName.isNotBlank() || deviceShortName.isNotBlank()

    /**
     * Phone-staged rename waiting for AuthResponse(Unlocked). Clears from prefs
     * after the repository writes SQLite — blank long name means nothing to apply.
     */
    fun pendingFromPrefs(savedName: String?, savedShort: String?, nodeId: Long): PendingNodeNameApply? {
        val name = savedName?.takeIf { it.isNotBlank() } ?: return null
        val short = savedShort?.takeIf { it.isNotBlank() }?.let { clipShort(it) }?.takeIf { it.isNotBlank() }
            ?: deriveShortName(name, nodeId)
        return PendingNodeNameApply(name, short)
    }

    /**
     * Device-reported names win, then phone prefs, then the directory row, then
     * a derived 4-char short. SQLite/prefs writes stay in the repository.
     */
    fun namesFromDevice(
        nodeId: Long,
        deviceLongName: String,
        deviceShortName: String,
        prefsShort: String?,
        existingName: String,
        existingShort: String
    ): DeviceNameHydrate {
        val deviceShort = clipShort(deviceShortName).takeIf { it.isNotBlank() }
        val fromPrefs = prefsShort?.let { clipShort(it) }?.takeIf { it.isNotBlank() }
        val fromRow = clipShort(existingShort).takeIf { it.isNotBlank() }
        val longName = deviceLongName.ifBlank { existingName }
        val short = deviceShort
            ?: fromPrefs
            ?: fromRow
            ?: deriveShortName(longName.ifBlank { existingName }, nodeId)
        return DeviceNameHydrate(
            longName = longName,
            shortName = short,
            persistDeviceShort = deviceShort != null,
            writeDb = longName.isNotBlank()
        )
    }

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
        val derivedShort = deriveShortName(longName, nodeId)
        val shortName = if (existingIsCustom && existingShortName.isNotBlank()) {
            clipShort(existingShortName)
        } else {
            derivedShort
        }
        return CanonicalNodeName(
            longName = longName,
            shortName = shortName,
            // Stick once the user (or Settings apply) chose a short name.
            isCustom = existingIsCustom ||
                (existingShortName.isNotBlank() && clipShort(existingShortName) != derivedShort)
        )
    }

    fun deriveShortName(longName: String, nodeId: Long): String =
        longName.replace("AetherMesh-", "").replace("Node ", "")
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .take(4)
            .uppercase()
            .ifEmpty { String.format("%04X", (nodeId and 0xFFFF).toInt()) }
}
