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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

private enum class NodesSort {
    LAST_HEARD, SIGNAL, NAME, DISTANCE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesView(
    nodes: List<MeshNode>,
    observedRoutes: Map<Long, com.example.aethermesh.data.RouteHopInfo>,
    phoneLocation: GeoPoint?,
    appLanguage: String,
    useImperialUnits: Boolean,
    onNodeClick: (Long) -> Unit,
    onRenameNode: (Long, String, String, String) -> Boolean,
    getTelemetryHistory: (Long) -> List<com.example.aethermesh.data.TelemetrySample> = { emptyList() },
    connectedNodeId: Long = 0L,
    onTraceRoute: (Long) -> Boolean = { false },
    onRemoteConfig: ((MeshNode) -> Unit)? = null,
    onViewOnMap: (Long) -> Unit = {},
    onRangeTest: (Long) -> Unit = {},
    onOpenNodeDetails: (Long) -> Unit = {},
    selectedNodeId: Long? = null,
    onRefresh: (() -> Unit)? = null
) {
    var renamingNode by remember { mutableStateOf<MeshNode?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(NodesSort.LAST_HEARD) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val relativeTick = rememberRelativeTimeTick()
    val refreshScope = rememberCoroutineScope()

    if (renamingNode != null) {
        RenameNodeDialog(
            node = renamingNode!!,
            connectedNodeId = connectedNodeId,
            appLanguage = appLanguage,
            onRename = onRenameNode,
            onDismiss = { renamingNode = null }
        )
    }


    val connectedNode = nodes.find { it.nodeId == connectedNodeId }
    val query = searchQuery.trim()
    fun matchesQuery(node: MeshNode): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        val idHex = "0x${node.nodeId.toString(16).lowercase()}"
        return node.name.lowercase().contains(q) ||
            node.shortName.lowercase().contains(q) ||
            idHex.contains(q) ||
            node.nodeId.toString().contains(q)
    }

    fun signalOf(node: MeshNode): Float {
        val route = observedRoutes[node.nodeId]
        return if (route != null && route.lastRssi != 0f) route.lastRssi else node.rssi
    }

    fun distanceKmOf(node: MeshNode): Double? {
        if (phoneLocation == null || !hasValidPosition(node.latitude, node.longitude)) return null
        return calculateDistance(
            phoneLocation.latitude, phoneLocation.longitude,
            node.latitude.toDouble(), node.longitude.toDouble()
        )
    }

    fun sortNodes(list: List<MeshNode>): List<MeshNode> = when (sortBy) {
        NodesSort.LAST_HEARD -> list.sortedByDescending { it.lastActive }
        NodesSort.SIGNAL -> list.sortedByDescending { signalOf(it) }
        NodesSort.NAME -> list.sortedBy { it.name.lowercase() }
        NodesSort.DISTANCE -> list.sortedWith(
            compareBy<MeshNode> { distanceKmOf(it) == null }
                .thenBy { distanceKmOf(it) ?: Double.MAX_VALUE }
        )
    }

    // relativeTick forces active/stale split + heard labels to refresh
    @Suppress("UNUSED_VARIABLE")
    val _heardClock = relativeTick
    val remoteNodes = nodes.filter { !sameMeshNodeId(it.nodeId, connectedNodeId) && matchesQuery(it) }
    val activeNodes = sortNodes(remoteNodes.filter { !isNodeStale(it.lastActive) })
    val staleNodes = sortNodes(remoteNodes.filter { isNodeStale(it.lastActive) })
    val showSelf = connectedNode != null && matchesQuery(connectedNode)
    val hasAny = showSelf || remoteNodes.isNotEmpty()

    val sortLabels = mapOf(
        NodesSort.LAST_HEARD to if (appLanguage == "Spanish") "Última actividad" else "Last heard",
        NodesSort.SIGNAL to if (appLanguage == "Spanish") "Señal" else "Signal",
        NodesSort.NAME to if (appLanguage == "Spanish") "Nombre" else "Name",
        NodesSort.DISTANCE to if (appLanguage == "Spanish") "Distancia" else "Distance"
    )

    val listContent: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            placeholder = {
                Text(
                    if (appLanguage == "Spanish") "Buscar nodos…" else "Search nodes…",
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = if (appLanguage == "Spanish") "Buscar" else "Search",
                    tint = TextMuted
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = if (appLanguage == "Spanish") "Borrar" else "Clear",
                            tint = TextMuted
                        )
                    }
                }
            },
            colors = aetherTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AetherSectionHeader(
                title = t("Active Nodes", appLanguage),
                trailing = "${activeNodes.size + if (showSelf && connectedNode != null && !isNodeStale(connectedNode.lastActive)) 1 else 0}",
                modifier = Modifier.weight(1f)
            )
            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(
                        sortLabels[sortBy]
                            ?: if (appLanguage == "Spanish") "Ordenar" else "Sort",
                        color = AccentCyan,
                        fontSize = 12.sp
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = if (appLanguage == "Spanish") "Ordenar" else "Sort",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    NodesSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(sortLabels[option] ?: option.name, color = TextLight) },
                            onClick = {
                                sortBy = option
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (!hasAny) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    RadarGraphic(size = 110.dp, sweep = AccentSteel, ring = AccentCyan)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        if (query.isNotEmpty()) {
                            if (appLanguage == "Spanish") "Ningún nodo coincide con la búsqueda."
                            else "No nodes match your search."
                        } else {
                            t("No nodes discovered yet. Waiting for telemetry...", appLanguage)
                        },
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    if (query.isEmpty() && connectedNodeId == 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            if (appLanguage == "Spanish")
                                "Vincula una radio (chip Conectar arriba) para descubrir la malla."
                            else
                                "Link a radio (Connect chip in the header) to discover the mesh.",
                            color = AccentAmber,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showSelf && connectedNode != null) {
                    item {
                        NodeItem(
                            node = connectedNode,
                            observedRoutes = observedRoutes,
                            phoneLocation = phoneLocation,
                            appLanguage = appLanguage,
                            useImperialUnits = useImperialUnits,
                            onClick = { onOpenNodeDetails(connectedNode.nodeId) },
                            onRenameClick = { renamingNode = connectedNode },
                            onTraceRoute = { false },
                            onMessageClick = { onNodeClick(connectedNode.nodeId) },
                            onViewOnMap = {
                                if (hasValidPosition(connectedNode.latitude, connectedNode.longitude)) {
                                    onViewOnMap(connectedNode.nodeId)
                                }
                            },
                            isConnectedNode = true,
                            selected = selectedNodeId != null && selectedNodeId == connectedNode.nodeId
                        )
                    }
                }
                items(activeNodes, key = { it.nodeId }) { node ->
                    NodeItem(
                        node = node,
                        observedRoutes = observedRoutes,
                        phoneLocation = phoneLocation,
                        appLanguage = appLanguage,
                        useImperialUnits = useImperialUnits,
                        onClick = { onOpenNodeDetails(node.nodeId) },
                        onRenameClick = { renamingNode = node },
                        onTraceRoute = { onTraceRoute(node.nodeId) },
                        onMessageClick = { onNodeClick(node.nodeId) },
                        onViewOnMap = {
                            if (hasValidPosition(node.latitude, node.longitude)) onViewOnMap(node.nodeId)
                        },
                        onRangeTest = { onRangeTest(node.nodeId) },
                        onRemoteConfig = { onRemoteConfig?.invoke(node) },
                        isConnectedNode = false,
                        selected = selectedNodeId != null && selectedNodeId == node.nodeId
                    )
                }
                if (staleNodes.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        AetherSectionHeader(
                            title = t("Stale", appLanguage),
                            trailing = "${staleNodes.size}",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(staleNodes, key = { it.nodeId }) { node ->
                        NodeItem(
                            node = node,
                            observedRoutes = observedRoutes,
                            phoneLocation = phoneLocation,
                            appLanguage = appLanguage,
                            useImperialUnits = useImperialUnits,
                            onClick = { onOpenNodeDetails(node.nodeId) },
                            onRenameClick = { renamingNode = node },
                            onTraceRoute = { onTraceRoute(node.nodeId) },
                            onMessageClick = { onNodeClick(node.nodeId) },
                            onViewOnMap = {
                                if (hasValidPosition(node.latitude, node.longitude)) onViewOnMap(node.nodeId)
                            },
                            onRangeTest = { onRangeTest(node.nodeId) },
                            onRemoteConfig = { onRemoteConfig?.invoke(node) },
                            isConnectedNode = false,
                            selected = selectedNodeId != null && selectedNodeId == node.nodeId
                        )
                    }
                }
            }
        }
    }
    }

    if (onRefresh != null) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshScope.launch {
                    refreshing = true
                    onRefresh()
                    kotlinx.coroutines.delay(450)
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            listContent()
        }
    } else {
        listContent()
    }
}

