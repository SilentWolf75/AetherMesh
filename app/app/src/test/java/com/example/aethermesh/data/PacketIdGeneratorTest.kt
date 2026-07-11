package com.example.aethermesh.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class PacketIdGeneratorTest {
    @Test
    fun sequenceUsesTheFullPositiveRangeAndWrapsWithoutZero() {
        val sequence = PacketIdSequence(Int.MAX_VALUE - 1)
        assertEquals(Int.MAX_VALUE, sequence.next())
        assertEquals(1, sequence.next())
        assertEquals(2, sequence.next())
    }

    @Test
    fun sequenceDoesNotRepeatAcrossLargeBurst() {
        val sequence = PacketIdSequence(1234)
        val ids = HashSet<Int>()
        repeat(100_000) { ids += sequence.next() }
        assertEquals(100_000, ids.size)
        assertTrue(ids.none { it == 0 })
    }
}
