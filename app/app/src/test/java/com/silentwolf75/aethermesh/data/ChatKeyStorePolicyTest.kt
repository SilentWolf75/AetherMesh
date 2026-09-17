package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChatKeyStorePolicyTest {
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setup() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("chat_key_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun prefKeyHashesAndSplitsChannelVsDm() {
        val channel = ChatKeyStorePolicy.prefKey("CHANNEL_General")
        val dm = ChatKeyStorePolicy.prefKey("DM_34")
        assertTrue(channel.startsWith(ChatKeyStorePolicy.CHANNEL_PREFIX))
        assertTrue(dm.startsWith(ChatKeyStorePolicy.DM_PREFIX))
        assertEquals(channel, ChatKeyStorePolicy.prefKey("CHANNEL_General"))
        assertNotEquals(channel, ChatKeyStorePolicy.prefKey("CHANNEL_Crew"))
        assertEquals(64, channel.removePrefix(ChatKeyStorePolicy.CHANNEL_PREFIX).length)
    }

    @Test
    fun lookupPrefersSecurePrefsThenMigratesLegacy() {
        val id = "CHANNEL_General"
        var legacyReads = 0
        ChatKeyStorePolicy.save(prefs, id, "secure")
        val hit = ChatKeyStorePolicy.lookup(prefs, id) {
            legacyReads++
            "legacy"
        }
        assertEquals("secure", hit.value)
        assertFalse(hit.migrated)
        assertEquals(0, legacyReads)

        prefs.edit().clear().commit()
        val migrated = ChatKeyStorePolicy.lookup(prefs, id) { "legacy" }
        assertEquals("legacy", migrated.value)
        assertTrue(migrated.migrated)
        assertEquals("legacy", prefs.getString(ChatKeyStorePolicy.prefKey(id), null))
    }

    @Test
    fun blankSaveRemovesAndEmptyLegacyIsIgnored() {
        val id = "DM_1"
        ChatKeyStorePolicy.save(prefs, id, "k")
        ChatKeyStorePolicy.save(prefs, id, "  ")
        assertNull(prefs.getString(ChatKeyStorePolicy.prefKey(id), null))
        val miss = ChatKeyStorePolicy.lookup(prefs, id) { "" }
        assertNull(miss.value)
        assertFalse(miss.migrated)
    }

    @Test
    fun clearAllMessagesOnlyDropsDmKeys() {
        ChatKeyStorePolicy.save(prefs, "CHANNEL_A", "c")
        ChatKeyStorePolicy.save(prefs, "DM_1", "d")
        val dm = ChatKeyStorePolicy.dmPrefKeys(prefs.all.keys.map { it as String }.toSet())
        assertEquals(1, dm.size)
        assertTrue(dm.first().startsWith(ChatKeyStorePolicy.DM_PREFIX))
    }
}
