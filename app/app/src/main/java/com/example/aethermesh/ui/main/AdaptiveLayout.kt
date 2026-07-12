package com.example.aethermesh.ui.main

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Simple width breakpoints (Material-style) without an extra Adaptive library dependency.
 * Compact: phones portrait / small devices
 * Medium: large phones landscape, small tablets
 * Expanded: tablets / foldables unfolded
 */
enum class AppWidthSizeClass {
    Compact,
    Medium,
    Expanded
}

data class AdaptiveLayoutInfo(
    val widthSizeClass: AppWidthSizeClass,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    /** Prefer a side navigation rail instead of the bottom tab bar. */
    val useNavigationRail: Boolean,
    /** List + detail side-by-side (chat, settings, nodes). */
    val useTwoPane: Boolean,
    /** Cap readable content width on large screens (forms, lists, settings). */
    val contentMaxWidth: Dp,
    val horizontalPadding: Dp,
    val isLandscape: Boolean
)

@Composable
fun rememberAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    return remember(widthDp, heightDp) {
        val widthClass = when {
            widthDp < 600 -> AppWidthSizeClass.Compact
            widthDp < 840 -> AppWidthSizeClass.Medium
            else -> AppWidthSizeClass.Expanded
        }
        AdaptiveLayoutInfo(
            widthSizeClass = widthClass,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            useNavigationRail = widthDp >= 600,
            useTwoPane = widthDp >= 840,
            contentMaxWidth = when (widthClass) {
                AppWidthSizeClass.Compact -> Dp.Unspecified // fill width
                AppWidthSizeClass.Medium -> 840.dp
                AppWidthSizeClass.Expanded -> 1080.dp
            },
            horizontalPadding = when {
                widthDp < 360 -> 10.dp
                widthDp < 600 -> 16.dp
                widthDp < 840 -> 20.dp
                else -> 28.dp
            },
            isLandscape = widthDp > heightDp
        )
    }
}

/** Constrain wide layouts so forms/lists don't stretch edge-to-edge on tablets. */
fun Modifier.adaptiveContentWidth(info: AdaptiveLayoutInfo): Modifier {
    return if (info.contentMaxWidth == Dp.Unspecified) {
        this
    } else {
        this.widthIn(max = info.contentMaxWidth)
    }
}
