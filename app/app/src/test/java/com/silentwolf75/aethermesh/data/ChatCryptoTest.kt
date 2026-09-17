package com.silentwolf75.aethermesh.data

import android.app.Application
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChatCryptoTest {
    /**
     * Cross-platform vector, generated independently with Python `cryptography`
     * (PBKDF2-HMAC-SHA256 120k, salt 00..0f, iv 10..1b, AAD "CHANNEL_Trail").
     * The iOS AetherMeshKit CryptoVectorTests decrypt and reproduce the same bytes.
     */
    @Test
    fun decryptsSharedCrossPlatformVector() {
        assertEquals(
            "meet at the ridge ✓",
            ChatCrypto.decrypt(
                "v2:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaG8s/TOyrGKi8UPX219VjeO/Pd8l6u8iyX6dn8BvCzJWQzaVuWHc=",
                "trail-key",
                "CHANNEL_Trail"
            )
        )
    }

    @Test
    fun v2RoundtripWithAad() {
        val cipher = ChatCrypto.encrypt("hello mesh", "pass-one", "CHANNEL_Primary")
        assertNotNull(cipher)
        assertTrue(cipher!!.startsWith("v2:"))
        assertEquals(
            "hello mesh",
            ChatCrypto.decrypt(cipher, "pass-one", "CHANNEL_Primary")
        )
    }

    @Test
    fun encryptTwiceYieldsDifferentCiphertext() {
        val a = ChatCrypto.encrypt("same", "k", "id")
        val b = ChatCrypto.encrypt("same", "k", "id")
        assertNotNull(a)
        assertNotNull(b)
        assertNotEquals(a, b)
    }

    @Test
    fun wrongPasscodeOrAadFailsClosed() {
        val cipher = ChatCrypto.encrypt("secret", "right", "chat-a")!!
        assertEquals(
            ChatCrypto.ERROR_BAD_CONTEXT,
            ChatCrypto.decrypt(cipher, "wrong", "chat-a")
        )
        assertEquals(
            ChatCrypto.ERROR_BAD_CONTEXT,
            ChatCrypto.decrypt(cipher, "right", "chat-b")
        )
    }

    @Test
    fun truncatedV2IsInvalid() {
        assertEquals(
            ChatCrypto.ERROR_INVALID,
            ChatCrypto.decrypt("v2:" + Base64.encodeToString(ByteArray(44), Base64.NO_WRAP), "k")
        )
    }

    @Test
    fun decryptsV1Gcm() {
        val key = ChatCrypto.deriveLegacyKey("legacy")
        val iv = ByteArray(12) { 3 }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val blob = iv + cipher.doFinal("old gcm".toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
        assertEquals("old gcm", ChatCrypto.decrypt(encoded, "legacy"))
    }

    @Test
    fun decryptsLegacyEcb() {
        val key = ChatCrypto.deriveLegacyKey("legacy")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encoded = Base64.encodeToString(
            cipher.doFinal("old ecb".toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        assertEquals("old ecb", ChatCrypto.decrypt(encoded, "legacy"))
    }

    @Test
    fun garbageYieldsBadKey() {
        assertEquals(ChatCrypto.ERROR_BAD_KEY, ChatCrypto.decrypt("not-cipher", "k"))
    }
}
