package com.silentwolf75.aethermesh.ui.components

import com.silentwolf75.aethermesh.theme.contentColorFor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentwolf75.aethermesh.theme.AccentAmber
import com.silentwolf75.aethermesh.theme.AccentCyan
import com.silentwolf75.aethermesh.theme.AccentCyanDim
import com.silentwolf75.aethermesh.theme.AccentMint
import com.silentwolf75.aethermesh.theme.AccentMintDim
import com.silentwolf75.aethermesh.theme.AccentOrange
import com.silentwolf75.aethermesh.theme.AccentRed
import com.silentwolf75.aethermesh.theme.AccentSteel
import com.silentwolf75.aethermesh.theme.BorderDark
import com.silentwolf75.aethermesh.theme.DarkBackground
import com.silentwolf75.aethermesh.theme.SectionHeaderStyle
import com.silentwolf75.aethermesh.theme.SurfaceDark
import com.silentwolf75.aethermesh.theme.SurfaceRaised
import com.silentwolf75.aethermesh.theme.TextLight
import com.silentwolf75.aethermesh.theme.TextMuted
import com.silentwolf75.aethermesh.data.SignalQualityPolicy
import com.silentwolf75.aethermesh.theme.batteryLevelColor
import com.silentwolf75.aethermesh.theme.signalBandColor
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun AetherSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (trailing != null) Arrangement.SpaceBetween else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = SectionHeaderStyle,
                color = AccentCyan
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceRaised)
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text(trailing, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AetherCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    accentStripe: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = shape,
        border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.55f)),
        modifier = if (onClick != null) {
            modifier.fillMaxWidth().clickable(onClick = onClick)
        } else {
            modifier.fillMaxWidth()
        }
    ) {
        Column {
            if (accentStripe) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(listOf(AccentCyan, AccentMint, AccentSteel))
                        )
                )
            }
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun AetherListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f), content = content)
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

@Composable
fun NodeBadge(
    shortName: String,
    color: Color,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    hops: Int? = null
) {
    Box(modifier = modifier.padding(2.dp)) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (muted) color.copy(alpha = 0.40f) else color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = shortName,
                color = contentColorFor(color),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        if (hops != null && hops > 0 && !muted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (hops == 1) AccentMint else AccentSteel)
                    .border(BorderStroke(1.dp, DarkBackground), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (hops > 9) "9+" else "$hops",
                    color = contentColorFor(if (hops == 1) AccentMint else AccentSteel),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun IconWell(
    icon: ImageVector,
    tint: Color = AccentCyan,
    well: Color = AccentCyanDim,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(well),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun StatusChip(
    text: String,
    background: Color = AccentOrange,
    contentColor: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun aetherTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = DarkBackground,
    unfocusedContainerColor = DarkBackground,
    disabledContainerColor = DarkBackground,
    focusedTextColor = TextLight,
    unfocusedTextColor = TextLight,
    disabledTextColor = TextMuted,
    cursorColor = AccentCyan,
    focusedIndicatorColor = AccentCyan,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted
)

@Composable
fun aetherFilledFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceRaised,
    unfocusedContainerColor = SurfaceDark,
    focusedTextColor = TextLight,
    unfocusedTextColor = TextLight,
    cursorColor = AccentCyan,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted
)

@Composable
fun SecureChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccentMintDim)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("Secure", color = AccentMint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ExpandableSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRaised)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = SectionHeaderStyle, color = AccentCyan)
            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceRaised)
                        .padding(horizontal = 9.dp, vertical = 2.dp)
                ) {
                    Text(badge, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            if (expanded) "Hide" else "Show",
            color = TextMuted,
            fontSize = 12.sp
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun PulseDot(
    active: Boolean,
    activeColor: Color = AccentMint,
    inactiveColor: Color = AccentRed,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = 0.25f))
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) activeColor else inactiveColor)
        )
    }
}

/** Concentric radar rings for empty / pairing states. */
@Composable
fun RadarGraphic(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    sweep: Color = AccentCyan,
    ring: Color = AccentSteel
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val c = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2f
            listOf(0.35f, 0.58f, 0.82f, 1.0f).forEach { f ->
                drawCircle(
                    color = ring.copy(alpha = 0.18f + (1f - f) * 0.12f),
                    radius = maxR * f,
                    center = c,
                    style = Stroke(width = 2f)
                )
            }
            drawArc(
                color = sweep.copy(alpha = 0.22f),
                startAngle = -70f,
                sweepAngle = 55f,
                useCenter = true,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - maxR, c.y - maxR),
                size = androidx.compose.ui.geometry.Size(maxR * 2, maxR * 2)
            )
            drawCircle(color = sweep, radius = 5f, center = c)
            drawLine(
                color = ring.copy(alpha = 0.35f),
                start = androidx.compose.ui.geometry.Offset(c.x - maxR, c.y),
                end = androidx.compose.ui.geometry.Offset(c.x + maxR, c.y),
                strokeWidth = 1.5f
            )
            drawLine(
                color = ring.copy(alpha = 0.35f),
                start = androidx.compose.ui.geometry.Offset(c.x, c.y - maxR),
                end = androidx.compose.ui.geometry.Offset(c.x, c.y + maxR),
                strokeWidth = 1.5f
            )
        }
    }
}

