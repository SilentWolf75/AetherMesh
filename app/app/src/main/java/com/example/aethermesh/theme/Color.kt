package com.example.aethermesh.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Graphite instrument panel + ember primary.
 * AccentCyan / AccentMint keep their API names so existing UI call sites pick up
 * the new scheme without a mass rename.
 */
data class AetherPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color
)

val DarkPalette = AetherPalette(
    background = Color(0xFF0B0D10),
    surface = Color(0xFF151920),
    surfaceRaised = Color(0xFF1C222C),
    border = Color(0xFF2C3440),
    textPrimary = Color(0xFFEEF1F4),
    textMuted = Color(0xFF8A93A0)
)

val LightPalette = AetherPalette(
    background = Color(0xFFE8ECF1),
    surface = Color(0xFFF7F9FC),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFC5CDD8),
    textPrimary = Color(0xFF12161C),
    textMuted = Color(0xFF5A6573)
)

private var activePalette by mutableStateOf(DarkPalette)

fun setAetherPalette(dark: Boolean) {
    activePalette = if (dark) DarkPalette else LightPalette
}

fun currentAetherPalette(): AetherPalette = activePalette

val DarkBackground: Color get() = activePalette.background
val SurfaceDark: Color get() = activePalette.surface
val SurfaceRaised: Color get() = activePalette.surfaceRaised
val BorderDark: Color get() = activePalette.border
val TextLight: Color get() = activePalette.textPrimary
val TextMuted: Color get() = activePalette.textMuted

/** Primary brand / actions / selected nav (ember). */
val AccentCyan = Color(0xFFE87B3A)

/** Success / connected / positive signal (seafoam). */
val AccentMint = Color(0xFF45C4A0)

/** Errors / disconnect. */
val AccentRed = Color(0xFFE85D5D)

/** Warning / mid battery. */
val AccentAmber = Color(0xFFE8B84A)

/** Connected-radio chip / warm highlight. */
val AccentOrange = Color(0xFFF0A04B)

/** Cool secondary info (routes, hops, steel accents). */
val AccentSteel = Color(0xFF6B8FB8)

/** Soft fills for icon wells and selected chrome. */
val AccentCyanDim = Color(0x33E87B3A)
val AccentMintDim = Color(0x3345C4A0)
val AccentSteelDim = Color(0x336B8FB8)

fun batteryLevelColor(level: Int): Color {
    return when {
        level < 0 -> TextMuted
        level <= 20 -> AccentRed
        level <= 50 -> AccentAmber
        else -> AccentMint
    }
}

fun appBackgroundBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        activePalette.background,
        activePalette.surface.copy(alpha = 0.55f),
        activePalette.background
    )
)

fun headerBarBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(
        activePalette.surfaceRaised,
        activePalette.surface,
        activePalette.surfaceRaised
    )
)

fun accentGlowBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(AccentCyan.copy(alpha = 0.0f), AccentCyan.copy(alpha = 0.55f), AccentCyan.copy(alpha = 0.0f))
)
