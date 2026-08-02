package com.example.aethermesh.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
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
/** Light online basemap — avoid tile.openstreetmap.org (MAPNIK), which 403s many apps. */
fun cartoVoyagerTileSource(): XYTileSource = XYTileSource(
    "CartoVoyager",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

fun cartoDarkTileSource(): XYTileSource = XYTileSource(
    "CartoDarkMatter",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

/** Bearing in degrees [0, 360) from point A to B. */
fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

fun offsetGeoPoint(point: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
    val earth = 6_378_137.0
    val lat1 = Math.toRadians(point.latitude)
    val lon1 = Math.toRadians(point.longitude)
    val brng = Math.toRadians(bearingDeg)
    val ang = meters / earth
    val lat2 = Math.asin(
        Math.sin(lat1) * Math.cos(ang) + Math.cos(lat1) * Math.sin(ang) * Math.cos(brng)
    )
    val lon2 = lon1 + Math.atan2(
        Math.sin(brng) * Math.sin(ang) * Math.cos(lat1),
        Math.cos(ang) - Math.sin(lat1) * Math.sin(lat2)
    )
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

/** Shift a polyline sideways so forward/return don't paint on the same pixels. */
fun parallelOffsetPath(points: List<GeoPoint>, offsetMeters: Double): List<GeoPoint> {
    if (points.size < 2 || offsetMeters == 0.0) return points
    return points.mapIndexed { i, p ->
        val bearing = when (i) {
            0 -> bearingDegrees(points[0], points[1])
            points.lastIndex -> bearingDegrees(points[i - 1], points[i])
            else -> {
                val b1 = bearingDegrees(points[i - 1], points[i])
                val b2 = bearingDegrees(points[i], points[i + 1])
                val x = Math.cos(Math.toRadians(b1)) + Math.cos(Math.toRadians(b2))
                val y = Math.sin(Math.toRadians(b1)) + Math.sin(Math.toRadians(b2))
                (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
            }
        }
        offsetGeoPoint(p, bearing + 90.0, offsetMeters)
    }
}

/**
 * Keep endpoints pinned to node badges; bow midpoints sideways so overlapping
 * forward/return paths both stay visible without missing the markers.
 */
fun bowedRoutePath(points: List<GeoPoint>, offsetMeters: Double): List<GeoPoint> {
    if (points.size < 2 || offsetMeters == 0.0) return points
    val out = ArrayList<GeoPoint>(points.size * 2)
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        out.add(a)
        val mid = GeoPoint((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)
        out.add(offsetGeoPoint(mid, bearingDegrees(a, b) + 90.0, offsetMeters))
    }
    out.add(points.last())
    return out
}

/** Only accept a new map pin when it moved far enough — kills GPS jitter. */
fun stabilizeMapPoint(
    cache: MutableMap<Long, GeoPoint>,
    id: Long,
    candidate: GeoPoint,
    thresholdMeters: Double
): GeoPoint {
    val prev = cache[id] ?: run {
        cache[id] = candidate
        return candidate
    }
    val movedM = calculateDistance(
        prev.latitude, prev.longitude,
        candidate.latitude, candidate.longitude
    ) * 1000.0
    if (movedM >= thresholdMeters) {
        cache[id] = candidate
        return candidate
    }
    return prev
}

/** Resolve the BLE-connected radio in the node list despite provisional ID mismatches. */
/** True when two IDs refer to the same node (full 32-bit or BLE-name 16-bit form). */
fun sameMeshNodeId(a: Long, b: Long): Boolean {
    if (a == 0L || b == 0L) return false
    val a32 = a and 0xFFFFFFFFL
    val b32 = b and 0xFFFFFFFFL
    if (a32 == b32) return true
    // Pre-auth BLE often only knows the 16-bit suffix from "AetherMesh-XXXX".
    return (a32 and 0xFFFFL) == (b32 and 0xFFFFL)
}

fun resolveConnectedMeshNode(
    nodes: List<MeshNode>,
    connectedId: Long,
    deviceName: String?
): MeshNode? {
    if (connectedId != 0L) {
        nodes.find { sameMeshNodeId(it.nodeId, connectedId) }?.let { return it }
    }
    val name = deviceName?.trim().orEmpty()
    if (name.isNotEmpty()) {
        nodes.find { it.name.equals(name, ignoreCase = true) }?.let { return it }
        val short = getShortName(name, connectedId)
        nodes.find {
            it.shortName.equals(short, ignoreCase = true) ||
                it.name.contains(name, ignoreCase = true)
        }?.let { return it }
    }
    return null
}

fun routePathLengthMeters(points: List<GeoPoint>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 0 until points.lastIndex) {
        total += calculateDistance(
            points[i].latitude, points[i].longitude,
            points[i + 1].latitude, points[i + 1].longitude
        ) * 1000.0
    }
    return total
}

fun addCasedRoutePolyline(
    mapView: MapView,
    points: List<GeoPoint>,
    color: Int,
    strokeDp: Float,
    dashed: Boolean
) {
    if (points.size < 2) return
    val caseWidth = strokeDp + 4f * mapView.context.resources.displayMetrics.density
    mapView.overlays.add(Polyline(mapView).apply {
        outlinePaint.apply {
            isAntiAlias = true
            this.color = 0xE60B1220.toInt()
            strokeWidth = caseWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(strokeDp * 3.2f, strokeDp * 2.2f), 0f)
        }
        setPoints(points)
    })
    mapView.overlays.add(Polyline(mapView).apply {
        outlinePaint.apply {
            isAntiAlias = true
            this.color = color
            strokeWidth = strokeDp
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(strokeDp * 3.2f, strokeDp * 2.2f), 0f)
        }
        setPoints(points)
    })
}

fun createRouteChevronDrawable(context: Context, colorInt: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (18f * density).toInt().coerceAtLeast(18)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        color = colorInt
        style = Paint.Style.FILL
    }
    val stroke = Paint().apply {
        isAntiAlias = true
        color = 0xE60B1220.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeJoin = Paint.Join.ROUND
    }
    val path = android.graphics.Path().apply {
        moveTo(size * 0.18f, size * 0.22f)
        lineTo(size * 0.82f, size * 0.50f)
        lineTo(size * 0.18f, size * 0.78f)
        close()
    }
    canvas.drawPath(path, paint)
    canvas.drawPath(path, stroke)
    return BitmapDrawable(context.resources, bitmap)
}

