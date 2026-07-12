package com.example.aethermesh.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.aethermesh.theme.AccentCyan
import com.example.aethermesh.theme.AccentCyanDim
import com.example.aethermesh.theme.AccentMint
import com.example.aethermesh.theme.AccentMintDim
import com.example.aethermesh.theme.AccentOrange
import com.example.aethermesh.theme.AccentRed
import com.example.aethermesh.theme.AccentSteel
import com.example.aethermesh.theme.BorderDark
import com.example.aethermesh.theme.DarkBackground
import com.example.aethermesh.theme.SectionHeaderStyle
import com.example.aethermesh.theme.SurfaceDark
import com.example.aethermesh.theme.SurfaceRaised
import com.example.aethermesh.theme.TextLight
import com.example.aethermesh.theme.TextMuted

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
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(listOf(AccentCyan, AccentMint))
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = SectionHeaderStyle,
                color = AccentCyan
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentCyanDim)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(trailing, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    val shape = RoundedCornerShape(14.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = shape,
        border = BorderStroke(1.dp, BorderDark),
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
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
    muted: Boolean = false
) {
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (muted) color.copy(alpha = 0.45f) else color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = shortName,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
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
        Text("SECURE", color = AccentMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(AccentCyan, AccentSteel)))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(title.uppercase(), style = SectionHeaderStyle, color = AccentCyan)
            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentMintDim)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(badge, color = AccentMint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
