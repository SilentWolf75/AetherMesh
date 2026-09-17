package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioRegionPolicyTest {
    @Test
    fun knownCodes() {
        assertTrue(RadioRegionPolicy.isKnown(RadioRegionPolicy.US915))
        assertTrue(RadioRegionPolicy.isKnown(RadioRegionPolicy.EU868))
        assertFalse(RadioRegionPolicy.isKnown(-1))
        assertFalse(RadioRegionPolicy.isKnown(2))
    }

    @Test
    fun shortLabels() {
        assertEquals("US915", RadioRegionPolicy.shortLabel(0))
        assertEquals("EU868", RadioRegionPolicy.shortLabel(1))
        assertEquals("Unknown", RadioRegionPolicy.shortLabel(-1))
    }

    @Test
    fun frequenciesAndDisplay() {
        assertEquals(906.875, RadioRegionPolicy.frequencyMhz(0), 0.0)
        assertEquals(869.525, RadioRegionPolicy.frequencyMhz(1), 0.0)
        assertEquals("906.875MHz", RadioRegionPolicy.frequencyCompact(0))
        assertEquals("869.525MHz", RadioRegionPolicy.frequencyCompact(1))
        assertEquals("US915 (906.875 MHz)", RadioRegionPolicy.labelWithFrequency(0))
        assertEquals("EU868 (869.525 MHz)", RadioRegionPolicy.labelWithFrequency(1))
        assertEquals("US915 (North America)", RadioRegionPolicy.setupChoiceLabel(0))
        assertEquals("EU868 (Europe)", RadioRegionPolicy.setupChoiceLabel(1))
        assertEquals(2, RadioRegionPolicy.SETUP_CHOICES.size)
    }
}