@Composable
fun SignalBars(rssi: Float) {
    val barsCount = when {
        rssi >= -70f -> 4
        rssi >= -85f -> 3
        rssi >= -100f -> 2
        rssi > -115f -> 1
        else -> 0
    }
    val barColor = when (barsCount) {
        4 -> AccentMint
        3 -> AccentCyan
        2 -> Color(0xFFFBBF24) // Amber
        1 -> AccentRed
        else -> TextMuted
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(11.dp)
    ) {
        for (i in 1..4) {
            val barHeight = (i * 2.5).dp
            val isFilled = i <= barsCount
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isFilled) barColor else BorderDark)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodeItem(
    node: MeshNode,
    observedRoutes: Map<Long, com.example.aethermesh.data.RouteHopInfo>,
    phoneLocation: GeoPoint?,
    appLanguage: String,
    useImperialUnits: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onTraceRoute: () -> Boolean = { false },
    onMessageClick: () -> Unit = {},
    onViewOnMap: (() -> Unit)? = null,
    onRangeTest: (() -> Unit)? = null,
    onRemoteConfig: (() -> Unit)? = null,
    isConnectedNode: Boolean = false,
    selected: Boolean = false
) {
    val shortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
    val badgeColor = getBadgeColor(node.name)
    val stale = isNodeStale(node.lastActive)
    val primaryText = if (stale) TextMuted else TextLight
    var menuExpanded by remember { mutableStateOf(false) }

    val route = observedRoutes[node.nodeId]
    val hasLiveSignal = route != null && route.lastRssi != 0f
    val sigRssi = if (hasLiveSignal) route!!.lastRssi else node.rssi
    val hops = route?.hops?.takeIf { it > 0 }

    val distanceLabel = if (phoneLocation != null && hasValidPosition(node.latitude, node.longitude)) {
        val distanceKm = calculateDistance(
            phoneLocation.latitude, phoneLocation.longitude,
            node.latitude.toDouble(), node.longitude.toDouble()
        )
        if (useImperialUnits) {
            val mi = distanceKm * 0.621371
            if (mi < 0.2) "${(mi * 5280).toInt()} ft" else "%.1f mi".format(mi)
        } else if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()} m"
        } else {
            "%.1f km".format(distanceKm)
        }
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> AccentCyan.copy(alpha = 0.16f)
                    stale -> SurfaceDark.copy(alpha = 0.55f)
                    else -> SurfaceDark
                }
            )
            .then(
                if (selected) Modifier.border(BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f)), RoundedCornerShape(12.dp))
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NodeBadge(shortName = shortName, color = badgeColor, muted = stale)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(node.name, color = primaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatLastHeard(node.lastActive, appLanguage), color = TextMuted, fontSize = 12.sp)
                if (distanceLabel != null) {
                    Text("  ·  ", color = TextMuted, fontSize = 12.sp)
                    Text(distanceLabel, color = if (stale) TextMuted else AccentMint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (hops != null && !isConnectedNode) {
                    Text("  ·  ", color = TextMuted, fontSize = 12.sp)
                    Text(
                        "$hops ${if (hops == 1) t("Hop", appLanguage) else t("Hops", appLanguage)}",
                        color = if (stale) TextMuted else AccentSteel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (node.region == 0 || node.region == 1) {
                    Text("  ·  ", color = TextMuted, fontSize = 12.sp)
                    Text(
                        if (node.region == 0) "US915" else "EU868",
                        color = if (stale) TextMuted else AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (isConnectedNode) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (appLanguage == "Spanish") "Este dispositivo (BLE)" else "This device (BLE)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else if (sigRssi != 0f) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(rssi = sigRssi)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${sigRssi.toInt()} dBm", color = TextMuted, fontSize = 11.sp)
                    if (node.loraSf in 7..12) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SF${node.loraSf}",
                            color = AccentSteel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (node.loraSf in 7..12) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "SF${node.loraSf}",
                    color = AccentSteel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.isCharging) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = if (appLanguage == "Spanish") "Cargando" else "Charging",
                        tint = AccentAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                val batteryUnknown = node.battery <= 0 && node.voltage <= 0f && !node.isCharging
                Icon(
                    imageVector = Icons.Default.BatteryFull,
                    contentDescription = if (appLanguage == "Spanish") "Batería" else "Battery",
                    tint = if (batteryUnknown) TextMuted else batteryLevelColor(node.battery),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    if (batteryUnknown) "—" else "${node.battery}%",
                    color = primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = if (appLanguage == "Spanish") "Acciones" else "Actions",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text(t("Rename Node", appLanguage), color = TextLight) },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            }
                        )
                        if (!isConnectedNode) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (appLanguage == "Spanish") "Mensaje" else "Message",
                                        color = TextLight
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onMessageClick()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (appLanguage == "Spanish") "Trazado de ruta" else "Traceroute",
                                        color = TextLight
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onTraceRoute()
                                }
                            )
                            if (onViewOnMap != null && hasValidPosition(node.latitude, node.longitude)) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (appLanguage == "Spanish") "Ver en mapa" else "View on map",
                                            color = TextLight
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onViewOnMap()
                                    }
                                )
                            }
                            if (onRangeTest != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (appLanguage == "Spanish") "Prueba de rango" else "Range test",
                                            color = TextLight
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRangeTest()
                                    }
                                )
                            }
                            if (onRemoteConfig != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (appLanguage == "Spanish") "Config. remota" else "Remote config",
                                            color = TextLight
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRemoteConfig()
                                    }
                                )
                            }
                        }
                        if (isConnectedNode && onViewOnMap != null &&
                            hasValidPosition(node.latitude, node.longitude)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (appLanguage == "Spanish") "Ver en mapa" else "View on map",
                                        color = TextLight
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onViewOnMap()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Carto Dark Matter basemap — used when Dark map is enabled. */

