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
@Composable
fun ConnectionView(
    viewModel: MainScreenViewModel,
    isConnected: Boolean,
    nodes: List<MeshNode>,
    scannedDevices: List<BleDeviceItem>,
    appLanguage: String = "English",
    onOpenMeshRouting: () -> Unit = {},
    onContinueToMesh: () -> Unit = {}
) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanBlockReason by viewModel.scanBlockReason.collectAsStateWithLifecycle()
    val spanish = appLanguage == "Spanish"
    val isDeviceAuthenticated by viewModel.isDeviceAuthenticated.collectAsStateWithLifecycle()
    val blePhase by viewModel.bleConnectionPhase.collectAsStateWithLifecycle()
    val bleReconnectAttempt by viewModel.bleReconnectAttempt.collectAsStateWithLifecycle()
    val bleReconnectGaveUp by viewModel.bleReconnectGaveUp.collectAsStateWithLifecycle()
    val connectedNode = resolveConnectedMeshNode(
        nodes = nodes,
        connectedId = viewModel.connectedNodeId,
        deviceName = viewModel.connectedDeviceName
    )
    val displayName = connectedNode?.name?.takeIf { it.isNotBlank() }
        ?: viewModel.connectedDeviceName
        ?: "Wolf Base"
    val shortName = connectedNode?.shortName?.takeIf { it.isNotBlank() }
        ?: getShortName(displayName, connectedNode?.nodeId ?: viewModel.connectedNodeId)
    val badgeColor = getBadgeColor(displayName)
    val batteryVal = connectedNode?.battery ?: 0

    // MeshCore-style: auto-scan once when the scanner is shown (not a loop).
    var didAutoScan by remember { mutableStateOf(false) }
    var showSwitchDevice by remember { mutableStateOf(false) }
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            didAutoScan = false
            showSwitchDevice = false
        } else if (isScanning) {
            viewModel.stopScanning()
        }
    }
    val scannerVisible = !isConnected || showSwitchDevice
    LaunchedEffect(scannerVisible, scanBlockReason) {
        if (scannerVisible &&
            !isConnected &&
            !didAutoScan &&
            !isScanning &&
            scanBlockReason == com.example.aethermesh.ble.BleScanBlockReason.None
        ) {
            didAutoScan = true
            viewModel.startScanning()
        }
    }
    LaunchedEffect(showSwitchDevice) {
        if (showSwitchDevice &&
            isConnected &&
            !isScanning &&
            scanBlockReason == com.example.aethermesh.ble.BleScanBlockReason.None
        ) {
            viewModel.startScanning()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (!isConnected) {
            Text(
                if (spanish)
                    "Conecta una radio AetherMesh por Bluetooth para usar chats, nodos y el mapa."
                else
                    "Connect an AetherMesh radio over Bluetooth to use chats, nodes, and the map.",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            when {
                bleReconnectGaveUp -> {
                    Text(
                        if (appLanguage == "Spanish")
                            "La reconexión automática se detuvo tras varios intentos."
                        else
                            "Auto-reconnect stopped after several attempts.",
                        color = AccentAmber,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.retryBleConnection() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (appLanguage == "Spanish") "Reintentar conexión" else "Retry connection",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                blePhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ||
                    blePhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = AccentAmber,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    when (blePhase) {
                                        com.example.aethermesh.ble.BleConnectionPhase.Connecting ->
                                            if (spanish) "Conectando…" else "Connecting…"
                                        else ->
                                            if (spanish) "Reconectando (intento $bleReconnectAttempt)…"
                                            else "Reconnecting (attempt $bleReconnectAttempt)…"
                                    },
                                    color = AccentAmber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            TextButton(
                                onClick = { viewModel.disconnectDevice() },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (spanish) "Cancelar" else "Cancel",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Connected Node Card
        if (isConnected) {
            Button(
                onClick = onContinueToMesh,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (spanish) "Continuar a Chats" else "Continue to Chats",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BatteryArcGauge(
                            level = batteryVal,
                            charging = connectedNode?.isCharging == true,
                            size = 72.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NodeBadge(shortName = shortName, color = badgeColor)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(displayName, color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    val fwVersion = connectedNode?.firmwareVersion?.takeIf { it.isNotEmpty() }
                                        ?: if (spanish) "desconocida" else "unknown"
                                    Text(
                                        "${t("Firmware Version", appLanguage)}: $fwVersion",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if ((connectedNode?.voltage ?: 0f) > 0f) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "%.2f V pack".format(connectedNode!!.voltage),
                                    color = AccentMint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GraphicStatTile(
                            label = "ID",
                            value = "0x${(connectedNode?.nodeId ?: viewModel.connectedNodeId).toString(16).uppercase().takeLast(4)}",
                            accent = AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                        GraphicStatTile(
                            label = if (appLanguage == "Spanish") "Modelo" else "Model",
                            value = connectedNode?.model?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                                ?.take(12)
                                ?: "—",
                            accent = AccentSteel,
                            modifier = Modifier.weight(1f)
                        )
                        GraphicStatTile(
                            label = if (appLanguage == "Spanish") "Enlace" else "Link",
                            value = "BLE",
                            accent = AccentMint,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val outdatedNodes = nodes.filter {
                        !isNodeStale(it.lastActive) &&
                            it.firmwareVersion.isNotEmpty() &&
                            isFirmwareTooOld(it.firmwareVersion)
                    }
                    if (outdatedNodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF422006))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (appLanguage == "Spanish")
                                    "Firmware demasiado antiguo para esta app en: " +
                                        outdatedNodes.joinToString(", ") { "${it.name} (${it.firmwareVersion})" } +
                                        ". Actualiza a $MIN_COMPATIBLE_FW o superior."
                                else
                                    "Firmware too old for this app on: " +
                                        outdatedNodes.joinToString(", ") { "${it.name} (${it.firmwareVersion})" } +
                                        ". Update to $MIN_COMPATIBLE_FW or newer.",
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
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
                                }
                            ) {
                                Text(
                                    if (appLanguage == "Spanish") "Flasher" else "Flasher",
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (isConnected && !isDeviceAuthenticated) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF422006))
                                .clickable { viewModel.promptDeviceAuthentication() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (spanish) "Bloqueado · Toca para autenticar" else "Locked · Tap to authenticate",
                                color = Color(0xFFFDE68A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onOpenMeshRouting,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            if (spanish)
                                "Salud mesh → Ajustes › Enrutamiento Mesh"
                            else
                                "Mesh health → Settings › Mesh Routing",
                            color = AccentCyan,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.disconnectDevice()
                            AppUiFeedback.show(
                                if (spanish) "Desconectado" else "Disconnected"
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(t("Disconnect", appLanguage), color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showSwitchDevice = !showSwitchDevice },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text(
                            when {
                                showSwitchDevice && spanish -> "Ocultar escáner"
                                showSwitchDevice -> "Hide scanner"
                                spanish -> "Cambiar dispositivo"
                                else -> "Switch device"
                            },
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            // Unconnected State Placeholder
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RadarGraphic(size = 128.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (appLanguage == "Spanish") "Ningún nodo conectado" else "No Node Connected", color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (appLanguage == "Spanish") "Busca un nodo WisBlock o Heltec y tócalo para emparejar." else "Scan for a WisBlock or Heltec node, then tap it to pair.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (!isScanning) viewModel.startScanning()
                        },
                        enabled = !isScanning,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color(0xFF061018)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = Color(0xFF061018),
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (spanish) "Escaneando…" else "Scanning…", color = Color(0xFF061018), fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF061018))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (spanish) "Buscar dispositivos" else "Scan for devices", color = Color(0xFF061018), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (scannerVisible) {
            // Bluetooth Scanner Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AetherSectionHeader(
                    title = if (appLanguage == "Spanish") "Dispositivos Bluetooth" else "Bluetooth Devices",
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        if (isScanning) viewModel.stopScanning() else viewModel.startScanning()
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isScanning) {
                            CircularProgressIndicator(color = AccentMint, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (spanish) "Parar" else "Stop", color = AccentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = AccentMint, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (spanish) "Escanear" else "Scan", color = AccentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (scanBlockReason != com.example.aethermesh.ble.BleScanBlockReason.None) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentAmber.copy(alpha = 0.18f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = when (scanBlockReason) {
                            com.example.aethermesh.ble.BleScanBlockReason.BluetoothOff ->
                                if (spanish) "Bluetooth está apagado. Actívalo para buscar nodos."
                                else "Bluetooth is off. Turn it on to scan for nodes."
                            com.example.aethermesh.ble.BleScanBlockReason.PermissionDenied ->
                                if (spanish)
                                    "Falta permiso de Bluetooth (o ubicación). Concédelo en Ajustes de la app."
                                else
                                    "Bluetooth (or location) permission is missing. Allow it in app Settings."
                            com.example.aethermesh.ble.BleScanBlockReason.NoScanner ->
                                if (spanish) "Este teléfono no ofrece un escáner BLE."
                                else "This phone has no BLE scanner available."
                            else -> ""
                        },
                        color = AccentAmber,
                        fontSize = 12.sp
                    )
                    when (scanBlockReason) {
                        com.example.aethermesh.ble.BleScanBlockReason.PermissionDenied -> {
                            TextButton(
                                onClick = {
                                    try {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                android.net.Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    } catch (_: Exception) { }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (spanish) "Abrir ajustes de la app" else "Open app settings",
                                    color = AccentCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        com.example.aethermesh.ble.BleScanBlockReason.BluetoothOff -> {
                            TextButton(
                                onClick = {
                                    try {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                        )
                                    } catch (_: Exception) { }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (spanish) "Abrir ajustes de Bluetooth" else "Open Bluetooth settings",
                                    color = AccentCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        else -> Unit
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bluetooth Devices Scan List
            if (scannedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .clickable(enabled = !isScanning) { viewModel.startScanning() }
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                scanBlockReason != com.example.aethermesh.ble.BleScanBlockReason.None ->
                                    if (spanish) "Escaneo bloqueado" else "Scan blocked"
                                isScanning ->
                                    if (spanish) "Buscando nodos AetherMesh…" else "Searching for AetherMesh nodes..."
                                else ->
                                    if (spanish) "Aún no hay dispositivos" else "No devices yet"
                            },
                            color = TextLight,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                scanBlockReason != com.example.aethermesh.ble.BleScanBlockReason.None ->
                                    if (spanish) "Corrige el aviso de arriba e inténtalo de nuevo."
                                    else "Fix the issue above, then try Scan again."
                                isScanning ->
                                    if (spanish) "Mantén el nodo encendido y cerca."
                                    else "Keep the node powered and nearby."
                                else ->
                                    if (spanish) "Pulsa Escanear arriba — o aquí — para buscar."
                                    else "Tap Scan above — or tap here — to search."
                            },
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scannedDevices.forEach { device ->
                        val isThisConnected = isConnected &&
                            viewModel.connectedDeviceAddress.equals(device.mac, ignoreCase = true)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (!isThisConnected) {
                                    viewModel.connectToDevice(device.mac)
                                }
                            },
                            border = BorderStroke(1.dp, if (isThisConnected) AccentMint else BorderDark)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = if (isThisConnected) AccentMint else TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(device.mac, color = TextMuted, fontSize = 12.sp)
                                }
                                if (device.rssi > -127) {
                                    Text(
                                        "${device.rssi} dBm",
                                        color = if (device.rssi >= -70) AccentMint else if (device.rssi >= -85) AccentAmber else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            color = if (isThisConnected) AccentMint else BorderDark,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isThisConnected) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(AccentMint)
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
}


