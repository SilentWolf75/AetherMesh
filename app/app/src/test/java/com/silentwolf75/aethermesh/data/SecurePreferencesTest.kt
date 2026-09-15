package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SecurePreferencesTest {
    private fun prefs(name: String) =
        RuntimeEnvironment.getApplication().getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test fun unavailableKeystoreFailsClosedAndLeavesLegacySecretsIntact() {
        val context = RuntimeEnvironment.getApplication()
        val fallback = prefs(SecurePrefsNames.FALLBACK)
        fallback.edit().putString("node_pwd_1", "preserve").commit()
        assertThrows(SecureStorageUnavailable::class.java) {
            SecurePreferences.open(context) { throw IllegalStateException("keystore unavailable") }
        }
        assertEquals("preserve", fallback.getString("node_pwd_1", null))
        assertEquals(1, fallback.all.size)
    }

    @Test fun migrationPreservesNewerEncryptedValuesAndCleansSource() {
        val source = prefs("source")
        val destination = prefs("destination")
        source.edit().putString("node_pwd_1", "old").putString("ecdh_private_key", "key").commit()
        destination.edit().putString("node_pwd_1", "new").commit()
        SecurePreferences.migrate(source, destination) { true }
        assertEquals("new", destination.getString("node_pwd_1", null))
        assertEquals("key", destination.getString("ecdh_private_key", null))
        assertTrue(source.all.isEmpty())
    }

    @Test fun failedEncryptedWriteKeepsOriginalSecrets() {
        val source = prefs("source")
        source.edit().putString("key", "preserve").commit()
        val real = prefs("destination")
        val broken = object : SharedPreferences by real {
            override fun edit(): SharedPreferences.Editor {
                val editor = real.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit() = false
                }
            }
        }
        assertThrows(IllegalStateException::class.java) {
            SecurePreferences.migrate(source, broken) { true }
        }
        assertEquals("preserve", source.getString("key", null))
    }
}
