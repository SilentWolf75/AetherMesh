package com.example.aethermesh.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

/** Dark text on ember buttons for contrast. */
private val ColorOnEmber = Color(0xFF1A1008)

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = ColorOnEmber,
    secondary = AccentMint,
    onSecondary = DarkPalette.background,
    tertiary = AccentSteel,
    onTertiary = DarkPalette.background,
    background = DarkPalette.background,
    onBackground = DarkPalette.textPrimary,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.textPrimary,
    surfaceVariant = DarkPalette.surfaceRaised,
    onSurfaceVariant = DarkPalette.textMuted,
    outline = DarkPalette.border,
    error = AccentRed,
    onError = DarkPalette.textPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = AccentCyan,
    onPrimary = ColorOnEmber,
    secondary = AccentMint,
    onSecondary = LightPalette.background,
    tertiary = AccentSteel,
    onTertiary = LightPalette.background,
    background = LightPalette.background,
    onBackground = LightPalette.textPrimary,
    surface = LightPalette.surface,
    onSurface = LightPalette.textPrimary,
    surfaceVariant = LightPalette.surfaceRaised,
    onSurfaceVariant = LightPalette.textMuted,
    outline = LightPalette.border,
    error = AccentRed,
    onError = LightPalette.textPrimary
)

@Composable
fun AetherMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    SideEffect {
        setAetherPalette(darkTheme)
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
