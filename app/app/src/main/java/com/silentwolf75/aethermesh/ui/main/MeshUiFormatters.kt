package com.silentwolf75.aethermesh.ui.main

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
import com.silentwolf75.aethermesh.data.ChatMessage
import com.silentwolf75.aethermesh.data.ChatSendPolicy
import com.silentwolf75.aethermesh.data.ChannelConfig
import com.silentwolf75.aethermesh.data.FirmwareCatalog
import com.silentwolf75.aethermesh.data.MeshNode
import com.silentwolf75.aethermesh.data.AppUiPrefs
import com.silentwolf75.aethermesh.data.NodeSettingsFormPolicy
import com.silentwolf75.aethermesh.data.NodeSettingsPrefs
import com.silentwolf75.aethermesh.data.PhoneLocationShare
import com.silentwolf75.aethermesh.data.RangeTestPolicy
import com.silentwolf75.aethermesh.data.RelativeTimePolicy
import com.silentwolf75.aethermesh.data.GithubFirmwareStatusPolicy
import com.silentwolf75.aethermesh.data.VoltageTrendPolicy
import com.silentwolf75.aethermesh.data.TraceRouteState
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import com.silentwolf75.aethermesh.ui.components.*
import com.silentwolf75.aethermesh.theme.AccentCyanDim
import com.silentwolf75.aethermesh.theme.AccentSteel
import com.silentwolf75.aethermesh.theme.AccentSteelDim
import com.silentwolf75.aethermesh.theme.appBackgroundBrush
import com.silentwolf75.aethermesh.theme.headerBarBrush
import com.silentwolf75.aethermesh.theme.primaryButtonBrush
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
const val CHAT_MAX_PLAIN_UTF8_BYTES = ChatSendPolicy.MAX_TEXT
const val CHAT_MAX_ENCRYPTED_UTF8_BYTES = ChatSendPolicy.MAX_ENCRYPTED

private const val NODE_STALE_MS = 5 * 60 * 1000L

fun isNodeStale(lastActive: Long): Boolean {
    return System.currentTimeMillis() - lastActive > NODE_STALE_MS
}

/**
 * Relative last-heard label used across Nodes / Map / Chat / Details.
 * Prefer “just now” then “Xm ago” (not “Ns ago”) for a consistent feel.
 */
fun formatLastHeard(lastActive: Long, appLanguage: String = "English"): String =
    RelativeTimePolicy.lastHeard(
        lastActive,
        appLanguage == "Spanish",
        System.currentTimeMillis()
    )

/** Compact age for diagnostics tiles (same vocabulary as [formatLastHeard]). */
fun formatRelativeAge(timestampMs: Long, appLanguage: String = "English"): String =
    RelativeTimePolicy.relativeAge(
        timestampMs,
        appLanguage == "Spanish",
        System.currentTimeMillis()
    )

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

fun formatUptime(seconds: Long, appLanguage: String = "English"): String =
    RelativeTimePolicy.uptime(seconds, appLanguage == "Spanish")

/** Firmware low-voltage safe enter threshold (LiPo pack volts). Phone UI matches. */
const val LOW_VOLTAGE_SAFE_ENTER_V = VoltageTrendPolicy.LOW_VOLTAGE_SAFE_ENTER_V

fun isLowVoltageSafeHint(voltage: Float, isCharging: Boolean): Boolean =
    VoltageTrendPolicy.isLowVoltageSafeHint(voltage, isCharging)

/** Whole days since last heard (0 if within 24h or never). */
fun daysSinceHeard(lastActive: Long): Long =
    RelativeTimePolicy.daysSinceHeard(lastActive, System.currentTimeMillis())

fun formatDaysSinceHeard(lastActive: Long, appLanguage: String = "English"): String? =
    RelativeTimePolicy.daysSinceHeardLabel(
        lastActive,
        appLanguage == "Spanish",
        System.currentTimeMillis()
    )

typealias VoltageTrend = com.silentwolf75.aethermesh.data.VoltageTrend

