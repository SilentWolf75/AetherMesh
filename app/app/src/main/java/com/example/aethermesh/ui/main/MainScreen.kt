package com.example.aethermesh.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import org.osmdroid.tileprovider.tilesource.XYTileSource
import com.example.aethermesh.ui.components.AnimatedAetherMeshLogo
import com.example.aethermesh.ui.components.AetherSectionHeader
import com.example.aethermesh.ui.components.BatteryArcGauge
import com.example.aethermesh.ui.components.ExpandableSectionHeader
import com.example.aethermesh.ui.components.GraphicStatTile
import com.example.aethermesh.ui.components.IconWell
import com.example.aethermesh.ui.components.NodeBadge
import com.example.aethermesh.ui.components.PulseDot
import com.example.aethermesh.ui.components.RadarGraphic
import com.example.aethermesh.ui.components.SecureChip
import com.example.aethermesh.ui.components.aetherFilledFieldColors
import com.example.aethermesh.ui.components.aetherTextFieldColors
import com.example.aethermesh.theme.appBackgroundBrush
import com.example.aethermesh.theme.headerBarBrush
import com.example.aethermesh.theme.primaryButtonBrush
import com.example.aethermesh.theme.AccentCyanDim
import com.example.aethermesh.theme.AccentSteel
import com.example.aethermesh.theme.AccentSteelDim

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aethermesh.AetherMeshApplication
import com.example.aethermesh.data.ChatMessage
import com.example.aethermesh.data.ChannelConfig
import com.example.aethermesh.data.MeshNode
import com.example.aethermesh.data.TraceRouteState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.util.BoundingBox
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.scale
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.DashPathEffect
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

// Palette lives in theme/; re-export so same-package screens keep compiling.
typealias AetherPalette = com.example.aethermesh.theme.AetherPalette

fun setAetherPalette(dark: Boolean) = com.example.aethermesh.theme.setAetherPalette(dark)

val DarkBackground: Color get() = com.example.aethermesh.theme.DarkBackground
val SurfaceDark: Color get() = com.example.aethermesh.theme.SurfaceDark
val BorderDark: Color get() = com.example.aethermesh.theme.BorderDark
val TextLight: Color get() = com.example.aethermesh.theme.TextLight
val TextMuted: Color get() = com.example.aethermesh.theme.TextMuted
val AccentCyan = com.example.aethermesh.theme.AccentCyan
val AccentMint = com.example.aethermesh.theme.AccentMint
val AccentRed = com.example.aethermesh.theme.AccentRed
val AccentAmber = com.example.aethermesh.theme.AccentAmber
val AccentOrange = com.example.aethermesh.theme.AccentOrange
val AccentSteel = com.example.aethermesh.theme.AccentSteel
val SurfaceRaised: Color get() = com.example.aethermesh.theme.SurfaceRaised

fun batteryLevelColor(level: Int): Color = com.example.aethermesh.theme.batteryLevelColor(level)

private const val NODE_STALE_MS = 5 * 60 * 1000L

fun isNodeStale(lastActive: Long): Boolean {
    return System.currentTimeMillis() - lastActive > NODE_STALE_MS
}

// Minimum firmware BASE version (the "1.2.0" in "1.2.0-abc1234") this app is
// compatible with. Mixed builds across the mesh are fine — like Meshtastic,
// nodes on different builds interoperate. Bump this ONLY when the app starts
// depending on a protocol feature that older firmware lacks.
const val MIN_COMPATIBLE_FW = "1.2.0"

private fun parseFwBase(version: String): List<Int>? {
    val m = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(version) ?: return null
    return m.groupValues.drop(1).map { it.toInt() }
}

// True when a node's reported firmware base version is older than what this
// app requires. Unknown/unparseable versions are NOT flagged (no false nags).
fun isFirmwareTooOld(version: String): Boolean {
    val v = parseFwBase(version) ?: return false
    val min = parseFwBase(MIN_COMPATIBLE_FW) ?: return false
    for (i in 0..2) {
        if (v[i] != min[i]) return v[i] < min[i]
    }
    return false
}

// Position privacy blur radius choices (meters); 0 = broadcast precise position
val POSITION_PRECISION_STEPS = listOf(0, 100, 250, 500, 1000, 2000, 5000, 10000)

fun formatPositionPrecision(meters: Int, imperial: Boolean, language: String): String {
    if (meters <= 0) return if (language == "Spanish") "Precisa" else "Precise"
    return if (imperial) {
        if (meters < 400) "±${(meters * 3.28084 / 10).toInt() * 10} ft"
        else "±%.1f mi".format(meters / 1609.34)
    } else {
        if (meters < 1000) "±$meters m"
        else "±%.1f km".format(meters / 1000.0)
    }
}

fun formatLastHeard(lastActive: Long, appLanguage: String = "English"): String {
    val elapsedSeconds = ((System.currentTimeMillis() - lastActive).coerceAtLeast(0L)) / 1000L
    val spanish = appLanguage == "Spanish"
    return when {
        elapsedSeconds < 60 -> if (spanish) "hace ${elapsedSeconds}s" else "${elapsedSeconds}s ago"
        elapsedSeconds < 3600 -> if (spanish) "hace ${elapsedSeconds / 60}m" else "${elapsedSeconds / 60}m ago"
        elapsedSeconds < 86_400 -> if (spanish) "hace ${elapsedSeconds / 3600}h" else "${elapsedSeconds / 3600}h ago"
        else -> if (spanish) "hace ${elapsedSeconds / 86_400}d" else "${elapsedSeconds / 86_400}d ago"
    }
}

/** Reject clearly wrong OTA payloads before flashing (Heltec .bin / RAK .zip). */
fun isValidOtaPayload(bytes: ByteArray, fileName: String, isRakNode: Boolean): String? {
    if (bytes.isEmpty()) return "Empty file"
    val lower = fileName.lowercase()
    return if (isRakNode) {
        when {
            !lower.endsWith(".zip") -> "RAK updates need a .zip DFU package"
            bytes.size < 256 -> "File too small to be a DFU package"
            bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte() -> "Not a valid ZIP (DFU) package"
            else -> null
        }
    } else {
        when {
            lower.endsWith(".zip") -> "Heltec updates need a .bin image (not .zip)"
            bytes.size < 1024 -> "Firmware image looks too small"
            bytes[0] != 0xE9.toByte() && !lower.endsWith(".bin") ->
                "Does not look like an ESP32 .bin image"
            else -> null
        }
    }
}

const val WEB_FLASHER_URL = "https://silentwolf75.github.io/AetherMesh/"

fun localizeChatPlaceholder(content: String, appLanguage: String): String {
    if (appLanguage != "Spanish") return content
    return when (content) {
        "[Encrypted Message - No Key Configured]" -> "[Mensaje cifrado — sin clave configurada]"
        "[Decryption Error - Invalid Message]" -> "[Error de descifrado — mensaje inválido]"
        "[Decryption Error - Bad Key or Context]" -> "[Error de descifrado — clave o contexto incorrecto]"
        else -> content
    }
}


fun t(text: String, lang: String): String {
    if (lang != "Spanish") return text
    return when (text) {
        "Distance Units" -> "Unidades de Distancia"
        "Imperial (Miles, Feet)" -> "Imperial (Millas, Pies)"
        "Metric (Kilometers, Meters)" -> "Métrico (Kilómetros, Metros)"
        "Chats" -> "Chats"
        "Nodes" -> "Nodos"
        "Map" -> "Mapa"
        "Settings" -> "Ajustes"
        "Connection" -> "Conexión"
        "Channels" -> "Canales"
        "Direct Messages" -> "Mensajes directos"
        "GPS & Position Settings" -> "Ajustes GPS y Posición"
        "Clear Chat History" -> "Borrar Historial de Chat"
        "Delete all messages from database" -> "Eliminar todos los mensajes de la base de datos"
        "Reset Node Directory" -> "Reiniciar Directorio de Nodos"
        "Clear all discovered nodes and restart directory" -> "Borrar todos los nodos descubiertos y reiniciar directorio"
        "App Preferences" -> "Preferencias de la Aplicación"
        "Theme" -> "Tema"
        "Language" -> "Idioma"
        "Device DB cache limit" -> "Límite de caché de la base de datos"
        "Max device databases to keep on this phone" -> "Bases de datos máximas a conservar en este teléfono"
        "Export rangetest packets" -> "Exportar paquetes de prueba de rango"
        "Export range pings to CSV and copy" -> "Exportar pings de rango a CSV y copiar"
        "Export all packets" -> "Exportar todos los paquetes"
        "Export full message list to CSV and copy" -> "Exportar lista completa de mensajes a CSV y copiar"
        "App Settings & Logs" -> "Ajustes y Registros de la Aplicación"
        "Show Introduction" -> "Mostrar Introducción"
        "Quick startup guide for AetherMesh" -> "Guía de inicio rápido para AetherMesh"
        "App Notifications" -> "Notificaciones de la Aplicación"
        "Configure background alerts" -> "Configurar alertas en segundo plano"
        "Diagnostic Console Logs" -> "Registro de Consola de Diagnóstico"
        "View raw system messages" -> "Ver mensajes del sistema sin procesar"
        "Version" -> "Versión"
        "Radio Configuration" -> "Configuración de Radio"
        "LoRa Radio Configuration" -> "Configuración de Radio LoRa"
        "Apply Settings" -> "Aplicar Ajustes"
        "Node Name" -> "Nombre del Nodo"
        "LoRa Spreading Factor (SF)" -> "Factor de Propagación LoRa (SF)"
        "LoRa Bandwidth (BW)" -> "Ancho de Banda LoRa (BW)"
        "Radio Region Frequency" -> "Frecuencia de Región de Radio"
        "Node Operation Role" -> "Rol de Operación del Nodo"
        "TX Transmit Power" -> "Potencia de Transmisión TX"
        "Change Device Password" -> "Cambiar Contraseña del Dispositivo"
        "Security & DM Keys" -> "Seguridad y Claves DM"
        "Direct Message Keys" -> "Claves de Mensaje Directo"
        "Public Key (Base64)" -> "Clave Pública (Base64)"
        "Private Key (Base64)" -> "Clave Privada (Base64)"
        "Regenerate Private Key" -> "Regenerar Clave Privada"
        "Export Keys" -> "Exportar Claves"
        "Admin Keys" -> "Claves de Administrador"
        "The public key authorized to send admin messages to this node." -> "La clave pública autorizada para enviar mensajes de administrador a este nodo."
        "Add Secondary Channel" -> "Añadir Canal Secundario"
        "Press and drag to reorder (Primary / Secondary)" -> "Presiona y arrastra para reordenar (Primario / Secundario)"
        "Got it" -> "Entendido"
        "Cancel" -> "Cancelar"
        "Save" -> "Guardar"
        "Create" -> "Crear"
        "Telemetry Broadcast Interval" -> "Intervalo de Transmisión de Telemetría"
        "Screen Timeout" -> "Tiempo de Espera de Pantalla"
        "Battery Saver Mode" -> "Modo Ahorro de Batería"
        "Always Off" -> "Siempre Apagada"
        "Always On" -> "Siempre Encendida"
        "10 seconds" -> "10 segundos"
        "15 seconds" -> "15 segundos"
        "30 seconds" -> "30 segundos"
        "1 minute" -> "1 minuto"
        "5 minutes" -> "5 minutos"
        "10 minutes" -> "10 minutos"
        "30 minutes" -> "30 minutos"
        "AetherMesh Guide" -> "Guía de AetherMesh"
        "Welcome to AetherMesh, your off-grid communication companion!" -> "¡Bienvenido a AetherMesh, su compañero de comunicación fuera de la red!"
        "1. Pair your hardware node via the Connection tab." -> "1. Vincule su nodo de hardware a través de la pestaña Conexión."
        "2. View active mesh participants in the Nodes tab." -> "2. Vea los participantes activos en la pestaña Nodos."
        "3. Chat securely over LoRa on the Chats tab." -> "3. Chatee de forma segura a través de LoRa en la pestaña Chats."
        "4. Set custom node name & LoRa parameters in Settings." -> "4. Configure el nombre del nodo y los parámetros LoRa en Ajustes."
        "Current Password" -> "Contraseña Actual"
        "New Password" -> "Nueva Contraseña"
        "Change" -> "Cambiar"
        "Delete All" -> "Eliminar Todo"
        "Are you sure you want to permanently delete all messages? This action cannot be undone." -> "¿Está seguro de que desea eliminar permanentemente todos los mensajes? Esta acción no se puede deshacer."
        "Reset" -> "Restablecer"
        "Are you sure you want to clear all discovered nodes? The active directory will rebuild as new packets are received." -> "¿Está seguro de que desea borrar todos los nodos descubiertos? El directorio activo se reconstruirá a medida que se reciban nuevos paquetes."
        "Enable Repeater Mode?" -> "¿Activar Modo Repetidor?"
        "WARNING: In Low-Power Repeater mode, the node turns off its BLE transceivers to maximize battery. You will lose connection immediately. To configure the node again, you must hold the hardware boot button on boot to trigger factory reset." -> "ADVERTENCIA: En el modo Repetidor de bajo consumo, el nodo apaga sus transceptores BLE para maximizar la batería. Perderá la conexión de inmediato. Para volver a configurar el nodo, debe mantener presionado el botón de arranque de hardware al iniciar para activar el restablecimiento de fábrica."
        "Apply & Disconnect" -> "Aplicar y Desconectar"
        "Channel Settings" -> "Ajustes de Canal"
        "Channel Name" -> "Nombre del Canal"
        "PSK" -> "Clave PSK"
        "PSK Key (Base64)" -> "Clave PSK (Base64)"
        "Uplink enabled" -> "Subida activada"
        "Downlink enabled" -> "Bajada activada"
        "Position enabled" -> "Posición activada"
        "Precise location" -> "Ubicación precisa"
        "Location Privacy Masking" -> "Enmascaramiento de Privacidad de Ubicación"
        "Exact" -> "Exacta"
        "Primary Channel" -> "Canal Primario"
        "Secondary Channel" -> "Canal Secundario"
        "Connection Status" -> "Estado de Conexión"
        "Firmware Version" -> "Versión de Firmware"
        "Disconnect" -> "Desconectar"
        "Mesh Routing Diagnostics" -> "Diagnósticos de Enrutamiento Mesh"
        "No routing paths observed yet.\nPaths are dynamically built as nodes transmit." -> "Aún no se han observado rutas.\nLas rutas se construyen dinámicamente a medida que transmiten los nodos."
        "Next Hop" -> "Siguiente Salto"
        "Hop" -> "Salto"
        "Hops" -> "Saltos"
        "Signal Range Testing" -> "Prueba de Rango de Señal"
        "ACTIVE" -> "ACTIVO"
        "Target Node" -> "Nodo Destino"
        "Select Node..." -> "Seleccionar Nodo..."
        "Ping Interval" -> "Intervalo de Ping"
        "Start Range Test" -> "Iniciar Prueba de Rango"
        "Stop Test" -> "Detener Prueba"
        "Export CSV" -> "Exportar CSV"
        "Clear Logs" -> "Borrar Registros"
        "PINGS SENT" -> "PINGS ENVIADOS"
        "ACKs RECVD" -> "ACKS RECIBIDOS"
        "SUCCESS RATE" -> "TASA DE ÉXITO"
        "RSSI Signal Level History (dBm)" -> "Historial del Nivel de Señal RSSI (dBm)"
        "Heard (No GPS)" -> "Escuchados (Sin GPS)"
        "Active Nodes" -> "Nodos Activos"
        "Stale" -> "Inactivos"
        "Share Channel" -> "Compartir Canal"
        "Join Channel" -> "Unirse al Canal"
        "Join" -> "Unirse"
        "Paste AetherMesh Channel Link" -> "Pega el enlace del canal de AetherMesh"
        "System Configuration Panel" -> "Panel de Configuración del Sistema"
        "Select a settings category below to manage your device." -> "Selecciona una categoría de ajustes abajo para gestionar tu dispositivo."
        "Manage secondary channels and share/join links" -> "Gestiona canales secundarios y enlaces de compartir/unirse"
        "Set spreading factor, bandwidth, power, and region" -> "Ajusta spreading factor, ancho de banda, potencia y región"
        "Security & Keys" -> "Seguridad y Claves"
        "Manage private keys, ECDH keypairs, and device password" -> "Gestiona claves privadas, pares de claves ECDH y contraseña de dispositivo"
        "Set language, theme, and background alerts" -> "Ajusta el idioma, el tema y las alertas en segundo plano"
        "Developer & Diagnostics" -> "Desarrollo y Diagnósticos"
        "Live logs console, packet exports, and system database reset" -> "Consola de registros, exportación de paquetes y restablecimiento"
        else -> text
    }
}



