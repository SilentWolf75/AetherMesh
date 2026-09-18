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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.silentwolf75.aethermesh.data.ChatMessage
import com.silentwolf75.aethermesh.data.ChannelConfig
import com.silentwolf75.aethermesh.data.MeshNode
import com.silentwolf75.aethermesh.data.RadioRegionPolicy
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

private enum class NodesSort {
    LAST_HEARD, SIGNAL, NAME, DISTANCE, STALE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesView(
    nodes: List<MeshNode>,
    observedRoutes: Map<Long, com.silentwolf75.aethermesh.data.RouteHopInfo>,
    phoneLocation: GeoPoint?,
    appLanguage: String,
    useImperialUnits: Boolean,
    onNodeClick: (Long) -> Unit,
    onRenameNode: (Long, String, String, String) -> Boolean,
    getTelemetryHistory: (Long) -> List<com.silentwolf75.aethermesh.data.TelemetrySample> = { emptyList() },
    connectedNodeId: Long = 0L,
    onTraceRoute: (Long) -> Boolean = { false },
    onRemoteConfig: ((MeshNode) -> Unit)? = null,
    onViewOnMap: (Long) -> Unit = {},
    onRangeTest: (Long) -> Unit = {},
    onOpenNodeDetails: (Long) -> Unit = {},
    selectedNodeId: Long? = null,
    onRefresh: (() -> Unit)? = null,
    queuedMessagesFor: (Long) -> Int = { 0 },
    routerQueueDepth: Int = 0
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
        // Oldest heard first (most stale at top)
        NodesSort.STALE -> list.sortedBy { it.lastActive }
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
        NodesSort.DISTANCE to if (appLanguage == "Spanish") "Distancia" else "Distance",
        NodesSort.STALE to if (appLanguage == "Spanish") "Por inactividad" else "By stale"
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
                            getTelemetryHistory = getTelemetryHistory,
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
                            selected = selectedNodeId != null && selectedNodeId == connectedNode.nodeId,
                            queuedCount = queuedMessagesFor(connectedNode.nodeId),
                            routerQueueDepth = routerQueueDepth
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
                        getTelemetryHistory = getTelemetryHistory,
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
                        selected = selectedNodeId != null && selectedNodeId == node.nodeId,
                        queuedCount = queuedMessagesFor(node.nodeId)
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
                            getTelemetryHistory = getTelemetryHistory,
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
                            selected = selectedNodeId != null && selectedNodeId == node.nodeId,
                            queuedCount = queuedMessagesFor(node.nodeId)
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NodeItem(
    node: MeshNode,
    observedRoutes: Map<Long, com.silentwolf75.aethermesh.data.RouteHopInfo>,
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
    selected: Boolean = false,
    getTelemetryHistory: (Long) -> List<com.silentwolf75.aethermesh.data.TelemetrySample> = { emptyList() },
    queuedCount: Int = 0,
    routerQueueDepth: Int = 0
) {
    val context = LocalContext.current
    val shortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
    val badgeColor = getBadgeColor(node.name)
    val stale = isNodeStale(node.lastActive)
    val primaryText = if (stale) TextMuted else TextLight
    var menuExpanded by remember { mutableStateOf(false) }

    val route = observedRoutes[node.nodeId]
    val hasLiveSignal = route != null && route.lastRssi != 0f
    val sigRssi = if (hasLiveSignal) route!!.lastRssi else node.rssi
    val hops = route?.hops?.takeIf { it > 0 }

    val history = remember(node.nodeId, node.lastActive, node.voltage) {
        getTelemetryHistory(node.nodeId)
    }
    val voltageTrendLabel = remember(history, appLanguage) {
        formatVoltageTrend(history, appLanguage)
    }
    val daysLabel = remember(node.lastActive, appLanguage) {
        formatDaysSinceHeard(node.lastActive, appLanguage)
    }
    val (cachedGpsMode, cachedDutySecs) = remember(node.nodeId) {
        readCachedGpsMode(context, node.nodeId)
    }
    val gpsDutyLabel = remember(cachedGpsMode, cachedDutySecs, appLanguage) {
        formatGpsDutyStatus(cachedGpsMode, cachedDutySecs, appLanguage)
    }
    val lvSafe = isLowVoltageSafeHint(node.voltage, node.isCharging)

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

    val batteryUnknown = node.battery <= 0 && node.voltage <= 0f && !node.isCharging
    val sigSnr = if (hasLiveSignal) route!!.lastSnr else node.snr
    val trend = remember(history) { voltageTrend(history) }
    val queueDepth = if (isConnectedNode) routerQueueDepth else queuedCount
    val spanish = appLanguage == "Spanish"
    val cardShape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (selected) AccentCyan.copy(alpha = 0.14f) else SurfaceDark)
            .border(
                BorderStroke(
                    1.dp,
                    when {
                        selected -> AccentCyan.copy(alpha = 0.55f)
                        isConnectedNode -> AccentCyan.copy(alpha = 0.35f)
                        else -> BorderDark.copy(alpha = 0.55f)
                    }
                ),
                cardShape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
    ) {
        // Header: identity on the left, battery and actions on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            NodeBadge(shortName = shortName, color = badgeColor, muted = stale)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.name,
                    color = primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val heardColor = if (stale) TextMuted else AccentMint
                    Icon(
                        if (isConnectedNode) Icons.Default.Bluetooth else Icons.Default.SettingsInputAntenna,
                        contentDescription = null,
                        tint = heardColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isConnectedNode) {
                            if (spanish) "Este dispositivo" else "This device"
                        } else {
                            formatLastHeard(node.lastActive, appLanguage)
                        },
                        color = heardColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    if (daysLabel != null) {
                        Text("  ·  ", color = TextMuted, fontSize = 12.sp)
                        Text(daysLabel, color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (node.isCharging) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = if (spanish) "Cargando" else "Charging",
                    tint = AccentAmber,
                    modifier = Modifier.size(14.dp)
                )
            }
            BatteryMeter(
                level = node.battery,
                charging = node.isCharging,
                unknown = batteryUnknown,
                size = 18.dp
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                if (batteryUnknown) "—" else "${node.battery}%",
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = if (spanish) "Acciones" else "Actions",
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

        // Readings: only the ones this node actually reported.
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isConnectedNode && sigRssi != 0f) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceRaised.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignalBars(rssi = sigRssi)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        buildString {
                            append("${sigRssi.toInt()} dBm")
                            if (sigSnr != 0f) append("  ·  SNR ${"%.1f".format(sigSnr)}")
                        },
                        color = TextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            if (hops != null && !isConnectedNode) {
                MetricPill(
                    icon = Icons.Default.Route,
                    text = if (hops == 1) {
                        if (spanish) "Directo" else "Direct"
                    } else {
                        if (spanish) "$hops saltos" else "$hops hops"
                    },
                    tint = if (hops == 1) AccentMint else AccentSteel
                )
            }
            if (distanceLabel != null) {
                MetricPill(icon = Icons.Default.NearMe, text = distanceLabel, tint = AccentCyan)
            }
            if (node.voltage > 0f) {
                MetricPill(
                    icon = when (trend) {
                        VoltageTrend.RISING -> Icons.AutoMirrored.Filled.TrendingUp
                        VoltageTrend.FALLING -> Icons.AutoMirrored.Filled.TrendingDown
                        else -> Icons.AutoMirrored.Filled.TrendingFlat
                    },
                    text = "%.2f V".format(node.voltage),
                    tint = if (lvSafe || trend == VoltageTrend.FALLING) AccentAmber else TextMuted
                )
            }
            if (node.lastPositionAt > 0L || hasValidPosition(node.latitude, node.longitude)) {
                MetricPill(
                    icon = Icons.Default.GpsFixed,
                    text = formatGpsLockAge(
                        if (node.lastPositionAt > 0L) node.lastPositionAt else node.lastActive,
                        appLanguage
                    ),
                    textColor = TextMuted
                )
            }
            if (queueDepth > 0) {
                MetricPill(
                    icon = Icons.Default.Schedule,
                    text = if (spanish) "$queueDepth en cola" else "$queueDepth queued",
                    tint = AccentAmber,
                    textColor = AccentAmber
                )
            }
            if (lvSafe) {
                MetricPill(
                    icon = Icons.Default.BatteryAlert,
                    text = if (spanish) "Modo bajo voltaje" else "Low-voltage mode",
                    tint = AccentAmber,
                    textColor = AccentAmber
                )
            }
        }

        // Footer: what the node is and how it is configured.
        val footer = buildList {
            if (node.model.isNotBlank()) add(Icons.Default.Memory to node.model)
            if (RadioRegionPolicy.isKnown(node.region)) {
                add(Icons.Default.Public to buildString {
                    append(RadioRegionPolicy.shortLabel(node.region))
                    if (node.loraSf in 7..12) append(" · SF${node.loraSf}")
                })
            } else if (node.loraSf in 7..12) {
                add(Icons.Default.Tune to "SF${node.loraSf}")
            }
            gpsDutyLabel?.let { add(Icons.Default.SatelliteAlt to it) }
        }
        if (footer.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderDark.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                footer.forEach { (icon, text) ->
                    FooterFact(icon = icon, text = text, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

/** Carto Dark Matter basemap — used when Dark map is enabled. */

