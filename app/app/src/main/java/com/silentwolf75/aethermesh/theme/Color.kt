package com.silentwolf75.aethermesh.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.silentwolf75.aethermesh.data.SignalBand

/**
 * AetherMesh design system.
 *
 * Calm graphite surfaces with a single teal brand accent. Semantic colours
 * (success, warning, error, info) are reserved for meaning, never decoration,
 * and every colour has a light-theme counterpart with readable contrast.
 *
 * The Accent* names predate this palette and are kept so existing call sites
 * pick up the new values: AccentCyan is the brand accent, AccentMint success,
 * AccentAmber warning, AccentRed error, AccentSteel info, AccentOrange a warm
 * highlight for attention that is not an error.
 */
data class AetherPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val brand: Color,
    val onBrand: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val warm: Color,
    /** Background of the outgoing chat bubble. */
    val bubbleOut: Color,
    val onBubbleOut: Color,
    /** Background of an incoming chat bubble. */
    val bubbleIn: Color
)

val DarkPalette = AetherPalette(
    background = Color(0xFF111317),
    surface = Color(0xFF1A1D23),
    surfaceRaised = Color(0xFF23272E),
    border = Color(0xFF2F343D),
    textPrimary = Color(0xFFE8EAEF),
    textMuted = Color(0xFF9AA2AF),
    brand = Color(0xFF3DD6C3),
    onBrand = Color(0xFF042420),
    success = Color(0xFF5BD98A),
    warning = Color(0xFFF5B83D),
    error = Color(0xFFF2727A),
    info = Color(0xFF6AAEF7),
    warm = Color(0xFFF59A57),
    bubbleOut = Color(0xFF1F5C55),
    onBubbleOut = Color(0xFFE9FBF8),
    bubbleIn = Color(0xFF262A32)
)

val LightPalette = AetherPalette(
    background = Color(0xFFF3F4F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFECEEF2),
    border = Color(0xFFD8DCE3),
    textPrimary = Color(0xFF161A20),
    textMuted = Color(0xFF5A6371),
    brand = Color(0xFF0B8A7E),
    onBrand = Color(0xFFFFFFFF),
    success = Color(0xFF16924A),
    warning = Color(0xFFB86E00),
    error = Color(0xFFCC2F3A),
    info = Color(0xFF2461C7),
    warm = Color(0xFFC75A12),
    bubbleOut = Color(0xFF0B8A7E),
    onBubbleOut = Color(0xFFFFFFFF),
    bubbleIn = Color(0xFFE6E9EE)
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

/** Brand accent: selection, primary actions, links, section titles. */
val AccentCyan: Color get() = activePalette.brand

/** Text and icons drawn on top of the brand accent. */
val OnAccent: Color get() = activePalette.onBrand

/** Success, good signal, full battery, delivered. */
val AccentMint: Color get() = activePalette.success

/** Errors and destructive actions. */
val AccentRed: Color get() = activePalette.error

/** Warnings and in-between states. */
val AccentAmber: Color get() = activePalette.warning

/** Warm attention that is not an error (radio, unread). */
val AccentOrange: Color get() = activePalette.warm

/** Informational blue (hops, routing detail). */
val AccentSteel: Color get() = activePalette.info

val AccentCyanDim: Color get() = activePalette.brand.copy(alpha = 0.16f)
val AccentMintDim: Color get() = activePalette.success.copy(alpha = 0.16f)
val AccentSteelDim: Color get() = activePalette.info.copy(alpha = 0.16f)
val AccentOrangeDim: Color get() = activePalette.warm.copy(alpha = 0.16f)

val BubbleOutgoing: Color get() = activePalette.bubbleOut
val OnBubbleOutgoing: Color get() = activePalette.onBubbleOut
val BubbleIncoming: Color get() = activePalette.bubbleIn

/** Readable text colour for an arbitrary fill such as a node badge. */
fun contentColorFor(fill: Color): Color =
    if (fill.luminance() > 0.42f) Color(0xFF14171C) else Color(0xFFF7F8FA)

fun batteryLevelColor(level: Int): Color {
    return when {
        level < 0 -> TextMuted
        level <= 20 -> AccentRed
        level <= 50 -> AccentAmber
        else -> AccentMint
    }
}

fun signalBandColor(band: SignalBand): Color = when (band) {
    SignalBand.STRONG -> AccentMint
    SignalBand.GOOD -> AccentCyan
    SignalBand.FAIR -> AccentAmber
    SignalBand.WEAK -> AccentRed
    SignalBand.NONE -> TextMuted
}

/** Flat app background. Kept as a Brush so callers need not change. */
fun appBackgroundBrush(): Brush = Brush.verticalGradient(
    colors = listOf(activePalette.background, activePalette.background)
)

/** Flat header background, level with the screen beneath it. */
fun headerBarBrush(): Brush = Brush.verticalGradient(
    colors = listOf(activePalette.background, activePalette.background)
)

fun cardTopStripeBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(activePalette.brand, activePalette.brand)
)

fun primaryButtonBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(activePalette.brand, activePalette.brand)
)
