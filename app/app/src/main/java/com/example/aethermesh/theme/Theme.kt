package com.example.aethermesh.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

/** Dark ink on bright azure / lime controls. */
private val ColorOnBright = Color(0xFF061018)

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = ColorOnBright,
    secondary = AccentMint,
    onSecondary = ColorOnBright,
    tertiary = AccentSteel,
    onTertiary = ColorOnBright,
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
    onPrimary = ColorOnBright,
    secondary = AccentMint,
    onSecondary = ColorOnBright,
    tertiary = AccentSteel,
    onTertiary = ColorOnBright,
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