/** Semi-circle battery / power gauge. */
@Composable
fun BatteryArcGauge(
    level: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    charging: Boolean = false
) {
    val color = batteryLevelColor(level.coerceIn(0, 100))
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 7.dp.toPx()
            val pad = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(pad, pad)
            drawArc(
                color = BorderDark,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val sweep = 240f * (level.coerceIn(0, 100) / 100f)
            drawArc(
                color = color,
                startAngle = 150f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${level.coerceAtLeast(0)}%",
                color = TextLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (charging) {
                Text("CHG", color = AccentAmber, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GraphicStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SignalBars(
    rssi: Float,
    modifier: Modifier = Modifier
) {
    val barsCount = SignalQualityPolicy.barsFromRssi(rssi)
    val barColor = signalBandColor(SignalQualityPolicy.bandFromRssi(rssi))
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.height(11.dp)
    ) {
        for (i in 1..4) {
            val barHeight = (i * 2.5).dp
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= barsCount) barColor else BorderDark)
            )
        }
    }
}

@Composable
fun SnrMeter(
    snr: Float,
    modifier: Modifier = Modifier
) {
    val fill = SignalQualityPolicy.snrFillFraction(snr)
    val color = signalBandColor(SignalQualityPolicy.bandFromSnr(snr))
    Box(
        modifier = modifier
            .width(28.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(BorderDark)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fill)
                .height(6.dp)
                .background(color)
        )
    }
}

@Composable
fun HopChip(
    hops: Int,
    modifier: Modifier = Modifier,
    muted: Boolean = false
) {
    val accent = when {
        muted -> TextMuted
        hops == 1 -> AccentMint
        else -> AccentSteel
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            if (hops == 1) "DIR" else "${hops}H",
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Fillable battery body — not the always-full Material icon. */
@Composable
fun BatteryMeter(
    level: Int,
    modifier: Modifier = Modifier,
    charging: Boolean = false,
    unknown: Boolean = false,
    size: Dp = 18.dp
) {
    val color = when {
        unknown -> TextMuted
        else -> batteryLevelColor(level.coerceIn(0, 100))
    }
    val fill = if (unknown) 0f else (level.coerceIn(0, 100) / 100f)
    Canvas(modifier = modifier.size(width = size, height = size * 0.62f)) {
        val stroke = 1.4.dp.toPx()
        val nubW = this.size.width * 0.10f
        val nubH = this.size.height * 0.38f
        val body = androidx.compose.ui.geometry.Size(this.size.width - nubW - stroke, this.size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f)
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = body,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(topLeft.x + body.width, this.size.height * 0.31f),
            size = androidx.compose.ui.geometry.Size(nubW, nubH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )
        if (fill > 0f) {
            val pad = stroke + 1.2.dp.toPx()
            val innerW = (body.width - pad * 2f).coerceAtLeast(0f) * fill
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(topLeft.x + pad, topLeft.y + pad),
                size = androidx.compose.ui.geometry.Size(innerW, (body.height - pad * 2f).coerceAtLeast(0f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
        if (charging) {
            val w = this.size.width
            val h = this.size.height
            val bolt = Path().apply {
                moveTo(w * 0.42f, h * 0.12f)
                lineTo(w * 0.30f, h * 0.55f)
                lineTo(w * 0.46f, h * 0.55f)
                lineTo(w * 0.36f, h * 0.90f)
                lineTo(w * 0.58f, h * 0.42f)
                lineTo(w * 0.42f, h * 0.42f)
                close()
            }
            drawPath(bolt, color = AccentAmber)
        }
    }
}

@Composable
fun DeliveryTicks(
    status: String,
    modifier: Modifier = Modifier,
    color: Color = AccentCyan,
    channelWaiting: Boolean = false
) {
    Canvas(modifier = modifier.size(12.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        when (status) {
            "DELIVERED" -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.55f), androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.82f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.82f), androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.22f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.55f), androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.82f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.82f), androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.18f), stroke.width, cap = StrokeCap.Round)
            }
            "HEARD" -> {
                drawCircle(color, radius = size.minDimension * 0.32f, center = center, style = stroke)
                drawCircle(color, radius = size.minDimension * 0.12f, center = center)
            }
            "FAILED", "EXPIRED" -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.22f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.78f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.22f), androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.78f), stroke.width, cap = StrokeCap.Round)
            }
            "QUEUED" -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.30f), androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.30f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.50f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.70f), androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.70f), stroke.width, cap = StrokeCap.Round)
            }
            "PENDING", "SENT" -> {
                val waiting = status == "PENDING" || channelWaiting
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.10f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.50f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.28f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.50f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.72f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.50f), stroke.width, cap = StrokeCap.Round)
                if (waiting) {
                    drawCircle(color.copy(alpha = 0.7f), radius = 1.6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.50f))
                }
            }
            else -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.10f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.50f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.28f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.50f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.72f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.50f), stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * One compact reading: a small icon and a value, optionally tinted. Used in
 * rows of node metrics so every figure carries its own meaning at a glance.
 */
@Composable
fun MetricPill(
    icon: ImageVector?,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = TextMuted,
    textColor: Color = TextLight,
    label: String? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceRaised.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
        }
        if (label != null) {
            Text(label, color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Small icon + muted text used in card footers (hardware, role, id). */
@Composable
fun FooterFact(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text,
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
