package com.example.aethermesh.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.junit.Test

class ChatSecurityHelpersTest {
    @Test
    fun pbkdf2IsDeterministicButSaltSpecific() {
        val first = ChatKeyDerivation.derive("trail-pass", ByteArray(16) { 1 }).encoded
        val same = ChatKeyDerivation.derive("trail-pass", ByteArray(16) { 1 }).encoded
        val otherSalt = ChatKeyDerivation.derive("trail-pass", ByteArray(16) { 2 }).encoded
        assertEquals(first.toList(), same.toList())
        assertFalse(first.contentEquals(otherSalt))
    }

    @Test
    fun pbkdf2MatchesPublishedSha256Vector() {
        val key = ChatKeyDerivation.derive("password", "salt".toByteArray(), iterations = 1).encoded
        val hex = key.joinToString("") { "%02x".format(it) }
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", hex)
    }

    @Test
    fun utf8BoundNeverSplitsEmojiOrExceedsBytes() {
        val result = "ab😀cd".takeUtf8Bytes(6)
        assertEquals("ab😀", result)
        assertEquals(6, result.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun directMessageContextIsSymmetricAcrossSenderAndReceiver() {
        assertEquals(
            ChatContext.authenticatedLabel(0x10, 0x20, ""),
            ChatContext.authenticatedLabel(0x20, 0x10, "")
        )
    }
}
