package com.silentwolf75.aethermesh.data

import android.app.Application
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChannelJoinApplyTest {
    private val primary = ChannelConfig(
        id = 1,
        name = "Primary",
        psk = "old",
        isPrimary = true,
        uplinkEnabled = true,
        downlinkEnabled = true,
        positionEnabled = true,
        preciseLocation = true
    )
    private val secondary = ChannelConfig(
        id = 2,
        name = "Crew",
        psk = "old2",
        isPrimary = false
    )

    @Test
    fun joiningPrimaryNameUpdatesInPlace() {
        val plan = ChannelJoinApply.plan(
            listOf(primary, secondary),
            ChannelInvite("Primary", "newpsk", uplinkEnabled = false, downlinkEnabled = true, positionEnabled = false, preciseLocation = false)
        )
        assertEquals(ChannelJoinKind.PRIMARY_UPDATE, plan.kind)
        assertEquals(1, plan.config.id)
        assertTrue(plan.config.isPrimary)
        assertEquals("newpsk", plan.config.psk)
        assertFalse(plan.config.uplinkEnabled)
        assertFalse(plan.config.positionEnabled)
    }

    @Test
    fun joiningUnknownNameInsertsSecondary() {
        val plan = ChannelJoinApply.plan(
            listOf(primary),
            ChannelInvite("Trail", "psk")
        )
        assertEquals(ChannelJoinKind.SECONDARY_INSERT, plan.kind)
        assertFalse(plan.config.isPrimary)
        assertEquals("Trail", plan.config.name)
        assertEquals(1f, plan.config.precisionMiles)
    }

    @Test
    fun joiningExistingSecondaryUpdatesSecondary() {
        val plan = ChannelJoinApply.plan(
            listOf(primary, secondary),
            ChannelInvite("crew", "psk2")
        )
        assertEquals(ChannelJoinKind.SECONDARY_UPDATE, plan.kind)
        assertFalse(plan.config.isPrimary)
        assertEquals("crew", plan.config.name)
    }

    @Test
    fun pskGenerateIsSixteenDecodedBytes() {
        val psk = ChannelPskPolicy.generate(SecureRandom())
        assertEquals(16, Base64.decode(psk, Base64.NO_WRAP).size)
        assertFalse(psk.contains("\n"))
        assertEquals("Trail", ChannelPskPolicy.clipName("  Trail  "))
        assertEquals(24, ChannelPskPolicy.clipName("x".repeat(40)).length)
    }

    @Test
    fun defaultPskIsSixteenBytesAndNotMeshtastic() {
        assertEquals("AQ==", ChannelPskPolicy.MESH_PSK_LEGACY)
        assertFalse(ChannelPskPolicy.isCustom(ChannelPskPolicy.DEFAULT_PSK))
        assertFalse(ChannelPskPolicy.isCustom(ChannelPskPolicy.MESH_PSK_LEGACY))
        assertTrue(ChannelPskPolicy.isCustom("custom"))
        val decoded = Base64.decode(ChannelPskPolicy.DEFAULT_PSK, Base64.NO_WRAP)
        assertEquals(16, decoded.size)
        assertFalse(ChannelPskPolicy.DEFAULT_PSK == ChannelPskPolicy.MESH_PSK_LEGACY)
    }

    @Test
    fun hydrateFillsKeyringFromSqliteAndClearsRow() {
        val fromRow = ChannelPskPolicy.hydrateFromStore(null, "custom")
        assertEquals("custom", fromRow.secret)
        assertTrue(fromRow.persistKeyring)
        assertTrue(fromRow.clearSqlitePsk)

        val fromLegacy = ChannelPskPolicy.hydrateFromStore("", ChannelPskPolicy.MESH_PSK_LEGACY)
        assertEquals(ChannelPskPolicy.MESH_PSK_LEGACY, fromLegacy.secret)
        assertTrue(fromLegacy.persistKeyring)
        assertTrue(fromLegacy.clearSqlitePsk)
    }

    @Test
    fun hydrateMigratesLegacyKeyringAndLeavesCleanKeyringAlone() {
        val migrated = ChannelPskPolicy.hydrateFromStore(ChannelPskPolicy.MESH_PSK_LEGACY, "stale")
        assertEquals(ChannelPskPolicy.MESH_PSK_LEGACY, migrated.secret)
        assertFalse(migrated.persistKeyring)
        assertTrue(migrated.clearSqlitePsk)

        val clean = ChannelPskPolicy.hydrateFromStore(ChannelPskPolicy.DEFAULT_PSK, "")
        assertEquals(ChannelPskPolicy.DEFAULT_PSK, clean.secret)
        assertFalse(clean.persistKeyring)
        assertFalse(clean.clearSqlitePsk)
    }

    @Test
    fun hydrateEmptyStoreStaysEmpty() {
        val empty = ChannelPskPolicy.hydrateFromStore(null, "")
        assertEquals("", empty.secret)
        assertFalse(empty.persistKeyring)
        assertFalse(empty.clearSqlitePsk)
    }
}