fun addRouteDirectionChevrons(
    mapView: MapView,
    context: Context,
    points: List<GeoPoint>,
    color: Int
) {
    if (points.size < 2) return
    val icon = createRouteChevronDrawable(context, color)
    points.zipWithNext().forEach { (from, to) ->
        val segM = calculateDistance(from.latitude, from.longitude, to.latitude, to.longitude) * 1000.0
        if (segM < 3.0) return@forEach
        val mid = GeoPoint(
            (from.latitude + to.latitude) / 2.0,
            (from.longitude + to.longitude) / 2.0
        )
        val bearing = bearingDegrees(from, to).toFloat()
        mapView.overlays.add(Marker(mapView).apply {
            position = mid
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
            title = null
            snippet = null
            setInfoWindow(null)
            this.icon = icon
            rotation = bearing
            setFlat(true)
            setOnMarkerClickListener { _, _ -> true }
        })
    }
}

// Blue "you are here" dot for the phone's GPS position (the stock osmdroid
// person icon is suppressed in the MyLocation overlay)
fun createPhoneDotDrawable(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (28f * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val c = size / 2f
    val halo = Paint().apply {
        isAntiAlias = true
        color = 0x333B82F6
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, c, halo)
    val ring = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, 8f * density, ring)
    val dot = Paint().apply {
        isAntiAlias = true
        color = 0xFF3B82F6.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, 6f * density, dot)
    return BitmapDrawable(context.resources, bitmap)
}

fun createBadgeMarkerDrawable(
    context: Context,
    label: String,
    colorInt: Int,
    isActive: Boolean = true,
    isPingMarker: Boolean = false
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    
    val textPaint = Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    
    val textWidth = textPaint.measureText(label)
    
    // Pill dimensions (compact and clean)
    val pillWidth = (textWidth + 16f * density).coerceAtLeast(48f * density)
    val pillHeight = 24f * density
    
    val sizeW = pillWidth.toInt() + (8f * density).toInt()
    val sizeH = pillHeight.toInt() + (8f * density).toInt()
    
    val bitmap = Bitmap.createBitmap(sizeW, sizeH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    
    val cx = sizeW / 2f
    val cy = sizeH / 2f
    
    val left = cx - pillWidth / 2f
    val right = cx + pillWidth / 2f
    val top = cy - pillHeight / 2f
    val bottom = cy + pillHeight / 2f
    val pillRect = RectF(left, top, right, bottom)
    
    val pillBgPaint = Paint().apply {
        isAntiAlias = true
        this.color = colorInt
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(pillRect, 6f * density, 6f * density, pillBgPaint)
    
    val pillBorderPaint = Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    canvas.drawRoundRect(pillRect, 6f * density, 6f * density, pillBorderPaint)
    
    // Draw text centered
    val textRect = Rect()
    textPaint.getTextBounds(label, 0, label.length, textRect)
    val textY = cy - textRect.exactCenterY()
    canvas.drawText(label, cx, textY, textPaint)
    
    return BitmapDrawable(context.resources, bitmap)
}


