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
/** Matches firmware/app chat payload caps (UTF-8 bytes). Encrypted fits under GCM+base64. */
const val CHAT_MAX_PLAIN_UTF8_BYTES = 127
const val CHAT_MAX_ENCRYPTED_UTF8_BYTES = 76

private const val NODE_STALE_MS = 5 * 60 * 1000L

fun isNodeStale(lastActive: Long): Boolean {
    return System.currentTimeMillis() - lastActive > NODE_STALE_MS
}

/**
 * Relative last-heard label used across Nodes / Map / Chat / Details.
 * Prefer “just now” then “Xm ago” (not “Ns ago”) for a consistent feel.
 */
fun formatLastHeard(lastActive: Long, appLanguage: String = "English"): String {
    val spanish = appLanguage == "Spanish"
    if (lastActive <= 0L) return if (spanish) "nunca" else "never"
    val elapsedSeconds = ((System.currentTimeMillis() - lastActive).coerceAtLeast(0L)) / 1000L
    return when {
        elapsedSeconds < 60L -> if (spanish) "ahora" else "just now"
        elapsedSeconds < 3600L -> {
            val m = elapsedSeconds / 60L
            if (spanish) "hace ${m}m" else "${m}m ago"
        }
        elapsedSeconds < 86_400L -> {
            val h = elapsedSeconds / 3600L
            if (spanish) "hace ${h}h" else "${h}h ago"
        }
        else -> {
            val d = elapsedSeconds / 86_400L
            if (spanish) "hace ${d}d" else "${d}d ago"
        }
    }
}

/** Compact age for diagnostics tiles (same vocabulary as [formatLastHeard]). */
fun formatRelativeAge(timestampMs: Long, appLanguage: String = "English"): String {
    val spanish = appLanguage == "Spanish"
    if (timestampMs <= 0L) return if (spanish) "—" else "—"
    val elapsedSeconds = ((System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)) / 1000L
    return when {
        elapsedSeconds < 60L -> if (spanish) "ahora" else "just now"
        elapsedSeconds < 3600L -> {
            val m = elapsedSeconds / 60L
            if (spanish) "hace ${m}m" else "${m}m ago"
        }
        else -> {
            val h = (elapsedSeconds / 3600L).coerceAtLeast(1L)
            if (spanish) "hace ${h}h" else "${h}h ago"
        }
    }
}

/** Bumps so relative “last heard” labels stay fresh without new telemetry. */
@Composable
fun rememberRelativeTimeTick(intervalMs: Long = 30_000L): Long {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            tick = System.currentTimeMillis()
        }
    }
    return tick
}

fun formatUptime(seconds: Long, appLanguage: String = "English"): String {
    val spanish = appLanguage == "Spanish"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        d > 0 -> if (spanish) "${d} días ${h} h" else "${d}d ${h}h"
        h > 0 -> if (spanish) "${h} h ${m} m" else "${h}h ${m}m"
        m > 0 -> if (spanish) "${m} m ${s} s" else "${m}m ${s}s"
        else -> if (spanish) "${s} s" else "${s}s"
    }
}

/** Firmware low-voltage safe enter threshold (LiPo pack volts). Phone UI matches. */
const val LOW_VOLTAGE_SAFE_ENTER_V = 3.50f

fun isLowVoltageSafeHint(voltage: Float, isCharging: Boolean): Boolean {
    return voltage > 0f && voltage < LOW_VOLTAGE_SAFE_ENTER_V && !isCharging
}

/** Whole days since last heard (0 if within 24h or never). */
fun daysSinceHeard(lastActive: Long): Long {
    if (lastActive <= 0L) return 0L
    val elapsed = (System.currentTimeMillis() - lastActive).coerceAtLeast(0L)
    return elapsed / 86_400_000L
}

fun formatDaysSinceHeard(lastActive: Long, appLanguage: String = "English"): String? {
    val d = daysSinceHeard(lastActive)
    if (d < 1L) return null
    val spanish = appLanguage == "Spanish"
    return if (spanish) {
        if (d == 1L) "1 día sin oír" else "$d días sin oír"
    } else {
        if (d == 1L) "1 day since heard" else "$d days since heard"
    }
}

enum class VoltageTrend { UNKNOWN, RISING, FALLING, FLAT }

