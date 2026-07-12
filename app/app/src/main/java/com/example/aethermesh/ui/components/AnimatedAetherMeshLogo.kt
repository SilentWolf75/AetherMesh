package com.example.aethermesh.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AetherMesh brand mark with signal-arc pulse matching the web flasher logo animation.
 */
@Composable
fun AnimatedAetherMeshLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val transition = rememberInfiniteTransition(label = "aetherLogo")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logoPhase"
    )

    // Inner peaks ~30% into the cycle; outer ~60% (web-flasher CSS keyframes).
    val innerPulse = signalPulse(t, peakAt = 0.30f)
    val outerPulse = signalPulse(t, peakAt = 0.60f)

    val outerWave = remember { PathParser().parsePathString("M38,20 A20,20 0 0,1 70,20").toPath() }
    val innerWave = remember { PathParser().parsePathString("M45,26 A12,12 0 0,1 63,26").toPath() }
    val peak = remember { PathParser().parsePathString("M32,76 L54,34 L76,76").toPath() }
    val crossbar = remember { PathParser().parsePathString("M40,62 L68,62").toPath() }
    val stem = remember { PathParser().parsePathString("M54,34 L54,62").toPath() }

    Canvas(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
    ) {
        val scale = this.size.minDimension / 108f

        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF070C14), Color(0xFF142136), Color(0xFF1A2D4A)),
                    start = Offset.Zero,
                    end = Offset(108f, 108f)
                ),
                size = androidx.compose.ui.geometry.Size(108f, 108f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
            )

            val outerAlpha = 0.15f + outerPulse * 0.85f
            val outerWidth = 3.5f + outerPulse * 1.5f
            drawPath(
                path = outerWave,
                color = lerpColor(Color(0xFF0088CC), Color(0xFF00FFFF), outerPulse).copy(alpha = outerAlpha),
                style = Stroke(width = outerWidth, cap = StrokeCap.Round)
            )

            val innerAlpha = 0.15f + innerPulse * 0.85f
            val innerWidth = 3f + innerPulse * 1.5f
            drawPath(
                path = innerWave,
                color = lerpColor(Color(0xFF0088CC), Color(0xFF00D5FF), innerPulse).copy(alpha = innerAlpha),
                style = Stroke(width = innerWidth, cap = StrokeCap.Round)
            )

            drawPath(
                path = peak,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF0072FF)),
                    start = Offset(32f, 34f),
                    end = Offset(76f, 76f)
                ),
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = crossbar,
                color = Color(0xFF00B0FF),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
            drawPath(
                path = stem,
                color = Color(0xFF00E5FF),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            drawCircle(Color.White, radius = 5f, center = Offset(54f, 34f))
            drawCircle(Color(0xFF00E5FF), radius = 5f, center = Offset(32f, 76f))
            drawCircle(Color(0xFF00E5FF), radius = 5f, center = Offset(76f, 76f))
            drawCircle(Color.White, radius = 4f, center = Offset(54f, 62f))
        }
    }
}

private fun signalPulse(t: Float, peakAt: Float): Float {
    val dist = kotlin.math.abs(t - peakAt)
    val width = 0.28f
    return (1f - (dist / width).coerceIn(0f, 1f)).let { x ->
        x * x * (3f - 2f * x)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * u,
        green = a.green + (b.green - a.green) * u,
        blue = a.blue + (b.blue - a.blue) * u,
        alpha = 1f
    )
}
