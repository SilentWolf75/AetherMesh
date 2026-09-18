package com.silentwolf75.aethermesh.ui.main

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silentwolf75.aethermesh.data.AppUiPrefs
import com.silentwolf75.aethermesh.data.ChatMessage
import com.silentwolf75.aethermesh.data.ChannelConfig
import com.silentwolf75.aethermesh.data.ChannelInviteLink
import com.silentwolf75.aethermesh.data.LocalConfigFixed
import com.silentwolf75.aethermesh.data.LocalConfigSavePolicy
import com.silentwolf75.aethermesh.data.MeshNode
import com.silentwolf75.aethermesh.data.NodeSettingsBackup
import com.silentwolf75.aethermesh.data.NodeSettingsFormPolicy
import com.silentwolf75.aethermesh.data.NodeSettingsSnapshot
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import com.silentwolf75.aethermesh.ui.components.*
import com.silentwolf75.aethermesh.theme.AccentCyanDim
import com.silentwolf75.aethermesh.theme.AccentSteel
import com.silentwolf75.aethermesh.theme.AccentSteelDim
import com.silentwolf75.aethermesh.theme.appBackgroundBrush
import com.silentwolf75.aethermesh.theme.headerBarBrush
import com.silentwolf75.aethermesh.theme.primaryButtonBrush
import kotlinx.coroutines.launch
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

    val nodeNameState = remember { mutableStateOf("") }
    var nodeName by nodeNameState
    val nodeShortNameState = remember { mutableStateOf("") }
    var nodeShortName by nodeShortNameState
    val sfState = remember { mutableIntStateOf(11) }
    var sf by sfState
    val bwState = remember { mutableFloatStateOf(125f) }
    var bw by bwState
    val txPowerState = remember { mutableIntStateOf(22) }
    var txPower by txPowerState
    val regionState = remember { mutableIntStateOf(0) } // 0 = US915, 1 = EU868
    var region by regionState
    val roleState = remember { mutableIntStateOf(0) } // 0 = Client, 1 = Router, 2 = Low-Power Repeater
    var role by roleState
    var meshHopLimit by remember { mutableIntStateOf(4) }
    var maxHopLimit by remember { mutableIntStateOf(com.silentwolf75.aethermesh.data.HopRangePolicy.LEGACY_MAX) }
    var rebroadcastTxdelayX100 by remember { mutableIntStateOf(100) }
    val telemetryIntervalSecsState = remember { mutableIntStateOf(60) }
    var telemetryIntervalSecs by telemetryIntervalSecsState
    val screenTimeoutSecsState = remember { mutableIntStateOf(30) }
    var screenTimeoutSecs by screenTimeoutSecsState
    val powerSaveModeEnabledState = remember { mutableStateOf(false) }
    var powerSaveModeEnabled by powerSaveModeEnabledState
    val positionPrecisionMState = remember { mutableIntStateOf(0) }
    var positionPrecisionM by positionPrecisionMState
    val nodeGpsModeState = remember { mutableIntStateOf(0) } // 0=on, 1=off, 2=duty-cycle
    var nodeGpsMode by nodeGpsModeState
    val gpsDutyIntervalSecsState = remember { mutableIntStateOf(900) }
    var gpsDutyIntervalSecs by gpsDutyIntervalSecsState
    val fixedPositionEnabledState = remember { mutableStateOf(false) }
    var fixedPositionEnabled by fixedPositionEnabledState
    val fixedLatInputState = remember { mutableStateOf("") }
    var fixedLatInput by fixedLatInputState
    val fixedLonInputState = remember { mutableStateOf("") }
    var fixedLonInput by fixedLonInputState
    val fixedAltInputState = remember { mutableStateOf("") }
    var fixedAltInput by fixedAltInputState

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var settingsImportDirty by remember { mutableStateOf(false) }

    var showRepeaterConfirmDialog by remember { mutableStateOf(false) }
    var showDeployConfirmDialog by remember { mutableStateOf(false) }
    var deployConfirmProfile by remember { mutableStateOf<DeployProfile?>(null) }
    var channelPendingDelete by remember { mutableStateOf<ChannelConfig?>(null) }

    val sharedPrefs = remember {
        context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE).also { prefs ->
            // 1.3.2: drop Phase I MQTT/APRS interop stubs (offline-first; never wired to a client).
            if (prefs.contains("mqtt_enabled") ||
                prefs.contains("mqtt_broker_url") ||
                prefs.contains("mqtt_topic_prefix") ||
                prefs.contains("mqtt_username") ||
                prefs.contains("aprs_callsign")
            ) {
                prefs.edit()
                    .remove("mqtt_enabled")
                    .remove("mqtt_broker_url")
                    .remove("mqtt_topic_prefix")
                    .remove("mqtt_username")
                    .remove("aprs_callsign")
                    .apply()
            }
        }
    }
    val useImperialUnitsSettingState = remember { mutableStateOf(sharedPrefs.getBoolean(AppUiPrefs.IMPERIAL, true)) }
    var useImperialUnitsSetting by useImperialUnitsSettingState
    val enablePhoneGpsSharingState = remember { mutableStateOf(sharedPrefs.getBoolean(AppUiPrefs.PHONE_GPS_SHARING, true)) }
    var enablePhoneGpsSharing by enablePhoneGpsSharingState

    val consoleMessages by viewModel.messages.collectAsStateWithLifecycle()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = NodeSettingsBackup.toJson(
                    NodeSettingsSnapshot(
                        nodeName = nodeName,
                        nodeShortName = nodeShortName,
                        loraSf = sf,
                        loraBw = bw,
                        loraTxPower = txPower,
                        region = region,
                        nodeRole = role,
                        telemetryInterval = telemetryIntervalSecs,
                        screenTimeout = screenTimeoutSecs,
                        powerSaveMode = powerSaveModeEnabled,
                        positionPrecision = positionPrecisionM,
                        gpsMode = nodeGpsMode,
                        gpsDutyIntervalSecs = gpsDutyIntervalSecs,
                        fixedPosition = fixedPositionEnabled,
                        fixedLatitude = fixedLatInput.toFloatOrNull() ?: 0f,
                        fixedLongitude = fixedLonInput.toFloatOrNull() ?: 0f,
                        fixedAltitude = fixedAltInput.toIntOrNull() ?: 0
                    )
                )
                
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
                    val fallback = NodeSettingsSnapshot(
                        nodeName = nodeName,
                        nodeShortName = nodeShortName,
                        loraSf = sf,
                        loraBw = bw,
                        loraTxPower = txPower,
                        region = region,
                        nodeRole = role,
                        telemetryInterval = telemetryIntervalSecs,
                        screenTimeout = screenTimeoutSecs,
                        powerSaveMode = powerSaveModeEnabled,
                        positionPrecision = positionPrecisionM,
                        gpsMode = nodeGpsMode,
                        gpsDutyIntervalSecs = gpsDutyIntervalSecs,
                        fixedPosition = fixedPositionEnabled,
                        fixedLatitude = fixedLatInput.toFloatOrNull() ?: 0f,
                        fixedLongitude = fixedLonInput.toFloatOrNull() ?: 0f,
                        fixedAltitude = fixedAltInput.toIntOrNull() ?: 0
                    )
                    val snap = NodeSettingsBackup.fromJson(jsonString, fallback)
                    nodeName = snap.nodeName
                    nodeShortName = snap.nodeShortName
                    sf = snap.loraSf
                    bw = snap.loraBw
                    txPower = snap.loraTxPower
                    region = snap.region
                    role = snap.nodeRole
                    telemetryIntervalSecs = snap.telemetryInterval
                    screenTimeoutSecs = snap.screenTimeout
                    powerSaveModeEnabled = snap.powerSaveMode
                    positionPrecisionM = snap.positionPrecision
                    nodeGpsMode = snap.gpsMode
                    gpsDutyIntervalSecs = snapGpsDutyIntervalSecs(snap.gpsDutyIntervalSecs)
                    fixedPositionEnabled = snap.fixedPosition
                    fixedLatInput = snap.fixedLatitude.toString()
                    fixedLonInput = snap.fixedLongitude.toString()
                    fixedAltInput = snap.fixedAltitude.toString()

                    // Persist so a concurrent device-config sync doesn't wipe the form.
                    val nodeKey = viewModel.connectedNodeId
                    if (nodeKey != 0L) {
                        NodeSettingsBackup.writeToPrefs(
                            context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE),
                            snap.copy(gpsDutyIntervalSecs = gpsDutyIntervalSecs)
                        )
                    }
                    settingsImportDirty = true
                    
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

    val exportMigrationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = viewModel.exportAppMigrationJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                AppUiFeedback.show(
                    if (sharedPrefs.getString("app_language", "English") == "Spanish")
                        "Datos exportados. Guárdalos antes de desinstalar la app antigua."
                    else
                        "App data exported. Save the file before uninstalling the old app.",
                    duration = SnackbarDuration.Long
                )
            } catch (e: Exception) {
                AppUiFeedback.show(
                    if (sharedPrefs.getString("app_language", "English") == "Spanish")
                        "Error al exportar: ${e.message}"
                    else
                        "Export failed: ${e.message}",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    val importMigrationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
                } ?: return@rememberLauncherForActivityResult
                val result = viewModel.importAppMigrationJson(json)
                AppUiFeedback.show(
                    if (result.success) result.summary
                    else if (sharedPrefs.getString("app_language", "English") == "Spanish")
                        "Importación fallida: ${result.summary}"
                    else
                        "Import failed: ${result.summary}",
                    duration = SnackbarDuration.Long
                )
            } catch (e: Exception) {
                AppUiFeedback.show(
                    if (sharedPrefs.getString("app_language", "English") == "Spanish")
                        "Error al importar: ${e.message}"
                    else
                        "Import failed: ${e.message}",
                    duration = SnackbarDuration.Long
                )
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

    val appThemeState = remember {
        mutableStateOf(AppUiPrefs.clampTheme(sharedPrefs.getString(AppUiPrefs.THEME, AppUiPrefs.THEME_SYSTEM)))
    }
    var appTheme by appThemeState
    val appLanguageState = remember {
        mutableStateOf(AppUiPrefs.clampLanguage(sharedPrefs.getString(AppUiPrefs.LANGUAGE, AppUiPrefs.LANG_ENGLISH)))
    }
    var appLanguage by appLanguageState
    val spanishUi = appLanguage == "Spanish"

    val settingsTwoPane = rememberAdaptiveLayoutInfo().useTwoPane

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppUiPrefs.LANGUAGE) {
                appLanguage = AppUiPrefs.clampLanguage(sharedPrefs.getString(AppUiPrefs.LANGUAGE, AppUiPrefs.LANG_ENGLISH))
            }
            if (key == AppUiPrefs.PHONE_GPS_SHARING) {
                enablePhoneGpsSharing = sharedPrefs.getBoolean(AppUiPrefs.PHONE_GPS_SHARING, true)
            }
            if (key == AppUiPrefs.IMPERIAL) {
                useImperialUnitsSetting = sharedPrefs.getBoolean(AppUiPrefs.IMPERIAL, true)
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
    val channelPrivacyStatus by viewModel.channelPrivacyStatus.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.connectedNodeId, nodes, deviceConfigSyncEpoch) {
        channelsList = viewModel.getChannelsList()
        val nodeKey = viewModel.connectedNodeId
        val shouldReloadConfig = NodeSettingsFormPolicy.shouldReload(
            nodeKey,
            configLoadedForNode,
            deviceConfigSyncEpoch,
            lastDeviceConfigSyncEpoch,
            settingsImportDirty
        )
        if (shouldReloadConfig) {
            configLoadedForNode = nodeKey
            lastDeviceConfigSyncEpoch = deviceConfigSyncEpoch
            val nodePrefs = context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
            val matchedNode = nodes.find { it.nodeId == nodeKey }
            val form = NodeSettingsFormPolicy.readFromPrefs(
                nodePrefs,
                matchedNode?.name,
                matchedNode?.shortName
            )
            nodeName = form.nodeName
            nodeShortName = form.nodeShortName
            sf = form.loraSf
            bw = form.loraBw
            txPower = form.loraTxPower
            region = form.region
            role = form.nodeRole
            meshHopLimit = form.meshHopLimit
            maxHopLimit = com.silentwolf75.aethermesh.data.NodeSettingsPrefs.readMaxHopLimit(nodePrefs)
            rebroadcastTxdelayX100 = form.rebroadcastTxdelayX100
            telemetryIntervalSecs = form.telemetryInterval
            screenTimeoutSecs = form.screenTimeout
            powerSaveModeEnabled = form.powerSaveMode
            positionPrecisionM = form.positionPrecision
            nodeGpsMode = form.gpsMode
            gpsDutyIntervalSecs = form.gpsDutyIntervalSecs
            fixedPositionEnabled = form.fixedPosition
            fixedLatInput = form.fixedLatInput
            fixedLonInput = form.fixedLonInput
            fixedAltInput = form.fixedAltInput
        }
    }

    val saveConfigAndNotify = fun() {
        val spanish = appLanguage == "Spanish"
        when (
            val fixed = LocalConfigSavePolicy.parse(
                fixedPositionEnabled, fixedLatInput, fixedLonInput, fixedAltInput
            )
        ) {
            LocalConfigFixed.Invalid -> {
                AppUiFeedback.show(
                    LocalConfigSavePolicy.invalidFixedMessage(spanish),
                    duration = SnackbarDuration.Long
                )
                return
            }
            is LocalConfigFixed.Ready -> {
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
                    fixedLatitude = fixed.latitude,
                    fixedLongitude = fixed.longitude,
                    fixedAltitude = fixed.altitude,
                    meshHopLimit = meshHopLimit,
                    rebroadcastTxdelayX100 = rebroadcastTxdelayX100
                )
                if (success) {
                    settingsImportDirty = false
                    val nodeKey = viewModel.connectedNodeId
                    if (nodeKey != 0L) {
                        LocalConfigSavePolicy.persistPrefs(
                            context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE),
                            NodeSettingsSnapshot(
                                nodeName = nodeName.trim(),
                                nodeShortName = nodeShortName.trim(),
                                loraSf = sf,
                                loraBw = bw,
                                loraTxPower = txPower,
                                region = region,
                                nodeRole = role,
                                telemetryInterval = telemetryIntervalSecs,
                                screenTimeout = screenTimeoutSecs,
                                powerSaveMode = powerSaveModeEnabled,
                                positionPrecision = positionPrecisionM,
                                gpsMode = nodeGpsMode,
                                gpsDutyIntervalSecs = gpsDutyIntervalSecs,
                                fixedPosition = fixedPositionEnabled,
                                fixedLatitude = fixed.latitude,
                                fixedLongitude = fixed.longitude,
                                fixedAltitude = fixed.altitude
                            ),
                            meshHopLimit,
                            rebroadcastTxdelayX100
                        )
                    }
                    AppUiFeedback.show(
                        LocalConfigSavePolicy.sentMessage(powerSaveModeEnabled, spanish),
                        duration = SnackbarDuration.Long
                    )
                } else {
                    AppUiFeedback.show(
                        LocalConfigSavePolicy.failedMessage(spanish),
                        duration = SnackbarDuration.Short
                    )
                }
            }
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
            Triple(SettingsCategory.FIRMWARE, "Firmware Update", "Stable Releases or Pages OTA; Heltec .bin / RAK .zip only"),
            Triple(SettingsCategory.SECURITY, "Security & Keys", "Manage private keys, ECDH keypairs, and device password")
        )
        val appCategories = listOf(
            Triple(SettingsCategory.PREFERENCES, "App Preferences", "Set language, theme, units, and background alerts"),
            Triple(SettingsCategory.DEVELOPER, "Developer & Diagnostics", "Live logs console, packet exports, and system database reset")
        )

        fun categoryNeedsDevice(cat: SettingsCategory): Boolean =
            cat != SettingsCategory.PREFERENCES &&
                cat != SettingsCategory.DEVELOPER

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
                                if (spanishUi) "Conecta un nodo para configurar esto."
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
                if (spanishUi) "Nodo" else "Device",
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
                if (spanishUi) "Aplicación" else "App",
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
                    if (spanishUi)
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
                            text = if (spanishUi)
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
                    if (spanishUi)
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
                title = if (spanishUi) "Nodo" else "Device",
                trailing = if (isConnected) null else if (spanishUi) "Bloqueado" else "Locked",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            deviceCategories.forEach { (cat, title, desc) ->
                SettingsCategoryCard(cat, title, desc)
            }
            Spacer(modifier = Modifier.height(8.dp))
            AetherSectionHeader(
                title = if (spanishUi) "Aplicación" else "App",
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
            ChannelSettings(
                privacyStatus = channelPrivacyStatus,
                channelsList = channelsList,
                connectedNodeId = viewModel.connectedNodeId,
                appLanguage = appLanguage,
                onAddChannel = { showAddChannelDialog = true },
                onEditChannel = { channel ->
                    editingChannel = channel
                    showEditChannelDialog = true
                },
                onDeleteChannel = { channel -> channelPendingDelete = channel },
                onJoinChannel = {
                    importChannelLinkInput = ""
                    showImportChannelDialog = true
                }
            )
        }

        if (activeCategory == SettingsCategory.RADIO) {
            RadioSettings(
                isConnected = isConnected,
                appLanguage = appLanguage,
                nodeNameState = nodeNameState,
                nodeShortNameState = nodeShortNameState,
                sfState = sfState,
                bwState = bwState,
                txPowerState = txPowerState,
                regionState = regionState,
                roleState = roleState,
                nodeGpsModeState = nodeGpsModeState,
                gpsDutyIntervalSecsState = gpsDutyIntervalSecsState,
                telemetryIntervalSecsState = telemetryIntervalSecsState,
                screenTimeoutSecsState = screenTimeoutSecsState,
                powerSaveModeEnabledState = powerSaveModeEnabledState,
                connectedModel = connectedNode?.model,
                onApply = { saveConfigAndNotify() },
                onRequestRepeaterConfirm = { showRepeaterConfirmDialog = true },
                onRequestDeployConfirm = { profile ->
                    deployConfirmProfile = profile
                    showDeployConfirmDialog = true
                },
                onRequestPasswordChange = { showChangePasswordDialog = true }
            )
        }

        if (activeCategory == SettingsCategory.POSITION) {
            PositionSettings(
                connectedNode = connectedNode,
                isConnected = isConnected,
                appLanguage = appLanguage,
                useImperialUnitsSetting = useImperialUnitsSetting,
                nodeGpsModeState = nodeGpsModeState,
                gpsDutyIntervalSecsState = gpsDutyIntervalSecsState,
                telemetryIntervalSecsState = telemetryIntervalSecsState,
                positionPrecisionMState = positionPrecisionMState,
                screenTimeoutSecsState = screenTimeoutSecsState,
                powerSaveModeEnabledState = powerSaveModeEnabledState,
                fixedPositionEnabledState = fixedPositionEnabledState,
                fixedLatInputState = fixedLatInputState,
                fixedLonInputState = fixedLonInputState,
                fixedAltInputState = fixedAltInputState,
                enablePhoneGpsSharingState = enablePhoneGpsSharingState,
                onApply = { saveConfigAndNotify() }
            )
        }

        if (activeCategory == SettingsCategory.FIRMWARE) {
            FirmwareUpdateSettings(
                viewModel = viewModel,
                isConnected = isConnected,
                isDeviceAuthenticated = isDeviceAuthenticated,
                connectedNode = connectedNode,
                appLanguage = appLanguage
            )
        }


        if (activeCategory == SettingsCategory.SECURITY) {
            SecuritySettings(
                viewModel = viewModel,
                appLanguage = appLanguage
            )
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
                maxHopLimit = maxHopLimit,
                onMeshHopLimitChange = { meshHopLimit = it },
                rebroadcastTxdelayX100 = rebroadcastTxdelayX100,
                onRebroadcastTxdelayChange = { rebroadcastTxdelayX100 = it },
                onApplyRoutingSettings = { saveConfigAndNotify() }
            )
        }


        if (activeCategory == SettingsCategory.PREFERENCES) {
            PreferencesSettings(
                viewModel = viewModel,
                sharedPrefs = sharedPrefs,
                appLanguageState = appLanguageState,
                appThemeState = appThemeState,
                useImperialUnitsSettingState = useImperialUnitsSettingState,
                consoleMessages = consoleMessages,
                diagnosticLogs = diagnosticLogs
            )
        }

        if (activeCategory == SettingsCategory.DEVELOPER) {
            DeveloperSettings(
                viewModel = viewModel,
                sharedPrefs = sharedPrefs,
                appLanguage = appLanguage,
                consoleMessages = consoleMessages,
                diagnosticLogs = diagnosticLogs,
                onBackupSettings = {
                    createDocLauncher.launch("aethermesh_backup_${connectedNode?.nodeId ?: 0L}.json")
                },
                onRestoreSettings = {
                    restoreSettingsLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*"))
                },
                onExportMigration = {
                    exportMigrationLauncher.launch("aethermesh_migration_${System.currentTimeMillis()}.json")
                },
                onImportMigration = {
                    importMigrationLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                }
            )
        }
    }

    // --- DIALOGS SECTION ---

    ChannelSettingsDialogs(
        viewModel = viewModel,
        appLanguage = appLanguage,
        spanishUi = spanishUi,
        channelsList = channelsList,
        onChannelsChanged = { channelsList = it },
        showAddChannelDialog = showAddChannelDialog,
        onDismissAdd = { showAddChannelDialog = false },
        showEditChannelDialog = showEditChannelDialog,
        editingChannel = editingChannel,
        onDismissEdit = { showEditChannelDialog = false },
        showImportChannelDialog = showImportChannelDialog,
        importChannelLinkInput = importChannelLinkInput,
        onImportInputChange = { importChannelLinkInput = it },
        onDismissImport = { showImportChannelDialog = false },
        channelPendingDelete = channelPendingDelete,
        onDismissDelete = { channelPendingDelete = null }
    )

    RadioSettingsDialogs(
        viewModel = viewModel,
        appLanguage = appLanguage,
        spanishUi = spanishUi,
        showChangePasswordDialog = showChangePasswordDialog,
        onDismissPassword = { showChangePasswordDialog = false },
        showRepeaterConfirmDialog = showRepeaterConfirmDialog,
        onDismissRepeater = { showRepeaterConfirmDialog = false },
        onConfirmRepeater = {
            saveConfigAndNotify()
            showRepeaterConfirmDialog = false
        },
        showDeployConfirmDialog = showDeployConfirmDialog,
        deployConfirmProfile = deployConfirmProfile,
        onDismissDeploy = {
            showDeployConfirmDialog = false
            deployConfirmProfile = null
        },
        onConfirmDeploy = {
            saveConfigAndNotify()
            showDeployConfirmDialog = false
            deployConfirmProfile = null
        },
        role = role,
        nodeGpsMode = nodeGpsMode,
        gpsDutyIntervalSecs = gpsDutyIntervalSecs,
        powerSaveModeEnabled = powerSaveModeEnabled,
        telemetryIntervalSecs = telemetryIntervalSecs,
        txPower = txPower
    )
}