fun voltageTrend(history: List<com.example.aethermesh.data.TelemetrySample>): VoltageTrend {
    val samples = history.filter { it.voltage > 0f }
    if (samples.size < 2) return VoltageTrend.UNKNOWN
    val newest = samples.takeLast(minOf(12, samples.size))
    val first = newest.first().voltage
    val last = newest.last().voltage
    val delta = last - first
    return when {
        delta >= 0.04f -> VoltageTrend.RISING
        delta <= -0.04f -> VoltageTrend.FALLING
        else -> VoltageTrend.FLAT
    }
}

fun formatVoltageTrend(history: List<com.example.aethermesh.data.TelemetrySample>, appLanguage: String): String? {
    val spanish = appLanguage == "Spanish"
    val samples = history.filter { it.voltage > 0f }
    if (samples.size < 2) return null
    val newest = samples.takeLast(minOf(12, samples.size))
    val first = newest.first().voltage
    val last = newest.last().voltage
    val delta = last - first
    val arrow = when {
        delta >= 0.04f -> "↑"
        delta <= -0.04f -> "↓"
        else -> "→"
    }
    val label = when {
        delta >= 0.04f -> if (spanish) "subiendo" else "rising"
        delta <= -0.04f -> if (spanish) "bajando" else "falling"
        else -> if (spanish) "estable" else "flat"
    }
    return "$arrow ${"%.2f".format(last)} V ($label ${"%+.2f".format(delta)} V)"
}

fun formatGpsLockAge(lastPositionAt: Long, appLanguage: String = "English"): String {
    val spanish = appLanguage == "Spanish"
    if (lastPositionAt <= 0L) {
        return if (spanish) "Sin fijación GPS" else "No GPS lock yet"
    }
    val age = formatLastHeard(lastPositionAt, appLanguage)
    return if (spanish) "GPS: $age" else "GPS $age"
}

/** gps_mode: 0 on, 1 off, 2 duty. Returns null when prefs unknown. */
fun formatGpsDutyStatus(gpsMode: Int?, dutyIntervalSecs: Int, appLanguage: String = "English"): String? {
    if (gpsMode == null || gpsMode !in 0..2) return null
    val spanish = appLanguage == "Spanish"
    val mins = ((dutyIntervalSecs.coerceAtLeast(60) + 59) / 60)
    return when (gpsMode) {
        0 -> if (spanish) "GPS: siempre encendido" else "GPS: always on"
        1 -> if (spanish) "GPS: apagado" else "GPS: off"
        else -> if (spanish) "GPS: periódico (${mins} min)" else "GPS: duty (${mins} min)"
    }
}

fun readCachedGpsMode(context: android.content.Context, nodeId: Long): Pair<Int?, Int> {
    val prefs = context.getSharedPreferences("node_settings_$nodeId", android.content.Context.MODE_PRIVATE)
    val mode = if (prefs.contains("gps_mode")) prefs.getInt("gps_mode", 0).coerceIn(0, 2) else null
    val duty = prefs.getInt("gps_duty_interval_secs", 900).let {
        when {
            it <= 0 -> 900
            else -> it.coerceIn(300, 3600)
        }
    }
    return mode to duty
}

fun getInitials(name: String): String {
    if (name.isBlank()) return "??"
    val cleanName = name.replace("AetherMesh-", "").replace("Node ", "")
    val parts = cleanName.trim().split(Regex("\\s+"))
    return if (parts.size >= 2) {
        val first = parts[0].firstOrNull()?.uppercase() ?: ""
        val second = parts[1].firstOrNull()?.uppercase() ?: ""
        "$first$second"
    } else {
        cleanName.take(2).uppercase()
    }
}

fun getShortName(name: String, nodeId: Long): String {
    if (name.isBlank()) return String.format("%04X", (nodeId and 0xFFFF).toInt())
    val cleanName = name.replace("AetherMesh-", "").replace("Node ", "")
    val parts = cleanName.trim().split(Regex("\\s+"))
    if (parts.size >= 2) {
        val cleanParts = parts.map { it.replace(Regex("[^a-zA-Z0-9]"), "") }.filter { it.isNotEmpty() }
        if (cleanParts.size >= 2) {
            val build = cleanParts.map { it.first().uppercase() }.joinToString("")
            if (build.length >= 2) {
                return build.take(4)
            }
        }
    }
    val clean = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")
    if (clean.isNotEmpty()) {
        return clean.take(4).uppercase()
    }
    return String.format("%04X", (nodeId and 0xFFFF).toInt())
}

