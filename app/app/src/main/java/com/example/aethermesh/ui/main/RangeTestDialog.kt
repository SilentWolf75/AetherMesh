package com.example.aethermesh.ui.main

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aethermesh.data.MeshNode

@Composable
fun RangeTestDialog(
    targetNode: MeshNode,
    viewModel: MainScreenViewModel,
    appLanguage: String = "English",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val spanish = appLanguage == "Spanish"
    val isConnected by viewModel.isBleConnected.collectAsStateWithLifecycle()
    val isDeviceAuthenticated by viewModel.isDeviceAuthenticated.collectAsStateWithLifecycle()
    val isRangeTestActive by viewModel.isRangeTestActive.collectAsStateWithLifecycle()
    val rangeTestLogs by viewModel.rangeTestLogs.collectAsStateWithLifecycle()
    val toolsPrefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
    var pingIntervalSec by remember {
        mutableFloatStateOf(toolsPrefs.getFloat("range_test_interval_sec", 5f).coerceIn(2f, 30f))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission =
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(targetNode.nodeId) {
        viewModel.loadRangeTestLogs(targetNode.nodeId)
        toolsPrefs.edit().putLong("range_test_target_id", targetNode.nodeId).apply()
    }
    LaunchedEffect(pingIntervalSec) {
        toolsPrefs.edit().putFloat("range_test_interval_sec", pingIntervalSec).apply()
    }

    val activeForThisTarget =
        isRangeTestActive && viewModel.rangeTestTargetId == targetNode.nodeId
    val activeForOtherTarget =
        isRangeTestActive && viewModel.rangeTestTargetId != 0L && !activeForThisTarget

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (spanish) "Cerrar" else "Close", color = TextMuted)
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (spanish) "Prueba de rango" else "Range Test",
                        color = TextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "${targetNode.name} (0x${targetNode.nodeId.toString(16).uppercase()})",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                if (activeForThisTarget) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentMint)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(t("ACTIVE", appLanguage), color = AccentMint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isConnected && !isDeviceAuthenticated) {
                    Text(
                        if (spanish)
                            "Autentica el dispositivo para usar la prueba de rango."
                        else
                            "Authenticate the device to use range test.",
                        color = AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (!hasLocationPermission) {
                    Text(
                        if (spanish)
                            "Sin permiso de ubicación: distancia y GPS del CSV usarán la posición del nodo."
                        else
                            "Location permission off — distance/CSV will fall back to the node’s reported position.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (activeForOtherTarget) {
                    Text(
                        if (spanish)
                            "Hay una prueba activa hacia otro nodo. Deténla para iniciar una nueva."
                        else
                            "A range test is active for another node. Stop it to start a new one.",
                        color = AccentAmber,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.stopRangeTest() },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(t("Stop Test", appLanguage), color = TextLight)
                    }
                } else if (!activeForThisTarget) {
                    Text(
                        if (spanish)
                            "Solo un salto: se excluyen repetidores para que distancia y señal describan los dos nodos elegidos."
                        else
                            "One-hop only: repeaters are excluded so distance and signal describe the two selected nodes.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (spanish) "Intervalo de ping: ${pingIntervalSec.toInt()} s"
                        else "Ping Interval: ${pingIntervalSec.toInt()} seconds",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = pingIntervalSec,
                        onValueChange = { pingIntervalSec = it },
                        valueRange = 2f..30f,
                        steps = 27,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = BorderDark
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.startRangeTest(targetNode.nodeId, pingIntervalSec.toInt())
                        },
                        enabled = isConnected && isDeviceAuthenticated,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (spanish) "Iniciar prueba de rango" else "Start Range Test",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isDeviceAuthenticated) {
                        Text(
                            if (spanish) "Bloqueado hasta autenticar" else "Locked until authenticated",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (rangeTestLogs.isNotEmpty()) {
                        val total = rangeTestLogs.size
                        val ok = rangeTestLogs.count { it.success }
                        val rate = if (total > 0) ok * 100 / total else 0
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (spanish)
                                "Última sesión: $ok/$total ($rate%)"
                            else
                                "Last session: $ok/$total replies ($rate%)",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        rangeTestLogs.lastOrNull { !it.success }?.let { miss ->
                            Text(
                                rangeTestFailureLabel(miss.failureReason, appLanguage),
                                color = AccentAmber,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    exportRangeTestLogsToCsv(
                                        context,
                                        viewModel.getAllRangeTestLogs(),
                                        viewModel.nodes.value.associate {
                                            it.nodeId to (it.latitude.toDouble() to it.longitude.toDouble())
                                        },
                                        appLanguage
                                    )
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF164E63)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(t("Export CSV", appLanguage), color = AccentCyan, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.clearRangeTestLogs(targetNode.nodeId) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF451a1a)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(t("Clear Logs", appLanguage), color = Color(0xFFFCA5A5), fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    val totalPings = rangeTestLogs.size
                    val successfulPings = rangeTestLogs.count { it.success }
                    val successRate = if (totalPings > 0) (successfulPings * 100 / totalPings) else 0
                    val failBuckets = rangeTestLogs
                        .filter { !it.success }
                        .groupingBy { it.failureReason ?: "timeout" }
                        .eachCount()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(if (spanish) "PINGS" else "PINGS SENT", color = TextMuted, fontSize = 10.sp)
                            Text("$totalPings", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(if (spanish) "RESPUESTAS" else "REPLIES", color = TextMuted, fontSize = 10.sp)
                            Text("$successfulPings", color = AccentMint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(if (spanish) "ÉXITO" else "SUCCESS RATE", color = TextMuted, fontSize = 10.sp)
                            Text(
                                "$successRate%",
                                color = if (successRate > 75) AccentMint else if (successRate > 40) Color(0xFFFBBF24) else Color(0xFFF87171),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (failBuckets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            failBuckets.entries.joinToString(" · ") { (reason, count) ->
                                "$count× ${rangeTestFailureShort(reason, appLanguage)}"
                            },
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        rangeTestLogs.lastOrNull { !it.success }?.let { miss ->
                            Text(
                                rangeTestFailureLabel(miss.failureReason, appLanguage),
                                color = AccentAmber,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    run {
                        val fix = viewModel.lastPhoneFix()
                        if (fix != null && hasValidPosition(targetNode.latitude, targetNode.longitude)) {
                            val km = calculateDistance(
                                fix.latitude, fix.longitude,
                                targetNode.latitude.toDouble(), targetNode.longitude.toDouble()
                            )
                            val useImperial = toolsPrefs.getBoolean("use_imperial_units", true)
                            val distStr = if (useImperial) {
                                val miles = km * 0.621371
                                if (miles < 0.2) "${(miles * 5280).toInt()} ft" else "%.2f mi".format(miles)
                            } else {
                                if (km < 1.0) "${(km * 1000).toInt()} m" else "%.2f km".format(km)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (spanish) "Distancia al objetivo: $distStr" else "Distance to target: $distStr",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    rangeTestLogs.lastOrNull { it.success }?.let { latest ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    if (spanish) "Respuesta (aquí)" else "Reply (here)",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(
                                    "${latest.rssi.toInt()} dBm",
                                    color = AccentCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("%.1f dB SNR".format(latest.snr), color = TextMuted, fontSize = 11.sp)
                            }
                            if (latest.remoteRssi != null && latest.remoteSnr != null) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkBackground)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        if (spanish) "Ping (en destino)" else "Ping (at target)",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        "${latest.remoteRssi.toInt()} dBm",
                                        color = AccentMint,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("%.1f dB SNR".format(latest.remoteSnr), color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t("RSSI Signal Level History (dBm)", appLanguage),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentMint))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (spanish) "OK" else "OK", color = TextMuted, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("✕", color = AccentRed, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(if (spanish) "Fallo" else "Miss", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(start = 28.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-26).dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("-40", color = TextMuted, fontSize = 9.sp)
                            Text("-90", color = TextMuted, fontSize = 9.sp)
                            Text("-140", color = TextMuted, fontSize = 9.sp)
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            listOf(-40f, -90f, -140f).forEach { dbm ->
                                val y = height * ((dbm - (-40f)) / (-140f - (-40f)))
                                drawLine(
                                    color = Color(0xFF334155),
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(width, y),
                                    strokeWidth = 1f
                                )
                            }
                            if (rangeTestLogs.isNotEmpty()) {
                                val points = rangeTestLogs.takeLast(15)
                                val stepX = if (points.size > 1) width / (points.size - 1) else width
                                var lastPointX = 0f
                                var lastPointY = 0f
                                points.forEachIndexed { index, log ->
                                    val x = index * stepX
                                    val clampedRssi = log.rssi.coerceIn(-140f, -40f)
                                    val y = height * ((clampedRssi - (-40f)) / (-140f - (-40f)))
                                    if (log.success) {
                                        drawCircle(
                                            color = if (clampedRssi > -90f) AccentMint else Color(0xFFFBBF24),
                                            radius = 4f,
                                            center = androidx.compose.ui.geometry.Offset(x, y)
                                        )
                                        if (index > 0) {
                                            drawLine(
                                                color = AccentMint.copy(alpha = 0.5f),
                                                start = androidx.compose.ui.geometry.Offset(lastPointX, lastPointY),
                                                end = androidx.compose.ui.geometry.Offset(x, y),
                                                strokeWidth = 2f
                                            )
                                        }
                                    } else {
                                        val sizeX = 4f
                                        val failY = height - 5f
                                        drawLine(
                                            color = Color(0xFFF87171),
                                            start = androidx.compose.ui.geometry.Offset(x - sizeX, failY - sizeX),
                                            end = androidx.compose.ui.geometry.Offset(x + sizeX, failY + sizeX),
                                            strokeWidth = 2f
                                        )
                                        drawLine(
                                            color = Color(0xFFF87171),
                                            start = androidx.compose.ui.geometry.Offset(x - sizeX, failY + sizeX),
                                            end = androidx.compose.ui.geometry.Offset(x + sizeX, failY - sizeX),
                                            strokeWidth = 2f
                                        )
                                    }
                                    lastPointX = x
                                    lastPointY = if (log.success) y else height - 5f
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.stopRangeTest() },
                            modifier = Modifier.weight(1f).height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(t("Stop Test", appLanguage), color = TextLight, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                exportRangeTestLogsToCsv(
                                    context,
                                    viewModel.getAllRangeTestLogs(),
                                    viewModel.nodes.value.associate {
                                        it.nodeId to (it.latitude.toDouble() to it.longitude.toDouble())
                                    },
                                    appLanguage
                                )
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF164E63)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(t("Export CSV", appLanguage), color = AccentCyan, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val targetId = if (isRangeTestActive && viewModel.rangeTestTargetId != 0L) {
                                    viewModel.rangeTestTargetId
                                } else {
                                    targetNode.nodeId
                                }
                                viewModel.clearRangeTestLogs(targetId)
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF451a1a)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(t("Clear Logs", appLanguage), color = Color(0xFFFCA5A5), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        containerColor = SurfaceDark
    )
}