enum class TabItem {
    CHATS, NODES, MAP, SETTINGS, CONNECTION
}

enum class SettingsCategory {
    CHANNELS, RADIO, POSITION, FIRMWARE, SECURITY, PREFERENCES, DEVELOPER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit,
    viewModel: MainScreenViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isConnected by viewModel.isBleConnected.collectAsStateWithLifecycle()
    val blePhase by viewModel.bleConnectionPhase.collectAsStateWithLifecycle()
    val bleReconnectAttempt by viewModel.bleReconnectAttempt.collectAsStateWithLifecycle()
    val bleReconnectGaveUp by viewModel.bleReconnectGaveUp.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(TabItem.CHATS) }
    var fitTraceRouteToken by remember { mutableIntStateOf(0) }
    var pendingMapRemoteConfigId by remember { mutableStateOf<Long?>(null) }
    var pendingMapFocusNodeId by remember { mutableStateOf<Long?>(null) }

    val isDeviceAuthenticated by viewModel.isDeviceAuthenticated.collectAsStateWithLifecycle()
    val authenticationRequired by viewModel.authenticationRequired.collectAsStateWithLifecycle()

    var authPasswordInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf(false) }

    val sharedPrefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
    var appLanguage by remember { mutableStateOf(sharedPrefs.getString("app_language", "English") ?: "English") }
    var useImperialUnitsSetting by remember { mutableStateOf(sharedPrefs.getBoolean("use_imperial_units", true)) }
    val phoneLocationFlow by viewModel.phoneLocation.collectAsStateWithLifecycle()
    var phoneLocation by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(phoneLocationFlow) {
        if (phoneLocationFlow != null) phoneLocation = phoneLocationFlow
    }
    val observedRoutes by viewModel.observedRoutes.collectAsStateWithLifecycle()
    val traceRouteState by viewModel.traceRouteState.collectAsStateWithLifecycle()

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val activeChatId by viewModel.activeChatId.collectAsStateWithLifecycle()
    val chatDeepLinkEpoch by viewModel.chatDeepLinkEpoch.collectAsStateWithLifecycle()

    val pendingOpenChats by viewModel.pendingOpenChatsTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenChats) {
        if (viewModel.consumeOpenChatsTab()) {
            activeTab = TabItem.CHATS
        }
    }
    val pendingOpenMap by viewModel.pendingOpenMapTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenMap) {
        if (viewModel.consumeOpenMapTab()) {
            activeTab = TabItem.MAP
            pendingMapFocusNodeId = viewModel.consumeFocusNodeId()
        }
    }
    val pendingOpenConnection by viewModel.pendingOpenConnectionTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenConnection) {
        if (viewModel.consumeOpenConnectionTab()) {
            activeTab = TabItem.CONNECTION
        }
    }
    val pendingRemoteConfigId by viewModel.pendingRemoteConfigNodeId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRemoteConfigId) {
        val id = viewModel.consumeRemoteConfigNodeId() ?: return@LaunchedEffect
        activeTab = TabItem.MAP
        pendingMapRemoteConfigId = id
    }
    val pendingChatDeep by viewModel.pendingChatDeepLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingChatDeep) {
        val link = viewModel.consumeChatDeepLink() ?: return@LaunchedEffect
        activeTab = TabItem.CHATS
        when {
            link.dmPeerId != null && link.dmPeerId != 0L -> viewModel.selectDirectMessage(link.dmPeerId)
            !link.channel.isNullOrBlank() -> viewModel.selectChannel(link.channel)
        }
    }
    val app = context.applicationContext as AetherMeshApplication
    val pendingNotifChat by app.pendingNotificationChat.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNotifChat) {
        val link = app.consumeNotificationChat() ?: return@LaunchedEffect
        viewModel.requestChatDeepLink(link.channel, link.dmPeerId)
    }
    val pendingNotifNode by app.pendingNotificationNodeId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNotifNode) {
        val id = app.consumeNotificationNode() ?: return@LaunchedEffect
        onItemClick(com.example.aethermesh.NodeDetails(id))
    }
    DisposableEffect(context) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "app_language") {
                appLanguage = prefs.getString("app_language", "English") ?: "English"
            }
            if (key == "use_imperial_units") {
                useImperialUnitsSetting = prefs.getBoolean("use_imperial_units", true)
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(authenticationRequired, isConnected) {
        authPasswordInput = ""
        authError = false
    }

    // Set OSMDroid configuration for maps
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Global location update listener to share location whenever connected
    DisposableEffect(isConnected) {
        if (isConnected) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    val gp = GeoPoint(location.latitude, location.longitude)
                    phoneLocation = gp
                    if (sharedPrefs.getBoolean("enable_phone_gps_sharing", true)) {
                        viewModel.sharePhoneLocation(gp.latitude, gp.longitude)
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            try {
                // Request location updates every 15 seconds, min distance 5 meters
                lm.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER,
                    15000L,
                    5f,
                    listener
                )
                // Fallback network provider
                lm.requestLocationUpdates(
                    android.location.LocationManager.NETWORK_PROVIDER,
                    15000L,
                    5f,
                    listener
                )
                // Share the last known fix immediately so a GPS-less node gets a
                // position right away, instead of waiting for the phone to move 5m
                val lastFix = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                if (lastFix != null) {
                    phoneLocation = GeoPoint(lastFix.latitude, lastFix.longitude)
                    if (sharedPrefs.getBoolean("enable_phone_gps_sharing", true)) {
                        viewModel.sharePhoneLocation(lastFix.latitude, lastFix.longitude)
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e("MainScreen", "Location permissions missing: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.e("MainScreen", "Error requesting location: ${e.message}")
            }
            onDispose {
                try {
                    lm.removeUpdates(listener)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } else {
            onDispose {}
        }
    }

    val headerTitle = when (activeTab) {
        TabItem.CHATS -> t("Chats", appLanguage)
        TabItem.NODES -> t("Nodes", appLanguage)
        TabItem.MAP -> t("Map", appLanguage)
        TabItem.SETTINGS -> t("Settings", appLanguage)
        TabItem.CONNECTION -> t("Connection", appLanguage)
    }
    
    val connectedNode = nodes.find { it.nodeId == viewModel.connectedNodeId }
    val connectedNodeName = connectedNode?.name ?: viewModel.connectedDeviceName

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            HeaderBar(
                title = headerTitle,
                isConnected = isConnected,
                connectionPhase = blePhase,
                reconnectAttempt = bleReconnectAttempt,
                connectedNodeName = connectedNodeName,
                appLanguage = appLanguage
            )

            // Content Area based on Tab Selection
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (!isConnected && activeTab != TabItem.CONNECTION) {
                    val bannerText = when {
                        bleReconnectGaveUp -> if (appLanguage == "Spanish")
                            "Reconexión agotada. Toque para escanear de nuevo."
                        else
                            "Reconnect gave up. Tap to scan again."
                        blePhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ||
                            blePhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting ->
                            if (appLanguage == "Spanish")
                                "Reconectando (intento $bleReconnectAttempt)…"
                            else
                                "Reconnecting (attempt $bleReconnectAttempt)…"
                        else -> if (appLanguage == "Spanish")
                            "Desconectado. Toque para volver a conectar."
                        else
                            "Disconnected from node. Tap to reconnect."
                    }
                    val bannerColor = when {
                        bleReconnectGaveUp -> AccentRed
                        blePhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ||
                            blePhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting -> AccentAmber
                        else -> AccentRed
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceDark)
                            .border(BorderStroke(1.dp, bannerColor.copy(alpha = 0.35f)))
                            .clickable { activeTab = TabItem.CONNECTION }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Disconnected",
                            tint = bannerColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bannerText,
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(androidx.compose.ui.graphics.RectangleShape)
                ) {
                    when (activeTab) {
                        TabItem.CHATS -> ChatView(
                            messages = messages,
                            channels = channels,
                            selectedChannel = selectedChannel,
                            localNodeId = viewModel.connectedNodeId,
                            activeChatId = activeChatId,
                            nodes = nodes,
                            appLanguage = appLanguage,
                            isConnected = isConnected,
                            isAuthenticated = isDeviceAuthenticated,
                            onSelectChannel = { viewModel.selectChannel(it) },
                            onSelectDirectMessage = { viewModel.selectDirectMessage(it) },
                            onCreateChannel = { viewModel.createChannel(it) },
                            onSendMessage = { viewModel.sendMessage(it) },
                            onRetryMessage = { viewModel.retryMessage(it) },
                            getChatKey = { viewModel.getChatKey(it) },
                            saveChatKey = { key, valStr -> viewModel.saveChatKey(key, valStr) },
                            channelPreviews = viewModel.getChannelInboxPreviews(),
                            dmPreviews = viewModel.getDmInboxPreviews(viewModel.connectedNodeId),
                            onGoToConnection = { activeTab = TabItem.CONNECTION },
                            deepLinkEpoch = chatDeepLinkEpoch
                        )
                        TabItem.NODES -> NodesView(
                            nodes = nodes,
                            observedRoutes = observedRoutes,
                            phoneLocation = phoneLocation,
                            appLanguage = appLanguage,
                            useImperialUnits = useImperialUnitsSetting,
                            onNodeClick = { nodeId ->
                                viewModel.selectDirectMessage(nodeId)
                                activeTab = TabItem.CHATS
                            },
                            onRenameNode = { nodeId, longName, shortName, password ->
                                viewModel.renameNode(nodeId, longName, shortName, password)
                            },
                            getTelemetryHistory = { nodeId -> viewModel.getTelemetryHistory(nodeId) },
                            connectedNodeId = viewModel.connectedNodeId,
                            onTraceRoute = { viewModel.startTraceRoute(it) },
                            onRemoteConfig = { node -> viewModel.requestRemoteConfig(node.nodeId) },
                            onViewOnMap = { nodeId -> viewModel.requestOpenMapTab(focusNodeId = nodeId) },
                            onRangeTest = { nodeId -> viewModel.requestOpenConnectionForRangeTest(nodeId) },
                            onOpenNodeDetails = { nodeId ->
                                onItemClick(com.example.aethermesh.NodeDetails(nodeId))
                            }
                        )
                        TabItem.MAP -> MapViewCompose(
                            nodes = nodes,
                            observedRoutes = observedRoutes,
                            traceRouteState = traceRouteState,
                            viewModel = viewModel,
                            appLanguage = appLanguage,
                            useImperialUnits = useImperialUnitsSetting,
                            phoneLocation = phoneLocation,
                            onPhoneLocationChanged = { gp ->
                                phoneLocation = gp
                                viewModel.updatePhoneLocation(gp.latitude, gp.longitude)
                                if (sharedPrefs.getBoolean("enable_phone_gps_sharing", true)) {
                                    viewModel.sharePhoneLocation(gp.latitude, gp.longitude)
                                }
                            },
                            onNavigateToChats = { activeTab = TabItem.CHATS },
                            fitTraceRouteToken = fitTraceRouteToken,
                            focusNodeId = pendingMapFocusNodeId,
                            onFocusNodeConsumed = { pendingMapFocusNodeId = null },
                            onOpenNodeDetails = { nodeId ->
                                onItemClick(com.example.aethermesh.NodeDetails(nodeId))
                            },
                            openRemoteConfigNodeId = pendingMapRemoteConfigId,
                            onRemoteConfigOpened = { pendingMapRemoteConfigId = null }
                        )
                        TabItem.SETTINGS -> SettingsView(
                            viewModel = viewModel,
                            isConnected = isConnected
                        )
                        TabItem.CONNECTION -> ConnectionView(
                            viewModel = viewModel,
                            isConnected = isConnected,
                            nodes = nodes,
                            scannedDevices = scannedDevices,
                            appLanguage = appLanguage
                        )
                    }
                }
            }

            // Bottom Navigation Bar
            AetherBottomNav(
                selectedTab = activeTab,
                onTabSelected = { activeTab = it },
                appLanguage = appLanguage
            )
        }

        if (traceRouteState.showDialog &&
            (traceRouteState.active ||
                traceRouteState.forward.isNotEmpty() ||
                traceRouteState.returning.isNotEmpty() ||
                traceRouteState.error != null)
        ) {
            TraceRouteResultDialog(
                state = traceRouteState,
                nodes = nodes,
                connectedNodeId = viewModel.connectedNodeId,
                appLanguage = appLanguage,
                onOk = { viewModel.clearTraceRouteResult() },
                onViewOnMap = {
                    viewModel.hideTraceRouteDialog()
                    activeTab = TabItem.MAP
                    fitTraceRouteToken++
                }
            )
        }

        // Overlay dialog for device password setting / authentication
        if (isConnected && !isDeviceAuthenticated && authenticationRequired != null) {
            val isFirstTime = authenticationRequired == false
            val spanish = appLanguage == "Spanish"
            AlertDialog(
                onDismissRequest = { /* Force auth, don't dismiss */ },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isFirstTime) Icons.Default.Info else Icons.Default.Lock,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isFirstTime && spanish -> "Configurar contraseña"
                                isFirstTime -> "Setup Device Password"
                                spanish -> "Desbloquear dispositivo"
                                else -> "Unlock Device"
                            },
                            color = TextLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = when {
                                isFirstTime && spanish ->
                                    "Este nodo no tiene contraseña. Configura una segura; la app la recordará."
                                isFirstTime ->
                                    "This node does not have a password configured. Please set a secure password for this device. The app will remember it for future connections."
                                spanish ->
                                    "Introduce la contraseña de este nodo para autenticarte."
                                else ->
                                    "Enter the password for this node to authenticate."
                            },
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = authPasswordInput,
                            onValueChange = { 
                                authPasswordInput = it
                                authError = false
                            },
                            label = { Text(if (spanish) "Contraseña" else "Password", color = TextMuted) },
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
                        
                        if (authError) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (spanish)
                                    "Autenticación fallida. Contraseña incorrecta."
                                else
                                    "Authentication failed. Incorrect password.",
                                color = AccentRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pass = authPasswordInput.trim()
                            if (pass.isNotEmpty()) {
                                val sent = viewModel.sendAuthRequest(pass)
                                if (!sent) {
                                    authError = true
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                    ) {
                        Text(
                            when {
                                isFirstTime && spanish -> "Establecer"
                                isFirstTime -> "Set Password"
                                spanish -> "Desbloquear"
                                else -> "Unlock"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.disconnect()
                        }
                    ) {
                        Text(if (spanish) "Desconectar" else "Disconnect", color = TextMuted)
                    }
                },
                containerColor = SurfaceDark
            )
        }
    }
}

