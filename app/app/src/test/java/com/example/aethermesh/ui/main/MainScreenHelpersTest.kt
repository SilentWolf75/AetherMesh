package com.example.aethermesh.ui.main

import junit.framework.TestCase.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure helper functions in MainScreen.kt. These avoid any Android
 * framework dependencies, so they run on the local JVM via :app:testDebugUnitTest.
 */
class MainScreenHelpersTest {

    @Test
    fun formatUptime_secondsOnly() {
        assertEquals("0s", formatUptime(0))
        assertEquals("45s", formatUptime(45))
    }

    @Test
    fun formatUptime_minutesAndSeconds() {
        assertEquals("1m 30s", formatUptime(90))
        assertEquals("59m 59s", formatUptime(3599))
    }

    @Test
    fun formatUptime_hoursAndMinutes() {
        assertEquals("1h 1m", formatUptime(3661))
        assertEquals("23h 59m", formatUptime(86399))
    }

    @Test
    fun formatUptime_daysAndHours() {
        assertEquals("1d 1h", formatUptime(90000))
        assertEquals("2d 0h", formatUptime(172800))
    }

    @Test
    fun getInitials_blankReturnsPlaceholder() {
        assertEquals("??", getInitials("   "))
    }

    @Test
    fun getInitials_twoWordsUsesFirstLetterOfEach() {
        assertEquals("WB", getInitials("Wolf Base"))
    }

    @Test
    fun getInitials_singleWordTakesFirstTwoLetters() {
        assertEquals("WO", getInitials("Wolf"))
    }

    @Test
    fun getInitials_stripsKnownPrefixes() {
        // "AetherMesh-" and "Node " prefixes are removed before deriving initials.
        assertEquals("1A", getInitials("AetherMesh-1A2B"))
        assertEquals("3C", getInitials("Node 3C4D"))
    }
}