fun getBadgeColor(name: String): Color {
    val hash = name.hashCode()
    // Stay on the night-radar palette (no purple AI-slop).
    val colors = listOf(
        Color(0xFFFFB347), // Amber
        Color(0xFFC8F547), // Mint
        Color(0xFF4DA3FF), // Azure
        Color(0xFF7AD4FF), // Steel
        Color(0xFFFF8C42), // Orange
        Color(0xFF14B8A6), // Teal
        Color(0xFFFF5C7A)  // Coral
    )
    val index = Math.abs(hash) % colors.size
    return colors[index]
}





fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

/** Snap GPS duty interval to Settings chip values (5 / 15 / 30 / 60 min). */
fun snapGpsDutyIntervalSecs(secs: Int): Int {
    val options = intArrayOf(300, 900, 1800, 3600)
    val clamped = when {
        secs <= 0 -> 900
        else -> secs.coerceIn(300, 3600)
    }
    return options.minBy { kotlin.math.abs(it - clamped) }
}

fun hasValidPosition(latitude: Number, longitude: Number): Boolean {
    val lat = latitude.toDouble()
    val lon = longitude.toDouble()
    return lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 &&
        !(lat == 0.0 && lon == 0.0)
}

fun rangeTestFailureShort(reason: String?, appLanguage: String = "English"): String {
    val spanish = appLanguage == "Spanish"
    return when (reason) {
        "ble_send_fail" -> if (spanish) "fallo BLE" else "BLE fail"
        "auth_blocked" -> if (spanish) "auth" else "auth"
        "test_stopped" -> if (spanish) "detenido" else "stopped"
        "self_target" -> if (spanish) "mismo nodo" else "self"
        else -> if (spanish) "timeout" else "timeout"
    }
}

fun rangeTestFailureLabel(reason: String?, appLanguage: String = "English"): String {
    val spanish = appLanguage == "Spanish"
    return when (reason) {
        "ble_send_fail" -> if (spanish)
            "Fallo al escribir por BLE — revisa el enlace."
        else
            "BLE write failed — check the phone↔node link."
        "auth_blocked" -> if (spanish)
            "Bloqueado: autentica el dispositivo."
        else
            "Blocked — unlock/authenticate the device."
        "test_stopped" -> if (spanish)
            "Prueba detenida."
        else
            "Test stopped."
        "self_target" -> if (spanish)
            "Ese es el nodo conectado por BLE — conéctate a otro nodo para probar este."
        else
            "That's the BLE-connected node — connect to a different node to range-test this one."
        else -> if (spanish)
            "Sin respuesta (timeout)."
        else
            "No reply (timeout)."
    }
}

fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = Math.sin(dLon) * Math.cos(lat2Rad)
    val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
            Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon)
    var brng = Math.toDegrees(Math.atan2(y, x))
    brng = (brng + 360) % 360
    
    return when {
        brng >= 337.5 || brng < 22.5 -> "N"
        brng >= 22.5 && brng < 67.5 -> "NE"
        brng >= 67.5 && brng < 112.5 -> "E"
        brng >= 112.5 && brng < 157.5 -> "SE"
        brng >= 157.5 && brng < 202.5 -> "S"
        brng >= 202.5 && brng < 247.5 -> "SW"
        brng >= 247.5 && brng < 292.5 -> "W"
        else -> "NW"
    }
}


