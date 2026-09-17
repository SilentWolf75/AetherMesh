package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SavedPasswordPolicyTest {
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setup() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("pwd_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun macKeyIsTrimmedUppercase() {
        assertEquals("AA:BB:CC:DD:EE:FF", SavedPasswordPolicy.normalizeMac(" aa:bb:cc:dd:ee:ff "))
        assertEquals(
            "node_pwd_AA:BB:CC:DD:EE:FF",
            SavedPasswordPolicy.keyForMac(" aa:bb:cc:dd:ee:ff ")
        )
        assertEquals("node_pwd_id_17", SavedPasswordPolicy.keyForNodeId(17L))
    }

    @Test
    fun emptyPasswordIsNotStored() {
        assertFalse(SavedPasswordPolicy.save(prefs, "aa:bb", 1L, ""))
        assertNull(prefs.getString(SavedPasswordPolicy.keyForMac("aa:bb"), null))
    }

    @Test
    fun lookupPrefersCanonicalMacThenLegacyThenNodeId() {
        prefs.edit().putString(SavedPasswordPolicy.keyForNodeId(9L), "by-id").commit()
        assertEquals("by-id", SavedPasswordPolicy.lookup(prefs, "aa:bb", 9L))

        prefs.edit().putString("node_pwd_aa:bb", "legacy").commit()
        assertEquals("legacy", SavedPasswordPolicy.lookup(prefs, "aa:bb", 9L))

        SavedPasswordPolicy.save(prefs, "aa:bb", 9L, "canonical")
        assertEquals("canonical", SavedPasswordPolicy.lookup(prefs, "aa:bb", 9L))
        assertEquals("canonical", SavedPasswordPolicy.lookup(prefs, "AA:BB", 0L))
    }

    @Test
    fun clearRemovesCanonicalLegacyAndIdKeys() {
        SavedPasswordPolicy.save(prefs, "aa:bb", 9L, "secret")
        prefs.edit().putString("node_pwd_aa:bb", "legacy").commit()
        val keys = SavedPasswordPolicy.keysToClear("aa:bb", 9L)
        assertTrue(keys.contains("node_pwd_AA:BB"))
        assertTrue(keys.contains("node_pwd_aa:bb"))
        assertTrue(keys.contains("node_pwd_id_9"))
        SavedPasswordPolicy.clear(prefs, "aa:bb", 9L)
        assertNull(SavedPasswordPolicy.lookup(prefs, "aa:bb", 9L))
        assertNull(prefs.getString("node_pwd_aa:bb", null))
    }
}
