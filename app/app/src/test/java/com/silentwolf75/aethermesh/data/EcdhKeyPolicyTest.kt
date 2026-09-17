package com.silentwolf75.aethermesh.data

import android.app.Application
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EcdhKeyPolicyTest {
    @Test
    fun generateProducesDistinctDecodableEcKeys() {
        val pair = EcdhKeyPolicy.generate()
        assertFalse(EcdhKeyPolicy.isError(pair))
        assertNotEquals(pair.publicKey, pair.privateKey)
        val pubBytes = Base64.decode(pair.publicKey, Base64.NO_WRAP)
        val privBytes = Base64.decode(pair.privateKey, Base64.NO_WRAP)
        val kf = KeyFactory.getInstance("EC")
        kf.generatePublic(X509EncodedKeySpec(pubBytes))
        kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
    }

    @Test
    fun generateTwiceYieldsDifferentPairs() {
        val a = EcdhKeyPolicy.generate()
        val b = EcdhKeyPolicy.generate()
        assertNotEquals(a.publicKey, b.publicKey)
        assertNotEquals(a.privateKey, b.privateKey)
    }

    @Test
    fun exportTextMatchesSettingsClipboardFormat() {
        assertEquals(
            "AetherMesh Security Keys:\nPublic: pub\nPrivate: priv",
            EcdhKeyPolicy.exportText("pub", "priv")
        )
    }

    @Test
    fun fingerprintIsEightHexAndStable() {
        val pair = EcdhKeyPolicy.generate()
        val a = EcdhKeyPolicy.fingerprint(pair.publicKey)
        val b = EcdhKeyPolicy.fingerprint(pair.publicKey)
        assertEquals(8, a.length)
        assertEquals(a, b)
        assertTrue(a.matches(Regex("[0-9A-F]{8}")))
        assertEquals("", EcdhKeyPolicy.fingerprint(""))
        assertEquals("", EcdhKeyPolicy.fingerprint(EcdhKeyPolicy.ERROR_PUBLIC))
    }

    @Test
    fun verificationCodeIsOrderIndependentSixDigits() {
        val a = EcdhKeyPolicy.generate()
        val b = EcdhKeyPolicy.generate()
        val ab = EcdhKeyPolicy.verificationCode(a.publicKey, b.publicKey)
        val ba = EcdhKeyPolicy.verificationCode(b.publicKey, a.publicKey)
        assertEquals(6, ab.length)
        assertEquals(ab, ba)
        assertTrue(ab.matches(Regex("\\d{6}")))
        assertEquals("", EcdhKeyPolicy.verificationCode(a.publicKey, ""))
        assertNotEquals(
            ab,
            EcdhKeyPolicy.verificationCode(a.publicKey, EcdhKeyPolicy.generate().publicKey)
        )
    }

    @Test
    fun existingPairRejectsBlankAndErrorSentinels() {
        val pair = EcdhKeyPolicy.generate()
        val kept = EcdhKeyPolicy.existingPair(pair.publicKey, pair.privateKey)
        assertEquals(pair.publicKey, kept!!.first)
        assertEquals(pair.privateKey, kept.second)
        assertEquals(null, EcdhKeyPolicy.existingPair(null, pair.privateKey))
        assertEquals(null, EcdhKeyPolicy.existingPair(pair.publicKey, "  "))
        assertEquals(
            null,
            EcdhKeyPolicy.existingPair(EcdhKeyPolicy.ERROR_PUBLIC, pair.privateKey)
        )
    }

    @Test
    fun prefKeysAlignWithMigrationSecretFilter() {
        assertEquals("ecdh_public_key", EcdhKeyPolicy.PREF_PUBLIC)
        assertEquals(AppMigrationExportPolicy.ECDH_PRIVATE_KEY, EcdhKeyPolicy.PREF_PRIVATE)
        assertTrue(AppMigrationExportPolicy.isSecretPrefKey(EcdhKeyPolicy.PREF_PRIVATE))
        assertFalse(AppMigrationExportPolicy.isSecretPrefKey(EcdhKeyPolicy.PREF_PUBLIC))
    }
}
