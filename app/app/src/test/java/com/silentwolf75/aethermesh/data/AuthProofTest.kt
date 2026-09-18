package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthProofTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    // Same vectors as firmware/test/test_authproof and the iOS tests.
    @Test
    fun matchesTheSharedVectors() {
        val counting = ByteArray(16) { it.toByte() }
        assertEquals(
            "2ab4d919d4aa92c4649a3541a25e6e73908a358a4faf2ca51a7e2577aa969932",
            hex(AuthProof.compute("admin", counting))
        )
        val ones = ByteArray(16) { 0xFF.toByte() }
        assertEquals(
            "83ba1a19d57fa4755405d91ac287085ac065ffd75e674ff4eecb86e151de273f",
            hex(AuthProof.compute("pässwörd-with-a-long-tail-0123456789", ones))
        )
    }

    @Test
    fun onlyFullLengthChallengesAreAnswered() {
        assertTrue(AuthProof.isUsableChallenge(ByteArray(16)))
        assertFalse(AuthProof.isUsableChallenge(ByteArray(0)))
        assertFalse(AuthProof.isUsableChallenge(null))
        assertFalse(AuthProof.isUsableChallenge(ByteArray(15)))
    }
}
