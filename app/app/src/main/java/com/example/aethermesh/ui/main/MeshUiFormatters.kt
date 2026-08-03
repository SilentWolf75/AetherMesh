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
        status.startsWith("Checking GitHub") -> "Consultando GitHub por firmware…"
        status.startsWith("Found ") -> "Encontrado ${status.removePrefix("Found ")}"
        status == "No OTA builds published yet." -> "Aún no hay builds OTA publicados."
        status == "No OTA package matches this node model." ->
            "Ningún paquete OTA coincide con este modelo de nodo."
        status.startsWith("OTA catalog not on GitHub Pages") ->
            "El catálogo OTA aún no está en GitHub Pages. Usa un .bin local por ahora, o reintenta tras el redespliegue."
        status.startsWith("OTA catalog not published") ->
            "Catálogo OTA no publicado aún."
        status.startsWith("Could not reach GitHub:") ->
            "No se pudo contactar GitHub:${status.removePrefix("Could not reach GitHub:")}"
        status.startsWith("Downloading… ") ->
            "Descargando… ${status.removePrefix("Downloading… ")}"
        status.startsWith("Downloading ") ->
            "Descargando ${status.removePrefix("Downloading ")}"
        status.startsWith("Verified ") ->
            "Verificado ${status.removePrefix("Verified ")}"
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

