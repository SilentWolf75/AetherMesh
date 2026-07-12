package com.example.aethermesh.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = DarkPalette.background,
    secondary = AccentMint,
    onSecondary = DarkPalette.background,
    tertiary = AccentAmber,
    onTertiary = DarkPalette.background,
    background = DarkPalette.background,
    onBackground = DarkPalette.textPrimary,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.textPrimary,
    surfaceVariant = DarkPalette.border,
    onSurfaceVariant = DarkPalette.textMuted,
    outline = DarkPalette.border,
    error = AccentRed,
    onError = DarkPalette.textPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = AccentCyan,
    onPrimary = LightPalette.background,
    secondary = AccentMint,
    onSecondary = LightPalette.background,
    tertiary = AccentOrange,
    onTertiary = LightPalette.background,
    background = LightPalette.background,
    onBackground = LightPalette.textPrimary,
    surface = LightPalette.surface,
    onSurface = LightPalette.textPrimary,
    surfaceVariant = LightPalette.border,
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
