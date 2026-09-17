package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUiPrefsTest {
    @Test
    fun clampThemeAcceptsKnownValuesAndFallsBackToSystem() {
        assertEquals("System", AppUiPrefs.clampTheme(null))
        assertEquals("System", AppUiPrefs.clampTheme(""))
        assertEquals("System", AppUiPrefs.clampTheme("Neon"))
        assertEquals("Dark", AppUiPrefs.clampTheme("Dark"))
        assertEquals("Light", AppUiPrefs.clampTheme("Light"))
        assertEquals("System", AppUiPrefs.clampTheme("System"))
    }

    @Test
    fun clampLanguageAcceptsKnownValuesAndFallsBackToEnglish() {
        assertEquals("English", AppUiPrefs.clampLanguage(null))
        assertEquals("English", AppUiPrefs.clampLanguage("Français"))
        assertEquals("Spanish", AppUiPrefs.clampLanguage("Spanish"))
        assertEquals("English", AppUiPrefs.clampLanguage("English"))
    }

    @Test
    fun isSpanishUsesClamp() {
        assertEquals(true, AppUiPrefs.isSpanish("Spanish"))
        assertEquals(false, AppUiPrefs.isSpanish("English"))
        assertEquals(false, AppUiPrefs.isSpanish(null))
        assertEquals(false, AppUiPrefs.isSpanish("Français"))
    }
    /**
     * Receipts are unconditional now; the UI only stops implying one is coming
     * after the window, so a quiet neighborhood does not leave a message pending.
     */
    @Test
    fun channelReceiptWindowBoundsThePendingLabel() {
        assertEquals(true, ChatSendPolicy.wantAck())
        assertEquals(true, ChatSendPolicy.channelReceiptPending(0L))
        assertEquals(true, ChatSendPolicy.channelReceiptPending(ChatSendPolicy.CHANNEL_RECEIPT_WINDOW_MS))
        assertEquals(false, ChatSendPolicy.channelReceiptPending(ChatSendPolicy.CHANNEL_RECEIPT_WINDOW_MS + 1))
        // A clock skewed backwards must not read as "still waiting" forever.
        assertEquals(false, ChatSendPolicy.channelReceiptPending(-1L))
    }
}