fun voltageTrend(history: List<com.silentwolf75.aethermesh.data.TelemetrySample>): VoltageTrend =
    VoltageTrendPolicy.trend(history)

fun formatVoltageTrend(history: List<com.silentwolf75.aethermesh.data.TelemetrySample>, appLanguage: String): String? =
    VoltageTrendPolicy.format(history, appLanguage == "Spanish")

fun formatGpsLockAge(lastPositionAt: Long, appLanguage: String = "English"): String =
    RelativeTimePolicy.gpsLockAge(
        lastPositionAt,
        appLanguage == "Spanish",
        System.currentTimeMillis()
    )

/** gps_mode: 0 on, 1 off, 2 duty. Returns null when prefs unknown. */
fun formatGpsDutyStatus(gpsMode: Int?, dutyIntervalSecs: Int, appLanguage: String = "English"): String? =
    RelativeTimePolicy.gpsDutyStatus(gpsMode, dutyIntervalSecs, appLanguage == "Spanish")

fun readCachedGpsMode(context: android.content.Context, nodeId: Long): Pair<Int?, Int> {
    val prefs = context.getSharedPreferences(
        NodeSettingsPrefs.prefsName(nodeId),
        android.content.Context.MODE_PRIVATE
    )
    val mode = if (prefs.contains(NodeSettingsPrefs.KEY_GPS_MODE)) {
        prefs.getInt(NodeSettingsPrefs.KEY_GPS_MODE, 0).coerceIn(0, 2)
    } else {
        null
    }
    val duty = RelativeTimePolicy.clampDutySecs(
        prefs.getInt(NodeSettingsPrefs.KEY_GPS_DUTY_SECS, RelativeTimePolicy.DEFAULT_DUTY_SECS)
    )
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
    // Twelve evenly spaced, medium-saturation hues: distinct at a glance on
    // both themes, none of them mistakable for the error red or brand teal.
    val colors = listOf(
        Color(0xFFE39A5B), // sand
        Color(0xFFD8C155), // mustard
        Color(0xFF94CB63), // leaf
        Color(0xFF52BE8E), // jade
        Color(0xFF4CB5CF), // sky
        Color(0xFF6A94E3), // cornflower
        Color(0xFF8E83E6), // periwinkle
        Color(0xFFB27FDB), // lavender
        Color(0xFFD57DBA), // orchid
        Color(0xFFE38089), // rose
        Color(0xFFB9A37F), // taupe
        Color(0xFF7FA9B8)  // slate
    )
    val index = Math.floorMod(name.hashCode(), colors.size)
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

fun snapGpsDutyIntervalSecs(secs: Int): Int =
    NodeSettingsFormPolicy.snapGpsDutyIntervalSecs(secs)

fun hasValidPosition(latitude: Number, longitude: Number): Boolean =
    PhoneLocationShare.isValidFix(latitude.toDouble(), longitude.toDouble())

fun rangeTestFailureShort(reason: String?, appLanguage: String = "English"): String =
    RangeTestPolicy.failureShort(reason, AppUiPrefs.isSpanish(appLanguage))

fun rangeTestFailureLabel(reason: String?, appLanguage: String = "English"): String =
    RangeTestPolicy.failureLabel(reason, AppUiPrefs.isSpanish(appLanguage))

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
    logs: List<com.silentwolf75.aethermesh.data.RangeTestLog>,
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
    snapshots: List<com.silentwolf75.aethermesh.data.MeshDiagnosticsSnapshot>,
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
    snapshot: com.silentwolf75.aethermesh.data.MeshDiagnosticsSnapshot?,
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
fun localizeGithubFirmwareStatus(status: String, appLanguage: String): String =
    GithubFirmwareStatusPolicy.localize(status, appLanguage == "Spanish")

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

/**
 * Local-only after-action bundle: chat messages + node directory.
 * Uses the share sheet / file export path — no cloud upload, no MQTT.
 */
fun exportAfterActionReport(
    context: Context,
    messages: List<ChatMessage>,
    nodes: List<MeshNode>,
    appLanguage: String = "English",
    diagnosticLines: List<String> = emptyList()
) {
    val spanish = appLanguage == "Spanish"
    if (messages.isEmpty() && nodes.isEmpty()) {
        AppUiFeedback.show(
            if (spanish) "Nada que exportar todavía." else "Nothing to export yet.",
            duration = SnackbarDuration.Short
        )
        return
    }
    try {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }

        val msgCsv = StringBuilder(
            "timestamp,datetime,sender_id,recipient_id,channel,status,heard_count,encrypted,content\n"
        )
        messages.forEach { msg ->
            msgCsv.append(
                "${msg.timestamp},\"${timeFmt.format(Date(msg.timestamp))}\"," +
                    "0x${msg.senderId.toString(16).uppercase()}," +
                    "0x${msg.recipientId.toString(16).uppercase()}," +
                    "\"${msg.channel.replace("\"", "\"\"")}\",\"${msg.status}\"," +
                    "${msg.heardCount},${msg.isEncrypted}," +
                    "\"${msg.content.replace("\"", "\"\"")}\"\n"
            )
        }
        val msgFile = java.io.File(exportDir, "aethermesh_after_action_messages_$stamp.csv")
        msgFile.writeText(msgCsv.toString())

        val nodeCsv = StringBuilder(
            "node_id,name,short_name,last_active,last_active_iso,snr,rssi,battery,voltage," +
                "latitude,longitude,model,firmware,lora_sf,region\n"
        )
        nodes.forEach { n ->
            val lastIso = if (n.lastActive > 0L) timeFmt.format(Date(n.lastActive)) else ""
            nodeCsv.append(
                "0x${n.nodeId.toString(16).uppercase()},\"${n.name.replace("\"", "\"\"")}\"," +
                    "\"${n.shortName.replace("\"", "\"\"")}\",${n.lastActive},\"$lastIso\"," +
                    "${n.snr},${n.rssi},${n.battery},${n.voltage}," +
                    "${n.latitude},${n.longitude},\"${n.model.replace("\"", "\"\"")}\"," +
                    "\"${n.firmwareVersion.replace("\"", "\"\"")}\",${n.loraSf},${n.region}\n"
            )
        }
        val nodeFile = java.io.File(exportDir, "aethermesh_after_action_nodes_$stamp.csv")
        nodeFile.writeText(nodeCsv.toString())

        val summary = StringBuilder()
        summary.appendLine("AetherMesh after-action export (local only — not uploaded)")
        summary.appendLine("Generated: ${timeFmt.format(Date())}")
        summary.appendLine("Messages: ${messages.size}")
        summary.appendLine("Nodes: ${nodes.size}")
        if (diagnosticLines.isNotEmpty()) {
            summary.appendLine("--- recent diagnostic lines ---")
            diagnosticLines.takeLast(40).forEach { summary.appendLine(it) }
        }
        summary.appendLine("--- files ---")
        summary.appendLine(msgFile.name)
        summary.appendLine(nodeFile.name)
        val summaryFile = java.io.File(exportDir, "aethermesh_after_action_readme_$stamp.txt")
        summaryFile.writeText(summary.toString())

        val uris = ArrayList<android.net.Uri>(3)
        listOf(msgFile, nodeFile, summaryFile).forEach { file ->
            uris.add(
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
            )
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            putExtra(
                android.content.Intent.EXTRA_SUBJECT,
                if (spanish) "AetherMesh informe post-evento" else "AetherMesh after-action export"
            )
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startShareChooser(
            context,
            intent,
            if (spanish) "Exportar post-evento (local)" else "Export after-action (local)"
        )
        AppUiFeedback.show(
            if (spanish)
                "Exportación local lista (${messages.size} msgs, ${nodes.size} nodos). Sin nube."
            else
                "Local export ready (${messages.size} msgs, ${nodes.size} nodes). No cloud upload.",
            duration = SnackbarDuration.Short
        )
    } catch (e: Exception) {
        AppUiFeedback.show(
            if (spanish) "Error al exportar: ${e.localizedMessage}"
            else "Export failed: ${e.localizedMessage}",
            duration = SnackbarDuration.Long
        )
    }
}

