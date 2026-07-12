package com.example.aethermesh.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Structural colors that swap with light/dark. Accents stay constant. */
data class AetherPalette(
    val background: Color,
    val surface: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color
)

val DarkPalette = AetherPalette(
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    border = Color(0xFF334155),
    textPrimary = Color(0xFFF8FAFC),
    textMuted = Color(0xFF94A3B8)
)

val LightPalette = AetherPalette(
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    border = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF475569)
)

private var activePalette by mutableStateOf(DarkPalette)

/** Called from the theme root when the effective light/dark mode changes. */
fun setAetherPalette(dark: Boolean) {
    activePalette = if (dark) DarkPalette else LightPalette
}

fun currentAetherPalette(): AetherPalette = activePalette

val DarkBackground: Color get() = activePalette.background
val SurfaceDark: Color get() = activePalette.surface
val BorderDark: Color get() = activePalette.border
val TextLight: Color get() = activePalette.textPrimary
val TextMuted: Color get() = activePalette.textMuted

val AccentCyan = Color(0xFF22D3EE)
val AccentMint = Color(0xFF34D399)
val AccentRed = Color(0xFFEF4444)
val AccentAmber = Color(0xFFFBBF24)
val AccentOrange = Color(0xFFF59E0B)

fun batteryLevelColor(level: Int): Color {
    return when {
        level < 0 -> TextMuted
        level <= 20 -> AccentRed
        level <= 50 -> AccentAmber
        else -> AccentMint
    }
}
