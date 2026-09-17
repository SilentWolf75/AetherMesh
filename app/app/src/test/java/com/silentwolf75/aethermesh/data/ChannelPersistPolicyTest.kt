package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPersistPolicyTest {
    private val primary = ChannelConfig(
        id = 1,
        name = "Primary",
        psk = "old",
        isPrimary = true
    )
    private val secondary = ChannelConfig(
        id = 2,
        name = "Crew",
        psk = "psk2",
        isPrimary = false
    )

    @Test
    fun nameCollisionKeepsPrimaryAndId() {
        val incoming = ChannelConfig(name = "primary", psk = "new", isPrimary = false)
        val plan = ChannelPersistPolicy.planInsert(listOf(primary, secondary), incoming)
            as ChannelInsertAction.UpdateExisting
        assertEquals(1L, plan.config.id)
        assertTrue(plan.config.isPrimary)
        assertEquals("new", plan.config.psk)
    }

    @Test
    fun unknownNameInsertsNew() {
        val incoming = ChannelConfig(name = "Trail", psk = "psk", isPrimary = false)
        val plan = ChannelPersistPolicy.planInsert(listOf(primary), incoming)
        assertTrue(plan is ChannelInsertAction.InsertNew)
        assertEquals("Trail", (plan as ChannelInsertAction.InsertNew).config.name)
    }

    @Test
    fun sqliteRowStripsPsk() {
        assertEquals("", ChannelPersistPolicy.sqliteRow(primary).psk)
        assertTrue(ChannelPersistPolicy.sqliteRow(primary).isPrimary)
    }

    @Test
    fun renameDropsPreviousKeyName() {
        assertEquals("Crew", ChannelPersistPolicy.previousNameIfRenamed(secondary, secondary.copy(name = "Trail")))
        assertNull(ChannelPersistPolicy.previousNameIfRenamed(secondary, secondary.copy(psk = "x")))
    }

    @Test
    fun inboxAndVisibleNames() {
        assertNull(ChannelPersistPolicy.inboxName("  "))
        assertEquals("Trail", ChannelPersistPolicy.inboxName(" Trail "))
        assertTrue(ChannelPersistPolicy.nameExists(listOf("General"), "general"))
        assertFalse(ChannelPersistPolicy.nameExists(listOf("General"), "Trail"))
        assertEquals(listOf("General"), ChannelPersistPolicy.visibleNames(emptyList()))
    }

    @Test
    fun mergeInboxKeepsDefaultDbCurrentAndSelected() {
        assertEquals(
            listOf("General", "Crew", "Trail", "Ops"),
            ChannelPersistPolicy.mergeInboxNames(
                dbNames = listOf("Crew", "Trail"),
                current = listOf("General", "Ops"),
                selected = " Trail "
            )
        )
        assertEquals(
            listOf("General"),
            ChannelPersistPolicy.mergeInboxNames(emptyList(), emptyList(), "  ")
        )
    }

    @Test
    fun planCreateRejectsBlankAndReportsCollision() {
        assertEquals(ChannelCreateResult.Invalid, ChannelPersistPolicy.planCreate(listOf("General"), "  "))
        assertEquals(
            ChannelCreateResult.AlreadyExists("General"),
            ChannelPersistPolicy.planCreate(listOf("General"), "general")
        )
        assertEquals(
            ChannelCreateResult.Created("Trail"),
            ChannelPersistPolicy.planCreate(listOf("General"), " Trail ")
        )
        assertFalse(ChannelPersistPolicy.canSelect(""))
        assertTrue(ChannelPersistPolicy.canSelect("Ops"))
    }
}
