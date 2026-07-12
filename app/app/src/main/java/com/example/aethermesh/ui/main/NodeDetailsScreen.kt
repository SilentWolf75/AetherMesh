package com.example.aethermesh.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aethermesh.data.MeshNode
import com.example.aethermesh.data.RouteHopInfo
import com.example.aethermesh.data.TelemetrySample
import org.osmdroid.util.GeoPoint

/**
 * Full-screen node details inspired by Meshtastic: Details → Actions → Tools → Position.
 * Hosted as a Navigation3 destination (not a Dialog overlay).
 */
@Composable
fun NodeDetailsScreen(
    node: MeshNode,
    observedRoutes: Map<Long, RouteHopInfo>,
    phoneLocation: GeoPoint?,
    appLanguage: String,
    useImperialUnits: Boolean,
    connectedNodeId: Long,
    getTelemetryHistory: (Long) -> List<TelemetrySample>,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onRename: () -> Unit,
    onTraceRoute: () -> Unit,
    onRemoteConfig: (() -> Unit)? = null,
    onViewOnMap: (() -> Unit)? = null,
    onStartRangeTest: (() -> Unit)? = null
) {
    val shortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
    val route = observedRoutes[node.nodeId]
    val hops = route?.hops
    val hasLiveSignal = route != null && route.lastRssi != 0f
    val sigRssi = if (hasLiveSignal) route!!.lastRssi else node.rssi
    val sigSnr = if (hasLiveSignal) route!!.lastSnr else node.snr
    val stale = isNodeStale(node.lastActive)
    val history = remember(node.nodeId, node.lastActive) { getTelemetryHistory(node.nodeId) }

    val distanceLabel = if (phoneLocation != null && hasValidPosition(node.latitude, node.longitude)) {
        val km = calculateDistance(
            phoneLocation.latitude, phoneLocation.longitude,
            node.latitude.toDouble(), node.longitude.toDouble()
        )
        if (useImperialUnits) {
            val mi = km * 0.621371
            if (mi < 0.2) "${(mi * 5280).toInt()} ft" else "%.2f mi".format(mi)
        } else if (km < 1.0) {
            "${(km * 1000).toInt()} m"
        } else {
            "%.2f km".format(km)
        }
    } else null

    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextLight
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (appLanguage == "Spanish") "Detalles" else "Details",
                    color = TextLight,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(node.name, color = TextMuted, fontSize = 13.sp)
            }
        }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                DetailsCard(
                    title = if (appLanguage == "Spanish") "Detalles" else "Details",
                    shortName = shortName,
                    node = node,
                    hops = hops,
                    stale = stale,
                    appLanguage = appLanguage
                )

                Spacer(modifier = Modifier.height(14.dp))

                SectionCard(title = if (appLanguage == "Spanish") "Acciones" else "Actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onMessage,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentMint,
                                contentColor = DarkBackground
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (appLanguage == "Spanish") "Mensaje directo" else "Direct Message",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        RoundActionButton(Icons.Default.Edit, if (appLanguage == "Spanish") "Renombrar" else "Rename", onRename)
                        if (onRemoteConfig != null && node.nodeId != connectedNodeId) {
                            Spacer(modifier = Modifier.width(8.dp))
                            RoundActionButton(
                                Icons.Default.Settings,
                                if (appLanguage == "Spanish") "Configurar" else "Remote Config",
                                onRemoteConfig
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SectionCard(title = if (appLanguage == "Spanish") "Herramientas" else "Tools") {
                    if (node.nodeId != connectedNodeId) {
                        ToolRow(
                            icon = Icons.Default.AltRoute,
                            label = if (appLanguage == "Spanish") "Traceroute" else "Traceroute",
                            subtitle = if (appLanguage == "Spanish") "Ruta en vivo hacia este nodo" else "Live forward and return path",
                            onClick = onTraceRoute,
                            trailingRefresh = true
                        )
                        HorizontalDivider(color = BorderDark)
                        if (onStartRangeTest != null) {
                            ToolRow(
                                icon = Icons.Default.Speed,
                                label = if (appLanguage == "Spanish") "Prueba de rango" else "Range test",
                                subtitle = if (appLanguage == "Spanish")
                                    "Abrir Conexión con este nodo como objetivo"
                                else
                                    "Open Connection with this node as the target",
                                onClick = onStartRangeTest
                            )
                            HorizontalDivider(color = BorderDark)
                        }
                    }
                    if (onViewOnMap != null && hasValidPosition(node.latitude, node.longitude)) {
                        ToolRow(
                            icon = Icons.Default.Map,
                            label = if (appLanguage == "Spanish") "Ver en el mapa" else "View on map",
                            subtitle = if (appLanguage == "Spanish") "Abrir la pestaña Mapa" else "Open the Map tab",
                            onClick = onViewOnMap
                        )
                        HorizontalDivider(color = BorderDark)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = AccentMint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (appLanguage == "Spanish") "Calidad de señal" else "Signal Quality",
                                color = TextLight,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (sigRssi != 0f) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    SignalBars(rssi = sigRssi)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "${sigRssi.toInt()} dBm  ·  ${"%.1f".format(sigSnr)} dB SNR",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Text(
                                    if (appLanguage == "Spanish") "Sin medición reciente" else "No recent measurement",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = BorderDark)
                    ToolRow(
                        icon = Icons.Default.Memory,
                        label = if (appLanguage == "Spanish") "Métricas del dispositivo" else "Device Metrics",
                        subtitle = buildString {
                            append("${node.battery}%")
                            if (node.voltage > 0f) append("  ·  ${"%.2f".format(node.voltage)} V")
                            if (node.isCharging) append(if (appLanguage == "Spanish") "  ·  cargando" else "  ·  charging")
                            if (node.firmwareVersion.isNotEmpty()) append("  ·  fw ${node.firmwareVersion}")
                        },
                        onClick = null,
                        trailingBolt = node.isCharging
                    )

                    if (history.size >= 2) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (appLanguage == "Spanish") "Historial de batería" else "Battery history",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            if (history.any { it.isCharging }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        if (appLanguage == "Spanish") "incluye carga" else "includes charging",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        BatteryHistorySparkline(history)
                    }
                }

                if (hasValidPosition(node.latitude, node.longitude)) {
                    Spacer(modifier = Modifier.height(14.dp))
                    SectionCard(title = if (appLanguage == "Spanish") "Posición" else "Position") {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AccentMint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (appLanguage == "Spanish") "Última posición" else "Last position update",
                                    color = TextLight,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(formatLastHeard(node.lastActive), color = TextMuted, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "%.5f, %.5f".format(node.latitude, node.longitude),
                                    color = TextLight,
                                    fontSize = 13.sp
                                )
                                if (distanceLabel != null) {
                                    Text(distanceLabel, color = AccentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                if (node.positionPrecision > 0) {
                                    Text(
                                        formatPositionPrecision(node.positionPrecision, useImperialUnits, appLanguage) +
                                            if (appLanguage == "Spanish") " (aproximada)" else " (approximate)",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
    }
}

@Composable
private fun DetailsCard(
    title: String,
    shortName: String,
    node: MeshNode,
    hops: Int?,
    stale: Boolean,
    appLanguage: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AccentMint)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(getBadgeColor(node.name).copy(alpha = if (stale) 0.45f else 1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        shortName,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (shortName.length > 2) 11.sp else 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(node.name, color = if (stale) TextMuted else TextLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        node.model.ifEmpty { if (appLanguage == "Spanish") "Modelo desconocido" else "Unknown model" },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderDark)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    MetaItem(Icons.Default.Person, if (appLanguage == "Spanish") "Nombre corto" else "Short Name", shortName)
                    MetaItem(Icons.Default.Tag, if (appLanguage == "Spanish") "ID del nodo" else "Node ID", "0x${node.nodeId.toString(16).uppercase()}")
                    MetaItem(Icons.Default.Refresh, if (appLanguage == "Spanish") "Último oído" else "Last heard", formatLastHeard(node.lastActive))
                }
                Column(modifier = Modifier.weight(1f)) {
                    MetaItem(
                        Icons.Default.BatteryFull,
                        if (appLanguage == "Spanish") "Batería" else "Battery",
                        "${node.battery}%" + if (node.isCharging) " ⚡" else ""
                    )
                    if (hops != null && hops > 0) {
                        MetaItem(
                            Icons.Default.AltRoute,
                            if (appLanguage == "Spanish") "Saltos" else "Hops Away",
                            hops.toString()
                        )
                    }
                    if (node.uptimeSeconds > 0) {
                        MetaItem(Icons.Default.Timer, if (appLanguage == "Spanish") "Activo" else "Uptime", formatUptime(node.uptimeSeconds))
                    }
                    if (node.firmwareVersion.isNotEmpty()) {
                        MetaItem(Icons.Default.Memory, if (appLanguage == "Spanish") "Firmware" else "Firmware", node.firmwareVersion)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AccentMint)
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = AccentMint, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, color = TextMuted, fontSize = 10.sp)
            Text(value, color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RoundActionButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BorderDark.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = AccentMint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailingRefresh: Boolean = false,
    trailingBolt: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AccentMint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextLight, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        if (trailingBolt) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (trailingRefresh && onClick != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AccentMint.copy(alpha = 0.15f))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentMint, modifier = Modifier.size(18.dp))
            }
        } else if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BatteryHistorySparkline(history: List<TelemetrySample>) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkBackground)
            .padding(6.dp)
    ) {
        val n = history.size
        val stepX = if (n > 1) size.width / (n - 1) else size.width
        fun yFor(b: Int) = size.height - (b / 100f) * size.height
        for (i in 0 until n - 1) {
            val a = history[i]
            val b = history[i + 1]
            drawLine(
                color = if (b.isCharging) Color(0xFF34D399) else AccentCyan,
                start = androidx.compose.ui.geometry.Offset(i * stepX, yFor(a.battery)),
                end = androidx.compose.ui.geometry.Offset((i + 1) * stepX, yFor(b.battery)),
                strokeWidth = 3f
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${history.first().battery}%", color = TextMuted, fontSize = 9.sp)
        Text("${history.size} samples", color = TextMuted, fontSize = 9.sp)
        Text("${history.last().battery}%", color = TextMuted, fontSize = 9.sp)
    }
}
