package com.example.aethermesh.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aethermesh.data.ChatMessage
import com.example.aethermesh.data.ChannelConfig
import com.example.aethermesh.data.MeshNode
import com.example.aethermesh.data.TraceRouteState
import com.example.aethermesh.ui.AppUiFeedback
import com.example.aethermesh.ui.components.*
import com.example.aethermesh.theme.AccentCyanDim
import com.example.aethermesh.theme.AccentSteel
import com.example.aethermesh.theme.AccentSteelDim
import com.example.aethermesh.theme.appBackgroundBrush
import com.example.aethermesh.theme.headerBarBrush
import com.example.aethermesh.theme.primaryButtonBrush
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@Composable
fun DiagnosticCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (compact) DarkBackground else SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        border = if (compact) BorderStroke(1.dp, BorderDark) else null
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = TextMuted, fontSize = if (compact) 10.sp else 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                color = color,
                fontSize = if (compact) 16.sp else 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

data class RadioProfile(val label: String, val sf: Int, val bw: Float, val hint: String)

val RADIO_PROFILES = listOf(
    RadioProfile("Fast", 9, 125f, "Baseline. Quick messages, shortest range."),
    RadioProfile("Balanced", 10, 125f, "+2.5 dB range vs Fast, 2x airtime."),
    RadioProfile("Long range", 11, 125f, "+5 dB range vs Fast, 4x airtime."),
    RadioProfile("Max range", 12, 125f, "+7.5 dB range vs Fast, 8x airtime. Use 10s+ ping intervals.")
)

fun radioProfileLabel(sf: Int): String =
    RADIO_PROFILES.firstOrNull { it.sf == sf }?.label ?: if (sf in 7..12) "SF$sf" else "Unknown"

fun radioRegionLabel(region: Int): String = when (region) {
    0 -> "US915"
    1 -> "EU868"
    else -> "Unknown"
}

@Composable
fun RadioProfileChips(currentSf: Int, currentBw: Float, onSelect: (RadioProfile) -> Unit) {
    val selectedProfile = RADIO_PROFILES.firstOrNull { it.sf == currentSf && it.bw == currentBw }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RADIO_PROFILES.forEach { p ->
            val isSel = selectedProfile == p
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) AccentCyan else DarkBackground)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    p.label,
                    color = if (isSel) SurfaceDark else TextLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        selectedProfile?.hint ?: "Custom SF/BW (not a preset)",
        color = TextMuted,
        fontSize = 10.sp
    )
    Text(
        "Every node must run the same profile - mismatched nodes can't hear each other.",
        color = Color(0xFFFACC15),
        fontSize = 10.sp
    )
}

