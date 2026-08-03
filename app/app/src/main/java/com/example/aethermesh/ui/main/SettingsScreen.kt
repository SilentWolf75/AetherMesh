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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@Composable
fun SettingsView(
    viewModel: MainScreenViewModel,
    isConnected: Boolean,
    initialCategory: SettingsCategory? = null,
    onInitialCategoryConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val connectedNode = resolveConnectedMeshNode(
        nodes = nodes,
        connectedId = viewModel.connectedNodeId,
        deviceName = viewModel.connectedDeviceName
    )

    val isDeviceAuthenticated by viewModel.isDeviceAuthenticated.collectAsStateWithLifecycle()

    var nodeName by remember { mutableStateOf("") }
    var nodeShortName by remember { mutableStateOf("") }
    var sf by remember { mutableIntStateOf(11) }
    var bw by remember { mutableFloatStateOf(125f) }
    var txPower by remember { mutableIntStateOf(22) }
    var region by remember { mutableIntStateOf(0) } // 0 = US915, 1 = EU868
    var role by remember { mutableIntStateOf(0) } // 0 = Client, 1 = Router, 2 = Low-Power Repeater
    var meshHopLimit by remember { mutableIntStateOf(4) }
    var rebroadcastTxdelayX100 by remember { mutableIntStateOf(100) }
    var telemetryIntervalSecs by remember { mutableIntStateOf(60) }
    var screenTimeoutSecs by remember { mutableIntStateOf(30) }
    var powerSaveModeEnabled by remember { mutableStateOf(false) }
    var positionPrecisionM by remember { mutableIntStateOf(0) }
    var nodeGpsMode by remember { mutableIntStateOf(0) } // 0=on, 1=off, 2=duty-cycle
    var gpsDutyIntervalSecs by remember { mutableIntStateOf(900) }
    var fixedPositionEnabled by remember { mutableStateOf(false) }
    var fixedLatInput by remember { mutableStateOf("") }
    var fixedLonInput by remember { mutableStateOf("") }
    var fixedAltInput by remember { mutableStateOf("") }

    var isExpandedSF by remember { mutableStateOf(false) }
    var isExpandedBW by remember { mutableStateOf(false) }
    var isExpandedRegion by remember { mutableStateOf(false) }
    var isExpandedRole by remember { mutableStateOf(false) }
    var isExpandedTelemetry by remember { mutableStateOf(false) }
    var isExpandedPosPrecision by remember { mutableStateOf(false) }
    var isExpandedScreenTimeout by remember { mutableStateOf(false) }
    var showConsoleLogs by remember { mutableStateOf(false) }
    var showIntroDialog by remember { mutableStateOf(false) }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var changeCurrentPasswordInput by remember { mutableStateOf("") }
    var changeNewPasswordInput by remember { mutableStateOf("") }
    var changePasswordError by remember { mutableStateOf(false) }

    var showClearChatDialog by remember { mutableStateOf(false) }
    var showResetNodesDialog by remember { mutableStateOf(false) }
    var showRepeaterConfirmDialog by remember { mutableStateOf(false) }
    var channelPendingDelete by remember { mutableStateOf<ChannelConfig?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
    var bgAlertsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("bg_alerts_enabled", true)) }
    var useImperialUnitsSetting by remember { mutableStateOf(sharedPrefs.getBoolean("use_imperial_units", true)) }
    var enablePhoneGpsSharing by remember { mutableStateOf(sharedPrefs.getBoolean("enable_phone_gps_sharing", true)) }

    val consoleMessages by viewModel.messages.collectAsStateWithLifecycle()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = org.json.JSONObject().apply {
                    put("node_name", nodeName)
                    put("node_short_name", nodeShortName)
                    put("lora_sf", sf)
                    put("lora_bw", bw.toDouble())
                    put("lora_tx_power", txPower)
                    put("region", region)
                    put("node_role", role)
                    put("telemetry_interval", telemetryIntervalSecs)
                    put("screen_timeout", screenTimeoutSecs)
                    put("power_save_mode", powerSaveModeEnabled)
                    put("position_precision", positionPrecisionM)
                    put("gps_mode", nodeGpsMode)
                    put("gps_duty_interval_secs", gpsDutyIntervalSecs)
                    put("fixed_position", fixedPositionEnabled)
                    put("fixed_latitude", fixedLatInput.toFloatOrNull() ?: 0f)
                    put("fixed_longitude", fixedLonInput.toFloatOrNull() ?: 0f)
                    put("fixed_altitude", fixedAltInput.toIntOrNull() ?: 0)
                }.toString(2)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                AppUiFeedback.show(if (sharedPrefs.getString("app_language", "English") == "Spanish")
                        "Ajustes exportados correctamente"
                    else
                        "Settings exported successfully", duration = SnackbarDuration.Short)
            } catch (e: Exception) {
                AppUiFeedback.show(if (sharedPrefs.getString("app_language", "English") == "Spanish")
                        "Error al exportar ajustes: ${e.message}"
                    else
                        "Failed to export settings: ${e.message}", duration = SnackbarDuration.Long)
            }
        }
    }

    val restoreSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (jsonString != null) {
                    val json = org.json.JSONObject(jsonString)
                    nodeName = json.optString("node_name", nodeName)
                    nodeShortName = json.optString("node_short_name", nodeShortName)
                    sf = json.optInt("lora_sf", sf)
                    bw = json.optDouble("lora_bw", bw.toDouble()).toFloat()
                    txPower = json.optInt("lora_tx_power", txPower)
                    region = json.optInt("region", region)
                    role = json.optInt("node_role", role)
                    telemetryIntervalSecs = json.optInt("telemetry_interval", telemetryIntervalSecs)
                    screenTimeoutSecs = json.optInt("screen_timeout", screenTimeoutSecs)
                    powerSaveModeEnabled = json.optBoolean("power_save_mode", powerSaveModeEnabled)
                    positionPrecisionM = json.optInt("position_precision", positionPrecisionM)
                    nodeGpsMode = json.optInt("gps_mode", nodeGpsMode).coerceIn(0, 2)
                    gpsDutyIntervalSecs = snapGpsDutyIntervalSecs(
                        json.optInt("gps_duty_interval_secs", gpsDutyIntervalSecs)
                    )
                    fixedPositionEnabled = json.optBoolean("fixed_position", fixedPositionEnabled)
                    fixedLatInput = json.optDouble("fixed_latitude", fixedLatInput.toDoubleOrNull() ?: 0.0).toFloat().toString()
                    fixedLonInput = json.optDouble("fixed_longitude", fixedLonInput.toDoubleOrNull() ?: 0.0).toFloat().toString()
                    fixedAltInput = json.optInt("fixed_altitude", fixedAltInput.toIntOrNull() ?: 0).toString()
                    
                    AppUiFeedback.show(if (sharedPrefs.getString("app_language", "English") == "Spanish")
                            "Ajustes importados. Pulsa Aplicar Ajustes para enviarlos al dispositivo."
                        else
                            "Settings imported. Tap Apply Settings to send them to the device.", duration = SnackbarDuration.Long)
                }
            } catch (e: Exception) {
                AppUiFeedback.show(if (sharedPrefs.getString("app_language", "English") == "Spanish")
                        "Error al importar ajustes: ${e.message}"
                    else
                        "Failed to import settings: ${e.message}", duration = SnackbarDuration.Long)
            }
        }
    }

    var channelsList by remember { mutableStateOf<List<ChannelConfig>>(emptyList()) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var showImportChannelDialog by remember { mutableStateOf(false) }
    var showEditChannelDialog by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableStateOf<ChannelConfig?>(null) }
    var importChannelLinkInput by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    val settingsScrollState = rememberScrollState()
    LaunchedEffect(activeCategory) {
        settingsScrollState.scrollTo(0)
    }
    LaunchedEffect(initialCategory) {
        val cat = initialCategory ?: return@LaunchedEffect
        activeCategory = cat
        onInitialCategoryConsumed()
    }

    var appTheme by remember { mutableStateOf(sharedPrefs.getString("app_theme", "System") ?: "System") }
    var appLanguage by remember { mutableStateOf(sharedPrefs.getString("app_language", "English") ?: "English") }
    var phoneLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isExpandedTheme by remember { mutableStateOf(false) }
    var isExpandedLanguage by remember { mutableStateOf(false) }

    var ecdhKeys by remember { mutableStateOf(Pair("", "")) }
    var showPrivateKey by remember { mutableStateOf(false) }
    var showRegenKeysDialog by remember { mutableStateOf(false) }
    val settingsTwoPane = rememberAdaptiveLayoutInfo().useTwoPane

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "app_language") {
                appLanguage = sharedPrefs.getString("app_language", "English") ?: "English"
            }
            if (key == "enable_phone_gps_sharing") {
                enablePhoneGpsSharing = sharedPrefs.getBoolean("enable_phone_gps_sharing", true)
            }
            if (key == "use_imperial_units") {
                useImperialUnitsSetting = sharedPrefs.getBoolean("use_imperial_units", true)
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Populate the config form ONCE per connected node. Keying this on `nodes`
    // used to clobber in-progress edits (name, sliders) every time a telemetry
    // packet refreshed the node list.
    var configLoadedForNode by remember { mutableStateOf(0L) }
    var lastDeviceConfigSyncEpoch by remember { mutableIntStateOf(-1) }
    val deviceConfigSyncEpoch by viewModel.deviceConfigSyncEpoch.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.connectedNodeId, nodes, deviceConfigSyncEpoch) {
        channelsList = viewModel.getChannelsList()
        ecdhKeys = viewModel.getOrCreateEcdhKeys()
        val nodeKey = viewModel.connectedNodeId
        val shouldReloadConfig = nodeKey != 0L && (
            nodeKey != configLoadedForNode || deviceConfigSyncEpoch != lastDeviceConfigSyncEpoch
        )
        if (shouldReloadConfig) {
            configLoadedForNode = nodeKey
            lastDeviceConfigSyncEpoch = deviceConfigSyncEpoch
            val nodePrefs = context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
            val matchedNode = nodes.find { it.nodeId == nodeKey }
            nodeName = nodePrefs.getString("node_name", null)?.takeIf { it.isNotBlank() }
                ?: matchedNode?.name?.replace("AetherMesh-", "")?.replace("Node ", "")
                ?: ""
            nodeShortName = nodePrefs.getString("node_short_name", null)?.takeIf { it.isNotBlank() }
                ?: matchedNode?.shortName
                ?: ""
            sf = nodePrefs.getInt("lora_sf", 11)
            bw = nodePrefs.getFloat("lora_bw", 125f)
            txPower = nodePrefs.getInt("lora_tx_power", 22)
            region = nodePrefs.getInt("region", 0)
            role = nodePrefs.getInt("node_role", 0)
            meshHopLimit = nodePrefs.getInt("mesh_hop_limit", 4).coerceIn(1, 8)
            rebroadcastTxdelayX100 = nodePrefs.getInt("rebroadcast_txdelay_x100", 100).let {
                if (it <= 0) 100 else it.coerceIn(50, 200)
            }
            telemetryIntervalSecs = nodePrefs.getInt("telemetry_interval", 60)
            screenTimeoutSecs = nodePrefs.getInt("screen_timeout", 30)
            powerSaveModeEnabled = nodePrefs.getBoolean("power_save_mode", false)
            positionPrecisionM = nodePrefs.getInt("position_precision", 0)
            nodeGpsMode = nodePrefs.getInt("gps_mode", 0).coerceIn(0, 2)
            gpsDutyIntervalSecs = snapGpsDutyIntervalSecs(
                nodePrefs.getInt("gps_duty_interval_secs", 900)
            )
            fixedPositionEnabled = nodePrefs.getBoolean("fixed_position", false)
            val fLat = nodePrefs.getFloat("fixed_latitude", 0f)
            val fLon = nodePrefs.getFloat("fixed_longitude", 0f)
            val fAlt = nodePrefs.getInt("fixed_altitude", 0)
            fixedLatInput = if (fLat != 0f) fLat.toString() else ""
            fixedLonInput = if (fLon != 0f) fLon.toString() else ""
            fixedAltInput = if (fAlt != 0) fAlt.toString() else ""
        }
    }

    val saveConfigAndNotify = fun() {
        val lat = fixedLatInput.toFloatOrNull() ?: 0f
        val lon = fixedLonInput.toFloatOrNull() ?: 0f
        val latLonOk = lat in -90f..90f && lon in -180f..180f && !(lat == 0f && lon == 0f)
        if (fixedPositionEnabled && !latLonOk) {
            AppUiFeedback.show(
                if (appLanguage == "Spanish")
                    "Posición fija inválida — usa coordenadas reales (no 0,0)."
                else
                    "Invalid fixed position — use real coordinates (not 0,0).",
                duration = SnackbarDuration.Long
            )
            return
        }
        val success = viewModel.sendNodeConfig(
            name = nodeName.trim(),
            shortName = nodeShortName.trim(),
            sf = sf,
            bw = bw,
            txPower = txPower,
            region = region,
            role = role,
            telemetryInterval = telemetryIntervalSecs,
            screenTimeout = screenTimeoutSecs,
            powerSaveMode = powerSaveModeEnabled,
            positionPrecision = positionPrecisionM,
            gpsMode = nodeGpsMode,
            gpsDutyIntervalSecs = gpsDutyIntervalSecs,
            fixedPosition = fixedPositionEnabled,
            fixedLatitude = lat,
            fixedLongitude = lon,
            fixedAltitude = fixedAltInput.toIntOrNull() ?: 0,
            meshHopLimit = meshHopLimit,
            rebroadcastTxdelayX100 = rebroadcastTxdelayX100
        )
        if (success) {
            val nodeKey = viewModel.connectedNodeId
            if (nodeKey != 0L) {
                val nodePrefs = context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
                nodePrefs.edit().apply {
                    putString("node_name", nodeName.trim())
                    putString("node_short_name", nodeShortName.trim())
                    putInt("lora_sf", sf)
                    putFloat("lora_bw", bw)
                    putInt("lora_tx_power", txPower)
                    putInt("region", region)
                    putBoolean("region_configured", true)
                    putInt("node_role", role)
                    putInt("mesh_hop_limit", meshHopLimit.coerceIn(1, 8))
                    putInt("rebroadcast_txdelay_x100", rebroadcastTxdelayX100.coerceIn(50, 200))
                    putInt("telemetry_interval", telemetryIntervalSecs)
                    putInt("screen_timeout", screenTimeoutSecs)
                    putBoolean("power_save_mode", powerSaveModeEnabled)
                    putInt("position_precision", positionPrecisionM)
                    putInt("gps_mode", nodeGpsMode)
                    putInt("gps_duty_interval_secs", gpsDutyIntervalSecs.coerceIn(300, 3600))
                    putBoolean("fixed_position", fixedPositionEnabled)
                    putFloat("fixed_latitude", lat)
                    putFloat("fixed_longitude", lon)
                    putInt("fixed_altitude", fixedAltInput.toIntOrNull() ?: 0)
                    apply()
                }
            }
            AppUiFeedback.show(
                if (appLanguage == "Spanish")
                    "¡Ajustes enviados! El nodo se reiniciará. Otros nodos no cambian — usa Configuración remota para igualar el perfil de radio."
                else
                    "Config sent! Node will reboot. Other nodes are unchanged — use Remote Config to match the radio profile.",
                duration = SnackbarDuration.Long
            )
        } else {
            AppUiFeedback.show(if (appLanguage == "Spanish") "Error al enviar la configuración." else "Failed to send configuration.", duration = SnackbarDuration.Short)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(settingsScrollState)
    ) {
        val deviceCategories = listOf(
            Triple(SettingsCategory.CHANNELS, "Channels", "Manage secondary channels and share/join links"),
            Triple(SettingsCategory.RADIO, "LoRa Radio Configuration", "Set spreading factor, bandwidth, power, and region"),
            Triple(SettingsCategory.POSITION, "GPS & Position Settings", "Configure onboard GPS mode, telemetry interval, and satellite lock status"),
            Triple(SettingsCategory.ROUTING, "Mesh Routing", "Hop limit, rebroadcast pace, and route health"),
            Triple(SettingsCategory.FIRMWARE, "Firmware Update", "Flash new firmware to the connected node over Bluetooth (BLE OTA)"),
            Triple(SettingsCategory.SECURITY, "Security & Keys", "Manage private keys, ECDH keypairs, and device password")
        )
        val appCategories = listOf(
            Triple(SettingsCategory.PREFERENCES, "App Preferences", "Set language, theme, units, and background alerts"),
            Triple(SettingsCategory.DEVELOPER, "Developer & Diagnostics", "Live logs console, packet exports, and system database reset")
        )
        val spanish = appLanguage == "Spanish"

        fun categoryNeedsDevice(cat: SettingsCategory): Boolean =
            cat != SettingsCategory.PREFERENCES && cat != SettingsCategory.DEVELOPER

        @Composable
        fun SettingsCategoryCard(cat: SettingsCategory, title: String, desc: String) {
            val needsDevice = categoryNeedsDevice(cat)
            val enabled = !needsDevice || isConnected
            val icon = when (cat) {
                SettingsCategory.CHANNELS -> Icons.Default.Layers
                SettingsCategory.RADIO -> Icons.Default.SettingsInputAntenna
                SettingsCategory.POSITION -> Icons.Default.Place
                SettingsCategory.FIRMWARE -> Icons.Default.SystemUpdate
                SettingsCategory.SECURITY -> Icons.Default.Lock
                SettingsCategory.ROUTING -> Icons.Default.AltRoute
                SettingsCategory.PREFERENCES -> Icons.Default.Palette
                SettingsCategory.DEVELOPER -> Icons.Default.Terminal
            }
            val iconColor = when (cat) {
                SettingsCategory.CHANNELS -> AccentCyan
                SettingsCategory.RADIO -> AccentMint
                SettingsCategory.POSITION -> Color(0xFF818CF8)
                SettingsCategory.FIRMWARE -> AccentMint
                SettingsCategory.SECURITY -> Color(0xFFEF4444)
                SettingsCategory.ROUTING -> AccentCyan
                SettingsCategory.PREFERENCES -> Color(0xFFFBBF24)
                SettingsCategory.DEVELOPER -> AccentSteel
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) SurfaceDark else SurfaceDark.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable(enabled = enabled) { activeCategory = cat }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = if (enabled) 0.15f else 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (enabled) iconColor else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t(title, appLanguage),
                            color = if (enabled) TextLight else TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!enabled) {
                                if (spanish) "Conecta un nodo para configurar esto."
                                else "Connect a node to configure this."
                            } else {
                                t(desc, appLanguage)
                            },
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (settingsTwoPane) {
            Text(
                text = t("Settings", appLanguage),
                color = AccentCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                if (spanish) "Nodo" else "Device",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                items(deviceCategories, key = { it.first.name }) { (cat, title, _) ->
                    val enabled = isConnected
                    val selected = activeCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                when {
                                    !enabled -> SurfaceDark.copy(alpha = 0.45f)
                                    selected -> AccentCyan.copy(alpha = 0.22f)
                                    else -> SurfaceDark
                                }
                            )
                            .border(
                                BorderStroke(1.dp, if (selected) AccentCyan.copy(alpha = 0.55f) else BorderDark),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable(enabled = enabled) { activeCategory = cat }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            t(title, appLanguage),
                            color = if (enabled) TextLight else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(
                if (spanish) "Aplicación" else "App",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                items(appCategories, key = { it.first.name }) { (cat, title, _) ->
                    val selected = activeCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) AccentAmber.copy(alpha = 0.22f) else SurfaceDark)
                            .border(
                                BorderStroke(1.dp, if (selected) AccentAmber.copy(alpha = 0.55f) else BorderDark),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { activeCategory = cat }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            t(title, appLanguage),
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }
            if (activeCategory == null) {
                Text(
                    if (spanish)
                        "Elige una categoría arriba. Los ajustes del nodo requieren Bluetooth."
                    else
                        "Choose a category above. Device settings require a Bluetooth link.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        } else if (activeCategory == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SurfaceRaised, SurfaceDark, AccentCyan.copy(alpha = 0.35f))
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column {
                        Text(
                            text = t("Settings", appLanguage),
                            color = AccentCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (spanish)
                                "Ajustes del nodo (Bluetooth) y preferencias de la app."
                            else
                                "Device settings (Bluetooth) and app preferences.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (!isConnected) {
                Text(
                    if (spanish)
                        "Sin radio conectada — solo están disponibles los ajustes de la app."
                    else
                        "No radio linked — only app settings are available.",
                    color = AccentAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            AetherSectionHeader(
                title = if (spanish) "Nodo" else "Device",
                trailing = if (isConnected) null else if (spanish) "Bloqueado" else "Locked",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            deviceCategories.forEach { (cat, title, desc) ->
                SettingsCategoryCard(cat, title, desc)
            }
            Spacer(modifier = Modifier.height(8.dp))
            AetherSectionHeader(
                title = if (spanish) "Aplicación" else "App",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            appCategories.forEach { (cat, title, desc) ->
                SettingsCategoryCard(cat, title, desc)
            }
        } else {
            BackHandler { activeCategory = null }
            // Header Bar inside categories
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeCategory = null }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (appLanguage == "Spanish") "Volver" else "Back",
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when(activeCategory) {
                        SettingsCategory.CHANNELS -> t("Channels", appLanguage)
                        SettingsCategory.RADIO -> t("LoRa Radio Configuration", appLanguage)
                        SettingsCategory.SECURITY -> t("Security & Keys", appLanguage)
                        SettingsCategory.ROUTING -> t("Mesh Routing", appLanguage)
                        SettingsCategory.PREFERENCES -> t("App Preferences", appLanguage)
                        SettingsCategory.DEVELOPER -> t("Developer & Diagnostics", appLanguage)
                        SettingsCategory.FIRMWARE -> t("Firmware Update", appLanguage)
                        SettingsCategory.POSITION -> t("GPS & Position Settings", appLanguage)
                        else -> ""
                    },
                    color = TextLight,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (activeCategory == SettingsCategory.CHANNELS) {
            val currentRegion = remember(viewModel.connectedNodeId) {
                val nodeKey = viewModel.connectedNodeId
                val nPrefs = context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
                nPrefs.getInt("region", 0)
            }
            val freqText = if (currentRegion == 1) "869.525MHz" else "906.875MHz"

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AetherSectionHeader(
                    title = t("Channels", appLanguage),
                    trailing = "${channelsList.size}",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (appLanguage == "Spanish") "Frec: $freqText" else "Freq: $freqText",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (channelsList.isEmpty()) {
                        Text(
                            text = t("No channels configured yet.", appLanguage),
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        channelsList.forEachIndexed { index, channel ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingChannel = channel
                                        showEditChannelDialog = true
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Slot number box
                                Box(
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E293B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = index.toString(),
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = channel.name,
                                            color = if (channel.isPrimary) AccentMint else TextLight,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (channel.isPrimary) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x204ADE80))
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Text(t("Primary", appLanguage), color = AccentMint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(
                                        "PSK: ${channel.psk.take(12)}${if (channel.psk.length > 12) "…" else ""}",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }

                                // Location status indicator
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = if (appLanguage == "Spanish")
                                        "Estado de ubicación"
                                    else
                                        "Location sharing status",
                                    tint = if (channel.positionEnabled) AccentCyan else TextMuted,
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                // Encryption status indicator
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = if (appLanguage == "Spanish")
                                        "Estado de cifrado"
                                    else
                                        "Encryption status",
                                    tint = if (channel.psk.isNotEmpty() && channel.psk != "AQ==") AccentMint else TextMuted,
                                    modifier = Modifier.size(18.dp).padding(end = 8.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                if (!channel.isPrimary) {
                                    IconButton(
                                        onClick = { channelPendingDelete = channel },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = if (appLanguage == "Spanish")
                                                "Eliminar canal"
                                            else
                                                "Delete channel",
                                            tint = AccentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (index < channelsList.lastIndex) {
                                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showAddChannelDialog = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(t("Add Secondary Channel", appLanguage), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (appLanguage == "Spanish")
                        "Comparte el enlace del canal primario. Unirse añade un canal secundario con esa PSK."
                    else
                        "Share sends the primary channel link. Join adds it as a secondary channel with that PSK.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val primary = channelsList.firstOrNull { it.isPrimary }
                            if (primary != null) {
                                val link = "aethermesh://channel?name=${android.net.Uri.encode(primary.name)}&psk=${android.net.Uri.encode(primary.psk)}&uplink=${primary.uplinkEnabled}&downlink=${primary.downlinkEnabled}&position=${primary.positionEnabled}&precise=${primary.preciseLocation}"
                                val base64Link = android.util.Base64.encodeToString(link.toByteArray(), android.util.Base64.NO_WRAP)
                                val shareText = "https://aethermesh.org/join#$base64Link"
                                try {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                        putExtra(
                                            android.content.Intent.EXTRA_SUBJECT,
                                            if (appLanguage == "Spanish") "Canal AetherMesh" else "AetherMesh Channel"
                                        )
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            send,
                                            if (appLanguage == "Spanish") "Compartir canal" else "Share channel"
                                        )
                                    )
                                } catch (_: Exception) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Channel Link", shareText))
                                    AppUiFeedback.show(if (appLanguage == "Spanish") "¡Enlace de canal copiado!" else "Channel link copied!", duration = SnackbarDuration.Short)
                                }
                            } else {
                                AppUiFeedback.show(if (appLanguage == "Spanish") "Crea un canal primario primero" else "Create a primary channel first", duration = SnackbarDuration.Short)
                            }
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = TextLight)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(t("Share Channel", appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = {
                            importChannelLinkInput = ""
                            showImportChannelDialog = true
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = TextLight)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(t("Join Channel", appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }

        if (activeCategory == SettingsCategory.RADIO) {
            // --- 2. LORA RADIO CONFIGURATION CARD ---
            AetherSectionHeader(
                title = t("LoRa Radio Configuration", appLanguage),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!isConnected) {
                    Text(
                        text = t("Connect to a hardware node via Bluetooth to configure LoRa radio settings.", appLanguage),
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                } else {
                    // Node Custom Name Input
                    Text(
                        text = t("Node Name", appLanguage),
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = nodeName,
                        onValueChange = {
                            if (it.length <= 16) {
                                nodeName = it
                            }
                        },
                        placeholder = {
                            Text(
                                if (appLanguage == "Spanish") "p. ej. Base Lobo" else "e.g. Wolf Base",
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (appLanguage == "Spanish")
                            "${nodeName.length}/16 caracteres"
                        else
                            "${nodeName.length}/16 characters",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Node Custom Short Name Input
                    Text(
                        text = t("Node Short Name", appLanguage),
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = nodeShortName,
                        onValueChange = {
                            if (it.length <= 4) {
                                nodeShortName = it.uppercase()
                            }
                        },
                        placeholder = {
                            Text(
                                if (appLanguage == "Spanish") "p. ej. LOBO" else "e.g. WOLF",
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (appLanguage == "Spanish")
                            "${nodeShortName.length}/4 caracteres"
                        else
                            "${nodeShortName.length}/4 characters",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Radio Profile presets (set SF+BW together, mesh-wide consistency)
                    Text(
                        text = t("Radio Profile", appLanguage),
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    RadioProfileChips(sf, bw) { profile ->
                        sf = profile.sf
                        bw = profile.bw
                    }
                    Text(
                        text = if (appLanguage == "Spanish")
                            "Solo afecta este nodo. Iguala los demás con Configuración remota o el range test fallará en silencio."
                        else
                            "Applies to this node only. Match other nodes via Remote Config or range tests will fail silently.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Spreading Factor Dropdown
                    Text(
                        text = t("LoRa Spreading Factor (SF)", appLanguage),
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isExpandedSF = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkBackground,
                                contentColor = TextLight
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SF$sf")
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                            }
                        }
                        DropdownMenu(
                            expanded = isExpandedSF,
                            onDismissRequest = { isExpandedSF = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            (7..12).forEach { valSF ->
                                DropdownMenuItem(
                                    text = { Text("SF$valSF", color = TextLight) },
                                    onClick = {
                                        sf = valSF
                                        isExpandedSF = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bandwidth Dropdown
                    Text(
                        text = t("LoRa Bandwidth (BW)", appLanguage),
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isExpandedBW = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkBackground,
                                contentColor = TextLight
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${bw.toInt()} kHz")
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                            }
                        }
                        DropdownMenu(
                            expanded = isExpandedBW,
                            onDismissRequest = { isExpandedBW = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            listOf(125f, 250f, 500f).forEach { valBW ->
                                DropdownMenuItem(
                                    text = { Text("${valBW.toInt()} kHz", color = TextLight) },
                                    onClick = {
                                        bw = valBW
                                        isExpandedBW = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Region Dropdown
                    Text(
                        text = t("Radio Region Frequency", appLanguage),
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isExpandedRegion = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkBackground,
                                contentColor = TextLight
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (region == 1) "EU868 (869.525 MHz)" else "US915 (906.875 MHz)")
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                            }
                        }
                        DropdownMenu(
                            expanded = isExpandedRegion,
                            onDismissRequest = { isExpandedRegion = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("US915 (906.875 MHz)", color = TextLight) },
                                onClick = {
                                    region = 0
                                    isExpandedRegion = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("EU868 (869.525 MHz)", color = TextLight) },
                                onClick = {
                                    region = 1
                                    isExpandedRegion = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Node Role Dropdown
                    Text(
                        text = t("Node Operation Role", appLanguage),
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (role) {
                            1 -> if (appLanguage == "Spanish")
                                "Router: reenvía mensajes LoRa. BLE sigue activo salvo que el Ahorro de Batería lo apague tras 5 min."
                            else
                                "Router: relays LoRa traffic. BLE stays on unless Battery Saver stops advertising after 5 min."
                            2 -> if (appLanguage == "Spanish")
                                "Repetidor: solo infraestructura LoRa; apaga BLE para ahorrar batería."
                            else
                                "Repeater: LoRa infrastructure only; turns BLE off to save power."
                            else -> if (appLanguage == "Spanish")
                                "Cliente: no reenvía LoRa (como companion MeshCore). Usa un Router/Repetidor para cobertura."
                            else
                                "Client: does not relay LoRa (MeshCore-style companion). Use a Router/Repeater for coverage."
                        },
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isExpandedRole = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkBackground,
                                contentColor = TextLight
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (role) {
                                        1 -> t("Router", appLanguage)
                                        2 -> t("Low-Power Repeater", appLanguage)
                                        else -> t("Client", appLanguage)
                                    }
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                            }
                        }
                        DropdownMenu(
                            expanded = isExpandedRole,
                            onDismissRequest = { isExpandedRole = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text(t("Client", appLanguage), color = TextLight) },
                                onClick = {
                                    role = 0
                                    isExpandedRole = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(t("Router", appLanguage), color = TextLight) },
                                onClick = {
                                    role = 1
                                    isExpandedRole = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(t("Low-Power Repeater", appLanguage), color = TextLight) },
                                onClick = {
                                    role = 2
                                    isExpandedRole = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // TX Power Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("TX Transmit Power", appLanguage),
                            color = TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$txPower dBm",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = txPower.toFloat(),
                        onValueChange = { txPower = it.toInt() },
                        valueRange = 10f..22f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = BorderDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (role == 2) {
                                showRepeaterConfirmDialog = true
                            } else {
                                saveConfigAndNotify()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = DarkBackground
                        )
                    ) {
                        Text(
                            text = t("Apply Settings", appLanguage),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (isConnected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                changeCurrentPasswordInput = ""
                                changeNewPasswordInput = ""
                                changePasswordError = false
                                showChangePasswordDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = AccentCyan
                            ),
                            border = BorderStroke(1.dp, AccentCyan)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = t("Change Device Password", appLanguage),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        }

        if (activeCategory == SettingsCategory.POSITION) {
            // --- POSITION & GPS CONFIGURATION VIEW ---
            AetherSectionHeader(
                title = t("GPS & Position Settings", appLanguage),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 1. LIVE GPS LOCK & TELEMETRY STATUS CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = t("GPS Status & Live Telemetry", appLanguage),
                        color = TextLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val hasLock = connectedNode != null && hasValidPosition(connectedNode.latitude, connectedNode.longitude)
                    val statusLabel = when {
                        hasLock -> t("LOCKED", appLanguage)
                        nodeGpsMode == 1 -> t("GPS OFF", appLanguage)
                        nodeGpsMode == 2 -> t("PERIODIC SLEEP", appLanguage)
                        else -> t("WAITING FOR LOCK", appLanguage)
                    }
                    val statusOk = hasLock
                    val statusMuted = nodeGpsMode == 1 || (nodeGpsMode == 2 && !hasLock)
                    val statusColor = when {
                        statusOk -> AccentMint
                        statusMuted -> TextMuted
                        else -> Color(0xFFF59E0B)
                    }
                    val statusBg = when {
                        statusOk -> Color(0x204ADE80)
                        statusMuted -> Color(0x20A1A1AA)
                        else -> Color(0x20F59E0B)
                    }

                    // Status Badge row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = t("GPS Lock Status", appLanguage) + ":",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                            if (nodeGpsMode == 2) {
                                Text(
                                    text = if (appLanguage == "Spanish")
                                        "Modo periódico · cada ${gpsDutyIntervalSecs / 60} min"
                                    else
                                        "Periodic mode · every ${gpsDutyIntervalSecs / 60} min",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            } else if (nodeGpsMode == 1) {
                                Text(
                                    text = if (appLanguage == "Spanish")
                                        "GPS del nodo apagado (usa GPS del teléfono si está activo)"
                                    else
                                        "Onboard GPS powered off (phone GPS used if sharing is on)",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))

                    // Coordinates row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = t("Coordinates", appLanguage),
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (hasLock) {
                                "${"%.6f".format(connectedNode!!.latitude)}, ${"%.6f".format(connectedNode!!.longitude)}"
                            } else {
                                if (appLanguage == "Spanish") "Sin bloqueo" else "No Lock"
                            },
                            color = if (hasLock) TextLight else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Uptime row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = t("Node Uptime", appLanguage),
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        val uptimeStr = if (connectedNode != null) {
                            val secs = connectedNode!!.uptimeSeconds
                            if (secs < 60) "$secs s"
                            else "${secs / 60} m ${secs % 60} s"
                        } else "-"
                        Text(
                            text = uptimeStr,
                            color = TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Battery row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = t("Battery Level", appLanguage),
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (connectedNode != null) "${connectedNode!!.battery}%" else "-",
                            color = if (connectedNode != null) {
                                if (connectedNode!!.battery > 20) AccentMint else AccentRed
                            } else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 1.5 FIXED POSITION CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = t("Fixed Position", appLanguage),
                                color = TextLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = t("Define static beacon/router position when device has no GPS.", appLanguage),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = fixedPositionEnabled,
                            onCheckedChange = { fixedPositionEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = AccentMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }

                    if (fixedPositionEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // Latitude Input
                        Text(t("Latitude", appLanguage), color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = fixedLatInput,
                            onValueChange = { fixedLatInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextLight, fontSize = 14.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Longitude Input
                        Text(t("Longitude", appLanguage), color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = fixedLonInput,
                            onValueChange = { fixedLonInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextLight, fontSize = 14.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Altitude Input
                        Text(t("Altitude (m)", appLanguage), color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = fixedAltInput,
                            onValueChange = { fixedAltInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextLight, fontSize = 14.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Set from current phone location button
                        Text(
                            text = t("Set from current phone location", appLanguage),
                            color = AccentMint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    // Query the phone directly rather than a screen-local state
                                    // variable that may be unpopulated on the Settings tab.
                                    try {
                                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                                        val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                            ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                                        if (loc != null && !(loc.latitude == 0.0 && loc.longitude == 0.0)) {
                                            fixedLatInput = "%.6f".format(loc.latitude)
                                            fixedLonInput = "%.6f".format(loc.longitude)
                                            fixedAltInput = "%.0f".format(loc.altitude)
                                            AppUiFeedback.show(t("Location loaded from phone GPS", appLanguage), duration = SnackbarDuration.Short)
                                        } else {
                                            AppUiFeedback.show(
                                                t("No phone GPS location lock yet — open the Map tab briefly to acquire one", appLanguage),
                                                duration = SnackbarDuration.Long
                                            )
                                        }
                                    } catch (e: SecurityException) {
                                        AppUiFeedback.show(t("Location permission needed", appLanguage), duration = SnackbarDuration.Short)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // 2. CONFIGURATION CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = t("Position Configuration", appLanguage),
                        color = TextLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = t("Phone GPS Sharing", appLanguage),
                                color = TextLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = t("Share your phone's GPS position with the node over BLE when connected.", appLanguage),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = enablePhoneGpsSharing,
                            onCheckedChange = {
                                enablePhoneGpsSharing = it
                                sharedPrefs.edit().putBoolean("enable_phone_gps_sharing", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = AccentMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }

                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 14.dp))

                    if (!isConnected) {
                        Text(
                            text = t("Connect to a hardware node via Bluetooth to configure LoRa position interval.", appLanguage),
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // Telemetry Broadcast Interval Dropdown
                        Text(
                            text = t("Telemetry Broadcast Interval", appLanguage),
                            color = TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isExpandedTelemetry = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkBackground,
                                    contentColor = TextLight
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val label = when (telemetryIntervalSecs) {
                                        15 -> "15 seconds"
                                        30 -> "30 seconds"
                                        60 -> "1 minute"
                                        300 -> "5 minutes"
                                        600 -> "10 minutes"
                                        1800 -> "30 minutes"
                                        else -> "$telemetryIntervalSecs seconds"
                                    }
                                    Text(t(label, appLanguage))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(
                                expanded = isExpandedTelemetry,
                                onDismissRequest = { isExpandedTelemetry = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                listOf(15, 30, 60, 300, 600, 1800).forEach { secs ->
                                    val label = when (secs) {
                                        15 -> "15 seconds"
                                        30 -> "30 seconds"
                                        60 -> "1 minute"
                                        300 -> "5 minutes"
                                        600 -> "10 minutes"
                                        1800 -> "30 minutes"
                                        else -> "$secs seconds"
                                    }
                                    DropdownMenuItem(
                                        text = { Text(t(label, appLanguage), color = TextLight) },
                                        onClick = {
                                            telemetryIntervalSecs = secs
                                            isExpandedTelemetry = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Node GPS: always on / always off / periodic duty-cycle
                        Text(
                            text = if (appLanguage == "Spanish") "GPS del Nodo" else "Node GPS",
                            color = TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Periódico enciende el GPS unos segundos para obtener ubicación y luego lo apaga — mejor para nodos dejados en el campo."
                            else
                                "Periodic wakes the GPS briefly for a location fix, then powers it off — better for leave-behind nodes than always on or always off.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to if (appLanguage == "Spanish") "Siempre" else "On",
                                2 to if (appLanguage == "Spanish") "Periódico" else "Periodic",
                                1 to if (appLanguage == "Spanish") "Apagado" else "Off"
                            ).forEach { (mode, label) ->
                                val selected = nodeGpsMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) AccentMint.copy(alpha = 0.22f) else SurfaceDark)
                                        .border(
                                            1.dp,
                                            if (selected) AccentMint else BorderDark,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { nodeGpsMode = mode }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (selected) AccentMint else TextLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        if (nodeGpsMode == 2) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (appLanguage == "Spanish") "Intervalo de despertar" else "Wake interval",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(300 to "5m", 900 to "15m", 1800 to "30m", 3600 to "60m").forEach { (secs, label) ->
                                    val selected = gpsDutyIntervalSecs == secs
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) AccentCyan.copy(alpha = 0.22f) else SurfaceDark)
                                            .border(
                                                1.dp,
                                                if (selected) AccentCyan else BorderDark,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { gpsDutyIntervalSecs = secs }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            color = if (selected) AccentCyan else TextLight,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Position Precision (privacy blur radius, Meshtastic-style)
                        Text(
                            text = if (appLanguage == "Spanish") "Precisión de Posición" else "Position Precision",
                            color = TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Difumina la posición transmitida por la malla. Otros ven el nodo en algún lugar dentro de este radio."
                            else
                                "Blurs the position broadcast over the mesh. Others see the node somewhere within this radius; only your own phone sees it exactly.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isExpandedPosPrecision = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkBackground,
                                    contentColor = TextLight
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(formatPositionPrecision(positionPrecisionM, useImperialUnitsSetting, appLanguage))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(
                                expanded = isExpandedPosPrecision,
                                onDismissRequest = { isExpandedPosPrecision = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                POSITION_PRECISION_STEPS.forEach { meters ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                formatPositionPrecision(meters, useImperialUnitsSetting, appLanguage),
                                                color = if (meters == 0) AccentMint else TextLight
                                            )
                                        },
                                        onClick = {
                                            positionPrecisionM = meters
                                            isExpandedPosPrecision = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Screen Timeout Select
                        Text(
                            text = t("Screen Timeout", appLanguage),
                            color = TextLight,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground)
                                    .clickable { isExpandedScreenTimeout = true }
                                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val label = when (screenTimeoutSecs) {
                                        0 -> "Always Off"
                                        10 -> "10 seconds"
                                        15 -> "15 seconds"
                                        30 -> "30 seconds"
                                        60 -> "1 minute"
                                        -1, 0xFFFFFFFF.toInt() -> "Always On"
                                        else -> "$screenTimeoutSecs seconds"
                                    }
                                    Text(t(label, appLanguage), color = TextLight)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(
                                expanded = isExpandedScreenTimeout,
                                onDismissRequest = { isExpandedScreenTimeout = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                listOf(0, 10, 15, 30, 60, -1).forEach { secs ->
                                    val label = when (secs) {
                                        0 -> "Always Off"
                                        10 -> "10 seconds"
                                        15 -> "15 seconds"
                                        30 -> "30 seconds"
                                        60 -> "1 minute"
                                        -1 -> "Always On"
                                        else -> "$secs seconds"
                                    }
                                    DropdownMenuItem(
                                        text = { Text(t(label, appLanguage), color = TextLight) },
                                        onClick = {
                                            screenTimeoutSecs = secs
                                            isExpandedScreenTimeout = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3.5 Battery Saver Mode Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = t("Battery Saver Mode", appLanguage),
                                    color = TextLight,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (appLanguage == "Spanish")
                                        "Pantalla máx. 10s; telemetría más lenta; apaga el anuncio BLE tras 5 min sin conexión (pulsa el botón del nodo para reactivarlo)."
                                    else
                                        "Caps screen to 10s, slows telemetry, and stops BLE advertising after 5 min idle (press the node button to wake it for scanning).",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = powerSaveModeEnabled,
                                onCheckedChange = { powerSaveModeEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentCyan,
                                    checkedTrackColor = AccentCyan.copy(alpha = 0.5f),
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceDark
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                saveConfigAndNotify()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = DarkBackground
                            )
                        ) {
                            Text(
                                text = t("Apply Settings", appLanguage),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (activeCategory == SettingsCategory.FIRMWARE) {
            // --- FIRMWARE UPDATE VIEW ---
            // Heltec/ESP32: our in-app chunked OTA of a .bin into the inactive slot.
            // RAK/nRF52: node reboots into its DFU bootloader and the Nordic DFU
            // library streams the .zip package to it (Meshtastic-style).
            val otaState by viewModel.otaState.collectAsStateWithLifecycle()
            var otaFileBytes by remember { mutableStateOf<ByteArray?>(null) }
            var otaFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var otaFileName by remember { mutableStateOf("") }
            var otaPickError by remember { mutableStateOf<String?>(null) }
            var showOtaWarning by remember { mutableStateOf(false) }
            val otaModelHint = connectedNode?.model
                ?: viewModel.connectedDeviceName
                ?: connectedNode?.name
            val isRakNode = isRakOtaTarget(
                connectedNode?.model,
                viewModel.connectedDeviceName,
                connectedNode?.name
            )
            val isEspOtaNode = isEspOtaTarget(
                connectedNode?.model,
                viewModel.connectedDeviceName,
                connectedNode?.name
            )
            // Connected radios that haven't reported a model yet still get the OTA UI;
            // only truly unknown / unsupported boards are blocked.
            val otaSupported = isEspOtaNode || isRakNode ||
                (isConnected && connectedNode?.model.isNullOrBlank()) ||
                (isConnected && connectedNode?.model.equals("Unknown", ignoreCase = true) == true) ||
                (isConnected && connectedNode == null)
            val githubArtifact by viewModel.githubFirmware.collectAsStateWithLifecycle()
            val githubStatus by viewModel.githubFirmwareStatus.collectAsStateWithLifecycle()
            val githubBusy by viewModel.githubFirmwareBusy.collectAsStateWithLifecycle()
            val githubProgress by viewModel.githubDownloadProgress.collectAsStateWithLifecycle()
            val firmwareScope = rememberCoroutineScope()
            LaunchedEffect(otaModelHint, isConnected, otaSupported) {
                if (isConnected && otaSupported) {
                    viewModel.refreshGithubFirmware(otaModelHint)
                }
            }
            val otaFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "firmware"
                        if (bytes != null && bytes.isNotEmpty()) {
                            val treatAsRak = isRakNode || name.lowercase().endsWith(".zip")
                            val err = isValidOtaPayload(bytes, name, treatAsRak)
                            if (err != null) {
                                otaFileBytes = null
                                otaFileUri = null
                                otaFileName = ""
                                otaPickError = localizeOtaPickError(err, appLanguage)
                                AppUiFeedback.show(localizeOtaPickError(err, appLanguage), duration = SnackbarDuration.Long)
                            } else {
                                otaFileBytes = bytes
                                otaFileUri = uri
                                otaFileName = name
                                otaPickError = null
                                viewModel.resetOtaState()
                            }
                        }
                    } catch (e: Exception) {
                        AppUiFeedback.show(
                            if (appLanguage == "Spanish") "No se pudo leer el archivo: ${e.message}"
                            else "Could not read file: ${e.message}",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (appLanguage == "Spanish") "Actualización de Firmware" else "Firmware Update",
                        color = TextLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (if (appLanguage == "Spanish") "Instalado: " else "Installed: ") +
                            (connectedNode?.firmwareVersion?.ifEmpty { "unknown" } ?: "—"),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    val installedFw = connectedNode?.firmwareVersion.orEmpty()
                    if (installedFw.isNotEmpty() && isFirmwareTooOld(installedFw)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (appLanguage == "Spanish")
                                "Firmware demasiado antiguo para esta app (mín. $MIN_COMPATIBLE_FW). Usa el flasher web por USB."
                            else
                                "Firmware too old for this app (need $MIN_COMPATIBLE_FW+). Use the web flasher over USB.",
                            color = AccentAmber,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(WEB_FLASHER_URL)
                                        )
                                    )
                                } catch (_: Exception) { }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (appLanguage == "Spanish") "Abrir web flasher" else "Open web flasher",
                                color = AccentCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // An active transfer ALWAYS owns this card. This must be the
                    // first branch: the RAK DFU flow deliberately disconnects our
                    // BLE link so the bootloader can take over, and the old
                    // !isConnected-first ordering swapped to the "connect to a
                    // node" prompt mid-flash - hiding the DFU progress entirely.
                    if (otaState.active) {
                        LinearProgressIndicator(
                            progress = { otaState.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = AccentCyan,
                            trackColor = BorderDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(localizeOtaStatus(otaState.status, appLanguage), color = TextLight, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (appLanguage == "Spanish") "No cierres la app durante la actualización." else "Keep the app open and the phone near the node.",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.cancelFirmwareUpdate() },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = AccentRed),
                            border = BorderStroke(1.dp, BorderDark)
                        ) {
                            Text(if (appLanguage == "Spanish") "Cancelar" else "Cancel Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (!isConnected) {
                        Text(
                            text = if (appLanguage == "Spanish") "Conéctate a un nodo para actualizar su firmware." else "Connect to a node to update its firmware over BLE.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        // A finished/failed update's result stays visible even
                        // though the node is still reconnecting
                        if (otaState.status.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                localizeOtaStatus(otaState.status, appLanguage),
                                color = if (otaState.error) AccentRed else if (otaState.done) AccentMint else TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else if (!otaSupported) {
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Este modelo de nodo no soporta OTA inalámbrica. Usa el flasher web por USB."
                            else
                                "This node model doesn't support wireless OTA. Use the web flasher over USB.",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp
                        )
                    } else {
                        if (connectedNode?.model.isNullOrBlank() && !isRakNode && !isEspOtaNode) {
                            Text(
                                text = if (appLanguage == "Spanish")
                                    "Esperando el modelo del nodo… puedes elegir un .bin/.zip mientras tanto."
                                else
                                    "Waiting for node model… you can still pick a .bin/.zip meanwhile.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        // GitHub Pages OTA catalog (published by pages.yml as ota-manifest.json)
                        OutlinedButton(
                            onClick = { viewModel.refreshGithubFirmware(otaModelHint) },
                            enabled = !githubBusy && !otaState.active,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkBackground,
                                contentColor = TextLight
                            )
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (appLanguage == "Spanish") "Buscar en GitHub" else "Check GitHub for updates",
                                fontSize = 12.sp
                            )
                        }
                        if (githubStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                localizeGithubFirmwareStatus(githubStatus, appLanguage),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (githubBusy && githubProgress in 1..99) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { githubProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = AccentMint,
                                trackColor = BorderDark
                            )
                        }
                        val artifact = githubArtifact
                        if (artifact != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                artifact.name,
                                color = TextLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${artifact.file} · ${artifact.size / 1024} kB",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    firmwareScope.launch {
                                        val result = viewModel.downloadGithubFirmware(context, artifact)
                                        if (result != null) {
                                            val treatAsRak = isRakNode || result.fileName.lowercase().endsWith(".zip") || artifact.isZip
                                            val err = isValidOtaPayload(
                                                result.bytes,
                                                result.fileName,
                                                treatAsRak
                                            )
                                            if (err != null) {
                                                otaPickError = localizeOtaPickError(err, appLanguage)
                                                AppUiFeedback.show(
                                                    localizeOtaPickError(err, appLanguage),
                                                    duration = SnackbarDuration.Long
                                                )
                                            } else {
                                                otaFileBytes = result.bytes
                                                otaFileUri = result.cacheUri
                                                otaFileName = result.fileName
                                                otaPickError = null
                                                viewModel.resetOtaState()
                                                AppUiFeedback.show(
                                                    if (appLanguage == "Spanish")
                                                        "Firmware de GitHub listo para flashear."
                                                    else
                                                        "GitHub firmware ready to flash."
                                                )
                                            }
                                        } else {
                                            AppUiFeedback.show(
                                                localizeGithubFirmwareStatus(
                                                    githubStatus.ifBlank { "Download failed" },
                                                    appLanguage
                                                ),
                                                duration = SnackbarDuration.Long
                                            )
                                        }
                                    }
                                },
                                enabled = !githubBusy && !otaState.active && isDeviceAuthenticated,
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan,
                                    contentColor = DarkBackground,
                                    disabledContainerColor = BorderDark,
                                    disabledContentColor = TextMuted
                                )
                            ) {
                                Text(
                                    if (appLanguage == "Spanish")
                                        "Descargar desde GitHub"
                                    else
                                        "Download from GitHub",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { otaFilePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (otaFileName.isEmpty()) {
                                    when {
                                        isRakNode ->
                                            if (appLanguage == "Spanish") "Elegir paquete .zip (DFU)" else "Choose firmware .zip (DFU package)"
                                        isEspOtaNode ->
                                            if (appLanguage == "Spanish") "Elegir archivo .bin" else "Choose firmware .bin"
                                        else ->
                                            if (appLanguage == "Spanish") "Elegir .bin o .zip" else "Choose firmware .bin or .zip"
                                    }
                                } else {
                                    "$otaFileName (${(otaFileBytes?.size ?: 0) / 1024} kB)"
                                },
                                fontSize = 12.sp
                            )
                        }
                        if (otaPickError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(otaPickError!!, color = AccentRed, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showOtaWarning = true },
                            enabled = otaFileBytes != null && isDeviceAuthenticated,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentMint,
                                contentColor = DarkBackground,
                                disabledContainerColor = BorderDark,
                                disabledContentColor = TextMuted
                            )
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (appLanguage == "Spanish") "Actualizar por BLE OTA" else "Update via BLE OTA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        if (otaState.status.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                localizeOtaStatus(otaState.status, appLanguage),
                                color = if (otaState.error) AccentRed else if (otaState.done) AccentMint else TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Text(
                text = if (appLanguage == "Spanish")
                    "El primer firmware con OTA debe instalarse por USB; después es inalámbrico. Heltec/T-Deck/CrowPanel usan .bin; RAK usa el paquete .zip (DFU). También puedes descargar el paquete OTA publicado en GitHub Pages."
                else
                    "The first OTA-capable firmware must be flashed over USB; after that, updates are wireless. Heltec/T-Deck/CrowPanel take the .bin; RAK takes the .zip DFU package. You can also download the matching OTA package from GitHub Pages.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            if (showOtaWarning) {
                AlertDialog(
                    onDismissRequest = { showOtaWarning = false },
                    title = { Text(if (appLanguage == "Spanish") "Advertencia" else "Update Warning", color = TextLight, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                if (appLanguage == "Spanish")
                                    "Vas a flashear nuevo firmware por Bluetooth."
                                else
                                    "You are about to flash new firmware to $otaFileName over Bluetooth.",
                                color = TextLight, fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                if (appLanguage == "Spanish")
                                    "• Asegúrate de que el nodo esté cargado o con USB."
                                else
                                    "• Make sure the node is charged or on USB.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                if (appLanguage == "Spanish")
                                    "• Mantén el nodo cerca del teléfono."
                                else
                                    "• Keep the node close to your phone.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                if (appLanguage == "Spanish")
                                    "• No cierres la app durante la actualización."
                                else
                                    "• Do not close the app during the update.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                if (appLanguage == "Spanish")
                                    "• Verifica que este build coincida con el hardware (Heltec V4)."
                                else
                                    "• Verify this build matches the hardware (Heltec V4).",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                if (appLanguage == "Spanish")
                                    "La imagen se verifica con checksum antes de reiniciar. Si falla la transferencia, el nodo sigue con el firmware actual."
                                else
                                    "The image is checksum-verified before the node reboots. If the transfer fails, the node keeps running its current firmware.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showOtaWarning = false
                            val useRakDfu = isRakNode || otaFileName.lowercase().endsWith(".zip")
                            if (useRakDfu) {
                                otaFileUri?.let { viewModel.startRakDfuUpdate(it) }
                            } else {
                                otaFileBytes?.let { viewModel.startFirmwareUpdate(it) }
                            }
                        }) {
                            Text(
                                if (appLanguage == "Spanish") "Sé lo que hago." else "I know what I'm doing.",
                                color = AccentMint,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOtaWarning = false }) {
                            Text(if (appLanguage == "Spanish") "Cancelar" else "Cancel", color = TextMuted)
                        }
                    },
                    containerColor = SurfaceDark
                )
            }
        }

        if (activeCategory == SettingsCategory.SECURITY) {
            // --- 3. SECURITY & DM KEYS CARD ---
            AetherSectionHeader(
                title = t("Security & DM Keys", appLanguage),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t("Direct Message Keys", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(t("Public Key (Base64)", appLanguage), color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkBackground)
                        .padding(10.dp)
                ) {
                    Text(ecdhKeys.first, color = AccentMint, fontSize = 11.sp)
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t("Private Key (Base64)", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Text(
                        text = if (showPrivateKey) t("Hide", appLanguage) else t("Show", appLanguage),
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showPrivateKey = !showPrivateKey }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkBackground)
                        .padding(10.dp)
                ) {
                    Text(
                        if (showPrivateKey) ecdhKeys.second else "••••••••••••••••••••••••",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showRegenKeysDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = AccentRed)
                    ) {
                        Text(t("Regenerate Private Key", appLanguage), fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            val shareTxt = "AetherMesh Security Keys:\nPublic: ${ecdhKeys.first}\nPrivate: ${ecdhKeys.second}"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Security Keys Export", shareTxt))
                            AppUiFeedback.show(t("Keys copied to clipboard", appLanguage), duration = SnackbarDuration.Short)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(t("Export Keys", appLanguage), fontSize = 11.sp)
                    }
                }
            }
        }
        }

        if (activeCategory == SettingsCategory.ROUTING) {
            MeshRoutingDiagnosticsPanel(
                viewModel = viewModel,
                nodes = nodes,
                isConnected = isConnected,
                isDeviceAuthenticated = isDeviceAuthenticated,
                appLanguage = appLanguage,
                onUnlockDevice = { viewModel.promptDeviceAuthentication() },
                meshHopLimit = meshHopLimit,
                onMeshHopLimitChange = { meshHopLimit = it },
                rebroadcastTxdelayX100 = rebroadcastTxdelayX100,
                onRebroadcastTxdelayChange = { rebroadcastTxdelayX100 = it },
                onApplyRoutingSettings = { saveConfigAndNotify() }
            )
        }

        if (activeCategory == SettingsCategory.PREFERENCES) {
            // --- 4. APP PREFERENCES CARD ---
            AetherSectionHeader(
                title = t("App Preferences", appLanguage),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Theme Choice
                Text(t("Theme", appLanguage), color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isExpandedTheme = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(t(appTheme, appLanguage))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                        }
                    }
                    DropdownMenu(expanded = isExpandedTheme, onDismissRequest = { isExpandedTheme = false }, modifier = Modifier.background(SurfaceDark)) {
                        listOf("System", "Dark", "Light").forEach { theme ->
                            DropdownMenuItem(text = { Text(t(theme, appLanguage), color = TextLight) }, onClick = {
                                appTheme = theme
                                sharedPrefs.edit().putString("app_theme", theme).apply()
                                isExpandedTheme = false
                            })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Language Choice
                Text(t("Language", appLanguage), color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isExpandedLanguage = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(t(appLanguage, appLanguage))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                        }
                    }
                    DropdownMenu(expanded = isExpandedLanguage, onDismissRequest = { isExpandedLanguage = false }, modifier = Modifier.background(SurfaceDark)) {
                        listOf("English", "Spanish").forEach { lang ->
                            DropdownMenuItem(text = { Text(t(lang, appLanguage), color = TextLight) }, onClick = {
                                appLanguage = lang
                                sharedPrefs.edit().putString("app_language", lang).apply()
                                isExpandedLanguage = false
                            })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(t("Distance Units", appLanguage), color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (useImperialUnitsSetting)
                        t("Imperial (Miles, Feet)", appLanguage)
                    else
                        t("Metric (Kilometers, Meters)", appLanguage),
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        true to t("Imperial", appLanguage),
                        false to t("Metric", appLanguage)
                    ).forEach { (imperial, label) ->
                        val selected = useImperialUnitsSetting == imperial
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AccentCyan.copy(alpha = 0.22f) else SurfaceDark)
                                .border(
                                    1.dp,
                                    if (selected) AccentCyan else BorderDark,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    useImperialUnitsSetting = imperial
                                    sharedPrefs.edit().putBoolean("use_imperial_units", imperial).apply()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) AccentCyan else TextLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CSV Exports
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        exportRangeTestLogsToCsv(context, viewModel.getAllRangeTestLogs(), viewModel.nodes.value.associate { it.nodeId to (it.latitude.toDouble() to it.longitude.toDouble()) }, appLanguage)
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Export rangetest packets", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Export range pings to CSV and copy", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val allMsg = consoleMessages
                        exportAllPacketsToCsv(context, allMsg, appLanguage)
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Export all packets", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Export full message list to CSV and copy", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        }

        if (activeCategory == SettingsCategory.DEVELOPER) {
            // --- 5. DATA & LOGS MANAGEMENT CARD ---
            AetherSectionHeader(
                title = t("Data & Logs Management", appLanguage),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Clear Chat log button
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showClearChatDialog = true }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Clear Chat History", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Delete all messages from database", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))
                
                // Reset Node Directory button
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showResetNodesDialog = true }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Reset Node Directory", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Clear all discovered nodes and restart directory", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))
                
                // Backup Settings button
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { createDocLauncher.launch("aethermesh_backup_${connectedNode?.nodeId ?: 0L}.json") }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Backup Device Settings", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Export configuration to JSON file", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))
                
                // Restore Settings button
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { restoreSettingsLauncher.launch(arrayOf("application/json")) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Restore Device Settings", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Import configuration from JSON file", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // --- 6. APP INFORMATION & DIAGNOSTICS CARD ---
        Text(
            text = t("App Settings & Logs", appLanguage),
            color = AccentCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Intro item
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showIntroDialog = true }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Show Introduction", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Quick startup guide for AetherMesh", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))
                
                // Notifications item
                val notificationsGranted = remember {
                    android.os.Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                }
                var notifPermGranted by remember { mutableStateOf(notificationsGranted) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && android.os.Build.VERSION.SDK_INT >= 33) {
                            notifPermGranted = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(t("App Notifications", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(t("Configure background alerts", appLanguage), color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = bgAlertsEnabled,
                        onCheckedChange = { isChecked ->
                            bgAlertsEnabled = isChecked
                            sharedPrefs.edit().putBoolean("bg_alerts_enabled", isChecked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentCyan,
                            checkedTrackColor = AccentCyan.copy(alpha = 0.5f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BorderDark
                        )
                    )
                }
                if (bgAlertsEnabled && !notifPermGranted && android.os.Build.VERSION.SDK_INT >= 33) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF422006))
                            .padding(10.dp)
                    ) {
                        Text(
                            if (appLanguage == "Spanish")
                                "Las notificaciones están bloqueadas. Actívalas en Ajustes del sistema para recibir alertas en segundo plano."
                            else
                                "Notification permission is blocked. Enable it in system Settings so background alerts can appear.",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                        ).apply {
                                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    )
                                } catch (_: Exception) {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (appLanguage == "Spanish") "Abrir ajustes de notificaciones" else "Open notification settings",
                                color = AccentCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                // Diagnostic Console logs collapsible item
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showConsoleLogs = !showConsoleLogs }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(t("Diagnostic Console Logs", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                t("Recent chat packet sizes (not a full system log)", appLanguage),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = if (showConsoleLogs) (if (appLanguage == "Spanish") "Ocultar" else "Hide") else (if (appLanguage == "Spanish") "Mostrar" else "Show"),
                        color = AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showConsoleLogs) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (diagnosticLogs.isEmpty() && consoleMessages.isEmpty()) {
                            Text(
                                if (appLanguage == "Spanish")
                                    "Sin eventos recientes. Errores BLE/malla y paquetes de chat aparecen aquí."
                                else
                                    "No recent events. BLE/mesh errors and chat packets appear here.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        } else {
                            diagnosticLogs.takeLast(30).forEach { line ->
                                Text(line, color = AccentAmber, fontSize = 11.sp)
                            }
                            consoleMessages.takeLast(12).forEach { msg ->
                                Text(
                                    if (appLanguage == "Spanish")
                                        "Paquete de 0x${msg.senderId.toString(16).uppercase()}: ${msg.content.length} bytes"
                                    else
                                        "Packet from 0x${msg.senderId.toString(16).uppercase()}: ${msg.content.length} bytes",
                                    color = TextLight,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                // App Version info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Menu, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Version", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "v${com.example.aethermesh.BuildConfig.VERSION_NAME} (${com.example.aethermesh.BuildConfig.VERSION_CODE})",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        }
    }

    // --- DIALOGS SECTION ---
    if (showIntroDialog) {
        AlertDialog(
            onDismissRequest = { showIntroDialog = false },
            title = { Text(t("AetherMesh Guide", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t("Welcome to AetherMesh, your off-grid communication companion!", appLanguage), color = TextLight, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(t("1. Pair your hardware node via the Connection tab.", appLanguage), color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(t("2. View active mesh participants in the Nodes tab.", appLanguage), color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(t("3. Chat securely over LoRa on the Chats tab.", appLanguage), color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(t("4. Set custom node name & LoRa parameters in Settings.", appLanguage), color = TextMuted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntroDialog = false }) {
                    Text(t("Got it", appLanguage), color = AccentCyan)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text(t("Change Device Password", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(if (appLanguage == "Spanish") "Ingrese la contraseña actual y una nueva contraseña para este nodo de hardware." else "Enter current password and a new password for this hardware node.", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = changeCurrentPasswordInput,
                        onValueChange = {
                            changeCurrentPasswordInput = it
                            changePasswordError = false
                        },
                        label = { Text(t("Current Password", appLanguage), color = TextMuted) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = changeNewPasswordInput,
                        onValueChange = {
                            changeNewPasswordInput = it
                            changePasswordError = false
                        },
                        label = { Text(t("New Password", appLanguage), color = TextMuted) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (changePasswordError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (appLanguage == "Spanish") "Contraseña actual incorrecta o error al actualizar." else "Incorrect current password or update failed.", color = AccentRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val curr = changeCurrentPasswordInput.trim()
                        val new = changeNewPasswordInput.trim()
                        if (curr.isNotEmpty() && new.isNotEmpty()) {
                            val success = viewModel.changeDevicePassword(curr, new)
                            if (success) {
                                showChangePasswordDialog = false
                                AppUiFeedback.show(if (appLanguage == "Spanish") "¡Petición de cambio de contraseña enviada!" else "Password change request sent!", duration = SnackbarDuration.Short)
                            } else {
                                changePasswordError = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                ) {
                    Text(t("Change", appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text(t("Clear Chat History", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = { Text(t("Are you sure you want to permanently delete all messages? This action cannot be undone.", appLanguage), color = TextMuted, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllMessages()
                        showClearChatDialog = false
                        AppUiFeedback.show(if (appLanguage == "Spanish") "Historial de chat borrado" else "Chat history cleared", duration = SnackbarDuration.Short)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextLight)
                ) {
                    Text(t("Delete All", appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextLight)
                }
            },
            containerColor = SurfaceDark
        )
    }

    channelPendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { channelPendingDelete = null },
            title = {
                Text(
                    if (appLanguage == "Spanish") "Eliminar canal" else "Delete channel",
                    color = TextLight,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (appLanguage == "Spanish")
                        "¿Eliminar «${pending.name}»? Los mensajes locales del canal no se borran."
                    else
                        "Remove “${pending.name}”? Local channel messages are kept.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChannel(pending.id)
                        channelsList = viewModel.getChannelsList()
                        channelPendingDelete = null
                        AppUiFeedback.show(t("Channel deleted.", appLanguage), duration = SnackbarDuration.Short)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextLight)
                ) {
                    Text(if (appLanguage == "Spanish") "Eliminar" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { channelPendingDelete = null }) {
                    Text(t("Cancel", appLanguage), color = TextLight)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showResetNodesDialog) {
        AlertDialog(
            onDismissRequest = { showResetNodesDialog = false },
            title = { Text(t("Reset Node Directory", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = { Text(t("Are you sure you want to clear all discovered nodes? The active directory will rebuild as new packets are received.", appLanguage), color = TextMuted, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllNodes()
                        showResetNodesDialog = false
                        AppUiFeedback.show(if (appLanguage == "Spanish") "Directorio de nodos reiniciado" else "Nodes directory reset", duration = SnackbarDuration.Short)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextLight)
                ) {
                    Text(t("Reset", appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetNodesDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextLight)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showRepeaterConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRepeaterConfirmDialog = false },
            title = { Text(t("Enable Repeater Mode?", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = { Text(t("WARNING: In Low-Power Repeater mode, the node turns off its BLE transceivers to maximize battery. You will lose connection immediately. To configure the node again, you must hold the hardware boot button on boot to trigger factory reset.", appLanguage), color = TextMuted, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        saveConfigAndNotify()
                        showRepeaterConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextLight)
                ) {
                    Text(t("Apply & Disconnect", appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepeaterConfirmDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextLight)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showRegenKeysDialog) {
        AlertDialog(
            onDismissRequest = { showRegenKeysDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    ecdhKeys = viewModel.regenerateEcdhKeys()
                    showPrivateKey = false
                    showRegenKeysDialog = false
                    AppUiFeedback.show(t("Keys regenerated.", appLanguage), duration = SnackbarDuration.Short)
                }) { Text(t("Regenerate Private Key", appLanguage), color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { showRegenKeysDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            title = { Text(t("Regenerate Private Key", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    t("This replaces your device keypair. Existing encrypted direct-message threads may become unreadable.", appLanguage),
                    color = TextMuted, fontSize = 13.sp
                )
            },
            containerColor = SurfaceDark
        )
    }

    if (showAddChannelDialog) {
        var newChannelName by remember { mutableStateOf("") }
        var newChannelPsk by remember { mutableStateOf(viewModel.generateRandomPsk()) }
        val nameValid = newChannelName.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = { showAddChannelDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.insertChannel(
                            ChannelConfig(
                                name = newChannelName.trim(),
                                psk = newChannelPsk,
                                isPrimary = false
                            )
                        )
                        channelsList = viewModel.getChannelsList()
                        showAddChannelDialog = false
                        AppUiFeedback.show(t("Channel added.", appLanguage), duration = SnackbarDuration.Short)
                    },
                    enabled = nameValid
                ) { Text(t("Save", appLanguage), color = if (nameValid) AccentMint else TextMuted) }
            },
            dismissButton = {
                TextButton(onClick = { showAddChannelDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            title = { Text(t("Add Secondary Channel", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        if (appLanguage == "Spanish")
                            "Crea un canal secundario. Todos los nodos necesitan el mismo nombre y PSK."
                        else
                            "Creates a secondary channel. Every node needs the same name and PSK.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(t("Channel Name", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    TextField(
                        value = newChannelName,
                        onValueChange = { if (it.length <= 24) newChannelName = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                if (appLanguage == "Spanish") "p. ej. Equipo-Sendero" else "e.g. Trail-Crew",
                                color = TextMuted
                            )
                        },
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(t("PSK Key (Base64)", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            newChannelPsk,
                            color = AccentMint,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (appLanguage == "Spanish")
                                "Regenerar PSK"
                            else
                                "Regenerate PSK",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp).clickable {
                                newChannelPsk = viewModel.generateRandomPsk()
                            }
                        )
                    }
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showEditChannelDialog && editingChannel != null) {
        var chanName by remember { mutableStateOf(editingChannel!!.name) }
        var chanPsk by remember { mutableStateOf(editingChannel!!.psk) }
        var uplink by remember { mutableStateOf(editingChannel!!.uplinkEnabled) }
        var downlink by remember { mutableStateOf(editingChannel!!.downlinkEnabled) }
        var position by remember { mutableStateOf(editingChannel!!.positionEnabled) }
        var precise by remember { mutableStateOf(editingChannel!!.preciseLocation) }
        var precision by remember { mutableStateOf(editingChannel!!.precisionMiles) }
        
        AlertDialog(
            onDismissRequest = { showEditChannelDialog = false },
            title = {
                Text(
                    text = t("Channels", appLanguage), 
                    color = TextLight, 
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(t("Channel Name", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    TextField(
                        value = chanName,
                        onValueChange = { if (it.length <= 24) chanName = it },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(t("PSK Key (Base64)", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = chanPsk,
                            color = AccentMint,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (appLanguage == "Spanish") "Regenerar PSK" else "Regenerate PSK",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp).clickable {
                                chanPsk = viewModel.generateRandomPsk()
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Switch Rows
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("Uplink enabled", appLanguage), color = TextLight, fontSize = 13.sp)
                        Switch(
                            checked = uplink,
                            onCheckedChange = { uplink = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = AccentMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("Downlink enabled", appLanguage), color = TextLight, fontSize = 13.sp)
                        Switch(
                            checked = downlink,
                            onCheckedChange = { downlink = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = AccentMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("Position enabled", appLanguage), color = TextLight, fontSize = 13.sp)
                        Switch(
                            checked = position,
                            onCheckedChange = { position = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = AccentMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }
                    
                    if (position) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t("Precise location", appLanguage), color = TextLight, fontSize = 13.sp)
                            Switch(
                                checked = precise,
                                onCheckedChange = { precise = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBackground,
                                    checkedTrackColor = AccentMint,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = BorderDark
                                )
                            )
                        }
                        
                        if (!precise) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = t("Location Fuzzing Precision", appLanguage),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = precision,
                                onValueChange = { precision = it },
                                valueRange = 0.5f..5.0f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentMint,
                                    activeTrackColor = AccentMint,
                                    inactiveTrackColor = BorderDark
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "± ${"%.1f".format(precision)} mi",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = editingChannel!!.copy(
                            name = chanName.trim(),
                            psk = chanPsk,
                            uplinkEnabled = uplink,
                            downlinkEnabled = downlink,
                            positionEnabled = position,
                            preciseLocation = precise,
                            precisionMiles = if (position && !precise) precision else 0.0f
                        )
                        viewModel.updateChannel(updated)
                        channelsList = viewModel.getChannelsList()
                        showEditChannelDialog = false
                    },
                    enabled = chanName.trim().isNotEmpty()
                ) {
                    Text(t("Save", appLanguage), color = AccentMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditChannelDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showImportChannelDialog) {
        val spanish = appLanguage == "Spanish"
        val parsedJoin = remember(importChannelLinkInput) {
            try {
                val cleaned = importChannelLinkInput.trim()
                if (cleaned.isEmpty()) return@remember null
                val base64Part = when {
                    cleaned.contains("#") -> cleaned.substringAfter("#")
                    cleaned.startsWith("aethermesh://channel") -> {
                        // Raw deep link — encode path for preview decode path below
                        return@remember android.net.Uri.parse(cleaned).let { uri ->
                            Triple(
                                uri.getQueryParameter("name") ?: "Imported",
                                uri.getQueryParameter("psk").orEmpty(),
                                true
                            )
                        }
                    }
                    else -> cleaned
                }
                val decodedStr = String(android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT))
                if (!decodedStr.startsWith("aethermesh://channel")) return@remember null
                val uri = android.net.Uri.parse(decodedStr)
                Triple(
                    uri.getQueryParameter("name") ?: "Imported",
                    uri.getQueryParameter("psk").orEmpty(),
                    true
                )
            } catch (_: Exception) {
                null
            }
        }
        AlertDialog(
            onDismissRequest = { showImportChannelDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val cleaned = importChannelLinkInput.trim()
                            val uri = if (cleaned.startsWith("aethermesh://channel")) {
                                android.net.Uri.parse(cleaned)
                            } else {
                                val base64Part = if (cleaned.contains("#")) {
                                    cleaned.substringAfter("#")
                                } else {
                                    cleaned
                                }
                                val decodedStr = String(
                                    android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                                )
                                if (!decodedStr.startsWith("aethermesh://channel")) {
                                    throw Exception("Invalid URI scheme")
                                }
                                android.net.Uri.parse(decodedStr)
                            }
                            val name = uri.getQueryParameter("name") ?: "Imported"
                            val psk = uri.getQueryParameter("psk") ?: ""
                            val uplink = uri.getQueryParameter("uplink")?.toBoolean() ?: true
                            val downlink = uri.getQueryParameter("downlink")?.toBoolean() ?: true
                            val position = uri.getQueryParameter("position")?.toBoolean() ?: true
                            val precise = uri.getQueryParameter("precise")?.toBoolean() ?: true

                            // Join always adds a secondary channel (doesn't replace primary).
                            val newChan = ChannelConfig(
                                name = name,
                                psk = psk,
                                isPrimary = false,
                                uplinkEnabled = uplink,
                                downlinkEnabled = downlink,
                                positionEnabled = position,
                                preciseLocation = precise,
                                precisionMiles = 1f
                            )
                            viewModel.insertChannel(newChan)
                            channelsList = viewModel.getChannelsList()
                            showImportChannelDialog = false
                            AppUiFeedback.show(
                                if (spanish) "Canal secundario «$name» añadido"
                                else "Secondary channel \"$name\" added",
                                duration = SnackbarDuration.Short
                            )
                        } catch (e: Exception) {
                            AppUiFeedback.show(
                                if (spanish) "Enlace de canal no válido"
                                else "Invalid channel link",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    enabled = parsedJoin != null
                ) {
                    Text(
                        t("Join", appLanguage),
                        color = if (parsedJoin != null) AccentMint else TextMuted
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportChannelDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            title = { Text(t("Join Channel", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        if (spanish)
                            "Pega un enlace AetherMesh. Se añadirá como canal secundario."
                        else
                            "Paste an AetherMesh link. It will be added as a secondary channel.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(t("Paste AetherMesh Channel Link", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = importChannelLinkInput,
                        onValueChange = { importChannelLinkInput = it },
                        singleLine = false,
                        maxLines = 3,
                        placeholder = {
                            Text(
                                if (spanish) "https://aethermesh.org/join#…"
                                else "https://aethermesh.org/join#...",
                                color = TextMuted
                            )
                        },
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    parsedJoin?.let { (name, psk, _) ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            if (spanish) "Vista previa" else "Preview",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (spanish) "Nombre: $name" else "Name: $name",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                        Text(
                            if (spanish)
                                "PSK: ${if (psk.isNotEmpty()) "presente (${psk.length} car.)" else "ninguna"}"
                            else
                                "PSK: ${if (psk.isNotEmpty()) "present (${psk.length} chars)" else "none"}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (importChannelLinkInput.trim().isNotEmpty() && parsedJoin == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (spanish) "Enlace no reconocido todavía"
                            else "Link not recognized yet",
                            color = AccentAmber,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            containerColor = SurfaceDark
        )
    }
}