/** Share via chooser; safe from non-Activity contexts (avoids NEW_TASK crash). */
private fun startShareChooser(context: Context, intent: android.content.Intent, title: String) {
    val chooser = android.content.Intent.createChooser(intent, title)
    if (context !is android.app.Activity) {
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

fun exportRangeTestLogsToCsv(
    context: Context,
    logs: List<com.example.aethermesh.data.RangeTestLog>,
    nodePositions: Map<Long, Pair<Double, Double>> = emptyMap(),
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    if (logs.isEmpty()) {
        AppUiFeedback.show(if (spanish) "Aún no hay datos de prueba de rango." else "No range test data to export yet.", duration = SnackbarDuration.Short)
        return
    }

    // Machine-friendly CSV: epoch ms for tooling, ISO local time for humans,
    // raw lat/lon plus BOTH directions of the direct one-hop link:
    //   ping_* = signal of our ping as heard by the target (from the ACK payload)
    //   ack_*  = signal of the target's ACK as heard by our node
    //   distance_m = row GPS -> target node's last reported position
    // Signal columns are blank (not placeholder values) on timeouts/unreported.
    val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
    val csv = StringBuilder("timestamp_ms,datetime,target_id,latitude,longitude,distance_m,speed_mps,gps_accuracy_m,ping_rssi_dbm,ping_snr_db,ack_rssi_dbm,ack_snr_db,success,failure_reason\n")
    logs.forEach {
        val ackRssi = if (it.success) "${it.rssi}" else ""
        val ackSnr = if (it.success) "${it.snr}" else ""
        val pingRssi = it.remoteRssi?.toString() ?: ""
        val pingSnr = it.remoteSnr?.toString() ?: ""
        val speed = it.speedMps?.toString() ?: ""
        val accuracy = it.gpsAccuracyM?.toString() ?: ""
        val failure = if (it.success) "" else (it.failureReason ?: "timeout")
        val targetPos = nodePositions[it.targetId]
        val distance = if (targetPos != null && hasValidPosition(it.latitude, it.longitude) &&
            targetPos.first != 0.0 && targetPos.second != 0.0
        ) {
            (calculateDistance(it.latitude, it.longitude, targetPos.first, targetPos.second) * 1000).toInt().toString()
        } else ""
        csv.append("${it.timestamp},${iso.format(java.util.Date(it.timestamp))},0x${it.targetId.toString(16).uppercase()},${it.latitude},${it.longitude},$distance,$speed,$accuracy,$pingRssi,$pingSnr,$ackRssi,$ackSnr,${it.success},$failure\n")
    }

    try {
        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val file = java.io.File(exportDir, "aethermesh_rangetest_$stamp.csv")
        file.writeText(csv.toString())

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "AetherMesh Range Test Export")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startShareChooser(context, intent, if (spanish) "Exportar CSV de rango" else "Export Range Test CSV")
    } catch (e: Exception) {
        // Fall back to the clipboard if no app can take the file
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Range Test Logs", csv.toString()))
        AppUiFeedback.show(if (spanish) "No se pudo compartir (${e.message}); CSV copiado al portapapeles."
            else "Share failed (${e.message}); CSV copied to clipboard instead.", duration = SnackbarDuration.Long)
    }
}

fun exportMeshDiagnosticsToCsv(
    context: Context,
    snapshots: List<com.example.aethermesh.data.MeshDiagnosticsSnapshot>,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    if (snapshots.isEmpty()) {
        AppUiFeedback.show(if (spanish) "Aún no hay datos de salud del mesh." else "No mesh health data to export yet.", duration = SnackbarDuration.Short)
        return
    }
    val csv = StringBuilder(
        "timestamp_ms,tx_packets,tx_failures,rx_packets,relayed,retries,acked,ack_timeouts," +
            "duplicates,cad_busy,queue_drops,route_changes,active_routes,rebroadcast_depth," +
            "pending_ack_depth,airtime_ms,uptime_seconds,protocol_version," +
            "range_pings_rx,range_pongs_queued,range_pongs_sent,range_pong_tx_failures,quiet_mode," +
            "directed_relays,suppress_relays,flood_unicasts,rreq_sent,early_repairs\n"
    )
    snapshots.sortedBy { it.timestamp }.forEach { value ->
        csv.append(
            "${value.timestamp},${value.txPackets},${value.txFailures},${value.rxPackets}," +
                "${value.relayedPackets},${value.retries},${value.ackedPackets},${value.ackTimeouts}," +
                "${value.duplicatePackets},${value.cadBusyEvents},${value.queueDrops},${value.routeChanges}," +
                "${value.activeRoutes},${value.rebroadcastQueueDepth},${value.pendingAckDepth}," +
                "${value.airtimeMs},${value.uptimeSeconds},${value.protocolVersion}," +
                "${value.rangePingsRx},${value.rangePongsQueued},${value.rangePongsSent}," +
                "${value.rangePongTxFailures},${value.quietMode}," +
                "${value.directedRelays},${value.suppressRelays},${value.floodUnicasts}," +
                "${value.rreqSent},${value.earlyRepairs}\n"
        )
    }
    try {
        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val file = java.io.File(exportDir, "aethermesh_mesh_health_$stamp.csv")
        file.writeText(csv.toString())
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "AetherMesh Mesh Health Export")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startShareChooser(context, intent, if (spanish) "Exportar salud del mesh" else "Export Mesh Health CSV")
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Mesh Health", csv.toString()))
        AppUiFeedback.show(if (spanish) "No se pudo compartir; CSV copiado al portapapeles."
            else "Share failed; CSV copied to clipboard.", duration = SnackbarDuration.Long)
    }
}

