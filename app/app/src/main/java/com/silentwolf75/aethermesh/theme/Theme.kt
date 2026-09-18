package com.silentwolf75.aethermesh.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.dp

private fun AetherPalette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = brand,
        onPrimary = onBrand,
        primaryContainer = brand.copy(alpha = 0.18f).compositeOver(surface),
        onPrimaryContainer = textPrimary,
        secondary = info,
        onSecondary = onBrand,
        secondaryContainer = surfaceRaised,
        onSecondaryContainer = textPrimary,
        tertiary = success,
        onTertiary = onBrand,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textMuted,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
        outline = border,
        outlineVariant = border,
        error = error,
        onError = onBrand,
        inverseSurface = textPrimary,
        inverseOnSurface = background
    )
}

private fun androidx.compose.ui.graphics.Color.compositeOver(
    background: androidx.compose.ui.graphics.Color
): androidx.compose.ui.graphics.Color {
    val a = alpha
    return androidx.compose.ui.graphics.Color(
        red = red * a + background.red * (1f - a),
        green = green * a + background.green * (1f - a),
        blue = blue * a + background.blue * (1f - a),
        alpha = 1f
    )
}

val AetherShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
        colorScheme = (if (darkTheme) DarkPalette else LightPalette).toColorScheme(darkTheme),
        typography = Typography,
        shapes = AetherShapes,
        content = content
    )
}
