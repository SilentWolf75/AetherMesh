package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogPolicyTest {
    @Test
    fun formatLinePrefixesUsTime() {
        val line = DiagnosticLogPolicy.formatLine("hello", 0L)
        assertTrue(line.matches(Regex("""\d{2}:\d{2}:\d{2} hello""")))
    }

    @Test
    fun ringDropsOldestPastCapacity() {
        val seed = (1..DiagnosticLogPolicy.CAPACITY).map { "old$it" }
        val next = DiagnosticLogPolicy.nextRing(seed, "newest", 0L)
        assertEquals(DiagnosticLogPolicy.CAPACITY, next.size)
        assertEquals("old2", next.first())
        assertTrue(next.last().endsWith(" newest"))
    }
}