/** One-tap plain-text snapshot of airtime / queue health (Phase J). */
fun shareMeshDiagnosticsSnapshotText(
    context: Context,
    snapshot: com.example.aethermesh.data.MeshDiagnosticsSnapshot?,
    queuedStoreForward: Int = 0,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    if (snapshot == null) {
        AppUiFeedback.show(
            if (spanish) "Aún no hay telemetría de salud del mesh."
            else "No mesh health telemetry yet.",
            duration = SnackbarDuration.Short
        )
        return
    }
    val text = buildString {
        appendLine("AetherMesh mesh health snapshot")
        appendLine("ts_ms=${snapshot.timestamp}")
        appendLine("tx=${snapshot.txPackets} rx=${snapshot.rxPackets} tx_fail=${snapshot.txFailures}")
        appendLine(
            "ack=${snapshot.ackedPackets} ack_timeout=${snapshot.ackTimeouts} " +
                "retries=${snapshot.retries} drops=${snapshot.queueDrops}"
        )
        appendLine(
            "airtime_ms=${snapshot.airtimeMs} uptime_s=${snapshot.uptimeSeconds} " +
                "rebroadcast_q=${snapshot.rebroadcastQueueDepth} pending_ack_q=${snapshot.pendingAckDepth}"
        )
        appendLine(
            "relayed=${snapshot.relayedPackets} routes=${snapshot.activeRoutes} " +
                "route_changes=${snapshot.routeChanges} quiet=${snapshot.quietMode}"
        )
        appendLine("queued_dm_store_forward=$queuedStoreForward")
        appendLine("proto_v=${snapshot.protocolVersion}")
    }
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "AetherMesh mesh health")
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startShareChooser(
            context,
            intent,
            if (spanish) "Compartir salud del mesh" else "Share mesh health"
        )
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Mesh Health", text))
        AppUiFeedback.show(
            if (spanish) "No se pudo compartir; texto copiado."
            else "Share failed; text copied.",
            duration = SnackbarDuration.Long
        )
    }
}

/** APRS-IS comment-style position line for paste into an external client (Phase I). */
fun shareAprsPositionTemplate(
    context: Context,
    callsign: String,
    nodeName: String,
    nodeId: Long,
    latitude: Float,
    longitude: Float,
    altitudeM: Int? = null,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    val cs = callsign.trim().uppercase(java.util.Locale.US).ifBlank { "NOCALL" }
    if (!hasValidPosition(latitude, longitude)) {
        AppUiFeedback.show(
            if (spanish) "Sin posición válida del nodo para APRS."
            else "No valid node position for APRS export.",
            duration = SnackbarDuration.Short
        )
        return
    }
    val lat = String.format(java.util.Locale.US, "%.5f", latitude)
    val lon = String.format(java.util.Locale.US, "%.5f", longitude)
    val altPart = altitudeM?.let { " alt=${it}m" } ?: ""
    val comment =
        "AetherMesh $nodeName 0x${nodeId.toString(16)} lat=$lat lon=$lon$altPart (not on-air APRS)"
    val line = "$cs>APRS,TCPIP*:$comment"
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "AetherMesh APRS comment")
            putExtra(android.content.Intent.EXTRA_TEXT, line)
        }
        startShareChooser(
            context,
            intent,
            if (spanish) "Compartir plantilla APRS" else "Share APRS template"
        )
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("APRS", line))
        AppUiFeedback.show(
            if (spanish) "Plantilla APRS copiada al portapapeles."
            else "APRS template copied to clipboard.",
            duration = SnackbarDuration.Short
        )
    }
}

