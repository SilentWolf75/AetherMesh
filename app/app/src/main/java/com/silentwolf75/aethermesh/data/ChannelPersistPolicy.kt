package com.silentwolf75.aethermesh.data

sealed class ChannelInsertAction {
    data class UpdateExisting(val config: ChannelConfig) : ChannelInsertAction()
    data class InsertNew(val config: ChannelConfig) : ChannelInsertAction()
}

sealed class ChannelCreateResult {
    data class Created(val name: String) : ChannelCreateResult()
    data class AlreadyExists(val name: String) : ChannelCreateResult()
    object Invalid : ChannelCreateResult()
}

/**
 * Channel row persist rules. SQLite must not store PSKs; a name collision
 * updates in place and never demotes/destroys the primary.
 */
object ChannelPersistPolicy {
    const val DEFAULT_NAME = "General"

    fun findByName(existing: List<ChannelConfig>, name: String): ChannelConfig? =
        existing.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun planInsert(existing: List<ChannelConfig>, incoming: ChannelConfig): ChannelInsertAction {
        val match = findByName(existing, incoming.name) ?: return ChannelInsertAction.InsertNew(incoming)
        return ChannelInsertAction.UpdateExisting(
            incoming.copy(id = match.id, isPrimary = match.isPrimary)
        )
    }

    fun sqliteRow(channel: ChannelConfig): ChannelConfig = channel.copy(psk = "")

    fun previousNameIfRenamed(previous: ChannelConfig?, incoming: ChannelConfig): String? =
        previous?.name?.takeIf { it != incoming.name }

    fun inboxName(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

    fun nameExists(names: Iterable<String>, name: String): Boolean =
        names.any { it.equals(name, ignoreCase = true) }

    fun existingName(names: Iterable<String>, name: String): String? =
        names.firstOrNull { it.equals(name, ignoreCase = true) }

    fun visibleNames(names: List<String>): List<String> =
        if (names.isEmpty()) listOf(DEFAULT_NAME) else names

    fun canSelect(channel: String): Boolean = channel.isNotBlank()

    /**
     * Keep General, SQLite names, in-memory names, and the selected channel
     * visible without duplicates (order: default → db → current → selected).
     */
    fun mergeInboxNames(
        dbNames: List<String>,
        current: List<String>,
        selected: String,
        defaultName: String = DEFAULT_NAME
    ): List<String> =
        (listOf(defaultName) + dbNames + current + listOfNotNull(inboxName(selected)))
            .distinct()

    fun planCreate(existing: List<String>, raw: String): ChannelCreateResult {
        val name = inboxName(raw) ?: return ChannelCreateResult.Invalid
        val collision = existingName(existing, name)
        return if (collision != null) ChannelCreateResult.AlreadyExists(collision)
        else ChannelCreateResult.Created(name)
    }
}
