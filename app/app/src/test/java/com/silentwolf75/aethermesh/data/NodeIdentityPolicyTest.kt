package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.NodeIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeIdentityPolicyTest {

    private fun key(seed: Int) = ByteArray(32) { (seed + it).toByte() }

    @Test
    fun trustVerdictsMapToSomethingTheUserCanAct0n() {
        assertEquals(NodeIdentityPolicy.State.LEARNED,
            NodeIdentityPolicy.stateOf(NodeIdentity.Trust.FIRST_USE))
        assertEquals(NodeIdentityPolicy.State.KNOWN,
            NodeIdentityPolicy.stateOf(NodeIdentity.Trust.KNOWN))
        assertEquals(NodeIdentityPolicy.State.ROTATED,
            NodeIdentityPolicy.stateOf(NodeIdentity.Trust.ROTATED))
        assertEquals(NodeIdentityPolicy.State.CONFLICT,
            NodeIdentityPolicy.stateOf(NodeIdentity.Trust.CONFLICT))
        // An older node, or a verdict this build does not know, is never
        // optimistically treated as verified.
        assertEquals(NodeIdentityPolicy.State.UNKNOWN,
            NodeIdentityPolicy.stateOf(NodeIdentity.Trust.TRUST_UNSPECIFIED))
        assertEquals(NodeIdentityPolicy.State.UNKNOWN, NodeIdentityPolicy.stateOf(null))
    }

    @Test
    fun onlySettledKeysAreSealedToAndTroubleIsSurfaced() {
        assertTrue(NodeIdentityPolicy.isSealable(NodeIdentityPolicy.State.LEARNED))
        assertTrue(NodeIdentityPolicy.isSealable(NodeIdentityPolicy.State.KNOWN))
        // A disputed or freshly rotated key is not sealed to until confirmed.
        assertFalse(NodeIdentityPolicy.isSealable(NodeIdentityPolicy.State.CONFLICT))
        assertFalse(NodeIdentityPolicy.isSealable(NodeIdentityPolicy.State.ROTATED))
        assertFalse(NodeIdentityPolicy.isSealable(NodeIdentityPolicy.State.UNKNOWN))

        assertTrue(NodeIdentityPolicy.needsAttention(NodeIdentityPolicy.State.CONFLICT))
        assertTrue(NodeIdentityPolicy.needsAttention(NodeIdentityPolicy.State.ROTATED))
        assertFalse(NodeIdentityPolicy.needsAttention(NodeIdentityPolicy.State.KNOWN))
        assertFalse(NodeIdentityPolicy.needsAttention(NodeIdentityPolicy.State.UNKNOWN))
    }

    @Test
    fun fingerprintIsStableAndShapedForReadingAloud() {
        val first = NodeIdentityPolicy.fingerprint(key(1))
        assertEquals(first, NodeIdentityPolicy.fingerprint(key(1)))
        assertEquals(19, first.length)
        assertEquals(4, first.split("-").size)
        assertTrue(first.all { it.isDigit() || it in 'A'..'F' || it == '-' })
        // A different key must not read the same aloud.
        assertTrue(first != NodeIdentityPolicy.fingerprint(key(2)))
    }

    @Test
    fun degenerateAndMalformedKeysHaveNoFingerprint() {
        // All-zero X25519 keys make every shared secret zero; they are not identities.
        assertEquals("", NodeIdentityPolicy.fingerprint(ByteArray(32)))
        assertEquals("", NodeIdentityPolicy.fingerprint(ByteArray(31) { 1 }))
        assertEquals("", NodeIdentityPolicy.fingerprint(null))
        assertFalse(NodeIdentityPolicy.keyIsUsable(ByteArray(32)))
        assertFalse(NodeIdentityPolicy.keyIsUsable(ByteArray(31) { 1 }))
        assertFalse(NodeIdentityPolicy.keyIsUsable(null))
        assertTrue(NodeIdentityPolicy.keyIsUsable(key(9)))
    }

    @Test
    fun conflictWordingSaysWhatHappenedAndWhatToDo() {
        val english = NodeIdentityPolicy.explanation(NodeIdentityPolicy.State.CONFLICT, false)
        // The user needs to know the original key was kept, not replaced.
        assertTrue(english.contains("original key was kept"))
        assertTrue(english.contains("Verify"))
        val spanish = NodeIdentityPolicy.explanation(NodeIdentityPolicy.State.CONFLICT, true)
        assertTrue(spanish.isNotEmpty() && spanish != english)
        // A rotation must not be dressed up as routine.
        val rotated = NodeIdentityPolicy.explanation(NodeIdentityPolicy.State.ROTATED, false)
        assertTrue(rotated.contains("reflashed"))
        assertTrue(NodeIdentityPolicy.label(NodeIdentityPolicy.State.CONFLICT, false)
            .contains("CONFLICT"))
    }
}
