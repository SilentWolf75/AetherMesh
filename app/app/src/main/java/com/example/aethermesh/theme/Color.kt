package com.example.aethermesh.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Night-radar scheme: deep navy field, azure primary, lime success.
 * AccentCyan / AccentMint keep API names for existing call sites.
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
    background = Color(0xFF060B14),
    surface = Color(0xFF0E1624),
    surfaceRaised = Color(0xFF152033),
    border = Color(0xFF243044),
    textPrimary = Color(0xFFE8F0FF),
    textMuted = Color(0xFF7E90A8)
)

val LightPalette = AetherPalette(
    background = Color(0xFFE6EEF8),
    surface = Color(0xFFF5F8FC),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFB8C7DA),
    textPrimary = Color(0xFF0B1524),
    textMuted = Color(0xFF4E6078)
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

/** Primary — azure radar blue. */
val AccentCyan = Color(0xFF4DA3FF)

/** Success — lime. */
val AccentMint = Color(0xFFC8F547)

/** Errors. */
val AccentRed = Color(0xFFFF5C7A)

/** Mid / warning. */
val AccentAmber = Color(0xFFFFB347)

/** Radio chip / warm callout. */
val AccentOrange = Color(0xFFFF8C42)

/** Cool secondary (hops, steel info). */
val AccentSteel = Color(0xFF7AD4FF)

val AccentCyanDim = Color(0x334DA3FF)
val AccentMintDim = Color(0x33C8F547)
val AccentSteelDim = Color(0x337AD4FF)
val AccentOrangeDim = Color(0x33FF8C42)

fun batteryLevelColor(level: Int): Color {
    return when {
        level < 0 -> TextMuted
        level <= 20 -> AccentRed
        level <= 50 -> AccentAmber
        else -> AccentMint
    }
}

fun appBackgroundBrush(): Brush = Brush.radialGradient(
    colors = listOf(
        Color(0xFF122038),
        activePalette.background,
        Color(0xFF04070C)
    )
)

fun headerBarBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        activePalette.surfaceRaised,
        activePalette.surface
    )
)

fun cardTopStripeBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(AccentCyan, AccentSteel, AccentMint)
)

fun primaryButtonBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(AccentCyan, AccentSteel)
)