/** Share one chat thread as plain text or CSV (NEW_TASK-safe chooser). */
fun exportThreadMessages(
    context: Context,
    messages: List<ChatMessage>,
    threadTitle: String,
    asCsv: Boolean,
    appLanguage: String = "English",
    nodeNames: Map<Long, String> = emptyMap()
) {
    val spanish = appLanguage == "Spanish"
    if (messages.isEmpty()) {
        AppUiFeedback.show(
            if (spanish) "No hay mensajes para exportar." else "No messages to export.",
            duration = SnackbarDuration.Short
        )
        return
    }
    try {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val safeTitle = threadTitle.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifEmpty { "thread" }
        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        if (asCsv) {
            val csv = StringBuilder("Timestamp,SenderId,SenderName,Content,Channel,Status,Encrypted\n")
            messages.forEach { msg ->
                val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(msg.timestamp))
                val name = nodeNames[msg.senderId]?.replace("\"", "\"\"") ?: ""
                csv.append(
                    "\"$date\",0x${msg.senderId.toString(16).uppercase()},\"$name\"," +
                        "\"${msg.content.replace("\"", "\"\"")}\",\"${msg.channel}\"," +
                        "\"${msg.status}\",${msg.isEncrypted}\n"
                )
            }
            val file = java.io.File(exportDir, "aethermesh_${safeTitle}_$stamp.csv")
            file.writeText(csv.toString())
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, threadTitle)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startShareChooser(
                context,
                intent,
                if (spanish) "Exportar hilo CSV" else "Export thread CSV"
            )
        } else {
            val body = StringBuilder()
            body.appendLine(threadTitle)
            body.appendLine("---")
            val timeFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            messages.forEach { msg ->
                val who = nodeNames[msg.senderId]
                    ?: "0x${msg.senderId.toString(16).uppercase()}"
                body.appendLine("[${timeFmt.format(java.util.Date(msg.timestamp))}] $who: ${msg.content}")
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, threadTitle)
                putExtra(android.content.Intent.EXTRA_TEXT, body.toString())
            }
            startShareChooser(
                context,
                intent,
                if (spanish) "Exportar hilo" else "Export thread"
            )
        }
    } catch (e: Exception) {
        AppUiFeedback.show(
            if (spanish) "Error al exportar: ${e.localizedMessage}"
            else "Export failed: ${e.localizedMessage}",
            duration = SnackbarDuration.Long
        )
    }
}

fun exportAllPacketsToCsv(context: Context, messages: List<ChatMessage>, appLanguage: String = "English") {
    val spanish = appLanguage == "Spanish"
    if (messages.isEmpty()) {
        AppUiFeedback.show(if (spanish) "No hay mensajes para exportar." else "No messages to export.")
        return
    }
    try {
        val csv = StringBuilder("Timestamp,SenderId,RecipientId,Content,Channel,Status,Encrypted\n")
        messages.forEach {
            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.timestamp))
            csv.append(
                "\"$date\",0x${it.senderId.toString(16).uppercase()},0x${it.recipientId.toString(16).uppercase()}," +
                    "\"${it.content.replace("\"", "\"\"")}\",\"${it.channel}\",\"${it.status}\",${it.isEncrypted}\n"
            )
        }
        val filename = "aethermesh_messages_${System.currentTimeMillis()}.csv"
        val outDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(outDir, filename)
        file.writeText(csv.toString())
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startShareChooser(
            context,
            intent,
            if (spanish) "Compartir mensajes CSV" else "Share messages CSV"
        )
    } catch (e: Exception) {
        AppUiFeedback.show(
            if (spanish) "Error al exportar: ${e.localizedMessage}"
            else "Export failed: ${e.localizedMessage}",
            duration = SnackbarDuration.Long
        )
    }
}