@Composable
fun HeaderBar(
    title: String,
    isConnected: Boolean,
    connectionPhase: com.example.aethermesh.ble.BleConnectionPhase =
        com.example.aethermesh.ble.BleConnectionPhase.Disconnected,
    reconnectAttempt: Int = 0,
    connectedNodeName: String?,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    val statusLabel = when {
        isConnected -> if (spanish) "ENLACE" else "LINK UP"
        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ->
            if (spanish) "RECON $reconnectAttempt" else "RECONNECT $reconnectAttempt"
        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting ->
            if (spanish) "CONECTANDO" else "CONNECTING"
        else -> if (spanish) "SIN RED" else "OFFLINE"
    }
    val statusColor = when {
        isConnected -> AccentMint
        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ||
            connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting -> AccentAmber
        else -> AccentRed
    }
    val pulseActive = isConnected ||
        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ||
        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBarBrush())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedAetherMeshLogo(size = 40.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title.uppercase(),
                        color = AccentCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(active = pulseActive)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            statusLabel,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            if (isConnected && !connectedNodeName.isNullOrBlank()) {
                val shortName = getShortName(connectedNodeName, 0L)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(primaryButtonBrush())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shortName,
                        color = Color(0xFF061018),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            AccentCyan,
                            AccentMint.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun ChatView(
    messages: List<ChatMessage>,
    channels: List<String>,
    selectedChannel: String,
    localNodeId: Long,
    activeChatId: Long?,
    nodes: List<MeshNode>,
    appLanguage: String = "English",
    isConnected: Boolean = true,
    isAuthenticated: Boolean = true,
    onSelectChannel: (String) -> Unit,
    onSelectDirectMessage: (Long) -> Unit,
    onCreateChannel: (String) -> Unit,
    onSendMessage: (String) -> Boolean,
    onRetryMessage: (ChatMessage) -> Unit,
    getChatKey: (String) -> String?,
    saveChatKey: (String, String) -> Unit,
    channelPreviews: Map<String, com.example.aethermesh.data.ChatInboxPreview> = emptyMap(),
    dmPreviews: Map<Long, com.example.aethermesh.data.ChatInboxPreview> = emptyMap(),
    onGoToConnection: () -> Unit = {},
    deepLinkEpoch: Int = 0
) {
    var textState by remember { mutableStateOf("") }
    var sendError by remember { mutableStateOf<String?>(null) }
    var showNewChannelDialog by remember { mutableStateOf(false) }
    var inThread by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val canSend = isConnected && isAuthenticated
    val spanish = appLanguage == "Spanish"

    fun formatInboxTime(ts: Long): String {
        if (ts <= 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    }

    fun previewSnippet(raw: String): String {
        val trimmed = raw.trim().replace('\n', ' ')
        return if (trimmed.length <= 48) trimmed else trimmed.take(45) + "…"
    }

    LaunchedEffect(activeChatId) {
        if (activeChatId != null && activeChatId != 0L) inThread = true
    }
    LaunchedEffect(deepLinkEpoch) {
        if (deepLinkEpoch > 0) inThread = true
    }

    if (showNewChannelDialog) {
        NewChannelDialog(
            appLanguage = appLanguage,
            onCreate = {
                onCreateChannel(it)
                showNewChannelDialog = false
                onSelectChannel(it)
                inThread = true
            },
            onDismiss = { showNewChannelDialog = false }
        )
    }

    if (!inThread) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val dmNodes = nodes.filter { it.nodeId != localNodeId }
            val sortedChannels = remember(channels, channelPreviews) {
                channels.sortedByDescending { channelPreviews[it]?.timestamp ?: 0L }
            }
            val sortedDmNodes = remember(dmNodes, dmPreviews) {
                dmNodes.sortedByDescending { node ->
                    dmPreviews[node.nodeId]?.timestamp?.takeIf { it > 0L } ?: node.lastActive
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AetherSectionHeader(
                            title = t("Channels", appLanguage),
                            trailing = "${channels.size}",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showNewChannelDialog = true }) {
                            Text(
                                if (spanish) "+ Canal" else "+ Channel",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                items(sortedChannels) { channel ->
                    val preview = channelPreviews[channel]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .clickable {
                                onSelectChannel(channel)
                                inThread = true
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentCyanDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("#", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(channel, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                preview?.let { previewSnippet(it.snippet) }
                                    ?: if (spanish) "Chat de canal" else "Channel chat",
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        if (preview != null && preview.timestamp > 0L) {
                            Text(formatInboxTime(preview.timestamp), color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    AetherSectionHeader(
                        title = if (spanish) "Mensajes directos" else "Direct Messages",
                        trailing = "${sortedDmNodes.size}",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (sortedDmNodes.isEmpty()) {
                    item {
                        Text(
                            if (spanish)
                                "Aún no hay nodos. Conecta una radio para ver contactos."
                            else
                                "No nodes discovered yet. Connect a radio to see contacts.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(sortedDmNodes) { node ->
                        val shortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
                        val stale = isNodeStale(node.lastActive)
                        val preview = dmPreviews[node.nodeId]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (stale) SurfaceDark.copy(alpha = 0.55f) else SurfaceDark)
                                .clickable {
                                    onSelectDirectMessage(node.nodeId)
                                    inThread = true
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NodeBadge(shortName = shortName, color = getBadgeColor(node.name), muted = stale)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(node.name, color = if (stale) TextMuted else TextLight, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        preview != null -> previewSnippet(preview.snippet)
                                        stale -> if (spanish)
                                            "Último aviso ${formatLastHeard(node.lastActive, appLanguage)}"
                                        else
                                            "Last heard ${formatLastHeard(node.lastActive, appLanguage)}"
                                        else -> if (spanish) "Mensaje directo" else "Direct message"
                                    },
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            if (preview != null && preview.timestamp > 0L) {
                                Text(formatInboxTime(preview.timestamp), color = TextMuted, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        return
    }

    val selectedNode = nodes.find { it.nodeId == activeChatId }
    val threadTitle = if (activeChatId == null) {
        "#$selectedChannel"
    } else {
        selectedNode?.name ?: "Node 0x${activeChatId.toString(16).uppercase()}"
    }
    val isChannelThread = activeChatId == null

    LaunchedEffect(activeChatId, selectedChannel, inThread) {
        if (inThread && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(messages.size) {
        if (inThread && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { inThread = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextLight)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(threadTitle, color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isChannelThread) {
                        if (spanish) "Canal" else "Channel"
                    } else {
                        if (spanish) "Mensaje directo" else "Direct message"
                    },
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!canSend) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentAmber.copy(alpha = 0.15f))
                    .clickable { onGoToConnection() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !isConnected -> if (spanish)
                            "Sin conexión BLE. Toca para ir a Conexión."
                        else
                            "Not connected. Tap to open Connection."
                        else -> if (spanish)
                            "Dispositivo bloqueado. Autentica en Conexión."
                        else
                            "Device locked. Authenticate on Connection."
                    },
                    color = TextLight,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (activeChatId == null || activeChatId != 0L) {
            val chatIdentifier = if (activeChatId == null) "CHANNEL_$selectedChannel" else "DM_$activeChatId"
            var passcode by remember(chatIdentifier) { mutableStateOf(getChatKey(chatIdentifier)) }
            var showPasscodeDialog by remember { mutableStateOf(false) }

            if (showPasscodeDialog) {
                PasscodeEntryDialog(
                    title = if (activeChatId == null) {
                        if (spanish) "Clave del canal (#$selectedChannel)" else "Channel Key (#$selectedChannel)"
                    } else {
                        if (spanish)
                            "Clave directa (Nodo 0x${activeChatId.toString(16).uppercase()})"
                        else
                            "Direct Key (Node 0x${activeChatId.toString(16).uppercase()})"
                    },
                    initialPasscode = passcode ?: "",
                    appLanguage = appLanguage,
                    onSave = { newKey ->
                        saveChatKey(chatIdentifier, newKey)
                        passcode = newKey.takeIf { it.isNotEmpty() }
                        showPasscodeDialog = false
                    },
                    onDismiss = { showPasscodeDialog = false }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                    .clickable { showPasscodeDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (!passcode.isNullOrEmpty()) AccentMint else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!passcode.isNullOrEmpty()) {
                            if (spanish) "Cifrado" else "Encrypted"
                        } else {
                            if (spanish) "Texto claro — toca para clave" else "Cleartext — tap to set key"
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                if (!passcode.isNullOrEmpty()) {
                    SecureChip()
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeChatId != null && activeChatId == 0L) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (spanish)
                        "No hay nodos para chat privado.\nEspera a que otros nodos emitan telemetría."
                    else
                        "No nodes available for private chat.\nWait for other nodes to broadcast telemetries.",
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = false
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (spanish) "Aún no hay mensajes. Escribe el primero."
                                else "No messages yet. Say hello.",
                                color = TextMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    val senderNode = nodes.find { it.nodeId == message.senderId }
                    val senderLabel = when {
                        !isChannelThread -> null
                        localNodeId != 0L && message.senderId == localNodeId -> null
                        senderNode != null -> senderNode.shortName.ifEmpty {
                            getShortName(senderNode.name, senderNode.nodeId)
                        }
                        message.senderId != 0L -> getShortName("", message.senderId)
                        else -> null
                    }
                    MessageBubble(
                        message = message,
                        localNodeId = localNodeId,
                        onRetryMessage = onRetryMessage,
                        senderLabel = senderLabel,
                        appLanguage = appLanguage
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeChatId == null || activeChatId != 0L) {
            val placeholderText = if (activeChatId == null) {
                if (spanish) "Mensaje #$selectedChannel…" else "Message #$selectedChannel..."
            } else {
                if (spanish)
                    "Mensaje a ${selectedNode?.name ?: "nodo"}…"
                else
                    "Message ${selectedNode?.name ?: "node"}..."
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textState,
                    onValueChange = { textState = it },
                    enabled = canSend,
                    placeholder = { Text(placeholderText, color = TextMuted) },
                    colors = aetherFilledFieldColors(),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (!canSend) {
                            sendError = if (!isConnected) {
                                if (spanish) "Conecta y autentica el nodo primero."
                                else "Connect and authenticate the node first."
                            } else {
                                if (spanish) "Autentica el dispositivo para enviar."
                                else "Authenticate the device to send."
                            }
                            return@IconButton
                        }
                        if (textState.trim().isNotEmpty()) {
                            if (onSendMessage(textState)) {
                                textState = ""
                                sendError = null
                            } else {
                                sendError = if (spanish)
                                    "No se envió. Revisa conexión BLE y autenticación."
                                else
                                    "Message not sent. Check BLE connection and authentication."
                            }
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) AccentCyan else TextMuted.copy(alpha = 0.35f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = DarkBackground
                    )
                }
            }
            sendError?.let {
                Text(
                    text = it,
                    color = AccentRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp, start = 4.dp)
                )
            }
        }
    }
}


@Composable
fun NewChannelDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    appLanguage: String = "English"
) {
    var name by remember { mutableStateOf("") }
    val spanish = appLanguage == "Spanish"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (name.trim().isNotEmpty()) onCreate(name.trim()) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(
                    if (spanish) "Crear" else "Create",
                    color = if (name.trim().isNotEmpty()) AccentMint else TextMuted
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (spanish) "Cancelar" else "Cancel", color = TextMuted)
            }
        },
        title = {
            Text(
                if (spanish) "Nuevo canal" else "New Channel",
                color = TextLight,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    if (spanish)
                        "Los mensajes se emiten en este nombre de canal. Solo los nodos sintonizados al mismo canal los verán."
                    else
                        "Messages you send here are broadcast on this channel name. Only nodes tuned to the same channel will display them.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = name,
                    onValueChange = { if (it.length <= 16) name = it.filterNot { c -> c.isWhitespace() } },
                    singleLine = true,
                    placeholder = { Text("e.g. Trail-Crew", color = TextMuted) },
                    colors = aetherTextFieldColors(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun PasscodeEntryDialog(
    title: String,
    initialPasscode: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    var keyState by remember { mutableStateOf(initialPasscode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(keyState.trim()) }
            ) {
                Text(if (spanish) "Guardar" else "Save", color = AccentMint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (spanish) "Cancelar" else "Cancel", color = TextMuted)
            }
        },
        title = { Text(title, color = TextLight, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    if (spanish)
                        "Todos los mensajes de este chat se cifran y descifran con AES-256 usando la clave de abajo. Manténla en secreto y compártela fuera de banda con los demás."
                    else
                        "All messages in this chat will be encrypted and decrypted using AES-256 with the key below. Keep this key secret and share it off-grid with other participants.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = keyState,
                    onValueChange = { keyState = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            if (spanish) "Introduce la clave (p. ej. secreto123)" else "Enter passcode (e.g. secret123)",
                            color = TextMuted
                        )
                    },
                    colors = aetherTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (keyState.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (spanish)
                            "Déjalo vacío para desactivar el cifrado y borrar la clave."
                        else
                            "Leave blank to disable encryption and clear key.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 10.sp
                    )
                }
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    localNodeId: Long,
    onRetryMessage: (ChatMessage) -> Unit,
    senderLabel: String? = null,
    appLanguage: String = "English"
) {
    val isMe = localNodeId != 0L && message.senderId == localNodeId
    val canRetry = isMe && message.status in setOf("FAILED", "EXPIRED")
    val spanish = appLanguage == "Spanish"
    val statusIcon = when (message.status) {
        "DELIVERED" -> "✓"
        "PENDING", "QUEUED" -> "…"
        "FAILED", "EXPIRED" -> "!"
        "RETRIED" -> "↻"
        else -> "✓"
    }
    val statusColor = when (message.status) {
        "DELIVERED", "SENT" -> AccentMint
        "FAILED", "EXPIRED" -> AccentRed
        "PENDING", "QUEUED" -> AccentAmber
        else -> TextMuted
    }
    val statusText = when (message.status) {
        "EXPIRED" -> if (spanish) "$statusIcon sin ACK" else "$statusIcon no ACK"
        "FAILED" -> if (spanish) "$statusIcon falló · reintentar" else "$statusIcon failed · retry"
        "PENDING", "QUEUED" -> if (spanish) "$statusIcon enviando" else statusIcon
        "RETRIED" -> if (spanish) "$statusIcon reenviado" else statusIcon
        else -> statusIcon
    }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (!senderLabel.isNullOrBlank() && !isMe) {
            Text(
                senderLabel,
                color = AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
            )
        }
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    )
                )
                .then(
                    if (isMe) Modifier.background(primaryButtonBrush())
                    else Modifier.background(SurfaceDark)
                )
                .clickable(enabled = canRetry) { onRetryMessage(message) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = localizeChatPlaceholder(message.content, appLanguage),
                color = if (isMe) Color(0xFF061018) else TextLight,
                fontSize = 15.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(time, color = TextMuted, fontSize = 10.sp)
            if (isMe) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TraceRouteResultDialog(
    state: TraceRouteState,
    nodes: List<MeshNode>,
    connectedNodeId: Long,
    onOk: () -> Unit,
    onViewOnMap: () -> Unit,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    val snrOrange = Color(0xFFFF9800)

    fun displayName(id: Long): String {
        val node = nodes.find { it.nodeId == id }
        val longName = node?.name?.takeIf { it.isNotBlank() }
            ?: "0x${id.toString(16).uppercase()}"
        val short = node?.shortName?.takeIf { it.isNotBlank() }
            ?: getShortName(longName, id)
        return "$longName ($short)"
    }

    @Composable
    fun HopSnr(snr: Float, rssi: Int) {
        val unknown = snr == 0f && rssi == 0
        Text(
            text = if (unknown) "? dB" else "%.2f dB".format(snr),
            color = if (unknown) TextMuted else snrOrange,
            fontSize = 13.sp,
            fontWeight = if (unknown) FontWeight.Normal else FontWeight.SemiBold
        )
    }

    @Composable
    fun RoutePath(
        title: String,
        startId: Long,
        hops: List<com.example.aethermesh.data.TraceHop>,
        truncated: Boolean
    ) {
        Text(title, color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("■", color = TextLight, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Text(displayName(startId), color = TextLight, fontSize = 14.sp)
        }
        hops.forEach { hop ->
            Row(
                modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⇊", color = TextMuted, fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                HopSnr(hop.snr, hop.rssi)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("■", color = TextLight, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Text(displayName(hop.nodeId), color = TextLight, fontSize = 14.sp)
            }
        }
        if (truncated) {
            Text(
                if (spanish) "La ruta superó el límite de 8 saltos" else "Path exceeded the 8-hop capture limit",
                color = snrOrange,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!state.active) onOk() },
        containerColor = Color(0xFF2A2F38),
        title = {
            Text(
                "Traceroute",
                color = TextLight,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when {
                    state.active -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentMint,
                            trackColor = BorderDark
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (spanish) "Trazando ruta…" else "Tracing route…",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    state.error != null -> {
                        Text(
                            state.error ?: (if (spanish) "Traza fallida" else "Trace failed"),
                            color = Color(0xFFF87171),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    else -> {
                        RoutePath(
                            title = if (spanish) "Ruta hacia el destino:" else "Route traced toward destination:",
                            startId = connectedNodeId,
                            hops = state.forward,
                            truncated = state.forwardTruncated
                        )
                        Spacer(Modifier.height(18.dp))
                        RoutePath(
                            title = if (spanish) "Ruta de vuelta:" else "Route traced back to us:",
                            startId = state.targetId,
                            hops = state.returning,
                            truncated = state.returnTruncated
                        )
                        state.durationSeconds?.let { secs ->
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (spanish) "Duración: ${"%.1f".format(secs)} s" else "Duration: ${"%.1f".format(secs)} s",
                                color = TextLight,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!state.active) {
                Row {
                    TextButton(onClick = onOk) {
                        Text("OK", color = TextLight, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.error == null &&
                        (state.forward.isNotEmpty() || state.returning.isNotEmpty())
                    ) {
                        TextButton(onClick = onViewOnMap) {
                            Text(
                                if (spanish) "Ver en mapa" else "View on map",
                                color = TextLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    )
}

private enum class NodesSort {
    LAST_HEARD, SIGNAL, NAME, DISTANCE
}

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
    onOpenNodeDetails: (Long) -> Unit = {}
) {
    var renamingNode by remember { mutableStateOf<MeshNode?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(NodesSort.LAST_HEARD) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    if (renamingNode != null) {
        val node = renamingNode!!
        val context = LocalContext.current
        var longName by remember { mutableStateOf(node.name) }
        var shortName by remember { mutableStateOf(node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }) }
        var adminPassword by remember { mutableStateOf("") }
        val isRemote = node.nodeId != connectedNodeId
        
        AlertDialog(
            onDismissRequest = { renamingNode = null },
            title = { Text(t("Rename Node", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t("Long Name (max 16 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        value = longName,
                        onValueChange = { if (it.length <= 16) longName = it },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(t("Short Name (max 4 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = shortName,
                        onValueChange = { if (it.length <= 4) shortName = it.uppercase() },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isRemote) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (appLanguage == "Spanish")
                                "Contraseña del nodo (para guardar en el mesh)"
                            else
                                "Node admin password (to store on the mesh)",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = adminPassword,
                            onValueChange = { adminPassword = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = aetherTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (appLanguage == "Spanish")
                                "Sin contraseña el nombre solo queda en este teléfono."
                            else
                                "Without a password the name stays on this phone only.",
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val persisted = onRenameNode(
                            node.nodeId,
                            longName.trim(),
                            shortName.trim(),
                            adminPassword
                        )
                        if (!persisted) {
                            android.widget.Toast.makeText(
                                context,
                                if (appLanguage == "Spanish")
                                    "Nombre guardado solo en el teléfono. Conéctate al nodo o usa Config remota."
                                else
                                    "Name saved on phone only. Connect to that node or use Remote Config.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        renamingNode = null
                    }
                ) {
                    Text(t("Save", appLanguage), color = AccentMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingNode = null }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            containerColor = SurfaceDark
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

    val remoteNodes = nodes.filter { it.nodeId != connectedNodeId && matchesQuery(it) }
    val activeNodes = sortNodes(remoteNodes.filter { !isNodeStale(it.lastActive) })
    val staleNodes = sortNodes(remoteNodes.filter { isNodeStale(it.lastActive) })
    val showSelf = connectedNode != null && matchesQuery(connectedNode)
    val hasAny = showSelf || remoteNodes.isNotEmpty()

    val sortLabels = mapOf(
        NodesSort.LAST_HEARD to if (appLanguage == "Spanish") "Último oído" else "Last heard",
        NodesSort.SIGNAL to if (appLanguage == "Spanish") "Señal" else "Signal",
        NodesSort.NAME to if (appLanguage == "Spanish") "Nombre" else "Name",
        NodesSort.DISTANCE to if (appLanguage == "Spanish") "Distancia" else "Distance"
    )

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
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
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
                    Text(sortLabels[sortBy] ?: "Sort", color = AccentCyan, fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
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
                    if (query.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Connect a radio on the Connection tab — nearby nodes appear here as they advertise.",
                            color = TextMuted.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
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
                            isConnectedNode = true
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
                        isConnectedNode = false
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
                            isConnectedNode = false
                        )
                    }
                }
            }
        }
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
    isConnectedNode: Boolean = false
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
            .background(if (stale) SurfaceDark.copy(alpha = 0.55f) else SurfaceDark)
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
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.isCharging) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Charging",
                        tint = AccentAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Icon(
                    imageVector = Icons.Default.BatteryFull,
                    contentDescription = "Battery",
                    tint = batteryLevelColor(node.battery),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("${node.battery}%", color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = TextMuted, modifier = Modifier.size(18.dp))
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
                                text = { Text("Traceroute", color = TextLight) },
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
fun cartoDarkTileSource(): XYTileSource = XYTileSource(
    "CartoDarkMatter",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

/** Bearing in degrees [0, 360) from point A to B. */
fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

fun offsetGeoPoint(point: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
    val earth = 6_378_137.0
    val lat1 = Math.toRadians(point.latitude)
    val lon1 = Math.toRadians(point.longitude)
    val brng = Math.toRadians(bearingDeg)
    val ang = meters / earth
    val lat2 = Math.asin(
        Math.sin(lat1) * Math.cos(ang) + Math.cos(lat1) * Math.sin(ang) * Math.cos(brng)
    )
    val lon2 = lon1 + Math.atan2(
        Math.sin(brng) * Math.sin(ang) * Math.cos(lat1),
        Math.cos(ang) - Math.sin(lat1) * Math.sin(lat2)
    )
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

/** Shift a polyline sideways so forward/return don't paint on the same pixels. */
fun parallelOffsetPath(points: List<GeoPoint>, offsetMeters: Double): List<GeoPoint> {
    if (points.size < 2 || offsetMeters == 0.0) return points
    return points.mapIndexed { i, p ->
        val bearing = when (i) {
            0 -> bearingDegrees(points[0], points[1])
            points.lastIndex -> bearingDegrees(points[i - 1], points[i])
            else -> {
                val b1 = bearingDegrees(points[i - 1], points[i])
                val b2 = bearingDegrees(points[i], points[i + 1])
                val x = Math.cos(Math.toRadians(b1)) + Math.cos(Math.toRadians(b2))
                val y = Math.sin(Math.toRadians(b1)) + Math.sin(Math.toRadians(b2))
                (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
            }
        }
        offsetGeoPoint(p, bearing + 90.0, offsetMeters)
    }
}

/**
 * Keep endpoints pinned to node badges; bow midpoints sideways so overlapping
 * forward/return paths both stay visible without missing the markers.
 */
fun bowedRoutePath(points: List<GeoPoint>, offsetMeters: Double): List<GeoPoint> {
    if (points.size < 2 || offsetMeters == 0.0) return points
    val out = ArrayList<GeoPoint>(points.size * 2)
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        out.add(a)
        val mid = GeoPoint((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)
        out.add(offsetGeoPoint(mid, bearingDegrees(a, b) + 90.0, offsetMeters))
    }
    out.add(points.last())
    return out
}

/** Only accept a new map pin when it moved far enough — kills GPS jitter. */
fun stabilizeMapPoint(
    cache: MutableMap<Long, GeoPoint>,
    id: Long,
    candidate: GeoPoint,
    thresholdMeters: Double
): GeoPoint {
    val prev = cache[id] ?: run {
        cache[id] = candidate
        return candidate
    }
    val movedM = calculateDistance(
        prev.latitude, prev.longitude,
        candidate.latitude, candidate.longitude
    ) * 1000.0
    if (movedM >= thresholdMeters) {
        cache[id] = candidate
        return candidate
    }
    return prev
}

/** Resolve the BLE-connected radio in the node list despite provisional ID mismatches. */
fun resolveConnectedMeshNode(
    nodes: List<MeshNode>,
    connectedId: Long,
    deviceName: String?
): MeshNode? {
    if (connectedId != 0L) {
        nodes.find { it.nodeId == connectedId }?.let { return it }
        nodes.find { (it.nodeId and 0xFFFFFFFFL) == (connectedId and 0xFFFFFFFFL) }?.let { return it }
        nodes.find { (it.nodeId and 0xFFFFL) == (connectedId and 0xFFFFL) }?.let { return it }
    }
    val name = deviceName?.trim().orEmpty()
    if (name.isNotEmpty()) {
        nodes.find { it.name.equals(name, ignoreCase = true) }?.let { return it }
        val short = getShortName(name, connectedId)
        nodes.find {
            it.shortName.equals(short, ignoreCase = true) ||
                it.name.contains(name, ignoreCase = true)
        }?.let { return it }
    }
    return null
}

fun routePathLengthMeters(points: List<GeoPoint>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 0 until points.lastIndex) {
        total += calculateDistance(
            points[i].latitude, points[i].longitude,
            points[i + 1].latitude, points[i + 1].longitude
        ) * 1000.0
    }
    return total
}

fun addCasedRoutePolyline(
    mapView: MapView,
    points: List<GeoPoint>,
    color: Int,
    strokeDp: Float,
    dashed: Boolean
) {
    if (points.size < 2) return
    val caseWidth = strokeDp + 4f * mapView.context.resources.displayMetrics.density
    mapView.overlays.add(Polyline(mapView).apply {
        outlinePaint.apply {
            isAntiAlias = true
            this.color = 0xE60B1220.toInt()
            strokeWidth = caseWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(strokeDp * 3.2f, strokeDp * 2.2f), 0f)
        }
        setPoints(points)
    })
    mapView.overlays.add(Polyline(mapView).apply {
        outlinePaint.apply {
            isAntiAlias = true
            this.color = color
            strokeWidth = strokeDp
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(strokeDp * 3.2f, strokeDp * 2.2f), 0f)
        }
        setPoints(points)
    })
}

fun createRouteChevronDrawable(context: Context, colorInt: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (18f * density).toInt().coerceAtLeast(18)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        color = colorInt
        style = Paint.Style.FILL
    }
    val stroke = Paint().apply {
        isAntiAlias = true
        color = 0xE60B1220.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeJoin = Paint.Join.ROUND
    }
    val path = android.graphics.Path().apply {
        moveTo(size * 0.18f, size * 0.22f)
        lineTo(size * 0.82f, size * 0.50f)
        lineTo(size * 0.18f, size * 0.78f)
        close()
    }
    canvas.drawPath(path, paint)
    canvas.drawPath(path, stroke)
    return BitmapDrawable(context.resources, bitmap)
}

fun addRouteDirectionChevrons(
    mapView: MapView,
    context: Context,
    points: List<GeoPoint>,
    color: Int
) {
    if (points.size < 2) return
    val icon = createRouteChevronDrawable(context, color)
    points.zipWithNext().forEach { (from, to) ->
        val segM = calculateDistance(from.latitude, from.longitude, to.latitude, to.longitude) * 1000.0
        if (segM < 3.0) return@forEach
        val mid = GeoPoint(
            (from.latitude + to.latitude) / 2.0,
            (from.longitude + to.longitude) / 2.0
        )
        val bearing = bearingDegrees(from, to).toFloat()
        mapView.overlays.add(Marker(mapView).apply {
            position = mid
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
            title = null
            snippet = null
            setInfoWindow(null)
            this.icon = icon
            rotation = bearing
            setFlat(true)
            setOnMarkerClickListener { _, _ -> true }
        })
    }
}

// Blue "you are here" dot for the phone's GPS position (the stock osmdroid
// person icon is suppressed in the MyLocation overlay)
fun createPhoneDotDrawable(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (28f * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val c = size / 2f
    val halo = Paint().apply {
        isAntiAlias = true
        color = 0x333B82F6
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, c, halo)
    val ring = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, 8f * density, ring)
    val dot = Paint().apply {
        isAntiAlias = true
        color = 0xFF3B82F6.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, 6f * density, dot)
    return BitmapDrawable(context.resources, bitmap)
}

fun createBadgeMarkerDrawable(
    context: Context,
    label: String,
    colorInt: Int,
    isActive: Boolean = true,
    isPingMarker: Boolean = false
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    
    val textPaint = Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    
    val textWidth = textPaint.measureText(label)
    
    // Pill dimensions (compact and clean)
    val pillWidth = (textWidth + 16f * density).coerceAtLeast(48f * density)
    val pillHeight = 24f * density
    
    val sizeW = pillWidth.toInt() + (8f * density).toInt()
    val sizeH = pillHeight.toInt() + (8f * density).toInt()
    
    val bitmap = Bitmap.createBitmap(sizeW, sizeH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    
    val cx = sizeW / 2f
    val cy = sizeH / 2f
    
    val left = cx - pillWidth / 2f
    val right = cx + pillWidth / 2f
    val top = cy - pillHeight / 2f
    val bottom = cy + pillHeight / 2f
    val pillRect = RectF(left, top, right, bottom)
    
    val pillBgPaint = Paint().apply {
        isAntiAlias = true
        this.color = colorInt
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(pillRect, 6f * density, 6f * density, pillBgPaint)
    
    val pillBorderPaint = Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    canvas.drawRoundRect(pillRect, 6f * density, 6f * density, pillBorderPaint)
    
    // Draw text centered
    val textRect = Rect()
    textPaint.getTextBounds(label, 0, label.length, textRect)
    val textY = cy - textRect.exactCenterY()
    canvas.drawText(label, cx, textY, textPaint)
    
    return BitmapDrawable(context.resources, bitmap)
}

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
    onOpenNodeDetails: (Long) -> Unit = {},
    openRemoteConfigNodeId: Long? = null,
    onRemoteConfigOpened: () -> Unit = {}
) {
    var hasCentered by remember { mutableStateOf(false) }
    var selectedMapNode by remember { mutableStateOf<MeshNode?>(null) }
    var renamingMapNode by remember { mutableStateOf<MeshNode?>(null) }
    var selectedPingLog by remember { mutableStateOf<com.example.aethermesh.data.RangeTestLog?>(null) }
    var showRemoteConfigDialog by remember { mutableStateOf<MeshNode?>(null) }

    LaunchedEffect(openRemoteConfigNodeId) {
        val id = openRemoteConfigNodeId ?: return@LaunchedEffect
        val node = nodes.find { it.nodeId == id }
            ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (id and 0xFFFFFFFFL) }
        if (node != null) showRemoteConfigDialog = node
        onRemoteConfigOpened()
    }
    val context = LocalContext.current
    val rangeTestLogs by viewModel.rangeTestLogs.collectAsStateWithLifecycle()
    val breadcrumbs = viewModel.breadcrumbs
    val mapPrefs = remember { context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE) }
    var darkMapTiles by remember { mutableStateOf(mapPrefs.getBoolean("dark_tiles", false)) }
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
        // Initialize osmdroid Configuration safely
        val osmConfig = org.osmdroid.config.Configuration.getInstance()
        osmConfig.userAgentValue = context.packageName
        osmConfig.osmdroidBasePath = java.io.File(context.filesDir, "osmdroid")
        osmConfig.osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid/tiles")
        osmConfig.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        MapView(context).apply {
            setTileSource(
                if (darkMapTiles) cartoDarkTileSource() else TileSourceFactory.MAPNIK
            )
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
                android.widget.Toast.makeText(
                    context,
                    "Offline map loaded (${info.entries} entries).",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                mapGeneration++
            } catch (e: Exception) {
                android.util.Log.e("MapView", "Failed to import offline map", e)
                android.widget.Toast.makeText(context, "Failed to import: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
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

    // Prefer a real dark basemap over MAPNIK + color-matrix invert.
    LaunchedEffect(darkMapTiles, mapView) {
        mapView.setTileSource(
            if (darkMapTiles) cartoDarkTileSource() else TileSourceFactory.MAPNIK
        )
        mapView.overlayManager.tilesOverlay.setColorFilter(null)
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
                                contentDescription = null,
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
                                                viewModel.selectDirectMessage(node.nodeId)
                                                onNavigateToChats()
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
                Icon(Icons.Default.Layers, contentDescription = "Map Layers", modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick = {
                    val myLocationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                    val myLoc = myLocationOverlay?.myLocation
                    if (myLoc != null) {
                        mapView.controller.animateTo(myLoc)
                        mapView.controller.setZoom(16.0)
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            if (appLanguage == "Spanish") "Esperando ubicación GPS..." else "Waiting for GPS location...",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                containerColor = SurfaceDark.copy(alpha = 0.92f),
                contentColor = AccentCyan,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location", modifier = Modifier.size(20.dp))
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
                        android.widget.Toast.makeText(
                            context,
                            if (appLanguage == "Spanish") "No hay nodos con posición GPS activa" else "No nodes with active GPS position",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                containerColor = SurfaceDark.copy(alpha = 0.85f),
                contentColor = AccentMint,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = "Center on Mesh", modifier = Modifier.size(20.dp))
            }
        }

        // Layers menu popup (left of the control column)
        if (showLayersMenu) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 16.dp, start = 16.dp, end = 72.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = if (appLanguage == "Spanish") "Capas del Mapa" else "Map Layers",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (appLanguage == "Spanish") "Mapa oscuro" else "Dark map",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = darkMapTiles,
                            onCheckedChange = {
                                darkMapTiles = it
                                mapPrefs.edit().putBoolean("dark_tiles", it).apply()
                            },
                            modifier = Modifier
                        )
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
                            onClick = { viewModel.clearBreadcrumbs() },
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
                    Text("RANGE PINS", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentMint))
                        Spacer(Modifier.width(6.dp))
                        Text("ACK", color = TextMuted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentRed))
                        Spacer(Modifier.width(6.dp))
                        Text("Timeout", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        val activeMapNode = selectedMapNode?.let { sel ->
            nodes.find { it.nodeId == sel.nodeId }
        } ?: selectedMapNode
        // Compact map callout — tap opens full Details (Meshtastic-style).
        activeMapNode?.let { node ->
            val nodeShortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
            val stale = isNodeStale(node.lastActive)
            val mapPrimaryText = if (stale) TextMuted else TextLight
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
                                    tint = batteryLevelColor(node.battery),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("${node.battery}%", color = TextMuted, fontSize = 11.sp)
                                route?.hops?.takeIf { it > 0 }?.let { h ->
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "$h ${if (h == 1) "hop" else "hops"}",
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
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
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
                                    text = if (log.success) "ACK" else "TIMEOUT",
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { selectedPingLog = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
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
        var longName by remember(node.nodeId) { mutableStateOf(node.name) }
        var shortName by remember(node.nodeId) {
            mutableStateOf(node.shortName.ifEmpty { getShortName(node.name, node.nodeId) })
        }
        var adminPassword by remember(node.nodeId) { mutableStateOf("") }
        val isRemote = node.nodeId != viewModel.connectedNodeId
        AlertDialog(
            onDismissRequest = { renamingMapNode = null },
            title = { Text(t("Rename Node", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t("Long Name (max 16 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = longName,
                        onValueChange = { if (it.length <= 16) longName = it },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(t("Short Name (max 4 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = shortName,
                        onValueChange = { if (it.length <= 4) shortName = it.uppercase() },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isRemote) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (appLanguage == "Spanish")
                                "Contraseña del nodo (para guardar en el mesh)"
                            else
                                "Node admin password (to store on the mesh)",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = adminPassword,
                            onValueChange = { adminPassword = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = aetherTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (appLanguage == "Spanish")
                                "Sin contraseña el nombre solo queda en este teléfono."
                            else
                                "Without a password the name stays on this phone only.",
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val persisted = viewModel.renameNode(
                            node.nodeId,
                            longName.trim(),
                            shortName.trim(),
                            adminPassword
                        )
                        if (!persisted && isRemote) {
                            android.widget.Toast.makeText(
                                context,
                                if (appLanguage == "Spanish")
                                    "Nombre guardado solo en el teléfono. Conéctate al nodo o usa Config remota."
                                else
                                    "Name saved on phone only. Connect to that node or use Remote Config.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        renamingMapNode = null
                    }
                ) {
                    Text(t("Save", appLanguage), color = AccentMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingMapNode = null }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showRemoteConfigDialog != null) {
        val node = showRemoteConfigDialog!!
        val spanish = appLanguage == "Spanish"
        // Prefill from the last settings this phone pushed to that node (if any),
        // so re-opening the dialog doesn't silently revert the node to defaults.
        val remotePrefs = remember(node.nodeId) {
            context.getSharedPreferences("node_settings_${node.nodeId}", Context.MODE_PRIVATE)
        }
        var remoteName by remember(node.nodeId) { mutableStateOf(node.name) }
        var remotePassword by remember(node.nodeId) { mutableStateOf("") }
        var remoteSF by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("lora_sf", 9)) }
        var remoteBW by remember(node.nodeId) { mutableFloatStateOf(remotePrefs.getFloat("lora_bw", 125f)) }
        var remoteTxPower by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("lora_tx_power", 22)) }
        var remoteRegion by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("region", 0)) }
        var remoteRole by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("node_role", 0)) }
        var remoteTelemetryInterval by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("telemetry_interval", 60)) }
        var remotePositionPrecision by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("position_precision", 0)) }
        var remoteGpsEnabled by remember(node.nodeId) { mutableStateOf(remotePrefs.getInt("gps_mode", 0) == 0) }
        var showRemoteRepeaterConfirm by remember(node.nodeId) { mutableStateOf(false) }

        fun applyRemoteConfig() {
            val success = viewModel.sendRemoteConfig(
                nodeId = node.nodeId,
                name = remoteName.trim(),
                password = remotePassword.trim(),
                sf = remoteSF,
                bw = remoteBW,
                txPower = remoteTxPower,
                region = remoteRegion,
                role = remoteRole,
                telemetryInterval = remoteTelemetryInterval,
                positionPrecision = remotePositionPrecision,
                gpsMode = if (remoteGpsEnabled) 0 else 1
            )
            if (success) {
                remotePrefs.edit().apply {
                    putInt("lora_sf", remoteSF)
                    putFloat("lora_bw", remoteBW)
                    putInt("lora_tx_power", remoteTxPower)
                    putInt("region", remoteRegion)
                    putInt("node_role", remoteRole)
                    putInt("telemetry_interval", remoteTelemetryInterval)
                    putInt("position_precision", remotePositionPrecision)
                    putInt("gps_mode", if (remoteGpsEnabled) 0 else 1)
                    apply()
                }
                android.widget.Toast.makeText(
                    context,
                    if (spanish) "Configuración remota enviada" else "Remote config dispatched!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    context,
                    if (spanish) "No se pudo enviar la configuración." else "Failed to dispatch config.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            showRemoteConfigDialog = null
        }

        if (showRemoteRepeaterConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoteRepeaterConfirm = false },
                title = {
                    Text(
                        t("Enable Repeater Mode?", appLanguage),
                        color = TextLight,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        t(
                            "WARNING: In Low-Power Repeater mode, the node turns off its BLE transceivers to maximize battery. You will lose connection immediately. To configure the node again, you must hold the hardware boot button on boot to trigger factory reset.",
                            appLanguage
                        ),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRemoteRepeaterConfirm = false
                            applyRemoteConfig()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextLight)
                    ) {
                        Text(if (spanish) "Aplicar" else "Apply", color = TextLight)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoteRepeaterConfirm = false }) {
                        Text(t("Cancel", appLanguage), color = TextMuted)
                    }
                },
                containerColor = SurfaceDark
            )
        }

        AlertDialog(
            onDismissRequest = { showRemoteConfigDialog = null },
            title = {
                Text(
                    if (spanish) "Configuración remota" else "Remote Node Configuration",
                    color = TextLight,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (spanish) "Objetivo: 0x${node.nodeId.toString(16).uppercase()}"
                        else "Target: 0x${node.nodeId.toString(16).uppercase()}",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(if (spanish) "Nombre" else "Custom Name", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = remoteName,
                        onValueChange = { if (it.length <= 16) remoteName = it },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        if (spanish) "Contraseña de admin (obligatoria)" else "Admin Password (Required)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = remotePassword,
                        onValueChange = { remotePassword = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(if (spanish) "Perfil de radio" else "Radio Profile", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    RadioProfileChips(remoteSF, remoteBW) { profile ->
                        remoteSF = profile.sf
                        remoteBW = profile.bw
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        if (spanish) "Telemetría (intervalo)" else "Telemetry Broadcast (Interval)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120, 300).forEach { interval ->
                            val isSel = remoteTelemetryInterval == interval
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) AccentCyan else SurfaceDark)
                                    .clickable { remoteTelemetryInterval = interval }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "${interval}s",
                                    color = if (isSel) DarkBackground else TextLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(if (spanish) "Rol del nodo" else "Node Role", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            (if (spanish) "Cliente" else "Client") to 0,
                            (if (spanish) "Router" else "Router") to 1,
                            (if (spanish) "Repetidor" else "Repeater") to 2
                        ).forEach { (label, value) ->
                            val isSel = remoteRole == value
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) AccentCyan else SurfaceDark)
                                    .clickable { remoteRole = value }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) DarkBackground else TextLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        if (spanish) "Precisión de posición (privacidad)" else "Position Precision (privacy blur)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(POSITION_PRECISION_STEPS) { meters ->
                            val isSel = remotePositionPrecision == meters
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) AccentCyan else SurfaceDark)
                                    .clickable { remotePositionPrecision = meters }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    formatPositionPrecision(meters, useImperialUnits, appLanguage),
                                    color = if (isSel) DarkBackground else TextLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(if (spanish) "GPS del nodo" else "Node GPS", color = TextMuted, fontSize = 11.sp)
                            Text(
                                if (spanish) "Apagado ahorra ~25% de batería" else "Off saves ~25% battery",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = remoteGpsEnabled,
                            onCheckedChange = { remoteGpsEnabled = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (remoteRole == 2) {
                            showRemoteRepeaterConfirm = true
                        } else {
                            applyRemoteConfig()
                        }
                    },
                    enabled = remotePassword.isNotEmpty()
                ) {
                    Text(
                        if (spanish) "Aplicar" else "Apply Settings",
                        color = if (remotePassword.isNotEmpty()) AccentMint else TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoteConfigDialog = null }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }
}


@Composable
fun DiagnosticCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (compact) DarkBackground else SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        border = if (compact) BorderStroke(1.dp, BorderDark) else null
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = TextMuted, fontSize = if (compact) 10.sp else 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                color = color,
                fontSize = if (compact) 16.sp else 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

data class RadioProfile(val label: String, val sf: Int, val bw: Float, val hint: String)

val radioProfiles = listOf(
    RadioProfile("Fast", 9, 125f, "Baseline. Quick messages, shortest range."),
    RadioProfile("Balanced", 10, 125f, "+2.5 dB range vs Fast, 2x airtime."),
    RadioProfile("Long range", 11, 125f, "+5 dB range vs Fast, 4x airtime."),
    RadioProfile("Max range", 12, 125f, "+7.5 dB range vs Fast, 8x airtime. Use 10s+ ping intervals.")
)

@Composable
fun RadioProfileChips(currentSf: Int, currentBw: Float, onSelect: (RadioProfile) -> Unit) {
    val selectedProfile = radioProfiles.firstOrNull { it.sf == currentSf && it.bw == currentBw }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        radioProfiles.forEach { p ->
            val isSel = selectedProfile == p
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) AccentCyan else DarkBackground)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    p.label,
                    color = if (isSel) SurfaceDark else TextLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        selectedProfile?.hint ?: "Custom SF/BW (not a preset)",
        color = TextMuted,
        fontSize = 10.sp
    )
    Text(
        "Every node must run the same profile - mismatched nodes can't hear each other.",
        color = Color(0xFFFACC15),
        fontSize = 10.sp
    )
}


@Composable
fun AetherBottomNav(
    selectedTab: TabItem,
    appLanguage: String,
    onTabSelected: (TabItem) -> Unit
) {
    data class NavTab(
        val tab: TabItem,
        val icon: ImageVector,
        val labelKey: String,
        val color: Color
    )
    val tabs = listOf(
        NavTab(TabItem.CHATS, Icons.AutoMirrored.Filled.Chat, "Chats", AccentCyan),
        NavTab(TabItem.NODES, Icons.Default.Hub, "Nodes", AccentMint),
        NavTab(TabItem.MAP, Icons.Default.Map, "Map", AccentSteel),
        NavTab(TabItem.SETTINGS, Icons.Default.Settings, "Settings", AccentAmber),
        NavTab(TabItem.CONNECTION, Icons.Default.SettingsInputAntenna, "Connection", AccentOrange)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceRaised)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderDark.copy(alpha = 0.7f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { item ->
                val selected = selectedTab == item.tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) item.color.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onTabSelected(item.tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = t(item.labelKey, appLanguage),
                        tint = if (selected) item.color else item.color.copy(alpha = 0.42f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    viewModel: MainScreenViewModel,
    isConnected: Boolean
) {
    val context = LocalContext.current
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val connectedNode = nodes.find { it.nodeId == viewModel.connectedNodeId }

    val isDeviceAuthenticated by viewModel.isDeviceAuthenticated.collectAsStateWithLifecycle()

    var nodeName by remember { mutableStateOf("") }
    var nodeShortName by remember { mutableStateOf("") }
    var sf by remember { mutableIntStateOf(9) }
    var bw by remember { mutableFloatStateOf(125f) }
    var txPower by remember { mutableIntStateOf(22) }
    var region by remember { mutableIntStateOf(0) } // 0 = US915, 1 = EU868
    var role by remember { mutableIntStateOf(0) } // 0 = Client, 1 = Router, 2 = Low-Power Repeater
    var telemetryIntervalSecs by remember { mutableIntStateOf(60) }
    var screenTimeoutSecs by remember { mutableIntStateOf(30) }
    var powerSaveModeEnabled by remember { mutableStateOf(false) }
    var positionPrecisionM by remember { mutableIntStateOf(0) }
    var nodeGpsEnabled by remember { mutableStateOf(true) }
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

    val sharedPrefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
    var bgAlertsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("bg_alerts_enabled", true)) }
    var useImperialUnitsSetting by remember { mutableStateOf(sharedPrefs.getBoolean("use_imperial_units", true)) }
    var enablePhoneGpsSharing by remember { mutableStateOf(sharedPrefs.getBoolean("enable_phone_gps_sharing", true)) }

    val consoleMessages by viewModel.messages.collectAsStateWithLifecycle()

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
                    put("gps_mode", if (nodeGpsEnabled) 0 else 1)
                    put("fixed_position", fixedPositionEnabled)
                    put("fixed_latitude", fixedLatInput.toFloatOrNull() ?: 0f)
                    put("fixed_longitude", fixedLonInput.toFloatOrNull() ?: 0f)
                    put("fixed_altitude", fixedAltInput.toIntOrNull() ?: 0)
                }.toString(2)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                android.widget.Toast.makeText(context, "Settings exported successfully", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to export settings: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
                    nodeGpsEnabled = json.optInt("gps_mode", if (nodeGpsEnabled) 0 else 1) == 0
                    fixedPositionEnabled = json.optBoolean("fixed_position", fixedPositionEnabled)
                    fixedLatInput = json.optDouble("fixed_latitude", fixedLatInput.toDoubleOrNull() ?: 0.0).toFloat().toString()
                    fixedLonInput = json.optDouble("fixed_longitude", fixedLonInput.toDoubleOrNull() ?: 0.0).toFloat().toString()
                    fixedAltInput = json.optInt("fixed_altitude", fixedAltInput.toIntOrNull() ?: 0).toString()
                    
                    android.widget.Toast.makeText(
                        context,
                        if (sharedPrefs.getString("app_language", "English") == "Spanish")
                            "Ajustes importados. Pulsa Guardar para aplicarlos al dispositivo."
                        else
                            "Settings imported. Click Save to apply to device.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    if (sharedPrefs.getString("app_language", "English") == "Spanish")
                        "Error al importar ajustes: ${e.message}"
                    else
                        "Failed to import settings: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
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

    var appTheme by remember { mutableStateOf(sharedPrefs.getString("app_theme", "System") ?: "System") }
    var appLanguage by remember { mutableStateOf(sharedPrefs.getString("app_language", "English") ?: "English") }
    var phoneLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isExpandedTheme by remember { mutableStateOf(false) }
    var isExpandedLanguage by remember { mutableStateOf(false) }

    var ecdhKeys by remember { mutableStateOf(Pair("", "")) }
    var showPrivateKey by remember { mutableStateOf(false) }
    var showRegenKeysDialog by remember { mutableStateOf(false) }

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
    LaunchedEffect(viewModel.connectedNodeId, nodes) {
        channelsList = viewModel.getChannelsList()
        ecdhKeys = viewModel.getOrCreateEcdhKeys()
        val nodeKey = viewModel.connectedNodeId
        if (nodeKey != 0L && nodeKey != configLoadedForNode) {
            configLoadedForNode = nodeKey
            val nodePrefs = context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
            val matchedNode = nodes.find { it.nodeId == nodeKey }
            nodeName = matchedNode?.name?.replace("AetherMesh-", "")?.replace("Node ", "") ?: ""
            nodeShortName = matchedNode?.shortName ?: ""
            sf = nodePrefs.getInt("lora_sf", 9)
            bw = nodePrefs.getFloat("lora_bw", 125f)
            txPower = nodePrefs.getInt("lora_tx_power", 22)
            region = nodePrefs.getInt("region", 0)
            role = nodePrefs.getInt("node_role", 0)
            telemetryIntervalSecs = nodePrefs.getInt("telemetry_interval", 60)
            screenTimeoutSecs = nodePrefs.getInt("screen_timeout", 30)
            powerSaveModeEnabled = nodePrefs.getBoolean("power_save_mode", false)
            positionPrecisionM = nodePrefs.getInt("position_precision", 0)
            nodeGpsEnabled = nodePrefs.getInt("gps_mode", 0) == 0
            // Fixed position isn't carried in telemetry, so it loads from the
            // last config this phone pushed (node_settings prefs), same as the
            // radio sliders. 0/blank shows as empty rather than "0.0".
            fixedPositionEnabled = nodePrefs.getBoolean("fixed_position", false)
            val fLat = nodePrefs.getFloat("fixed_latitude", 0f)
            val fLon = nodePrefs.getFloat("fixed_longitude", 0f)
            val fAlt = nodePrefs.getInt("fixed_altitude", 0)
            fixedLatInput = if (fLat != 0f) fLat.toString() else ""
            fixedLonInput = if (fLon != 0f) fLon.toString() else ""
            fixedAltInput = if (fAlt != 0) fAlt.toString() else ""
        }
    }

    val saveConfigAndNotify = {
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
            gpsMode = if (nodeGpsEnabled) 0 else 1,
            fixedPosition = fixedPositionEnabled,
            fixedLatitude = fixedLatInput.toFloatOrNull() ?: 0f,
            fixedLongitude = fixedLonInput.toFloatOrNull() ?: 0f,
            fixedAltitude = fixedAltInput.toIntOrNull() ?: 0
        )
        if (success) {
            val nodeKey = viewModel.connectedNodeId
            if (nodeKey != 0L) {
                val nodePrefs = context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
                nodePrefs.edit().apply {
                    putInt("lora_sf", sf)
                    putFloat("lora_bw", bw)
                    putInt("lora_tx_power", txPower)
                    putInt("region", region)
                    putInt("node_role", role)
                    putInt("telemetry_interval", telemetryIntervalSecs)
                    putInt("screen_timeout", screenTimeoutSecs)
                    putBoolean("power_save_mode", powerSaveModeEnabled)
                    putInt("position_precision", positionPrecisionM)
                    putInt("gps_mode", if (nodeGpsEnabled) 0 else 1)
                    putBoolean("fixed_position", fixedPositionEnabled)
                    putFloat("fixed_latitude", fixedLatInput.toFloatOrNull() ?: 0f)
                    putFloat("fixed_longitude", fixedLonInput.toFloatOrNull() ?: 0f)
                    putInt("fixed_altitude", fixedAltInput.toIntOrNull() ?: 0)
                    apply()
                }
            }
            android.widget.Toast.makeText(
                context,
                if (appLanguage == "Spanish") "¡Ajustes enviados! El nodo se reiniciará." else "Config sent! Node will reboot now.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } else {
            android.widget.Toast.makeText(
                context,
                if (appLanguage == "Spanish") "Error al enviar la configuración." else "Failed to send configuration.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (activeCategory == null) {
            // Gradient Graphic Header Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F766E))
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column {
                        Text(
                            text = "Settings",
                            color = AccentCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = t("Select a settings category below to manage your device.", appLanguage),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Categories Menu List
            val categories = listOf(
                Triple(SettingsCategory.CHANNELS, "Channels", "Manage secondary channels and share/join links"),
                Triple(SettingsCategory.RADIO, "LoRa Radio Configuration", "Set spreading factor, bandwidth, power, and region"),
                Triple(SettingsCategory.POSITION, "GPS & Position Settings", "Configure GPS enable, telemetry interval, and view satellite lock status"),
                Triple(SettingsCategory.FIRMWARE, "Firmware Update", "Flash new firmware to the connected node over Bluetooth (BLE OTA)"),
                Triple(SettingsCategory.SECURITY, "Security & Keys", "Manage private keys, ECDH keypairs, and device password"),
                Triple(SettingsCategory.PREFERENCES, "App Preferences", "Set language, theme, and background alerts"),
                Triple(SettingsCategory.DEVELOPER, "Developer & Diagnostics", "Live logs console, packet exports, and system database reset")
            )

            categories.forEach { (cat, title, desc) ->
                val needsDevice = cat == SettingsCategory.CHANNELS ||
                    cat == SettingsCategory.RADIO ||
                    cat == SettingsCategory.POSITION ||
                    cat == SettingsCategory.FIRMWARE ||
                    cat == SettingsCategory.SECURITY
                val enabled = !needsDevice || isConnected
                val icon = when(cat) {
                    SettingsCategory.CHANNELS -> Icons.Default.Layers
                    SettingsCategory.RADIO -> Icons.Default.Settings
                    SettingsCategory.POSITION -> Icons.Default.Place
                    SettingsCategory.FIRMWARE -> Icons.Default.SystemUpdate
                    SettingsCategory.SECURITY -> Icons.Default.Lock
                    SettingsCategory.PREFERENCES -> Icons.Default.Palette
                    SettingsCategory.DEVELOPER -> Icons.Default.Terminal
                }
                val iconColor = when(cat) {
                    SettingsCategory.CHANNELS -> AccentCyan
                    SettingsCategory.RADIO -> AccentMint
                    SettingsCategory.POSITION -> Color(0xFF818CF8)
                    SettingsCategory.FIRMWARE -> AccentMint
                    SettingsCategory.SECURITY -> Color(0xFFEF4444)
                    SettingsCategory.PREFERENCES -> Color(0xFFFBBF24)
                    SettingsCategory.DEVELOPER -> AccentCyan
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (enabled) SurfaceDark else SurfaceDark.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable(enabled = enabled) { activeCategory = cat }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colored Circle Icon Container
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
                                    if (appLanguage == "Spanish")
                                        "Conecta un nodo para configurar esto."
                                    else
                                        "Connect a node to configure this."
                                } else {
                                    t(desc, appLanguage)
                                },
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Navigate",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else {
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
                    contentDescription = "Back",
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when(activeCategory) {
                        SettingsCategory.CHANNELS -> t("Channels", appLanguage)
                        SettingsCategory.RADIO -> t("LoRa Radio Configuration", appLanguage)
                        SettingsCategory.SECURITY -> t("Security & Keys", appLanguage)
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
                Text(
                    text = t("Channels", appLanguage),
                    color = AccentCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Freq: $freqText",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Slot: ${channelsList.size}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
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
                                    contentDescription = "Location Sharing Status",
                                    tint = if (channel.positionEnabled) AccentCyan else TextMuted,
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                // Encryption status indicator
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encryption Status",
                                    tint = if (channel.psk.isNotEmpty() && channel.psk != "AQ==") AccentMint else TextMuted,
                                    modifier = Modifier.size(18.dp).padding(end = 8.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                if (!channel.isPrimary) {
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteChannel(channel.id)
                                            channelsList = viewModel.getChannelsList()
                                            android.widget.Toast.makeText(context, t("Channel deleted.", appLanguage), android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Channel",
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

                Spacer(modifier = Modifier.height(8.dp))
                
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
                                
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Channel Link", shareText))
                                android.widget.Toast.makeText(context, if (appLanguage == "Spanish") "¡Enlace de canal copiado!" else "Channel link copied!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, if (appLanguage == "Spanish") "Crea un canal primario primero" else "Create a primary channel first", android.widget.Toast.LENGTH_SHORT).show()
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
            Text(
            text = t("LoRa Radio Configuration", appLanguage),
            color = AccentCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
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
                        placeholder = { Text("e.g. Wolf Base", color = TextMuted) },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${nodeName.length}/16 characters",
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
                        placeholder = { Text("e.g. WOLF", color = TextMuted) },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${nodeShortName.length}/4 characters",
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
            Text(
                text = t("GPS & Position Settings", appLanguage),
                color = AccentCyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
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
                    
                    // Status Badge row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("GPS Lock Status", appLanguage) + ":",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasLock) Color(0x204ADE80) else Color(0x20F59E0B))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Small indicator dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (hasLock) AccentMint else Color(0xFFF59E0B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasLock) t("LOCKED", appLanguage) else t("WAITING FOR LOCK", appLanguage),
                                    color = if (hasLock) AccentMint else Color(0xFFF59E0B),
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
                            text = if (hasLock) "${"%.6f".format(connectedNode!!.latitude)}, ${"%.6f".format(connectedNode!!.longitude)}" else "No Lock",
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
                                            android.widget.Toast.makeText(context, "Location loaded from phone GPS", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "No phone GPS location lock yet — open the Map tab briefly to acquire one", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: SecurityException) {
                                        android.widget.Toast.makeText(context, "Location permission needed", android.widget.Toast.LENGTH_SHORT).show()
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

                        // Node GPS power toggle (biggest single battery consumer)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = if (appLanguage == "Spanish") "GPS del Nodo" else "Node GPS",
                                    color = TextLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (appLanguage == "Spanish")
                                        "Apagar ahorra ~25% de batería; la posición usa el GPS del teléfono."
                                    else
                                        "Powers the onboard GPS module. Off saves ~25% battery; position falls back to phone GPS sharing.",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = nodeGpsEnabled,
                                onCheckedChange = { nodeGpsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBackground,
                                    checkedTrackColor = AccentMint,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = BorderDark
                                )
                            )
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
                                        "Atenúa la pantalla rápido, reduce BLE y telemetría para ahorrar batería." 
                                        else "Caps screen to 10s, slows BLE/telemetry to maximize battery.",
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
            val isHeltecNode = connectedNode?.model?.contains("Heltec", ignoreCase = true) == true
            val isRakNode = connectedNode?.model?.contains("RAK", ignoreCase = true) == true
            val otaSupported = isHeltecNode || isRakNode
            val otaFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "firmware"
                        if (bytes != null && bytes.isNotEmpty()) {
                            val err = isValidOtaPayload(bytes, name, isRakNode)
                            if (err != null) {
                                otaFileBytes = null
                                otaFileUri = null
                                otaFileName = ""
                                otaPickError = err
                                android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                otaFileBytes = bytes
                                otaFileUri = uri
                                otaFileName = name
                                otaPickError = null
                                viewModel.resetOtaState()
                            }
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Could not read file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
                        Text(otaState.status, color = TextLight, fontSize = 12.sp)
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
                                otaState.status,
                                color = if (otaState.error) AccentRed else if (otaState.done) AccentMint else TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else if (!otaSupported) {
                        Text(
                            text = if (appLanguage == "Spanish") "Este modelo de nodo no soporta OTA." else "This node model doesn't support OTA updates.",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp
                        )
                    } else {
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
                                    if (isRakNode)
                                        (if (appLanguage == "Spanish") "Elegir paquete .zip (DFU)" else "Choose firmware .zip (DFU package)")
                                    else
                                        (if (appLanguage == "Spanish") "Elegir archivo .bin" else "Choose firmware .bin")
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
                                otaState.status,
                                color = if (otaState.error) AccentRed else if (otaState.done) AccentMint else TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Text(
                text = if (appLanguage == "Spanish")
                    "El primer firmware con OTA debe instalarse por USB; después es inalámbrico. Heltec usa .bin; RAK usa el paquete .zip (DFU del bootloader)."
                else
                    "The first OTA-capable firmware must be flashed over USB; after that, updates are wireless. Heltec takes the .bin (verified before reboot); RAK takes the .zip DFU package (streamed to its bootloader). A failed transfer leaves the node on its current firmware.",
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
                            Text("• Make sure the node is charged or on USB.", color = TextMuted, fontSize = 12.sp)
                            Text("• Keep the node close to your phone.", color = TextMuted, fontSize = 12.sp)
                            Text("• Do not close the app during the update.", color = TextMuted, fontSize = 12.sp)
                            Text("• Verify this build matches the hardware (Heltec V4).", color = TextMuted, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "The image is checksum-verified before the node reboots. If the transfer fails, the node keeps running its current firmware.",
                                color = TextMuted, fontSize = 11.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showOtaWarning = false
                            if (isRakNode) {
                                otaFileUri?.let { viewModel.startRakDfuUpdate(it) }
                            } else {
                                otaFileBytes?.let { viewModel.startFirmwareUpdate(it) }
                            }
                        }) {
                            Text("I know what I'm doing.", color = AccentMint, fontWeight = FontWeight.Bold)
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
            Text(
            text = t("Security & DM Keys", appLanguage),
            color = AccentCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
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
                            android.widget.Toast.makeText(context, t("Export Keys", appLanguage), android.widget.Toast.LENGTH_SHORT).show()
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

        if (activeCategory == SettingsCategory.PREFERENCES) {
            // --- 4. APP PREFERENCES CARD ---
            Text(
            text = t("App Preferences", appLanguage),
            color = AccentCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t("Distance Units", appLanguage), color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (useImperialUnitsSetting) t("Imperial (Miles, Feet)", appLanguage) else t("Metric (Kilometers, Meters)", appLanguage),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = useImperialUnitsSetting,
                        onCheckedChange = { isChecked ->
                            useImperialUnitsSetting = isChecked
                            sharedPrefs.edit().putBoolean("use_imperial_units", isChecked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentCyan,
                            checkedTrackColor = AccentCyan.copy(alpha = 0.5f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CSV Exports
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        exportRangeTestLogsToCsv(context, viewModel.getAllRangeTestLogs(), viewModel.nodes.value.associate { it.nodeId to (it.latitude.toDouble() to it.longitude.toDouble()) })
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
                        exportAllPacketsToCsv(context, allMsg)
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
            Text(
            text = t("Data & Logs Management", appLanguage),
            color = AccentCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
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
                            Text(t("View raw system messages", appLanguage), color = TextMuted, fontSize = 11.sp)
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
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("System initialized on US915.", color = AccentCyan, fontSize = 11.sp)
                        consoleMessages.takeLast(15).forEach { msg ->
                            Text(
                                "Packet from 0x${msg.senderId.toString(16).uppercase()}: Msg size ${msg.content.length} bytes.",
                                color = TextLight,
                                fontSize = 11.sp
                            )
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
                        Text("v2.7.14 (Stable Release) google", color = TextMuted, fontSize = 11.sp)
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
                                android.widget.Toast.makeText(context, if (appLanguage == "Spanish") "¡Petición de cambio de contraseña enviada!" else "Password change request sent!", android.widget.Toast.LENGTH_SHORT).show()
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
                        android.widget.Toast.makeText(context, if (appLanguage == "Spanish") "Historial de chat borrado" else "Chat history cleared", android.widget.Toast.LENGTH_SHORT).show()
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
                        android.widget.Toast.makeText(context, if (appLanguage == "Spanish") "Directorio de nodos reiniciado" else "Nodes directory reset", android.widget.Toast.LENGTH_SHORT).show()
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
                    Text(t("Channel Name", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    TextField(
                        value = newChannelName,
                        onValueChange = { if (it.length <= 24) newChannelName = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Trail-Crew", color = TextMuted) },
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
                            contentDescription = "Regenerate PSK",
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
                            contentDescription = "Regenerate PSK",
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
        AlertDialog(
            onDismissRequest = { showImportChannelDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val cleaned = importChannelLinkInput.trim()
                            val base64Part = if (cleaned.contains("#")) {
                                cleaned.substringAfter("#")
                            } else if (cleaned.startsWith("aethermesh://")) {
                                cleaned.substringAfter("aethermesh://channel?")
                            } else {
                                cleaned
                            }
                            
                            val decodedBytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                            val decodedStr = String(decodedBytes)
                            
                            if (decodedStr.startsWith("aethermesh://channel")) {
                                val uri = android.net.Uri.parse(decodedStr)
                                val name = uri.getQueryParameter("name") ?: "Imported"
                                val psk = uri.getQueryParameter("psk") ?: ""
                                val uplink = uri.getQueryParameter("uplink")?.toBoolean() ?: true
                                val downlink = uri.getQueryParameter("downlink")?.toBoolean() ?: true
                                val position = uri.getQueryParameter("position")?.toBoolean() ?: true
                                val precise = uri.getQueryParameter("precise")?.toBoolean() ?: true
                                
                                // Create the channel!
                                val newChan = ChannelConfig(
                                    name = name,
                                    psk = psk,
                                    isPrimary = true, // Set as primary
                                    uplinkEnabled = uplink,
                                    downlinkEnabled = downlink,
                                    positionEnabled = position,
                                    preciseLocation = precise,
                                    precisionMiles = 1f
                                )
                                viewModel.insertChannel(newChan)
                                channelsList = viewModel.getChannelsList() // refresh list
                                showImportChannelDialog = false
                                android.widget.Toast.makeText(context, if (appLanguage == "Spanish") "¡Canal importado con éxito!" else "Channel imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                throw Exception("Invalid URI scheme")
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, if (appLanguage == "Spanish") "Enlace de canal no válido" else "Invalid channel link", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = importChannelLinkInput.trim().isNotEmpty()
                ) { Text(t("Join", appLanguage), color = AccentMint) }
            },
            dismissButton = {
                TextButton(onClick = { showImportChannelDialog = false }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            title = { Text(t("Join Channel", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t("Paste AetherMesh Channel Link", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = importChannelLinkInput,
                        onValueChange = { importChannelLinkInput = it },
                        singleLine = false,
                        maxLines = 3,
                        placeholder = { Text("https://aethermesh.org/join#...", color = TextMuted) },
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = SurfaceDark
        )
    }
}


@Composable
fun ConnectionView(
    viewModel: MainScreenViewModel,
    isConnected: Boolean,
    nodes: List<MeshNode>,
    scannedDevices: List<BleDeviceItem>,
    appLanguage: String = "English"
) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanBlockReason by viewModel.scanBlockReason.collectAsStateWithLifecycle()
    val spanish = appLanguage == "Spanish"
    val isRangeTestActive by viewModel.isRangeTestActive.collectAsStateWithLifecycle()
    val isDeviceAuthenticated by viewModel.isDeviceAuthenticated.collectAsStateWithLifecycle()
    val blePhase by viewModel.bleConnectionPhase.collectAsStateWithLifecycle()
    val bleReconnectAttempt by viewModel.bleReconnectAttempt.collectAsStateWithLifecycle()
    val bleReconnectGaveUp by viewModel.bleReconnectGaveUp.collectAsStateWithLifecycle()
    val connectedNode = nodes.find { it.nodeId == viewModel.connectedNodeId }
    val displayName = connectedNode?.name ?: viewModel.connectedDeviceName ?: "Wolf Base"
    val shortName = getShortName(displayName, viewModel.connectedNodeId ?: 0L)
    val badgeColor = getBadgeColor(displayName)
    val batteryVal = connectedNode?.battery ?: 98
    var toolsExpanded by remember { mutableStateOf(false) }
    val toolsPrefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
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

    LaunchedEffect(isRangeTestActive) {
        if (isRangeTestActive) toolsExpanded = true
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        AetherSectionHeader(title = t("Connection", appLanguage))
        Spacer(modifier = Modifier.height(16.dp))

        if (!isConnected) {
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
                    Text(
                        if (appLanguage == "Spanish")
                            "Reconectando (intento $bleReconnectAttempt)…"
                        else
                            "Reconnecting (attempt $bleReconnectAttempt)…",
                        color = AccentAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // 1. Connected Node Card
        if (isConnected) {
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
                                    val fwVersion = connectedNode?.firmwareVersion?.takeIf { it.isNotEmpty() } ?: "unknown"
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
                            value = "0x${viewModel.connectedNodeId.toString(16).uppercase().takeLast(4)}",
                            accent = AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                        GraphicStatTile(
                            label = if (appLanguage == "Spanish") "Modelo" else "Model",
                            value = connectedNode?.model?.takeIf { it.isNotEmpty() }?.take(8) ?: "—",
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
                                Text("Flasher", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.disconnectDevice() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(t("Disconnect", appLanguage), color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            ExpandableSectionHeader(
                title = if (appLanguage == "Spanish") "Herramientas" else "Tools",
                expanded = toolsExpanded,
                onToggle = { toolsExpanded = !toolsExpanded },
                badge = when {
                    isRangeTestActive -> t("ACTIVE", appLanguage)
                    isConnected && !isDeviceAuthenticated -> if (appLanguage == "Spanish") "Auth" else "Auth"
                    toolsExpanded -> null
                    else -> if (appLanguage == "Spanish") "Rango · Enrutamiento" else "Range · Routing"
                }
            )
            if (toolsExpanded) {
            if (isConnected && !isDeviceAuthenticated) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (appLanguage == "Spanish") "Herramientas bloqueadas"
                            else "Tools locked",
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (appLanguage == "Spanish")
                                "Autentica el nodo conectado para usar prueba de rango, modo silencioso y diagnósticos en vivo."
                            else
                                "Authenticate the connected node to use range test, quiet mode, and live diagnostics.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
            // Mesh health
            val observedRoutes by viewModel.observedRoutes.collectAsStateWithLifecycle()
            val meshDiagnostics by viewModel.meshDiagnostics.collectAsStateWithLifecycle()
            AetherSectionHeader(
                title = t("Mesh Routing Diagnostics", appLanguage),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    meshDiagnostics?.let { diagnostics ->
                        // ACK % is only for want_ack unicast (DMs). Range pings and
                        // channel broadcasts never touch ackedPackets/ackTimeouts, so
                        // an empty value is expected — not a broken gauge.
                        val deliveryAttempts = diagnostics.ackedPackets + diagnostics.ackTimeouts
                        val deliveryLabel = if (deliveryAttempts > 0) {
                            "${diagnostics.ackedPackets * 100 / deliveryAttempts}%"
                        } else {
                            if (appLanguage == "Spanish") "n/d" else "n/a"
                        }
                        val deliveryColor = when {
                            deliveryAttempts == 0L -> TextMuted
                            diagnostics.ackTimeouts == 0L -> AccentMint
                            diagnostics.ackedPackets * 100 / deliveryAttempts >= 70L -> AccentMint
                            else -> AccentAmber
                        }
                        val ageSec = ((System.currentTimeMillis() - diagnostics.timestamp) / 1000L).coerceAtLeast(0L)
                        val ageLabel = when {
                            ageSec < 5L -> if (appLanguage == "Spanish") "ahora" else "just now"
                            ageSec < 60L -> "${ageSec}s"
                            else -> "${ageSec / 60}m"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagnosticCard("TX / RX", "${diagnostics.txPackets} / ${diagnostics.rxPackets}", AccentCyan, Modifier.weight(1f), compact = true)
                            DiagnosticCard(
                                if (appLanguage == "Spanish") "ACK %" else "ACK %",
                                deliveryLabel,
                                deliveryColor,
                                Modifier.weight(1f),
                                compact = true
                            )
                        }
                        if (deliveryAttempts == 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (appLanguage == "Spanish")
                                    "ACK % solo cuenta DMs con recibo — no pruebas de rango ni canales."
                                else
                                    "ACK % counts DMs with receipts — not range tests or channel chats.",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (appLanguage == "Spanish")
                                    "ACK ${diagnostics.ackedPackets} · timeout ${diagnostics.ackTimeouts}"
                                else
                                    "ACK ${diagnostics.ackedPackets} · timeout ${diagnostics.ackTimeouts}",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagnosticCard(if (appLanguage == "Spanish") "TX fallos" else "TX fail", "${diagnostics.txFailures}", TextMuted, Modifier.weight(1f), compact = true)
                            DiagnosticCard(if (appLanguage == "Spanish") "Caídas" else "Drops", "${diagnostics.queueDrops}", TextMuted, Modifier.weight(1f), compact = true)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagnosticCard("CAD busy", "${diagnostics.cadBusyEvents}", TextMuted, Modifier.weight(1f), compact = true)
                            DiagnosticCard("ACK Q", "${diagnostics.pendingAckDepth}", TextMuted, Modifier.weight(1f), compact = true)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagnosticCard("Rebroadcast Q", "${diagnostics.rebroadcastQueueDepth}", TextMuted, Modifier.weight(1f), compact = true)
                            DiagnosticCard(
                                if (appLanguage == "Spanish") "Silencio" else "Quiet",
                                if (diagnostics.quietMode) "ON" else "off",
                                if (diagnostics.quietMode) AccentMint else TextMuted,
                                Modifier.weight(1f),
                                compact = true
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Relayed ${diagnostics.relayedPackets}  ·  Retries ${diagnostics.retries}  ·  Airtime ${diagnostics.airtimeMs / 1000}s  ·  Up ${diagnostics.uptimeSeconds}s  ·  V${diagnostics.protocolVersion}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            if (appLanguage == "Spanish") "Actualizado: $ageLabel" else "Updated: $ageLabel",
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (diagnostics.rangePingsRx > 0L || diagnostics.rangePongsSent > 0L || diagnostics.quietMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                buildString {
                                    if (diagnostics.quietMode) {
                                        append(if (appLanguage == "Spanish") "Modo silencioso activo  ·  " else "Quiet mode active  ·  ")
                                    }
                                    append("Range RX ${diagnostics.rangePingsRx}")
                                    append("  ·  PONGs queued/sent/fail ${diagnostics.rangePongsQueued}/${diagnostics.rangePongsSent}/${diagnostics.rangePongTxFailures}")
                                },
                                color = if (diagnostics.quietMode) AccentMint else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = { exportMeshDiagnosticsToCsv(context, viewModel.getMeshDiagnosticsHistory()) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (appLanguage == "Spanish") "Exportar salud mesh CSV" else "Export mesh health CSV",
                                fontSize = 11.sp
                            )
                        }
                        HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))
                    } ?: Text(
                        if (appLanguage == "Spanish") "Esperando telemetría del mesh…" else "Waiting for mesh telemetry…",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    if (observedRoutes.isEmpty()) {
                        Text(
                            text = t("No routing paths observed yet.\nPaths are dynamically built as nodes transmit.", appLanguage),
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (appLanguage == "Spanish") "Destino" else "Target", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text(t("Next Hop", appLanguage), color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text(t("Hops", appLanguage), color = TextMuted, fontSize = 10.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            observedRoutes.values.forEach { route ->
                                val targetNode = nodes.find { it.nodeId == route.targetId || (it.nodeId and 0xFFFFL) == (route.targetId and 0xFFFFL) }
                                val nextHopNode = nodes.find { it.nodeId == route.nextHopId || (it.nodeId and 0xFFFFL) == (route.nextHopId and 0xFFFFL) }
                                val targetName = targetNode?.name ?: "Node ${String.format("%04X", (route.targetId and 0xFFFFL).toInt())}"
                                val nextHopName = nextHopNode?.name ?: "Node ${String.format("%04X", (route.nextHopId and 0xFFFFL).toInt())}"
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkBackground)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(targetName, color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(nextHopName, color = TextMuted, fontSize = 11.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SurfaceDark)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${route.hops} ${if (route.hops == 1) t("Hop", appLanguage) else t("Hops", appLanguage)}",
                                            color = AccentCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AetherSectionHeader(
                title = t("Signal Range Testing", appLanguage),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val rangeTestLogs by viewModel.rangeTestLogs.collectAsStateWithLifecycle()
            var rangeTestTargetDropdownExpanded by remember { mutableStateOf(false) }
            val rangeTargets = remember(nodes, viewModel.connectedNodeId) {
                nodes.filter { it.nodeId != viewModel.connectedNodeId }
            }
            var selectedRangeTargetNode by remember {
                val savedId = toolsPrefs.getLong("range_test_target_id", 0L)
                mutableStateOf(rangeTargets.find { it.nodeId == savedId })
            }
            var pingIntervalSec by remember {
                mutableFloatStateOf(toolsPrefs.getFloat("range_test_interval_sec", 5f).coerceIn(2f, 30f))
            }

            val preferredRangeTarget by viewModel.preferredRangeTestTargetId.collectAsStateWithLifecycle()
            LaunchedEffect(preferredRangeTarget) {
                viewModel.consumePreferredRangeTestTargetId()?.let { id ->
                    rangeTargets.find { it.nodeId == id }?.let { selectedRangeTargetNode = it }
                    toolsExpanded = true
                }
            }
            LaunchedEffect(rangeTargets) {
                if (selectedRangeTargetNode == null && rangeTargets.isNotEmpty()) {
                    selectedRangeTargetNode = rangeTargets.first()
                } else {
                    selectedRangeTargetNode?.let { sel ->
                        selectedRangeTargetNode = rangeTargets.find { it.nodeId == sel.nodeId } ?: rangeTargets.firstOrNull()
                    }
                }
            }
            LaunchedEffect(selectedRangeTargetNode?.nodeId) {
                selectedRangeTargetNode?.nodeId?.let { viewModel.loadRangeTestLogs(it) }
            }
            LaunchedEffect(pingIntervalSec) {
                toolsPrefs.edit().putFloat("range_test_interval_sec", pingIntervalSec).apply()
            }
            LaunchedEffect(selectedRangeTargetNode?.nodeId) {
                selectedRangeTargetNode?.nodeId?.let {
                    toolsPrefs.edit().putLong("range_test_target_id", it).apply()
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isConnected && !isDeviceAuthenticated) {
                        Text(
                            if (appLanguage == "Spanish")
                                "Autentica el dispositivo para usar la prueba de rango y el modo silencioso."
                            else
                                "Authenticate the device to use range test and quiet mode.",
                            color = AccentAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    if (!hasLocationPermission) {
                        Text(
                            if (appLanguage == "Spanish")
                                "Sin permiso de ubicación: distancia y GPS del CSV usarán la posición del nodo."
                            else
                                "Location permission off — distance/CSV will fall back to the node’s reported position.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Direct Signal Range Test",
                            color = TextLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRangeTestActive) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentMint)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ACTIVE", color = AccentMint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (!isRangeTestActive) {
                        // Configuration Panel
                        Text(
                            "One-hop only: repeaters are excluded so distance and signal describe the two selected nodes.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Target Node", color = TextMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .clickable { rangeTestTargetDropdownExpanded = true }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = selectedRangeTargetNode?.let { "${it.name} (0x${it.nodeId.toString(16).uppercase()})" } ?: "Select Node...",
                                color = TextLight,
                                fontSize = 14.sp
                            )
                        }
                        
                        DropdownMenu(
                            expanded = rangeTestTargetDropdownExpanded,
                            onDismissRequest = { rangeTestTargetDropdownExpanded = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            rangeTargets.forEach { node ->
                                DropdownMenuItem(
                                    text = { Text("${node.name} (0x${node.nodeId.toString(16).uppercase()})", color = TextLight) },
                                    onClick = {
                                        selectedRangeTargetNode = node
                                        rangeTestTargetDropdownExpanded = false
                                    }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Ping Interval: ${pingIntervalSec.toInt()} seconds", color = TextMuted, fontSize = 12.sp)
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
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                selectedRangeTargetNode?.let {
                                    viewModel.startRangeTest(it.nodeId, pingIntervalSec.toInt())
                                }
                            },
                            enabled = selectedRangeTargetNode != null && isDeviceAuthenticated,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Start Range Test", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                        if (!isDeviceAuthenticated) {
                            Text(
                                if (appLanguage == "Spanish") "Bloqueado hasta autenticar" else "Locked until authenticated",
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
                                if (appLanguage == "Spanish")
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
                        }
                    } else {
                        // Active range test statistics
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
                                Text("PINGS SENT", color = TextMuted, fontSize = 10.sp)
                                Text("$totalPings", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("REPLIES", color = TextMuted, fontSize = 10.sp)
                                Text("$successfulPings", color = AccentMint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("SUCCESS RATE", color = TextMuted, fontSize = 10.sp)
                                Text("$successRate%", color = if (successRate > 75) AccentMint else if (successRate > 40) Color(0xFFFBBF24) else Color(0xFFF87171), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

                        // Live distance to the target (phone GPS -> target's reported position),
                        // so field tests show how far the link is stretching right now.
                        run {
                            val target = selectedRangeTargetNode
                            val fix = viewModel.lastPhoneFix()
                            if (target != null && fix != null &&
                                hasValidPosition(target.latitude, target.longitude)
                            ) {
                                val km = calculateDistance(
                                    fix.latitude, fix.longitude,
                                    target.latitude.toDouble(), target.longitude.toDouble()
                                )
                                val useImperial = context
                                    .getSharedPreferences("aethermesh_prefs", android.content.Context.MODE_PRIVATE)
                                    .getBoolean("use_imperial_units", true)
                                val distStr = if (useImperial) {
                                    val miles = km * 0.621371
                                    if (miles < 0.2) "${(miles * 5280).toInt()} ft" else "%.2f mi".format(miles)
                                } else {
                                    if (km < 1.0) "${(km * 1000).toInt()} m" else "%.2f km".format(km)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Distance to target: $distStr",
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                                        if (appLanguage == "Spanish") "Respuesta (aquí)" else "Reply (here)",
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
                                            if (appLanguage == "Spanish") "Ping (en destino)" else "Ping (at target)",
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
                                Text("OK", color = TextMuted, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("✕", color = AccentRed, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Miss", color = TextMuted, fontSize = 10.sp)
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
                            // Y-axis labels
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
                                onClick = { exportRangeTestLogsToCsv(context, viewModel.getAllRangeTestLogs(), viewModel.nodes.value.associate { it.nodeId to (it.latitude.toDouble() to it.longitude.toDouble()) }) },
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
                                        selectedRangeTargetNode?.nodeId
                                    }
                                    if (targetId != null) {
                                        viewModel.clearRangeTestLogs(targetId)
                                    }
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
            }
            } // end authenticated tools
            } // end toolsExpanded
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
                            Text("Scanning…", color = Color(0xFF061018), fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF061018))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan for devices", color = Color(0xFF061018), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text(
            "Bluetooth only for now — TCP and USB coming later.",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 3. Bluetooth Scanner Header
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
                        Text("Stop", color = AccentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = AccentMint, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan", color = AccentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    .background(Color(0xFF422006))
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
                    color = Color(0xFFFDE68A),
                    fontSize = 12.sp
                )
                if (scanBlockReason == com.example.aethermesh.ble.BleScanBlockReason.PermissionDenied) {
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
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 4. Bluetooth Devices Scan List
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

fun formatUptime(seconds: Long): String {
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
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
    val colors = listOf(
        Color(0xFFF59E0B), // Orange/Amber
        Color(0xFF10B981), // Emerald/Mint
        Color(0xFF3B82F6), // Blue
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4), // Cyan
        Color(0xFF14B8A6)  // Teal
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


fun exportRangeTestLogsToCsv(
    context: Context,
    logs: List<com.example.aethermesh.data.RangeTestLog>,
    nodePositions: Map<Long, Pair<Double, Double>> = emptyMap()
) {
    if (logs.isEmpty()) {
        android.widget.Toast.makeText(context, "No range test data to export yet.", android.widget.Toast.LENGTH_SHORT).show()
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
        context.startActivity(android.content.Intent.createChooser(intent, "Export Range Test CSV"))
    } catch (e: Exception) {
        // Fall back to the clipboard if no app can take the file
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Range Test Logs", csv.toString()))
        android.widget.Toast.makeText(context, "Share failed (${e.message}); CSV copied to clipboard instead.", android.widget.Toast.LENGTH_LONG).show()
    }
}

fun exportMeshDiagnosticsToCsv(
    context: Context,
    snapshots: List<com.example.aethermesh.data.MeshDiagnosticsSnapshot>
) {
    if (snapshots.isEmpty()) {
        android.widget.Toast.makeText(context, "No mesh health data to export yet.", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val csv = StringBuilder(
        "timestamp_ms,tx_packets,tx_failures,rx_packets,relayed,retries,acked,ack_timeouts," +
            "duplicates,cad_busy,queue_drops,route_changes,active_routes,rebroadcast_depth," +
            "pending_ack_depth,airtime_ms,uptime_seconds,protocol_version," +
            "range_pings_rx,range_pongs_queued,range_pongs_sent,range_pong_tx_failures,quiet_mode\n"
    )
    snapshots.sortedBy { it.timestamp }.forEach { value ->
        csv.append(
            "${value.timestamp},${value.txPackets},${value.txFailures},${value.rxPackets}," +
                "${value.relayedPackets},${value.retries},${value.ackedPackets},${value.ackTimeouts}," +
                "${value.duplicatePackets},${value.cadBusyEvents},${value.queueDrops},${value.routeChanges}," +
                "${value.activeRoutes},${value.rebroadcastQueueDepth},${value.pendingAckDepth}," +
                "${value.airtimeMs},${value.uptimeSeconds},${value.protocolVersion}," +
                "${value.rangePingsRx},${value.rangePongsQueued},${value.rangePongsSent}," +
                "${value.rangePongTxFailures},${value.quietMode}\n"
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
        context.startActivity(android.content.Intent.createChooser(intent, "Export Mesh Health CSV"))
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Mesh Health", csv.toString()))
        android.widget.Toast.makeText(context, "Share failed; CSV copied to clipboard.", android.widget.Toast.LENGTH_LONG).show()
    }
}

fun exportAllPacketsToCsv(context: Context, messages: List<ChatMessage>) {
    val csv = StringBuilder("Timestamp,SenderId,RecipientId,Content,Channel,Status,Encrypted\n")
    messages.forEach {
        val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.timestamp))
        csv.append("\"$date\",0x${it.senderId.toString(16).uppercase()},0x${it.recipientId.toString(16).uppercase()},\"${it.content.replace("\"", "\"\"")}\",\"${it.channel}\",\"${it.status}\",${it.isEncrypted}\n")
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("All Messages CSV", csv.toString()))
    android.widget.Toast.makeText(context, "All messages exported to CSV and copied to clipboard!", android.widget.Toast.LENGTH_LONG).show()
}

fun exportBreadcrumbsToKml(
    context: Context,
    breadcrumbs: List<Pair<Double, Double>>,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    if (breadcrumbs.isEmpty()) {
        android.widget.Toast.makeText(
            context,
            if (spanish) "Aún no hay rastro GPS para exportar." else "No breadcrumbs to export yet.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
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
        context.startActivity(
            android.content.Intent.createChooser(
                intent,
                if (spanish) "Compartir rastro KML" else "Share KML Tracklog"
            )
        )
    } catch (e: java.lang.Exception) {
        android.widget.Toast.makeText(
            context,
            if (spanish) "Error al exportar: ${e.localizedMessage}" else "Export failed: ${e.localizedMessage}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
