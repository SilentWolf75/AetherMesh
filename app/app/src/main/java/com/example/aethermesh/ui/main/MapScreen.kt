package com.example.aethermesh.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
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
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
@Composable
fun MapViewCompose(
    nodes: List<MeshNode>,
    observedRoutes: Map<Long, com.example.aethermesh.data.RouteHopInfo>,
    traceRouteState: TraceRouteState,
    viewModel: MainScreenViewModel,
    appLanguage: String,
    useImperialUnits: Boolean,
    phoneLocation: GeoPoint?,
    onPhoneLocationChanged: (GeoPoint) -> Unit,
    onNavigateToChats: () -> Unit,
    fitTraceRouteToken: Int = 0,
    focusNodeId: Long? = null,
    onFocusNodeConsumed: () -> Unit = {},
    onOpenNodeDetails: (Long) -> Unit = {}
) {
    var hasCentered by remember { mutableStateOf(false) }
    var selectedMapNode by remember { mutableStateOf<MeshNode?>(null) }
    var renamingMapNode by remember { mutableStateOf<MeshNode?>(null) }
    var selectedPingLog by remember { mutableStateOf<com.example.aethermesh.data.RangeTestLog?>(null) }

    BackHandler(enabled = selectedMapNode != null || selectedPingLog != null) {
        selectedMapNode = null
        selectedPingLog = null
    }

    val context = LocalContext.current
    val rangeTestLogs by viewModel.rangeTestLogs.collectAsStateWithLifecycle()
    val breadcrumbs = viewModel.breadcrumbs
    val mapPrefs = remember { context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE) }
    var selectedBasemap by remember { mutableStateOf(MapBasemap.load(context)) }
    var showRangeTestHistory by remember {
        mutableStateOf(mapPrefs.getBoolean("show_range_test_history", false))
    }
    var showPhoneTrack by remember {
        mutableStateOf(mapPrefs.getBoolean("show_phone_track", false))
    }
    var showDirectLinks by remember {
        mutableStateOf(mapPrefs.getBoolean("show_direct_links", false))
    }
    var showLayersMenu by remember { mutableStateOf(false) }
    var mapGeneration by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val adaptive = rememberAdaptiveLayoutInfo()
    val usingOfflineTiles = remember(mapGeneration) { OfflineMapTiles.hasArchive(context) }
    var locationPermissionTick by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationPermissionTick = android.os.SystemClock.elapsedRealtime()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasLocationPermission = remember(locationPermissionTick) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
    // Sticky map pins — raw GPS/node updates only move a badge after a real shift.
    val mapPositionCache = remember { mutableMapOf<Long, GeoPoint>() }
    val stablePhoneForMap = remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(phoneLocation) {
        val incoming = phoneLocation ?: return@LaunchedEffect
        val prev = stablePhoneForMap.value
        if (prev == null) {
            stablePhoneForMap.value = incoming
        } else {
            val movedM = calculateDistance(
                prev.latitude, prev.longitude,
                incoming.latitude, incoming.longitude
            ) * 1000.0
            if (movedM >= 12.0) stablePhoneForMap.value = incoming
        }
    }
    val mapPhoneLocation = stablePhoneForMap.value

    // Remembered MapView to avoid reloading tiles on recomposition
    val mapView = remember(mapGeneration) {
        // Must use AetherMesh UA + Carto — MAPNIK/com.example agents get HTTP 403.
        OsmMapConfig.configure(context)

        MapView(context).apply {
            OfflineMapTiles.applyTileSource(this, context, MapBasemap.load(context))
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(12.0)
            
            // Disallow parent layout from intercepting touch gestures during map drags
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }

            // Add built-in "My Location" overlay with location change hook
            val myLocationOverlay = object : MyLocationNewOverlay(GpsMyLocationProvider(context), this) {
                override fun onLocationChanged(location: android.location.Location?, source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?) {
                    super.onLocationChanged(location, source)
                    val myLoc = myLocation
                    if (myLoc != null) {
                        post {
                            onPhoneLocationChanged(myLoc)
                            viewModel.addBreadcrumb(myLoc.latitude, myLoc.longitude)
                        }
                    }
                }
                override fun draw(canvas: android.graphics.Canvas, map: MapView, shadow: Boolean) {
                    // Suppress default drawing of the person/man figure
                }
            }.apply {
                enableMyLocation()
                runOnFirstFix {
                    val myLoc = myLocation
                    if (myLoc != null) {
                        post {
                            onPhoneLocationChanged(myLoc)
                            viewModel.addBreadcrumb(myLoc.latitude, myLoc.longitude)
                            if (!hasCentered) {
                                controller.animateTo(myLoc)
                                controller.setZoom(15.0)
                                hasCentered = true
                            }
                        }
                    }
                }
            }
            overlays.add(myLocationOverlay)

            val compassOverlay = CompassOverlay(context, InternalCompassOrientationProvider(context), this).apply {
                enableCompass()
            }
            overlays.add(compassOverlay)

            val d = context.resources.displayMetrics.density
            val scaleBar = ScaleBarOverlay(this).apply {
                setAlignBottom(true)
                setScaleBarOffset((16 * d).toInt(), (24 * d).toInt())
            }
            overlays.add(scaleBar)
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val osmdroidDir = java.io.File(context.filesDir, "osmdroid").apply { mkdirs() }
                val destFile = java.io.File(osmdroidDir, "offline_map.zip")
                val info = context.contentResolver.openInputStream(uri)?.use { input ->
                    OfflineMapArchive.install(input, destFile)
                } ?: error("Could not open selected map archive")
                AppUiFeedback.show(
                    if (appLanguage == "Spanish")
                        "Mapa offline cargado (${info.entries} entradas)."
                    else
                        "Offline map loaded (${info.entries} entries).",
                    duration = SnackbarDuration.Long
                )
                mapGeneration++
            } catch (e: Exception) {
                android.util.Log.e("MapView", "Failed to import offline map", e)
                AppUiFeedback.show(
                    if (appLanguage == "Spanish") "Error al importar: ${e.localizedMessage}"
                    else "Failed to import: ${e.localizedMessage}",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.overlays.filterIsInstance<MyLocationNewOverlay>().forEach { it.disableMyLocation() }
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Switch online basemap when the user picks a source (online only).
    LaunchedEffect(selectedBasemap, mapView, mapGeneration) {
        OfflineMapTiles.applyTileSource(mapView, context, selectedBasemap)
        mapView.invalidate()
    }

    LaunchedEffect(useImperialUnits) {
        mapView.overlays.filterIsInstance<ScaleBarOverlay>().firstOrNull()?.unitsOfMeasure =
            if (useImperialUnits) ScaleBarOverlay.UnitsOfMeasure.imperial else ScaleBarOverlay.UnitsOfMeasure.metric
        mapView.invalidate()
    }

    // Update overlays reactively whenever nodes, rangeTestLogs, phoneLocation, or breadcrumbs size changes
    LaunchedEffect(nodes, observedRoutes, traceRouteState, rangeTestLogs, mapPhoneLocation, breadcrumbs.size, showRangeTestHistory, showPhoneTrack, showDirectLinks, viewModel.connectedNodeId) {
        // Keep the long-lived overlays (location, compass, scale bar); rebuild the rest
        val persistentOverlays = mapView.overlays.filter {
            it is MyLocationNewOverlay || it is CompassOverlay || it is ScaleBarOverlay
        }
        InfoWindow.closeAllInfoWindowsOn(mapView)
        mapView.overlays.clear()
        mapView.overlays.addAll(persistentOverlays)

        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                selectedMapNode = null
                selectedPingLog = null
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        })
        mapView.overlays.add(0, mapEventsOverlay)

        val tracingOnMap = traceRouteState.visible &&
            (traceRouteState.forward.isNotEmpty() || traceRouteState.returning.isNotEmpty())

        // Phone GPS track is opt-in (Layers) — otherwise it looks like "stuck" blue lines on open.
        if (showPhoneTrack && !tracingOnMap && breadcrumbs.size > 1) {
            val breadcrumbPolyline = Polyline(mapView).apply {
                outlinePaint.apply {
                    color = Color(0xFF3B82F6).copy(alpha = 0.7f).toArgb()
                    strokeWidth = 5f
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                }
                setPoints(breadcrumbs.map { org.osmdroid.util.GeoPoint(it.first, it.second) })
            }
            mapView.overlays.add(breadcrumbPolyline)
        }

        val connectedId = viewModel.connectedNodeId
        val connectedNode = resolveConnectedMeshNode(
            nodes = nodes,
            connectedId = connectedId,
            deviceName = viewModel.connectedDeviceName
        )
        val connectedLabel = connectedNode?.shortName?.takeIf { it.isNotBlank() }
            ?: connectedNode?.name?.takeIf { it.isNotBlank() }?.let { getShortName(it, connectedId) }
            ?: viewModel.connectedDeviceName?.takeIf { it.isNotBlank() }?.let { getShortName(it, connectedId) }
            ?: if (connectedId != 0L) getShortName("Node", connectedId) else null

        // Map pin for the radio we're linked to (stabilized phone GPS). No blue "person" dot.
        fun connectedMapPoint(): GeoPoint? {
            val raw = mapPhoneLocation ?: connectedNode?.takeIf {
                hasValidPosition(it.latitude, it.longitude)
            }?.let { GeoPoint(it.latitude.toDouble(), it.longitude.toDouble()) }
                ?: return null
            return if (connectedId != 0L) {
                stabilizeMapPoint(mapPositionCache, connectedId, raw, thresholdMeters = 12.0)
            } else {
                raw
            }
        }

        fun rawNodePoint(nodeId: Long): GeoPoint? {
            val isConnected = nodeId != 0L && (
                nodeId == connectedId ||
                    (connectedNode != null && nodeId == connectedNode.nodeId)
                )
            if (isConnected) return connectedMapPoint()
            val node = nodes.find { it.nodeId == nodeId } ?: return null
            if (!hasValidPosition(node.latitude, node.longitude)) return null
            return GeoPoint(node.latitude.toDouble(), node.longitude.toDouble())
        }

        fun positionFor(nodeId: Long): GeoPoint? {
            val raw = rawNodePoint(nodeId) ?: return null
            val isConnected = nodeId == connectedId ||
                (connectedNode != null && nodeId == connectedNode.nodeId)
            val threshold = if (isConnected) 12.0 else 10.0
            val cacheId = if (isConnected && connectedId != 0L) connectedId else nodeId
            return stabilizeMapPoint(mapPositionCache, cacheId, raw, threshold)
        }

        val localPoint = connectedMapPoint()

        // Drop stale cache entries for nodes that left the mesh list.
        val liveIds = nodes.map { it.nodeId }.toHashSet().also {
            if (connectedId != 0L) it.add(connectedId)
            connectedNode?.let { n -> it.add(n.nodeId) }
        }
        mapPositionCache.keys.retainAll(liveIds)

        // Direct 1-hop links are opt-in (Layers) so the map opens clean.
        // Drawn later once badge display positions exist.

        // 2. Optional range-test history (off by default). Map shows current
        // node positions; ping pins cluttered the view after long tests.
        if (showRangeTestHistory && rangeTestLogs.isNotEmpty()) {
            val validLogs = rangeTestLogs.filter { hasValidPosition(it.latitude, it.longitude) }
            val pathsByTarget = validLogs.groupBy { it.targetId }.values
            pathsByTarget.forEach { targetLogs ->
              val orderedLogs = targetLogs.sortedBy { it.timestamp }
              for (i in 0 until orderedLogs.size - 1) {
                val startLog = orderedLogs[i]
                val endLog = orderedLogs[i + 1]

                val startPoint = GeoPoint(startLog.latitude, startLog.longitude)
                val endPoint = GeoPoint(endLog.latitude, endLog.longitude)

                val rssi = endLog.rssi
                val segmentColor = when {
                    !endLog.success -> Color(0xFFEF4444) // Red for timeouts
                    rssi > -80f -> Color(0xFF10B981) // Green for strong signal
                    rssi > -105f -> Color(0xFFFBBF24) // Yellow/Orange for medium
                    else -> Color(0xFFEF4444) // Red for weak
                }

                val polyline = Polyline(mapView).apply {
                    outlinePaint.apply {
                        color = segmentColor.toArgb()
                        strokeWidth = 8f
                        strokeCap = Paint.Cap.ROUND
                    }
                    setPoints(listOf(startPoint, endPoint))
                }
                mapView.overlays.add(polyline)
              }
            }

            // Draw Range Test target node markers with sequence pins.
            // No osmdroid InfoWindow (it renders as an empty bubble) — tapping a
            // pin opens the styled ping-detail card instead.
            validLogs.forEachIndexed { index, log ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(log.latitude, log.longitude)
                    infoWindow = null
                    setOnMarkerClickListener { _, _ ->
                        selectedPingLog = log
                        selectedMapNode = null
                        true
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = if (log.success) {
                        createBadgeMarkerDrawable(
                            context,
                            "${index + 1}",
                            if (log.rssi > -90f) 0xFF10B981.toInt() else 0xFFFBBF24.toInt(),
                            isActive = true,
                            isPingMarker = true
                        )
                    } else {
                        createBadgeMarkerDrawable(
                            context,
                            "X",
                            0xFFEF4444.toInt(),
                            isActive = false,
                            isPingMarker = true
                        )
                    }
                }
                mapView.overlays.add(marker)
            }
        } else if (selectedPingLog != null) {
            selectedPingLog = null
        }

        // 3. Badge markers for every node, including the connected radio at phone GPS.
        // Nodes sitting at (nearly) the same spot get fanned out on a small ring so
        // every badge stays visible and tappable instead of stacking.
        val placedNodes = nodes.filter { node ->
            val isConnected = node.nodeId == connectedId ||
                (connectedNode != null && node.nodeId == connectedNode.nodeId)
            if (isConnected) return@filter localPoint != null
            hasValidPosition(node.latitude, node.longitude)
        }
        val nodeGroups = mutableListOf<MutableList<MeshNode>>()
        for (node in placedNodes.sortedBy { it.nodeId }) {
            val anchor = positionFor(node.nodeId) ?: continue
            val group = nodeGroups.find { g ->
                val gAnchor = positionFor(g[0].nodeId) ?: return@find false
                calculateDistance(
                    gAnchor.latitude, gAnchor.longitude,
                    anchor.latitude, anchor.longitude
                ) < 0.025 // within 25 m of stabilized pins
            }
            if (group != null) group.add(node) else nodeGroups.add(mutableListOf(node))
        }
        val displayPositions = mutableMapOf<Long, GeoPoint>()
        for (group in nodeGroups) {
            val ordered = group.sortedBy { it.nodeId }
            if (ordered.size == 1) {
                val n = ordered[0]
                displayPositions[n.nodeId] = positionFor(n.nodeId) ?: continue
            } else {
                val centers = ordered.mapNotNull { positionFor(it.nodeId) }
                if (centers.isEmpty()) continue
                val cLat = centers.map { it.latitude }.average()
                val cLon = centers.map { it.longitude }.average()
                // Stable fan: radius from node count, angles from sorted nodeId order.
                val fanRadiusM = 18.0
                ordered.forEachIndexed { i, n ->
                    val angle = 2.0 * Math.PI * i / ordered.size - Math.PI / 2.0
                    val dLat = fanRadiusM * kotlin.math.cos(angle) / 111_320.0
                    val dLon = fanRadiusM * kotlin.math.sin(angle) /
                        (111_320.0 * kotlin.math.cos(Math.toRadians(cLat)).coerceAtLeast(0.2))
                    displayPositions[n.nodeId] = GeoPoint(cLat + dLat, cLon + dLon)
                }
            }
        }

        fun routePoint(nodeId: Long): GeoPoint? =
            displayPositions[nodeId] ?: positionFor(nodeId)

        // Optional direct links — use the same badge pins so lines aren't "stuck" elsewhere.
        if (showDirectLinks && !tracingOnMap && localPoint != null) {
            val from = displayPositions[connectedId]
                ?: connectedNode?.nodeId?.let { displayPositions[it] }
                ?: localPoint
            observedRoutes.values.filter { it.hops == 1 }.forEach { route ->
                if (route.targetId == connectedId ||
                    (connectedNode != null && route.targetId == connectedNode.nodeId)
                ) return@forEach
                val hopPoint = routePoint(route.targetId) ?: return@forEach
                mapView.overlays.add(Polyline(mapView).apply {
                    outlinePaint.apply {
                        isAntiAlias = true
                        color = AccentSteel.copy(alpha = 0.65f).toArgb()
                        strokeWidth = 4f * context.resources.displayMetrics.density
                        strokeCap = Paint.Cap.ROUND
                        pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
                    }
                    setPoints(listOf(from, hopPoint))
                })
            }
        }

        // Traceroute — both directions share the same centerline (stacked, not parallel).
        if (tracingOnMap) {
            val density = context.resources.displayMetrics.density
            val outgoingColor = android.graphics.Color.parseColor("#FF9800")
            val returnColor = android.graphics.Color.parseColor("#64B5F6")
            val stroke = 4.5f * density

            fun pointsFor(ids: List<Long>): List<GeoPoint> {
                val pts = ids.mapNotNull { routePoint(it) }
                if (pts.size < 2) return emptyList()
                val spanM = routePathLengthMeters(pts)
                return if (spanM < 2.0) {
                    val base = pts.first()
                    listOf(
                        offsetGeoPoint(base, 0.0, 10.0),
                        offsetGeoPoint(base, 180.0, 10.0)
                    )
                } else {
                    pts
                }
            }

            fun drawRoute(pts: List<GeoPoint>, color: Int, dashed: Boolean) {
                if (pts.size < 2) return
                mapView.overlays.add(Polyline(mapView).apply {
                    outlinePaint.apply {
                        isAntiAlias = true
                        this.color = color
                        strokeWidth = stroke
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        style = Paint.Style.STROKE
                        if (dashed) {
                            pathEffect = DashPathEffect(floatArrayOf(stroke * 2.8f, stroke * 2.2f), 0f)
                        }
                    }
                    setPoints(ArrayList(pts))
                })
            }

            val forwardIds = (listOf(connectedId) + traceRouteState.forward.map { it.nodeId })
                .filter { it != 0L }
                .distinct()
            val outgoingIds = if (forwardIds.size >= 2) {
                forwardIds
            } else {
                listOfNotNull(connectedId.takeIf { it != 0L }, traceRouteState.targetId.takeIf { it != 0L })
            }

            val returnIds = if (traceRouteState.returning.isNotEmpty()) {
                (listOf(traceRouteState.targetId) + traceRouteState.returning.map { it.nodeId })
                    .filter { it != 0L }
            } else emptyList()

            val outgoingPts = pointsFor(outgoingIds)
            val returnPts = pointsFor(returnIds)
            val sameCenterline = outgoingPts.size >= 2 && returnPts.size >= 2 &&
                outgoingPts.first().latitude == returnPts.last().latitude &&
                outgoingPts.first().longitude == returnPts.last().longitude &&
                outgoingPts.last().latitude == returnPts.first().latitude &&
                outgoingPts.last().longitude == returnPts.first().longitude

            // Stack on one path: solid orange, then blue on top (dashed when it's the same hop).
            drawRoute(outgoingPts, outgoingColor, dashed = false)
            if (returnPts.size >= 2) {
                drawRoute(
                    // Reuse outgoing geometry when return is just the reverse — exact overlap.
                    if (sameCenterline) outgoingPts else returnPts,
                    returnColor,
                    dashed = sameCenterline
                )
            }
        }

        for (node in placedNodes) {
            val color = getBadgeColor(node.name).toArgb()
            val density = context.resources.displayMetrics.density

            // Position-privacy circle (Meshtastic-style): the node blurs its
            // broadcast position, so it is "somewhere within this radius" of the
            // reported point. Only drawn when the node reports a precision.
            if (node.nodeId != connectedId && node.positionPrecision > 0) {
                val circle = Polygon(mapView).apply {
                    val center = GeoPoint(node.latitude.toDouble(), node.longitude.toDouble())
                    points = Polygon.pointsAsCircle(center, node.positionPrecision.toDouble())
                    fillPaint.color = color
                    fillPaint.alpha = 20
                    fillPaint.style = Paint.Style.FILL

                    outlinePaint.color = color
                    outlinePaint.alpha = 90
                    outlinePaint.style = Paint.Style.STROKE
                    outlinePaint.strokeWidth = 1.5f * density
                }
                mapView.overlays.add(circle)
            }

            val marker = Marker(mapView).apply {
                position = displayPositions[node.nodeId]
                    ?: GeoPoint(node.latitude.toDouble(), node.longitude.toDouble())
                infoWindow = null
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                val nodeShortName = when {
                    connectedNode != null && node.nodeId == connectedNode.nodeId ->
                        connectedLabel ?: node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
                    node.nodeId == connectedId ->
                        connectedLabel ?: node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
                    else ->
                        node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
                }
                val isNodeActive = !isNodeStale(node.lastActive)
                icon = createBadgeMarkerDrawable(context, nodeShortName, color, isActive = isNodeActive, isPingMarker = false)

                setOnMarkerClickListener { _, _ ->
                    selectedMapNode = node
                    selectedPingLog = null
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        // Connected radio not yet in the node DB — still show its real short name (never "ME").
        if (connectedNode == null && localPoint != null && !connectedLabel.isNullOrBlank()) {
            mapView.overlays.add(Marker(mapView).apply {
                position = localPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                icon = createBadgeMarkerDrawable(
                    context,
                    connectedLabel,
                    getBadgeColor(connectedLabel).toArgb(),
                    isActive = true,
                    isPingMarker = false
                )
                setOnMarkerClickListener { _, _ -> true }
            })
        }

        // Auto-center on first valid node if we haven't already centered
        if (!hasCentered && nodes.isNotEmpty()) {
            val validNode = nodes.firstOrNull { hasValidPosition(it.latitude, it.longitude) }
            if (validNode != null) {
                mapView.controller.setCenter(GeoPoint(validNode.latitude.toDouble(), validNode.longitude.toDouble()))
                mapView.controller.setZoom(15.0)
                hasCentered = true
            }
        }

        mapView.invalidate()
    }

    // "View on map" from traceroute — fit the hop positions into view.
    LaunchedEffect(fitTraceRouteToken) {
        if (fitTraceRouteToken <= 0) return@LaunchedEffect
        val connectedId = viewModel.connectedNodeId
        val idSet = linkedSetOf<Long>()
        if (connectedId != 0L) idSet += connectedId
        if (traceRouteState.targetId != 0L) idSet += traceRouteState.targetId
        traceRouteState.forward.forEach { idSet += it.nodeId }
        traceRouteState.returning.forEach { idSet += it.nodeId }
        val points = idSet.mapNotNull { id ->
            val node = nodes.find { it.nodeId == id }
                ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (id and 0xFFFFFFFFL) }
            if (node != null && hasValidPosition(node.latitude, node.longitude)) {
                GeoPoint(node.latitude.toDouble(), node.longitude.toDouble())
            } else null
        } + listOfNotNull(phoneLocation)
        if (points.isEmpty()) return@LaunchedEffect
        val bb = BoundingBox.fromGeoPoints(points)
        val pad = (56 * context.resources.displayMetrics.density).toInt()
        if (bb.latitudeSpan < 0.0005 && bb.longitudeSpanWithDateLine < 0.0005) {
            mapView.controller.animateTo(bb.centerWithDateLine)
            mapView.controller.setZoom(16.0)
        } else {
            mapView.zoomToBoundingBox(bb, true, pad)
        }
        hasCentered = true
    }

    // NodeDetails / Nodes overflow "View on map" — fly to that node.
    LaunchedEffect(focusNodeId, nodes) {
        val id = focusNodeId ?: return@LaunchedEffect
        val node = nodes.find { it.nodeId == id }
            ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (id and 0xFFFFFFFFL) }
        if (node != null && hasValidPosition(node.latitude, node.longitude)) {
            selectedMapNode = node
            mapView.controller.animateTo(GeoPoint(node.latitude.toDouble(), node.longitude.toDouble()))
            mapView.controller.setZoom(16.0)
            hasCentered = true
            onFocusNodeConsumed()
        } else if (nodes.isNotEmpty()) {
            onFocusNodeConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // expandable Heard (No GPS) Nodes overlay Card — top-start, clear of compass/legend
        val noGpsNodes = nodes.filter { it.nodeId != viewModel.connectedNodeId }.filterNot { hasValidPosition(it.latitude, it.longitude) }
        var showNoGpsNodesList by remember { mutableStateOf(false) }
        val tracingLegend = traceRouteState.visible &&
            (traceRouteState.forward.isNotEmpty() || traceRouteState.returning.isNotEmpty())
        
        if (noGpsNodes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 16.dp, end = if (tracingLegend) 140.dp else 16.dp)
                    .widthIn(max = 240.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showNoGpsNodesList = !showNoGpsNodesList },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${t("Heard (No GPS)", appLanguage)} (${noGpsNodes.size})",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (showNoGpsNodesList) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showNoGpsNodesList) {
                                    if (appLanguage == "Spanish") "Ocultar lista" else "Collapse list"
                                } else {
                                    if (appLanguage == "Spanish") "Mostrar lista" else "Expand list"
                                },
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        if (showNoGpsNodesList) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                noGpsNodes.forEach { node ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkBackground.copy(alpha = 0.6f))
                                            .clickable {
                                                onOpenNodeDetails(node.nodeId)
                                            }
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 24.dp, height = 18.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(getBadgeColor(node.name)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(getShortName(node.name, node.nodeId), color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = node.name,
                                            color = TextLight,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Map controls — pinch for zoom; layers + locate + mesh home.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .width(44.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FloatingActionButton(
                onClick = { showLayersMenu = !showLayersMenu },
                containerColor = SurfaceDark.copy(alpha = 0.92f),
                contentColor = if (showLayersMenu) AccentMint else AccentCyan,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = if (appLanguage == "Spanish") "Capas del mapa" else "Map layers",
                    modifier = Modifier.size(20.dp)
                )
            }
            FloatingActionButton(
                onClick = {
                    if (!hasLocationPermission) {
                        AppUiFeedback.show(
                            text = if (appLanguage == "Spanish")
                                "Activa el permiso de ubicación para ver tu posición."
                            else
                                "Allow location permission to show your position.",
                            actionLabel = if (appLanguage == "Spanish") "Ajustes" else "Settings"
                        ) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                        return@FloatingActionButton
                    }
                    val myLocationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                    val myLoc = myLocationOverlay?.myLocation
                    if (myLoc != null) {
                        mapView.controller.animateTo(myLoc)
                        mapView.controller.setZoom(16.0)
                    } else {
                        AppUiFeedback.show(
                            if (appLanguage == "Spanish") "Esperando ubicación GPS..." else "Waiting for GPS location..."
                        )
                    }
                },
                containerColor = SurfaceDark.copy(alpha = 0.92f),
                contentColor = AccentCyan,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = if (appLanguage == "Spanish") "Mi ubicación" else "My location",
                    modifier = Modifier.size(20.dp)
                )
            }
            // Center on Mesh (Home)
            FloatingActionButton(
                onClick = {
                    val validNodes = nodes.filter { hasValidPosition(it.latitude, it.longitude) }
                    if (validNodes.isNotEmpty()) {
                        val points = validNodes.map {
                            GeoPoint(it.latitude.toDouble(), it.longitude.toDouble())
                        } + listOfNotNull(phoneLocation)
                        val bb = BoundingBox.fromGeoPoints(points)
                        // Co-located points give a degenerate box — just center on them
                        if (bb.latitudeSpan < 0.0005 && bb.longitudeSpanWithDateLine < 0.0005) {
                            mapView.controller.animateTo(bb.centerWithDateLine)
                            mapView.controller.setZoom(16.0)
                        } else {
                            val pad = (48 * context.resources.displayMetrics.density).toInt()
                            mapView.zoomToBoundingBox(bb, true, pad)
                        }
                    } else {
                        AppUiFeedback.show(
                            if (appLanguage == "Spanish") "No hay nodos con posición GPS activa"
                            else "No nodes with active GPS position"
                        )
                    }
                },
                containerColor = SurfaceDark.copy(alpha = 0.85f),
                contentColor = AccentMint,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = if (appLanguage == "Spanish") "Centrar en la malla" else "Center on mesh",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        val nodesWithGps = remember(nodes) {
            nodes.count { hasValidPosition(it.latitude, it.longitude) }
        }

        // Status chips: offline tiles / no mesh GPS yet
        if (usingOfflineTiles || nodesWithGps == 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp, end = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (usingOfflineTiles) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AccentMint.copy(alpha = 0.18f))
                            .border(BorderStroke(1.dp, AccentMint.copy(alpha = 0.45f)), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (appLanguage == "Spanish") "Mapa offline" else "Offline map",
                            color = AccentMint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (nodesWithGps == 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AccentAmber.copy(alpha = 0.16f))
                            .border(BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f)), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (appLanguage == "Spanish") "Sin GPS de nodos" else "No node GPS yet",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (!hasLocationPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.45f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (usingOfflineTiles || nodesWithGps == 0) 48.dp else 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(if (adaptive.isLandscape) 0.55f else 1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (appLanguage == "Spanish") "Ubicación desactivada" else "Location permission off",
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (appLanguage == "Spanish")
                            "Activa la ubicación para ver tu teléfono en el mapa y usar la prueba de rango."
                        else
                            "Allow location to show your phone on the map and run range tests.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    TextButton(
                        onClick = {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text(
                            if (appLanguage == "Spanish") "Abrir ajustes" else "Open settings",
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Layers menu popup — side panel in landscape so it doesn't fight FABs
        if (showLayersMenu) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .align(
                        if (adaptive.isLandscape) Alignment.CenterStart else Alignment.BottomStart
                    )
                    .padding(
                        bottom = if (adaptive.isLandscape) 16.dp else 16.dp,
                        start = 16.dp,
                        end = if (adaptive.isLandscape) 16.dp else 72.dp,
                        top = if (adaptive.isLandscape) 16.dp else 0.dp
                    )
                    .then(
                        if (adaptive.isLandscape) Modifier
                            .fillMaxHeight(0.9f)
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(0.42f)
                        else Modifier.fillMaxWidth()
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (appLanguage == "Spanish") "Capas del Mapa" else "Map Layers",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (usingOfflineTiles) {
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Usando mapa offline importado"
                            else
                                "Using imported offline map",
                            color = AccentMint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        Text(
                            text = if (appLanguage == "Spanish") "Mapa base" else "Basemap",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                        )
                        MapBasemap.entries.forEach { basemap ->
                            val selected = selectedBasemap == basemap
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) AccentCyan.copy(alpha = 0.18f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selectedBasemap = basemap
                                        MapBasemap.save(context, basemap)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        selectedBasemap = basemap
                                        MapBasemap.save(context, basemap)
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text(
                                        text = basemap.label(appLanguage),
                                        color = TextLight,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = when (basemap) {
                                            MapBasemap.CARTO_STREETS ->
                                                if (appLanguage == "Spanish") "Más fiable (recomendado)" else "Most reliable (recommended)"
                                            MapBasemap.CARTO_DARK ->
                                                if (appLanguage == "Spanish") "Misma red, tema oscuro" else "Same CDN, dark theme"
                                            MapBasemap.OPEN_TOPO ->
                                                if (appLanguage == "Spanish") "Relieve / outdoor" else "Terrain / outdoor"
                                            MapBasemap.OSM_DE ->
                                                if (appLanguage == "Spanish") "Calles (alternativa OSM)" else "Streets (OSM alternative)"
                                        },
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                OsmMapConfig.clearTileCache(context)
                                OfflineMapTiles.applyTileSource(mapView, context, selectedBasemap)
                                mapView.invalidate()
                                AppUiFeedback.show(
                                    if (appLanguage == "Spanish") "Caché de teselas borrada"
                                    else "Map tile cache cleared"
                                )
                            },
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        ) {
                            Text(
                                if (appLanguage == "Spanish") "Borrar caché de teselas" else "Clear tile cache",
                                color = AccentAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (appLanguage == "Spanish") "Historial de rango" else "Range test history",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = showRangeTestHistory,
                            onCheckedChange = {
                                showRangeTestHistory = it
                                mapPrefs.edit().putBoolean("show_range_test_history", it).apply()
                            },
                            modifier = Modifier
                        )
                    }
                    if (showRangeTestHistory && rangeTestLogs.isEmpty()) {
                        Text(
                            if (appLanguage == "Spanish")
                                "Sin pines aún — abre un nodo → Prueba de rango."
                            else
                                "No pins yet — open a node → Range test.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (appLanguage == "Spanish") "Rastro GPS" else "Phone GPS track",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = showPhoneTrack,
                            onCheckedChange = {
                                showPhoneTrack = it
                                mapPrefs.edit().putBoolean("show_phone_track", it).apply()
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (appLanguage == "Spanish") "Enlaces 1 salto" else "Direct 1-hop links",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = showDirectLinks,
                            onCheckedChange = {
                                showDirectLinks = it
                                mapPrefs.edit().putBoolean("show_direct_links", it).apply()
                            }
                        )
                    }
                    Text(
                        text = if (appLanguage == "Spanish")
                            "Los círculos alrededor de los nodos muestran su radio de privacidad de posición."
                        else
                            "Circles around nodes show their position-privacy radius.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderDark)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Import offline map button
                    Button(
                        onClick = { importLauncher.launch("application/zip") },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f), contentColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(
                            text = if (appLanguage == "Spanish") "Importar Mapa (.zip)" else "Import Map (.zip)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (usingOfflineTiles) {
                        val info = remember(mapGeneration) { OfflineMapTiles.archiveInfo(context) }
                        val sizeMb = ((info?.compressedBytes ?: 0L) / (1024.0 * 1024.0))
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Archivo: ${info?.entries ?: 0} entradas · %.1f MB".format(sizeMb)
                            else
                                "Archive: ${info?.entries ?: 0} entries · %.1f MB".format(sizeMb),
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                        )
                        TextButton(
                            onClick = {
                                if (OfflineMapTiles.clearArchive(context)) {
                                    AppUiFeedback.show(
                                        if (appLanguage == "Spanish")
                                            "Mapa offline eliminado. Usando tiles en línea."
                                        else
                                            "Offline map removed. Using online tiles."
                                    )
                                    mapGeneration++
                                } else {
                                    AppUiFeedback.show(
                                        if (appLanguage == "Spanish")
                                            "No se pudo eliminar el mapa offline."
                                        else
                                            "Could not remove offline map."
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (appLanguage == "Spanish") "Quitar mapa offline" else "Remove offline map",
                                color = AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Export / clear tracklog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportBreadcrumbsToKml(context, breadcrumbs, appLanguage) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentMint.copy(alpha = 0.2f), contentColor = AccentMint),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(
                                text = if (appLanguage == "Spanish") "Exportar KML" else "Export Track (.kml)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.clearBreadcrumbs()
                                AppUiFeedback.show(
                                    if (appLanguage == "Spanish") "Rastro borrado" else "Track cleared"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.15f), contentColor = AccentRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            enabled = breadcrumbs.isNotEmpty()
                        ) {
                            Text(
                                text = if (appLanguage == "Spanish") "Borrar rastro" else "Clear track",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (tracingLegend) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 52.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFFF9800))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (appLanguage == "Spanish") "Ruta de ida" else "Outgoing route",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                    }
                    if (traceRouteState.returning.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF64B5F6))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (appLanguage == "Spanish") "Ruta de vuelta" else "Return route", color = TextLight, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { viewModel.clearTraceRouteResult() },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(if (appLanguage == "Spanish") "Borrar ruta" else "Clear route", color = AccentMint, fontSize = 12.sp)
                    }
                }
            }
        }
        if (showRangeTestHistory && !tracingLegend) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 56.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(if (appLanguage == "Spanish") "PINS DE RANGO" else "RANGE PINS", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (rangeTestLogs.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (appLanguage == "Spanish")
                                "Abre un nodo → Prueba de rango primero."
                            else
                                "Open a node → Range test first.",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentMint))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ACK", color = TextMuted, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentRed))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (appLanguage == "Spanish") "Tiempo agotado" else "Timeout", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        val activeMapNode = selectedMapNode?.let { sel ->
            nodes.find { it.nodeId == sel.nodeId }
        } ?: selectedMapNode
        // Compact map callout — tap opens full Details (Meshtastic-style).
        val mapRelativeTick = rememberRelativeTimeTick()
        activeMapNode?.let { node ->
            val nodeShortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
            @Suppress("UNUSED_VARIABLE")
            val _heardClock = mapRelativeTick
            val stale = isNodeStale(node.lastActive)
            val mapPrimaryText = if (stale) TextMuted else TextLight
            val nearbyClusterCount = remember(node.nodeId, nodes) {
                if (!hasValidPosition(node.latitude, node.longitude)) 1
                else nodes.count { other ->
                    hasValidPosition(other.latitude, other.longitude) &&
                        calculateDistance(
                            node.latitude.toDouble(), node.longitude.toDouble(),
                            other.latitude.toDouble(), other.longitude.toDouble()
                        ) < 0.025
                }.coerceAtLeast(1)
            }
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp, start = 16.dp, end = 72.dp)
                    .fillMaxWidth()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedMapNode = null
                            onOpenNodeDetails(node.nodeId)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(getBadgeColor(node.name).copy(alpha = if (stale) 0.45f else 1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                nodeShortName,
                                color = Color.Black,
                                fontSize = if (nodeShortName.length > 2) 9.sp else 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(node.name, color = mapPrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                buildString {
                                    append(formatLastHeard(node.lastActive, appLanguage))
                                    if (distanceLabel != null) append("  ·  $distanceLabel")
                                },
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            if (nearbyClusterCount > 1) {
                                Text(
                                    if (appLanguage == "Spanish")
                                        "$nearbyClusterCount nodos a ~25 m — pines separados"
                                    else
                                        "$nearbyClusterCount nodes within ~25 m — pins fanned out",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val route = observedRoutes[node.nodeId]
                                val hasLiveSignal = route != null && route.lastRssi != 0f
                                val sigRssi = if (hasLiveSignal) route!!.lastRssi else node.rssi
                                if (sigRssi != 0f) {
                                    SignalBars(rssi = sigRssi)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${sigRssi.toInt()} dBm", color = TextMuted, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Icon(
                                    Icons.Default.BatteryFull,
                                    contentDescription = null,
                                    tint = if (node.battery <= 0 && node.voltage <= 0f && !node.isCharging)
                                        TextMuted
                                    else
                                        batteryLevelColor(node.battery),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    if (node.battery <= 0 && node.voltage <= 0f && !node.isCharging) "—"
                                    else "${node.battery}%",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                route?.hops?.takeIf { it > 0 }?.let { h ->
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "$h ${if (h == 1) t("Hop", appLanguage) else t("Hops", appLanguage)}",
                                        color = AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                if (appLanguage == "Spanish") "Toca para detalles" else "Tap for details",
                                color = AccentMint.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        IconButton(
                            onClick = { selectedMapNode = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = if (appLanguage == "Spanish") "Cerrar" else "Close",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = if (appLanguage == "Spanish") "Detalles" else "Details",
                            tint = AccentMint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Ping detail card — shown when a range-test pin is tapped
        if (activeMapNode == null) selectedPingLog?.let { log ->
            val validLogs = rangeTestLogs.filter { hasValidPosition(it.latitude, it.longitude) }
            val pingNumber = validLogs.indexOfFirst { it.id == log.id } + 1
            val statusColor = if (log.success) AccentMint else AccentRed
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp, start = 16.dp, end = 72.dp)
                    .fillMaxWidth()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(statusColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (log.success) "$pingNumber" else "X",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (appLanguage == "Spanish") "Prueba #$pingNumber" else "Ping #$pingNumber",
                                        color = TextLight,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(log.timestamp)),
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when {
                                        log.success -> "ACK"
                                        appLanguage == "Spanish" -> "TIEMPO AGOTADO"
                                        else -> "TIMEOUT"
                                    },
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { selectedPingLog = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                Icons.Default.Close,
                                contentDescription = if (appLanguage == "Spanish") "Cerrar" else "Close",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                                }
                            }
                        }

                        if (log.success) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = BorderDark)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(
                                        text = if (appLanguage == "Spanish") "ACK recibido" else "ACK signal",
                                        color = TextMuted, fontSize = 9.sp
                                    )
                                    Text(
                                        "${log.rssi.toInt()} dBm / ${log.snr} dB",
                                        color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (log.remoteRssi != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (appLanguage == "Spanish") "En el destino" else "At target",
                                            color = TextMuted, fontSize = 9.sp
                                        )
                                        Text(
                                            "${log.remoteRssi.toInt()} dBm" + (log.remoteSnr?.let { " / $it dB" } ?: ""),
                                            color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val targetNode = nodes.find {
                                        it.nodeId == log.targetId && hasValidPosition(it.latitude, it.longitude)
                                    }
                                    if (targetNode != null) {
                                        val distKm = calculateDistance(
                                            log.latitude, log.longitude,
                                            targetNode.latitude.toDouble(), targetNode.longitude.toDouble()
                                        )
                                        Text(
                                            text = if (appLanguage == "Spanish") "Distancia" else "Distance",
                                            color = TextMuted, fontSize = 9.sp
                                        )
                                        Text(
                                            text = if (useImperialUnits)
                                                "%.0f ft".format(distKm * 3280.84)
                                            else
                                                "%.0f m".format(distKm * 1000),
                                            color = AccentMint, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                        )
                                    } else if (log.speedMps != null) {
                                        Text(
                                            text = if (appLanguage == "Spanish") "Velocidad" else "Speed",
                                            color = TextMuted, fontSize = 9.sp
                                        )
                                        Text(
                                            text = if (useImperialUnits)
                                                "%.0f mph".format(log.speedMps * 2.237)
                                            else
                                                "%.0f km/h".format(log.speedMps * 3.6),
                                            color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamingMapNode?.let { node ->
        RenameNodeDialog(
            node = node,
            connectedNodeId = viewModel.connectedNodeId,
            appLanguage = appLanguage,
            onRename = { id, longName, shortName, password ->
                viewModel.renameNode(id, longName, shortName, password)
            },
            onDismiss = { renamingMapNode = null }
        )
    }

}