/** Localize GitHub OTA status strings produced by [MainScreenViewModel]. */
fun localizeGithubFirmwareStatus(status: String, appLanguage: String): String {
    if (appLanguage != "Spanish" || status.isBlank()) return status
    return when {
        status.startsWith("Checking GitHub Releases") ->
            "Consultando GitHub Releases (estable)…"
        status.startsWith("Checking GitHub Pages") ->
            "Consultando GitHub Pages (último)…"
        status.startsWith("Checking GitHub") -> "Consultando GitHub por firmware…"
        status.startsWith("Found ") && status.contains("(stable") -> {
            val name = status.substringAfter("Found ").substringBefore(" (stable")
            val tag = status.substringAfter("(stable ").removeSuffix(")")
            "Encontrado $name (estable $tag)"
        }
        status.startsWith("Found ") && status.contains("(latest") -> {
            val name = status.substringAfter("Found ").substringBefore(" (latest")
            "Encontrado $name (último / Pages)"
        }
        status.startsWith("Found ") -> "Encontrado ${status.removePrefix("Found ")}"
        status == "No OTA builds published yet." -> "Aún no hay builds OTA publicados."
        status == "No OTA package matches this node model." ->
            "Ningún paquete OTA coincide con este modelo de nodo."
        status.startsWith("No GitHub Release assets yet") ->
            status.replace(
                "No GitHub Release assets yet — using latest Pages build:",
                "Aún no hay assets en GitHub Releases — usando el build Pages más reciente:"
            )
        status.startsWith("No stable GitHub Release for this board yet.") ->
            "Aún no hay Release estable para esta placa. " +
                localizeGithubFirmwareStatus(
                    status.removePrefix("No stable GitHub Release for this board yet. ").trim(),
                    appLanguage
                )
        status.startsWith("No stable Release asset matches") ->
            "Ningún asset de Release estable coincide con esta placa" +
                status.substringAfter("this board")
        status.startsWith("Stable Releases found, but node model") ->
            "Hay Releases estables, pero el modelo del nodo es desconocido — elige un archivo local o espera la telemetría."
        status.startsWith("Connect a known board") ->
            "Conecta una placa conocida para elegir automáticamente, o elige un archivo local."
        status.startsWith("Releases unavailable") ->
            "Releases no disponibles" + status.removePrefix("Releases unavailable")
        status.startsWith("OTA catalog not on GitHub Pages") ->
            "El catálogo OTA aún no está en GitHub Pages. Usa un .bin local por ahora, o reintenta tras el redespliegue."
        status.startsWith("OTA catalog not published") ->
            "Catálogo OTA no publicado aún."
        status.startsWith("Could not reach GitHub Releases:") ->
            "No se pudo contactar GitHub Releases:${status.removePrefix("Could not reach GitHub Releases:")}"
        status.startsWith("Could not reach GitHub:") ->
            "No se pudo contactar GitHub:${status.removePrefix("Could not reach GitHub:")}"
        status.startsWith("Downloading… ") ->
            "Descargando… ${status.removePrefix("Downloading… ")}"
        status.startsWith("Downloading ") ->
            "Descargando ${status.removePrefix("Downloading ")}"
        status.startsWith("Verified ") ->
            "Verificado ${status.removePrefix("Verified ")}"
        status.startsWith("Downloaded ") && status.contains("(size OK)") ->
            "Descargado ${status.removePrefix("Downloaded ").removeSuffix(" (size OK)")} (tamaño OK)"
        status.startsWith("Download failed") ->
            "Error de descarga${status.removePrefix("Download failed")}"
        else -> status
    }
}

fun exportBreadcrumbsToKml(
    context: Context,
    breadcrumbs: List<Pair<Double, Double>>,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    if (breadcrumbs.isEmpty()) {
        AppUiFeedback.show(if (spanish) "Aún no hay rastro GPS para exportar." else "No breadcrumbs to export yet.", duration = SnackbarDuration.Short)
        return
    }
    try {
        val kml = MapExport.buildKml(breadcrumbs)

        val filename = "aethermesh_track_${System.currentTimeMillis()}.kml"
        val outDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(outDir, filename)
        file.writeText(kml)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/vnd.google-earth.kml+xml"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startShareChooser(
            context,
            intent,
            if (spanish) "Compartir rastro KML" else "Share KML Tracklog"
        )
    } catch (e: java.lang.Exception) {
        AppUiFeedback.show(if (spanish) "Error al exportar: ${e.localizedMessage}" else "Export failed: ${e.localizedMessage}", duration = SnackbarDuration.Long)
    }
}

