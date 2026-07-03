package com.example.aethermesh.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aethermesh.AetherMeshApplication
import com.example.aethermesh.data.ChatMessage
import com.example.aethermesh.data.ChannelConfig
import com.example.aethermesh.data.MeshNode
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
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.DashPathEffect
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

// Styling palette. The five structural colors are theme-driven: they read from a
// Compose state so switching Theme (Dark/Light) recomposes every consumer without
// touching the ~200 call sites that reference these names. Accents stay constant.
private data class AetherPalette(
    val background: Color,
    val surface: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color
)

private val DarkPalette = AetherPalette(
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    border = Color(0xFF334155),
    textPrimary = Color(0xFFF8FAFC),
    textMuted = Color(0xFF94A3B8)
)

private val LightPalette = AetherPalette(
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    border = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF475569)
)

private var activePalette by mutableStateOf(DarkPalette)

// Called from the theme root when the effective light/dark mode changes.
fun setAetherPalette(dark: Boolean) {
    activePalette = if (dark) DarkPalette else LightPalette
}

val DarkBackground: Color get() = activePalette.background
val SurfaceDark: Color get() = activePalette.surface
val BorderDark: Color get() = activePalette.border
val TextLight: Color get() = activePalette.textPrimary
val TextMuted: Color get() = activePalette.textMuted
val AccentCyan = Color(0xFF22D3EE)
val AccentMint = Color(0xFF34D399)
val AccentRed = Color(0xFFEF4444)


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
        "Channels" -> "Canales"
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
    CHANNELS, RADIO, POSITION, SECURITY, PREFERENCES, DEVELOPER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as AetherMeshApplication
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(app.repository) }

    val isConnected by viewModel.isBleConnected.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(TabItem.CHATS) }

    val isDeviceAuthenticated by viewModel.isDeviceAuthenticated.collectAsStateWithLifecycle()
    val authenticationRequired by viewModel.authenticationRequired.collectAsStateWithLifecycle()

    var authPasswordInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf(false) }

    val sharedPrefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
    var appLanguage by remember { mutableStateOf(sharedPrefs.getString("app_language", "English") ?: "English") }
    var useImperialUnitsSetting by remember { mutableStateOf(sharedPrefs.getBoolean("use_imperial_units", true)) }
    var phoneLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val observedRoutes by viewModel.observedRoutes.collectAsStateWithLifecycle()

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val activeChatId by viewModel.activeChatId.collectAsStateWithLifecycle()

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

    val headerTitle = when (activeTab) {
        TabItem.CHATS -> "Chats"
        TabItem.NODES -> "Nodes"
        TabItem.MAP -> "Map"
        TabItem.SETTINGS -> "Settings"
        TabItem.CONNECTION -> "Connection"
    }
    
    val connectedNode = nodes.find { it.nodeId == viewModel.connectedNodeId }
    val connectedNodeName = connectedNode?.name ?: viewModel.connectedDeviceName

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            HeaderBar(
                title = headerTitle,
                isConnected = isConnected,
                connectedNodeName = connectedNodeName
            )

            // Content Area based on Tab Selection
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    TabItem.CHATS -> ChatView(
                        messages = messages,
                        channels = channels,
                        selectedChannel = selectedChannel,
                        localNodeId = viewModel.connectedNodeId,
                        activeChatId = activeChatId,
                        nodes = nodes,
                        onSelectChannel = { viewModel.selectChannel(it) },
                        onSelectDirectMessage = { viewModel.selectDirectMessage(it) },
                        onCreateChannel = { viewModel.createChannel(it) },
                        onSendMessage = { viewModel.sendMessage(it) },
                        getChatKey = { viewModel.getChatKey(it) },
                        saveChatKey = { key, valStr -> viewModel.saveChatKey(key, valStr) }
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
                        onRenameNode = { nodeId, longName, shortName ->
                            viewModel.updateNodeNameAndShortName(nodeId, longName, shortName)
                        }
                    )
                    TabItem.MAP -> MapViewCompose(
                        nodes = nodes,
                        viewModel = viewModel,
                        appLanguage = appLanguage,
                        useImperialUnits = useImperialUnitsSetting,
                        phoneLocation = phoneLocation,
                        onPhoneLocationChanged = { gp ->
                            phoneLocation = gp
                            if (sharedPrefs.getBoolean("enable_phone_gps_sharing", true)) {
                                viewModel.sharePhoneLocation(gp.latitude, gp.longitude)
                            }
                        },
                        onNavigateToChats = { activeTab = TabItem.CHATS }
                    )
                    TabItem.SETTINGS -> SettingsView(
                        viewModel = viewModel,
                        isConnected = isConnected
                    )
                    TabItem.CONNECTION -> ConnectionView(
                        viewModel = viewModel,
                        isConnected = isConnected,
                        nodes = nodes,
                        scannedDevices = scannedDevices
                    )
                }
            }

            // Bottom Navigation Bar
            AetherBottomNav(
                selectedTab = activeTab,
                onTabSelected = { activeTab = it },
                appLanguage = appLanguage
            )
        }

        // Overlay dialog for device password setting / authentication
        if (isConnected && !isDeviceAuthenticated && authenticationRequired != null) {
            val isFirstTime = authenticationRequired == false
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
                            text = if (isFirstTime) "Setup Device Password" else "Unlock Device",
                            color = TextLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = if (isFirstTime) {
                                "This node does not have a password configured. Please set a secure password for this device. The app will remember it for future connections."
                            } else {
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
                            label = { Text("Password", color = TextMuted) },
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
                                text = "Authentication failed. Incorrect password.",
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
                        Text(if (isFirstTime) "Set Password" else "Unlock")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.disconnect()
                        }
                    ) {
                        Text("Disconnect", color = TextMuted)
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
    connectedNodeName: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                color = TextLight,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge.copy(
                    brush = Brush.horizontalGradient(listOf(AccentCyan, AccentMint))
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) AccentMint else Color(0xFFEF4444))
            )
        }

        // Initials badge on the right if connected
        if (isConnected && !connectedNodeName.isNullOrBlank()) {
            val initials = getInitials(connectedNodeName)
            val badgeColor = getBadgeColor(connectedNodeName)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
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
    onSelectChannel: (String) -> Unit,
    onSelectDirectMessage: (Long) -> Unit,
    onCreateChannel: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    getChatKey: (String) -> String?,
    saveChatKey: (String, String) -> Unit
) {
    var textState by remember { mutableStateOf("") }
    var showNewChannelDialog by remember { mutableStateOf(false) }

    if (showNewChannelDialog) {
        NewChannelDialog(
            onCreate = {
                onCreateChannel(it)
                showNewChannelDialog = false
            },
            onDismiss = { showNewChannelDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Toggle tabs for Channels vs DMs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (activeChatId == null) AccentCyan else Color.Transparent)
                    .clickable { onSelectChannel(selectedChannel) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Channels",
                    color = if (activeChatId == null) DarkBackground else TextLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (activeChatId != null) AccentCyan else Color.Transparent)
                    .clickable {
                        val firstNode = nodes.firstOrNull()
                        if (firstNode != null) {
                            onSelectDirectMessage(firstNode.nodeId)
                        } else {
                            onSelectDirectMessage(0L)
                        }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Direct Messages",
                    color = if (activeChatId != null) DarkBackground else TextLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeChatId == null) {
            // Channel mode: Show Channel Chips Selector
            ChannelSelector(
                channels = channels,
                selectedChannel = selectedChannel,
                onSelectChannel = onSelectChannel,
                onAddChannel = { showNewChannelDialog = true }
            )
        } else {
            // DM Mode: Show Horizontal Contact List
            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No nodes discovered yet.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(nodes) { node ->
                        val isSelected = activeChatId == node.nodeId
                        val initials = getInitials(node.name)
                        val badgeColor = getBadgeColor(node.name)
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onSelectDirectMessage(node.nodeId) }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) AccentCyan else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = node.name.take(8),
                                color = if (isSelected) AccentCyan else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // E2EE Status Bar
        if (activeChatId == null || activeChatId != 0L) {
            val chatIdentifier = if (activeChatId == null) "CHANNEL_$selectedChannel" else "DM_$activeChatId"
            var passcode by remember(chatIdentifier) { mutableStateOf(getChatKey(chatIdentifier)) }
            var showPasscodeDialog by remember { mutableStateOf(false) }
            
            if (showPasscodeDialog) {
                PasscodeEntryDialog(
                    title = if (activeChatId == null) "Channel Key (#$selectedChannel)" else "Direct Key (Node 0x${activeChatId.toString(16).uppercase()})",
                    initialPasscode = passcode ?: "",
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
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
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (!passcode.isNullOrEmpty()) "End-to-End Encrypted (AES-256)" else "Cleartext Chat (Unencrypted)",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!passcode.isNullOrEmpty()) "Tap to change or clear chat key" else "Tap to set a channel passcode",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                if (!passcode.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentMint.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("SECURE", color = AccentMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Message list
        val selectedNode = nodes.find { it.nodeId == activeChatId }
        if (activeChatId != null && activeChatId == 0L) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No nodes available for private chat.\nWait for other nodes to broadcast telemetries.",
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    MessageBubble(message = message, localNodeId = localNodeId)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message input bar
        if (activeChatId == null || activeChatId != 0L) {
            val placeholderText = if (activeChatId == null) "Message #$selectedChannel..." else "Message ${selectedNode?.name ?: "Node 0x${activeChatId.toString(16).uppercase()}"}..."
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textState,
                    onValueChange = { textState = it },
                    placeholder = { Text(placeholderText, color = TextMuted) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        cursorColor = AccentCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (textState.trim().isNotEmpty()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = DarkBackground
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelSelector(
    channels: List<String>,
    selectedChannel: String,
    onSelectChannel: (String) -> Unit,
    onAddChannel: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(channels) { channel ->
            val isSelected = channel == selectedChannel
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) AccentCyan else SurfaceDark)
                    .clickable { onSelectChannel(channel) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "# $channel",
                    color = if (isSelected) DarkBackground else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .clickable { onAddChannel() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New channel",
                    tint = AccentMint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun NewChannelDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (name.trim().isNotEmpty()) onCreate(name.trim()) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text("Create", color = if (name.trim().isNotEmpty()) AccentMint else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        title = { Text("New Channel", color = TextLight, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
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
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        cursorColor = AccentCyan,
                        focusedIndicatorColor = AccentCyan,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun MessageBubble(message: ChatMessage, localNodeId: Long) {
    // A message is "ours" when its sender matches the locally connected node.
    val isMe = localNodeId != 0L && message.senderId == localNodeId

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isMe) 12.dp else 0.dp,
                        bottomEnd = if (isMe) 0.dp else 12.dp
                    )
                )
                .background(if (isMe) AccentCyan else SurfaceDark)
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = if (isMe) DarkBackground else TextLight,
                fontSize = 15.sp
            )
        }
        
        Text(
            text = "From: 0x${message.senderId.toString(16).uppercase()} | ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))}",
            color = TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun NodesView(
    nodes: List<MeshNode>,
    observedRoutes: Map<Long, com.example.aethermesh.data.RouteHopInfo>,
    phoneLocation: GeoPoint?,
    appLanguage: String,
    useImperialUnits: Boolean,
    onNodeClick: (Long) -> Unit,
    onRenameNode: (Long, String, String) -> Unit
) {
    var renamingNode by remember { mutableStateOf<MeshNode?>(null) }

    if (renamingNode != null) {
        val node = renamingNode!!
        var longName by remember { mutableStateOf(node.name) }
        var shortName by remember { mutableStateOf(node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }) }
        
        AlertDialog(
            onDismissRequest = { renamingNode = null },
            title = { Text(t("Rename Node", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t("Long Name (max 16 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = longName,
                        onValueChange = { if (it.length <= 16) longName = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
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
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameNode(node.nodeId, longName.trim(), shortName.trim())
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(t("Active Nodes Directory", appLanguage), color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (nodes.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(t("No nodes discovered yet. Waiting for telemetry...", appLanguage), color = TextMuted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(nodes) { node ->
                    NodeItem(
                        node = node,
                        observedRoutes = observedRoutes,
                        phoneLocation = phoneLocation,
                        appLanguage = appLanguage,
                        useImperialUnits = useImperialUnits,
                        onClick = { onNodeClick(node.nodeId) },
                        onRenameClick = { renamingNode = node }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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

@Composable
fun NodeItem(
    node: MeshNode,
    observedRoutes: Map<Long, com.example.aethermesh.data.RouteHopInfo>,
    phoneLocation: GeoPoint?,
    appLanguage: String,
    useImperialUnits: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    val initials = getInitials(node.name)
    val badgeColor = getBadgeColor(node.name)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Initials badge on left
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(node.name, color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "ID: 0x${node.nodeId.toString(16).uppercase()} | Model: ${node.model}",
                color = TextMuted,
                fontSize = 12.sp
            )
            
            // Distance & Bearing calculation
            if (phoneLocation != null && node.latitude != 0.0f && node.longitude != 0.0f) {
                val distanceKm = calculateDistance(
                    phoneLocation.latitude, phoneLocation.longitude,
                    node.latitude.toDouble(), node.longitude.toDouble()
                )
                val bearing = calculateBearing(
                    phoneLocation.latitude, phoneLocation.longitude,
                    node.latitude.toDouble(), node.longitude.toDouble()
                )
                val distStr = if (useImperialUnits) {
                    val distanceMiles = distanceKm * 0.621371
                    if (appLanguage == "Spanish") {
                        "%.2f mi al %s".format(distanceMiles, bearing)
                    } else {
                        "%.2f mi %s".format(distanceMiles, bearing)
                    }
                } else {
                    if (appLanguage == "Spanish") {
                        "%.2f km al %s".format(distanceKm, bearing)
                    } else {
                        "%.2f km %s".format(distanceKm, bearing)
                    }
                }
                Text(
                    text = distStr,
                    color = AccentMint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                "${t("Last Active", appLanguage)}: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(node.lastActive))}",
                color = AccentCyan,
                fontSize = 11.sp
            )
            if (node.uptimeSeconds > 0 || node.firmwareVersion.isNotEmpty()) {
                val parts = buildList {
                    if (node.firmwareVersion.isNotEmpty()) add("fw ${node.firmwareVersion}")
                    if (node.uptimeSeconds > 0) add("up ${formatUptime(node.uptimeSeconds)}")
                }
                Text(parts.joinToString("  •  "), color = TextMuted, fontSize = 10.sp)
            }
            
            val route = observedRoutes[node.nodeId]
            if (route != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(rssi = route.lastRssi)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RSSI: ${route.lastRssi.toInt()} dBm  •  SNR: ${"%.1f".format(route.lastSnr)} dB",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.isCharging) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Solar charging",
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Battery",
                    tint = AccentMint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("${node.battery}%", color = TextLight, fontSize = 14.sp)
            }
            Text("Lat: %.4f".format(node.latitude), color = TextMuted, fontSize = 10.sp)
            Text("Lon: %.4f".format(node.longitude), color = TextMuted, fontSize = 10.sp)
        }
    }
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
    viewModel: MainScreenViewModel,
    appLanguage: String,
    useImperialUnits: Boolean,
    phoneLocation: GeoPoint?,
    onPhoneLocationChanged: (GeoPoint) -> Unit,
    onNavigateToChats: () -> Unit
) {
    var hasCentered by remember { mutableStateOf(false) }
    var selectedMapNode by remember { mutableStateOf<MeshNode?>(null) }
    var showRemoteConfigDialog by remember { mutableStateOf<MeshNode?>(null) }
    val context = LocalContext.current
    val rangeTestLogs by viewModel.rangeTestLogs.collectAsStateWithLifecycle()
    val breadcrumbs = remember { mutableStateListOf<GeoPoint>() }

    // Remembered MapView to avoid reloading tiles on recomposition
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)

            // Add built-in "My Location" overlay with location change hook
            val myLocationOverlay = object : MyLocationNewOverlay(GpsMyLocationProvider(context), this) {
                override fun onLocationChanged(location: android.location.Location?, source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?) {
                    super.onLocationChanged(location, source)
                    val myLoc = myLocation
                    if (myLoc != null) {
                        post {
                            onPhoneLocationChanged(myLoc)
                            if (breadcrumbs.isEmpty() || calculateDistance(
                                    breadcrumbs.last().latitude, breadcrumbs.last().longitude,
                                    myLoc.latitude, myLoc.longitude
                                ) > 0.005
                            ) {
                                breadcrumbs.add(myLoc)
                                if (breadcrumbs.size > 200) {
                                    breadcrumbs.removeAt(0)
                                }
                            }
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
                            if (breadcrumbs.isEmpty()) {
                                breadcrumbs.add(myLoc)
                            }
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
        }
    }

    // Update overlays reactively whenever nodes, rangeTestLogs, phoneLocation, or breadcrumbs size changes
    LaunchedEffect(nodes, rangeTestLogs, phoneLocation, breadcrumbs.size) {
        val myLocationOverlays = mapView.overlays.filterIsInstance<MyLocationNewOverlay>()
        mapView.overlays.clear()
        mapView.overlays.addAll(myLocationOverlays)

        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                selectedMapNode = null
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        })
        mapView.overlays.add(0, mapEventsOverlay)

        // Draw Phone Breadcrumbs Trail (dotted blue line)
        if (breadcrumbs.size > 1) {
            val breadcrumbPolyline = Polyline(mapView).apply {
                outlinePaint.apply {
                    color = Color(0xFF3B82F6).copy(alpha = 0.7f).toArgb() // Beautiful royal blue
                    strokeWidth = 5f
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) // Dotted trail
                }
                setPoints(breadcrumbs.toList())
            }
            mapView.overlays.add(breadcrumbPolyline)
        }

        // 1. Draw mesh link polylines (dashed cyan lines) from the local node to remote nodes
        val localNode = nodes.find { it.nodeId == viewModel.connectedNodeId }
        if (localNode != null && localNode.latitude != 0.0f && localNode.longitude != 0.0f) {
            val localPoint = GeoPoint(localNode.latitude.toDouble(), localNode.longitude.toDouble())

            for (node in nodes) {
                if (node.nodeId != localNode.nodeId && node.latitude != 0.0f && node.longitude != 0.0f) {
                    val peerPoint = GeoPoint(node.latitude.toDouble(), node.longitude.toDouble())
                    val polyline = Polyline(mapView).apply {
                        outlinePaint.apply {
                            color = AccentCyan.copy(alpha = 0.6f).toArgb()
                            strokeWidth = 6f
                            strokeCap = Paint.Cap.ROUND
                            pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f) // Dashed line effect
                        }
                        setPoints(listOf(localPoint, peerPoint))
                    }
                    mapView.overlays.add(polyline)
                }
            }
        }

        // 2. Draw Range Test trace path segments (color-coded by RSSI)
        if (rangeTestLogs.isNotEmpty()) {
            val validLogs = rangeTestLogs.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            for (i in 0 until validLogs.size - 1) {
                val startLog = validLogs[i]
                val endLog = validLogs[i + 1]

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

            // Draw Range Test target node markers with sequence pins
            validLogs.forEachIndexed { index, log ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(log.latitude, log.longitude)
                    title = if (appLanguage == "Spanish") "Prueba #${index + 1}" else "Ping #${index + 1}"
                    subDescription = if (log.success) {
                        val pingSide = log.remoteRssi?.let { "Ping@target: $it dBm | " } ?: ""
                        val speedPart = log.speedMps?.let {
                            " | ${String.format(java.util.Locale.US, "%.0f", it * 2.237)} mph"
                        } ?: ""
                        "${pingSide}ACK: ${log.rssi} dBm / ${log.snr} dB$speedPart"
                    } else "TIMEOUT"
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
        }

        // 3. Draw custom initials-badge markers for each active node
        for (node in nodes) {
            if (node.latitude != 0.0f && node.longitude != 0.0f) {
                val color = getBadgeColor(node.name).toArgb()
                val density = context.resources.displayMetrics.density

                // Draw geographical precision circle polygon (natively scales with zoom level)
                val circle = Polygon(mapView).apply {
                    val center = GeoPoint(node.latitude.toDouble(), node.longitude.toDouble())
                    points = Polygon.pointsAsCircle(center, 350.0) // 350 meters radius
                    fillPaint.color = color
                    fillPaint.alpha = 32
                    fillPaint.style = Paint.Style.FILL
                    
                    outlinePaint.color = color
                    outlinePaint.alpha = 110
                    outlinePaint.style = Paint.Style.STROKE
                    outlinePaint.strokeWidth = 1.5f * density
                }
                mapView.overlays.add(circle)

                val marker = Marker(mapView).apply {
                    position = GeoPoint(node.latitude.toDouble(), node.longitude.toDouble())
                    title = node.name
                    subDescription = "Battery: ${node.battery}% | Model: ${node.model}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) // Center of the badge circle

                    val nodeShortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
                    val isNodeActive = (System.currentTimeMillis() - node.lastActive) < 300000L // Active if heard within 5 mins
                    icon = createBadgeMarkerDrawable(context, nodeShortName, color, isActive = isNodeActive, isPingMarker = false)

                    setOnMarkerClickListener { _, _ ->
                        selectedMapNode = node
                        true
                    }
                }
                mapView.overlays.add(marker)
            }
        }

        // Auto-center on first valid node if we haven't already centered
        if (!hasCentered && nodes.isNotEmpty()) {
            val validNode = nodes.firstOrNull { it.latitude != 0.0f && it.longitude != 0.0f }
            if (validNode != null) {
                mapView.controller.setCenter(GeoPoint(validNode.latitude.toDouble(), validNode.longitude.toDouble()))
                mapView.controller.setZoom(15.0)
                hasCentered = true
            }
        }

        mapView.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // expandable Heard (No GPS) Nodes overlay Card
        val noGpsNodes = nodes.filter { it.latitude == 0.0f || it.longitude == 0.0f }
        var showNoGpsNodesList by remember { mutableStateOf(false) }
        
        if (noGpsNodes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 16.dp)
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
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(getBadgeColor(node.name)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(getInitials(node.name), color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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

        // Floating Glassmorphic Map Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .width(44.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom In
            FloatingActionButton(
                onClick = { mapView.controller.zoomIn() },
                containerColor = SurfaceDark.copy(alpha = 0.85f),
                contentColor = AccentCyan,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
            }
            // Zoom Out
            FloatingActionButton(
                onClick = { mapView.controller.zoomOut() },
                containerColor = SurfaceDark.copy(alpha = 0.85f),
                contentColor = AccentCyan,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
            }
            // Center on Self
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
                containerColor = SurfaceDark.copy(alpha = 0.85f),
                contentColor = AccentCyan,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "My Location", modifier = Modifier.size(20.dp))
            }
            // Center on Mesh (Home)
            FloatingActionButton(
                onClick = {
                    val validNodes = nodes.filter { it.latitude != 0.0f && it.longitude != 0.0f }
                    if (validNodes.isNotEmpty()) {
                        val avgLat = validNodes.map { it.latitude.toDouble() }.average()
                        val avgLon = validNodes.map { it.longitude.toDouble() }.average()
                        mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
                        mapView.controller.setZoom(14.0)
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

        val activeMapNode = selectedMapNode?.let { sel ->
            nodes.find { it.nodeId == sel.nodeId }
        } ?: selectedMapNode
        activeMapNode?.let { node ->
            val nodeShortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp, start = 16.dp, end = 16.dp)
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(getBadgeColor(node.name)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(nodeShortName, color = Color.Black, fontSize = if (nodeShortName.length > 2) 9.sp else 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(node.name, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0x203B82F6))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(nodeShortName, color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text("0x${node.nodeId.toString(16).uppercase()}", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                            IconButton(
                                onClick = { selectedMapNode = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = BorderDark)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(if (appLanguage == "Spanish") "Modelo" else "Model", color = TextMuted, fontSize = 9.sp)
                                Text(node.model, color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (appLanguage == "Spanish") "Batería" else "Battery", color = TextMuted, fontSize = 9.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = AccentMint, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("${node.battery}%", color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (appLanguage == "Spanish") "Último Activo" else "Last Active", color = TextMuted, fontSize = 9.sp)
                                Text(
                                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(node.lastActive)),
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(if (appLanguage == "Spanish") "Ubicación" else "Location", color = TextMuted, fontSize = 9.sp)
                                Text("Lat: %.5f, Lon: %.5f".format(node.latitude, node.longitude), color = TextLight, fontSize = 11.sp)
                            }
                            
                            if (phoneLocation != null) {
                                val distanceKm = calculateDistance(
                                    phoneLocation.latitude, phoneLocation.longitude,
                                    node.latitude.toDouble(), node.longitude.toDouble()
                                )
                                val bearing = calculateBearing(
                                    phoneLocation.latitude, phoneLocation.longitude,
                                    node.latitude.toDouble(), node.longitude.toDouble()
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (appLanguage == "Spanish") "Distancia" else "Distance", color = TextMuted, fontSize = 9.sp)
                                    val distStr = if (useImperialUnits) {
                                        "%.2f mi %s".format(distanceKm * 0.621371, bearing)
                                    } else {
                                        "%.2f km %s".format(distanceKm, bearing)
                                    }
                                    Text(
                                        text = distStr,
                                        color = AccentMint,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val isLocalNode = node.nodeId == viewModel.connectedNodeId
                        if (isLocalNode) {
                            Button(
                                onClick = {
                                    viewModel.selectDirectMessage(node.nodeId)
                                    onNavigateToChats()
                                    selectedMapNode = null
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (appLanguage == "Spanish") "Enviar Mensaje" else "Send Message", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        viewModel.selectDirectMessage(node.nodeId)
                                        onNavigateToChats()
                                        selectedMapNode = null
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (appLanguage == "Spanish") "Enviar" else "Send Msg", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        showRemoteConfigDialog = node
                                        selectedMapNode = null
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextLight),
                                    border = BorderStroke(1.dp, BorderDark)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentMint)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (appLanguage == "Spanish") "Configurar" else "Remote Config", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRemoteConfigDialog != null) {
        val node = showRemoteConfigDialog!!
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

        AlertDialog(
            onDismissRequest = { showRemoteConfigDialog = null },
            title = { Text("Remote Node Configuration", color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Target: 0x${node.nodeId.toString(16).uppercase()}", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Custom Name", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = remoteName,
                        onValueChange = { if (it.length <= 16) remoteName = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Admin Password (Required)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = remotePassword,
                        onValueChange = { remotePassword = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Radio Profile", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    RadioProfileChips(remoteSF, remoteBW) { profile ->
                        remoteSF = profile.sf
                        remoteBW = profile.bw
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Telemetry Broadcast (Interval)", color = TextMuted, fontSize = 11.sp)
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

                    Text("Node Role", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Client" to 0, "Router" to 1, "Repeater" to 2).forEach { (label, value) ->
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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val success = viewModel.sendRemoteConfig(
                            nodeId = node.nodeId,
                            name = remoteName.trim(),
                            password = remotePassword.trim(),
                            sf = remoteSF,
                            bw = remoteBW,
                            txPower = remoteTxPower,
                            region = remoteRegion,
                            role = remoteRole,
                            telemetryInterval = remoteTelemetryInterval
                        )
                        if (success) {
                            android.widget.Toast.makeText(context, "Remote config dispatched!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Failed to dispatch config.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showRemoteConfigDialog = null
                    },
                    enabled = remotePassword.isNotEmpty()
                ) {
                    Text("Apply Settings", color = if (remotePassword.isNotEmpty()) AccentMint else TextMuted, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoteConfigDialog = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
fun DiagnosticsView(nodes: List<MeshNode>, messages: List<ChatMessage>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AetherMesh Diagnostic Console", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            DiagnosticCard(
                title = "Total Nodes",
                value = nodes.size.toString(),
                color = AccentCyan,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            DiagnosticCard(
                title = "Packets Heard",
                value = messages.size.toString(),
                color = AccentMint,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Network Telemetry Log", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .padding(12.dp)
        ) {
            item {
                Text("System initialized on US915.", color = AccentCyan, fontSize = 12.sp)
            }
            items(messages.takeLast(10)) { msg ->
                Text(
                    "Packet from 0x${msg.senderId.toString(16).uppercase()}: Msg size ${msg.content.length} bytes.",
                    color = TextLight,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun DiagnosticCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AetherBottomNav(
    selectedTab: TabItem,
    appLanguage: String,
    onTabSelected: (TabItem) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceDark,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == TabItem.CHATS,
            onClick = { onTabSelected(TabItem.CHATS) },
            icon = { Icon(imageVector = Icons.Default.Email, contentDescription = t("Chats", appLanguage)) },
            label = { Text(t("Chats", appLanguage)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentCyan,
                selectedTextColor = AccentCyan,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted,
                indicatorColor = BorderDark
            )
        )
        NavigationBarItem(
            selected = selectedTab == TabItem.NODES,
            onClick = { onTabSelected(TabItem.NODES) },
            icon = { Icon(imageVector = Icons.Default.Menu, contentDescription = t("Nodes", appLanguage)) },
            label = { Text(t("Nodes", appLanguage)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentCyan,
                selectedTextColor = AccentCyan,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted,
                indicatorColor = BorderDark
            )
        )
        NavigationBarItem(
            selected = selectedTab == TabItem.MAP,
            onClick = { onTabSelected(TabItem.MAP) },
            icon = { Icon(imageVector = Icons.Default.Place, contentDescription = t("Map", appLanguage)) },
            label = { Text(t("Map", appLanguage)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentCyan,
                selectedTextColor = AccentCyan,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted,
                indicatorColor = BorderDark
            )
        )
        NavigationBarItem(
            selected = selectedTab == TabItem.SETTINGS,
            onClick = { onTabSelected(TabItem.SETTINGS) },
            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = t("Settings", appLanguage)) },
            label = { Text(t("Settings", appLanguage)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentCyan,
                selectedTextColor = AccentCyan,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted,
                indicatorColor = BorderDark
            )
        )
        NavigationBarItem(
            selected = selectedTab == TabItem.CONNECTION,
            onClick = { onTabSelected(TabItem.CONNECTION) },
            icon = { Icon(imageVector = Icons.Default.Share, contentDescription = t("Connection", appLanguage)) },
            label = { Text(t("Connection", appLanguage)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentCyan,
                selectedTextColor = AccentCyan,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted,
                indicatorColor = BorderDark
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScanDialog(
    devices: List<BleDeviceItem>,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AccentCyan)
            }
        },
        title = {
            Text("Select AetherMesh Node", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                Text("Make sure your WisBlock or Heltec node is turned on and BLE is advertising.", color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (devices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(devices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onConnect(device.mac) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Device",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(device.mac, color = TextMuted, fontSize = 12.sp)
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Connect",
                                    tint = AccentMint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            HorizontalDivider(color = BorderDark)
                        }
                    }
                }
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
    onDismiss: () -> Unit
) {
    var keyState by remember { mutableStateOf(initialPasscode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(keyState.trim()) }
            ) {
                Text("Save", color = AccentMint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        title = { Text(title, color = TextLight, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "All messages in this chat will be encrypted and decrypted using AES-256 with the key below. Keep this key secret and share it off-grid with other participants.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = keyState,
                    onValueChange = { keyState = it },
                    singleLine = true,
                    placeholder = { Text("Enter passcode (e.g. secret123)", color = TextMuted) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        cursorColor = AccentCyan,
                        focusedIndicatorColor = AccentCyan,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (keyState.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
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
    val authenticationRequired by viewModel.authenticationRequired.collectAsStateWithLifecycle()
    var authPasswordInput by remember { mutableStateOf("") }
    var authPasswordError by remember { mutableStateOf(false) }

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

    var isExpandedSF by remember { mutableStateOf(false) }
    var isExpandedBW by remember { mutableStateOf(false) }
    var isExpandedRegion by remember { mutableStateOf(false) }
    var isExpandedRole by remember { mutableStateOf(false) }
    var isExpandedTelemetry by remember { mutableStateOf(false) }
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

    // Only reset transient auth state when the connected node actually changes
    LaunchedEffect(viewModel.connectedNodeId) {
        authPasswordInput = ""
        authPasswordError = false
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
            nodeName = matchedNode?.name?.replace("AetherMesh-", "")?.replace("Node ", "") ?: nodePrefs.getString("node_name", "") ?: ""
            nodeShortName = matchedNode?.shortName ?: nodePrefs.getString("node_short_name", "") ?: ""
            sf = nodePrefs.getInt("lora_sf", 9)
            bw = nodePrefs.getFloat("lora_bw", 125f)
            txPower = nodePrefs.getInt("lora_tx_power", 22)
            region = nodePrefs.getInt("region", 0)
            role = nodePrefs.getInt("node_role", 0)
            telemetryIntervalSecs = nodePrefs.getInt("telemetry_interval", 60)
            screenTimeoutSecs = nodePrefs.getInt("screen_timeout", 30)
            powerSaveModeEnabled = nodePrefs.getBoolean("power_save_mode", false)
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
            powerSaveMode = powerSaveModeEnabled
        )
        if (success) {
            val nodeKey = viewModel.connectedNodeId
            if (nodeKey != 0L) {
                val nodePrefs = context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
                nodePrefs.edit().apply {
                    putString("node_name", nodeName)
                    putString("node_short_name", nodeShortName)
                    putInt("lora_sf", sf)
                    putFloat("lora_bw", bw)
                    putInt("lora_tx_power", txPower)
                    putInt("region", region)
                    putInt("node_role", role)
                    putInt("telemetry_interval", telemetryIntervalSecs)
                    putInt("screen_timeout", screenTimeoutSecs)
                    putBoolean("power_save_mode", powerSaveModeEnabled)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AETHERMESH",
                                color = TextLight,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = t("System Configuration Panel", appLanguage),
                            color = AccentMint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = t("Select a settings category below to manage your device.", appLanguage),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Categories Menu List
            val categories = listOf(
                Triple(SettingsCategory.CHANNELS, "Channels", "Manage secondary channels and share/join links"),
                Triple(SettingsCategory.RADIO, "LoRa Radio Configuration", "Set spreading factor, bandwidth, power, and region"),
                Triple(SettingsCategory.POSITION, "GPS & Position Settings", "Configure GPS enable, telemetry interval, and view satellite lock status"),
                Triple(SettingsCategory.SECURITY, "Security & Keys", "Manage private keys, ECDH keypairs, and device password"),
                Triple(SettingsCategory.PREFERENCES, "App Preferences", "Set language, theme, and background alerts"),
                Triple(SettingsCategory.DEVELOPER, "Developer & Diagnostics", "Live logs console, packet exports, and system database reset")
            )

            categories.forEach { (cat, title, desc) ->
                val icon = when(cat) {
                    SettingsCategory.CHANNELS -> Icons.Default.Layers
                    SettingsCategory.RADIO -> Icons.Default.Settings
                    SettingsCategory.POSITION -> Icons.Default.Place
                    SettingsCategory.SECURITY -> Icons.Default.Lock
                    SettingsCategory.PREFERENCES -> Icons.Default.Palette
                    SettingsCategory.DEVELOPER -> Icons.Default.Terminal
                }
                val iconColor = when(cat) {
                    SettingsCategory.CHANNELS -> AccentCyan
                    SettingsCategory.RADIO -> AccentMint
                    SettingsCategory.POSITION -> Color(0xFF818CF8)
                    SettingsCategory.SECURITY -> Color(0xFFEF4444)
                    SettingsCategory.PREFERENCES -> Color(0xFFFBBF24)
                    SettingsCategory.DEVELOPER -> AccentCyan
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { activeCategory = cat }
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
                                .background(iconColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = t(title, appLanguage), color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(text = t(desc, appLanguage), color = TextMuted, fontSize = 11.sp)
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
                    imageVector = Icons.Default.ArrowBack,
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
            val currentRegion = remember<Int>(viewModel.connectedNodeId) {
                val nodeKey = java.lang.Long.toHexString(viewModel.connectedNodeId).uppercase()
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
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
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
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
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

                    val hasLock = connectedNode != null && connectedNode.latitude != 0.0f && connectedNode.longitude != 0.0f
                    
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
                        exportRangeTestLogsToCsv(context, viewModel.getAllRangeTestLogs())
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
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
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
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
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
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AccentCyan,
                            focusedIndicatorColor = AccentCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = SurfaceDark
        )
    }

    // --- INITIAL CONNECTION AUTHENTICATION OVERLAY ---
    if (isConnected && !isDeviceAuthenticated) {
        if (authenticationRequired == true) {
            AlertDialog(
                onDismissRequest = { /* Force authentication, do not dismiss */ },
                title = { Text(if (appLanguage == "Spanish") "Autenticación del Nodo" else "Node Authentication", color = TextLight, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = if (appLanguage == "Spanish") 
                                "Este nodo está protegido. Ingrese la contraseña del dispositivo." 
                                else "This node is password-protected. Please enter the device password to connect.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = authPasswordInput,
                            onValueChange = {
                                authPasswordInput = it
                                authPasswordError = false
                            },
                            label = { Text(if (appLanguage == "Spanish") "Contraseña" else "Password", color = TextMuted) },
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
                        if (authPasswordError) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (appLanguage == "Spanish") "Contraseña incorrecta." else "Incorrect password.",
                                color = AccentRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val success = viewModel.sendAuthRequest(authPasswordInput.trim())
                            if (!success) {
                                authPasswordError = true
                            }
                        }
                    ) {
                        Text(if (appLanguage == "Spanish") "Ingresar" else "Unlock", color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.disconnect()
                        }
                    ) {
                        Text(if (appLanguage == "Spanish") "Desconectar" else "Disconnect", color = TextMuted)
                    }
                },
                containerColor = SurfaceDark
            )
        } else if (authenticationRequired == false) {
            AlertDialog(
                onDismissRequest = { /* Force initial setup, do not dismiss */ },
                title = { Text(if (appLanguage == "Spanish") "Establecer Contraseña" else "Set Node Password", color = TextLight, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Este es el primer inicio del nodo. Establezca una contraseña para proteger la red."
                                else "This is the node's first connection. Please set a password to secure your device.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = authPasswordInput,
                            onValueChange = {
                                authPasswordInput = it
                                authPasswordError = false
                            },
                            label = { Text(if (appLanguage == "Spanish") "Contraseña Nueva" else "New Password", color = TextMuted) },
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
                        if (authPasswordError) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (appLanguage == "Spanish") "La contraseña no puede estar vacía." else "Password cannot be empty.",
                                color = AccentRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (authPasswordInput.trim().isNotEmpty()) {
                                viewModel.sendAuthRequest(authPasswordInput.trim())
                            } else {
                                authPasswordError = true
                            }
                        }
                    ) {
                        Text(if (appLanguage == "Spanish") "Establecer" else "Set Password", color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.disconnect()
                        }
                    ) {
                        Text(if (appLanguage == "Spanish") "Desconectar" else "Disconnect", color = TextMuted)
                    }
                },
                containerColor = SurfaceDark
            )
        }
    }
}


@Composable
fun ConnectionView(
    viewModel: MainScreenViewModel,
    isConnected: Boolean,
    nodes: List<MeshNode>,
    scannedDevices: List<BleDeviceItem>
) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val connectedNode = nodes.find { it.nodeId == viewModel.connectedNodeId }
    val displayName = connectedNode?.name ?: viewModel.connectedDeviceName ?: "Wolf Base"
    val initials = getInitials(displayName)
    val badgeColor = getBadgeColor(displayName)
    val batteryVal = connectedNode?.battery ?: 98
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Connection Status", color = TextLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Connected Node Card
        if (isConnected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // PWR and RSSI row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = AccentMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PWR ${batteryVal}%", color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("RSSI -78 dBm", color = AccentMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Initials Badge and Name/Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(displayName, color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Firmware Version: 1.2.0-b532da",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Disconnect button
                    Button(
                        onClick = { viewModel.disconnectDevice() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Disconnect", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // 1.5 Mesh Routing Diagnostics Card (only visible when connected)
            val observedRoutes by viewModel.observedRoutes.collectAsStateWithLifecycle()
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mesh Routing Diagnostics",
                        color = TextLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (observedRoutes.isEmpty()) {
                        Text(
                            text = "No routing paths observed yet.\nPaths are dynamically built as nodes transmit.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
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
                                    Column {
                                        Text(targetName, color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "Next Hop: $nextHopName",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SurfaceDark)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${route.hops} Hop${if (route.hops > 1) "s" else ""}",
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

            // 1.6 LoRa Range Test Card (only visible when connected)
            val isRangeTestActive by viewModel.isRangeTestActive.collectAsStateWithLifecycle()
            val rangeTestLogs by viewModel.rangeTestLogs.collectAsStateWithLifecycle()
            var rangeTestTargetDropdownExpanded by remember { mutableStateOf(false) }
            var selectedRangeTargetNode by remember { mutableStateOf<MeshNode?>(null) }
            var pingIntervalSec by remember { mutableFloatStateOf(5f) }
            
            // Auto-select target if null
            if (selectedRangeTargetNode == null && nodes.isNotEmpty()) {
                selectedRangeTargetNode = nodes.firstOrNull { it.nodeId != viewModel.connectedNodeId }
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Signal Range Testing",
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
                                        .background(Color(0xFFEF4444)) // Pulse red
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ACTIVE", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (!isRangeTestActive) {
                        // Configuration Panel
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
                            nodes.filter { it.nodeId != viewModel.connectedNodeId }.forEach { node ->
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
                            enabled = selectedRangeTargetNode != null,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Start Range Test", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Active range test statistics
                        val totalPings = rangeTestLogs.size
                        val successfulPings = rangeTestLogs.count { it.success }
                        val successRate = if (totalPings > 0) (successfulPings * 100 / totalPings) else 0
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("PINGS SENT", color = TextMuted, fontSize = 10.sp)
                                Text("$totalPings", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("ACKs RECVD", color = TextMuted, fontSize = 10.sp)
                                Text("$successfulPings", color = AccentMint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("SUCCESS RATE", color = TextMuted, fontSize = 10.sp)
                                Text("$successRate%", color = if (successRate > 75) AccentMint else if (successRate > 40) Color(0xFFFBBF24) else Color(0xFFF87171), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Draw Mini Signal Chart using Canvas
                        Text("RSSI Signal Level History (dBm)", color = TextMuted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .padding(8.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                
                                // Draw grid line at -100 dBm
                                val thresholdY = height * ((-100f - (-40f)) / (-140f - (-40f)))
                                drawLine(
                                    color = Color(0xFF334155),
                                    start = androidx.compose.ui.geometry.Offset(0f, thresholdY),
                                    end = androidx.compose.ui.geometry.Offset(width, thresholdY),
                                    strokeWidth = 1f
                                )
                                
                                if (rangeTestLogs.isNotEmpty()) {
                                    val points = rangeTestLogs.takeLast(15)
                                    val stepX = if (points.size > 1) width / (points.size - 1) else width
                                    
                                    var lastPointX = 0f
                                    var lastPointY = 0f
                                    
                                    points.forEachIndexed { index, log ->
                                        val x = index * stepX
                                        // RSSI range: -40 (top) to -140 (bottom)
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
                                            // Timeout red cross
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
                                Text("Stop Test", color = TextLight, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { exportRangeTestLogsToCsv(context, viewModel.getAllRangeTestLogs()) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF164E63)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Export CSV", color = AccentCyan, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { selectedRangeTargetNode?.let { viewModel.clearRangeTestLogs(it.nodeId) } },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF451a1a)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Clear Logs", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                            }
                        }
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Node Connected", color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Pair a Bluetooth device below to get started.", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // 2. Connection Method Toggles
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("BLE", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    android.widget.Toast.makeText(context, "TCP connection mode coming soon!", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(40.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(BorderDark, BorderDark)))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("TCP", color = TextMuted, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    android.widget.Toast.makeText(context, "USB connection mode coming soon!", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(40.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(BorderDark, BorderDark)))
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("USB", color = TextMuted, fontSize = 12.sp)
            }
        }

        // 3. Bluetooth Scanner Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bluetooth Devices", color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(
                onClick = {
                    if (isScanning) viewModel.stopScanning() else viewModel.startScanning()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isScanning) {
                        CircularProgressIndicator(color = AccentMint, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scanning...", color = AccentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = AccentMint, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan", color = AccentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Bluetooth Devices Scan List
        if (scannedDevices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Searching for AetherMesh nodes..." else "No AetherMesh nodes found.\nTap Scan to search.",
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scannedDevices.forEach { device ->
                    val isThisConnected = isConnected && viewModel.connectedDeviceName == device.name
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
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = if (isThisConnected) AccentMint else TextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(device.mac, color = TextMuted, fontSize = 12.sp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPortalView(
    portalName: String,
    onBack: () -> Unit,
    context: Context,
    sharedPrefs: android.content.SharedPreferences,
    viewModel: MainScreenViewModel,
    connectedNode: MeshNode?,
    telemetryIntervalSecs: Int,
    onTelemetryIntervalChanged: (Int) -> Unit
) {
    val nodeKey = connectedNode?.nodeId ?: 0L
    val nodePrefs = remember(nodeKey) { context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE) }
    
    // Sub-states per category (initialized with saved values or defaults)
    // 1. Device Portal State
    var deviceRole by remember { mutableIntStateOf(nodePrefs.getInt("lora_role", 0)) }
    var rebroadcastMode by remember { mutableStateOf(nodePrefs.getString("rebroadcast_mode", "LOCAL_ONLY") ?: "LOCAL_ONLY") }
    var nodeInfoInterval by remember { mutableStateOf(nodePrefs.getString("node_info_interval", "3 hours") ?: "3 hours") }
    var doubleTapButton by remember { mutableStateOf(nodePrefs.getBoolean("double_tap_button", false)) }
    var tripleClickPing by remember { mutableStateOf(nodePrefs.getBoolean("triple_click_ping", true)) }
    var buttonGpio by remember { mutableStateOf(nodePrefs.getString("button_gpio", "0") ?: "0") }
    var buzzerGpio by remember { mutableStateOf(nodePrefs.getString("buzzer_gpio", "21") ?: "21") }

    // 2. Position Portal State
    var posInterval by remember { mutableStateOf(nodePrefs.getString("pos_interval", "1 hour") ?: "1 hour") }
    var smartPosition by remember { mutableStateOf(nodePrefs.getBoolean("smart_position", true)) }
    var smartInterval by remember { mutableStateOf(nodePrefs.getString("smart_interval", "5 minutes") ?: "5 minutes") }
    var smartDistance by remember { mutableStateOf(nodePrefs.getString("smart_distance", "100") ?: "100") }
    var gpsMode by remember { mutableStateOf(nodePrefs.getString("gps_mode", "ENABLED") ?: "ENABLED") }
    var fixedPosition by remember { mutableStateOf(nodePrefs.getBoolean("fixed_position", false)) }

    // 3. Power Portal State
    var powerSaveMode by remember { mutableStateOf(nodePrefs.getBoolean("power_save_mode", false)) }
    var shutdownPowerLoss by remember { mutableStateOf(nodePrefs.getString("shutdown_power_loss", "Unset") ?: "Unset") }
    var waitBluetoothDuration by remember { mutableStateOf(nodePrefs.getString("wait_ble_duration", "1 minute") ?: "1 minute") }
    var superDeepSleepDuration by remember { mutableStateOf(nodePrefs.getString("deep_sleep_duration", "Unset") ?: "Unset") }
    var minWakeTime by remember { mutableStateOf(nodePrefs.getString("min_wake_time", "10 seconds") ?: "10 seconds") }

    // 4. Network Portal State
    var wifiEnabled by remember { mutableStateOf(nodePrefs.getBoolean("wifi_enabled", false)) }
    var ethernetEnabled by remember { mutableStateOf(nodePrefs.getBoolean("ethernet_enabled", false)) }
    var ntpServer by remember { mutableStateOf(nodePrefs.getString("ntp_server", "meshtastic.pool.ntp.org") ?: "meshtastic.pool.ntp.org") }
    var udpBroadcasting by remember { mutableStateOf(nodePrefs.getBoolean("udp_broadcasting", false)) }
    var ipv4Mode by remember { mutableStateOf(nodePrefs.getString("ipv4_mode", "DHCP") ?: "DHCP") }

    // 5. Display Portal State
    var carouselInterval by remember { mutableStateOf(nodePrefs.getString("carousel_interval", "Unset") ?: "Unset") }
    var wakeOnMotion by remember { mutableStateOf(nodePrefs.getBoolean("wake_on_motion", true)) }
    var flipScreen by remember { mutableStateOf(nodePrefs.getBoolean("flip_screen", false)) }
    var alwaysPointNorth by remember { mutableStateOf(nodePrefs.getBoolean("always_point_north", false)) }
    var use12hClock by remember { mutableStateOf(nodePrefs.getBoolean("use_12h_clock", true)) }
    var boldHeading by remember { mutableStateOf(nodePrefs.getBoolean("bold_heading", false)) }
    var displayUnits by remember { mutableStateOf(nodePrefs.getString("display_units", "IMPERIAL") ?: "IMPERIAL") }
    var screenOnFor by remember { mutableStateOf(nodePrefs.getString("screen_on_for", "10 minutes") ?: "10 minutes") }

    // 6. Bluetooth Portal State
    var bleEnabled by remember { mutableStateOf(nodePrefs.getBoolean("ble_enabled", true)) }
    var blePincodeAuth by remember { mutableStateOf(nodePrefs.getBoolean("ble_pincode_auth", true)) }
    var bleTxPower by remember { mutableStateOf(nodePrefs.getString("ble_tx_power", "High") ?: "High") }

    // Dropdown expansion triggers
    var isExpandedRole by remember { mutableStateOf(false) }
    var isExpandedRebroadcast by remember { mutableStateOf(false) }
    var isExpandedInfoInterval by remember { mutableStateOf(false) }
    var isExpandedPosInterval by remember { mutableStateOf(false) }
    var isExpandedSmartInterval by remember { mutableStateOf(false) }
    var isExpandedGpsMode by remember { mutableStateOf(false) }
    var isExpandedShutdown by remember { mutableStateOf(false) }
    var isExpandedBleDur by remember { mutableStateOf(false) }
    var isExpandedDeepSleep by remember { mutableStateOf(false) }
    var isExpandedMinWake by remember { mutableStateOf(false) }
    var isExpandedIpv4 by remember { mutableStateOf(false) }
    var isExpandedCarousel by remember { mutableStateOf(false) }
    var isExpandedUnits by remember { mutableStateOf(false) }
    var isExpandedScreenOn by remember { mutableStateOf(false) }
    var isExpandedBlePower by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Sub-portal Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentCyan)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (portalName) {
                    "DEVICE" -> "Device configuration"
                    "POSITION" -> "Position configuration"
                    "POWER" -> "Power configuration"
                    "NETWORK" -> "Network configuration"
                    "DISPLAY" -> "Display configuration"
                    else -> "Bluetooth configuration"
                },
                color = TextLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (portalName) {
                    "DEVICE" -> {
                        Text("Options", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Device Role Dropdown
                        Text("Device Role", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isExpandedRole = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight),
                                border = ButtonDefaults.outlinedButtonBorder
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = when(deviceRole) {
                                        0 -> "CLIENT"
                                        1 -> "ROUTER"
                                        2 -> "LOW_POWER_REPEATER"
                                        else -> "CLIENT_BASE"
                                    })
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedRole, onDismissRequest = { isExpandedRole = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("CLIENT", "ROUTER", "LOW_POWER_REPEATER", "CLIENT_BASE").forEachIndexed { idx, name ->
                                    DropdownMenuItem(text = { Text(name, color = TextLight) }, onClick = { deviceRole = idx; isExpandedRole = false })
                                }
                            }
                        }
                        Text("Treats packets from or to favorited nodes as ROUTER_LATE, and all other packets as CLIENT.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        // Rebroadcast Mode Dropdown
                        Text("Rebroadcast Mode", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedRebroadcast = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(rebroadcastMode)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedRebroadcast, onDismissRequest = { isExpandedRebroadcast = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("ALL", "LOCAL_ONLY", "NONE").forEach { mode ->
                                    DropdownMenuItem(text = { Text(mode, color = TextLight) }, onClick = { rebroadcastMode = mode; isExpandedRebroadcast = false })
                                }
                            }
                        }
                        Text("Ignores observed messages from foreign meshes that are open or those which it cannot decrypt. Only rebroadcasts message on the nodes local primary / secondary channels.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        // Node Info Interval
                        Text("Node Info Broadcast Interval", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedInfoInterval = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(nodeInfoInterval)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedInfoInterval, onDismissRequest = { isExpandedInfoInterval = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("1 hour", "3 hours", "6 hours", "12 hours").forEach { interval ->
                                    DropdownMenuItem(text = { Text(interval, color = TextLight) }, onClick = { nodeInfoInterval = interval; isExpandedInfoInterval = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Hardware", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Switches
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Double Tap as Button", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Treat double tap on supported accelerometers as a user button press.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = doubleTapButton, onCheckedChange = { doubleTapButton = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Triple Click Ad Hoc Ping", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Send a position on the primary channel when the user button is triple clicked.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = tripleClickPing, onCheckedChange = { tripleClickPing = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("GPIO Settings", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Button GPIO", color = TextLight, fontSize = 13.sp)
                        TextField(value = buttonGpio, onValueChange = { buttonGpio = it }, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground, focusedTextColor = TextLight, unfocusedTextColor = TextLight), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Buzzer GPIO", color = TextLight, fontSize = 13.sp)
                        TextField(value = buzzerGpio, onValueChange = { buzzerGpio = it }, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground, focusedTextColor = TextLight, unfocusedTextColor = TextLight), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
                    }
                    
                    "POSITION" -> {
                        Text("Position Packet", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Broadcast Interval
                        Text("Broadcast Interval", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedPosInterval = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(posInterval)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedPosInterval, onDismissRequest = { isExpandedPosInterval = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("15 mins", "30 mins", "1 hour", "2 hours").forEach { interval ->
                                    DropdownMenuItem(text = { Text(interval, color = TextLight) }, onClick = { posInterval = interval; isExpandedPosInterval = false })
                                }
                            }
                        }
                        Text("The maximum interval that can elapse without a node broadcasting a position.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        // Smart Position
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Smart Position", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Vary broadcast rate dynamically depending on motion thresholds.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = smartPosition, onCheckedChange = { smartPosition = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        if (smartPosition) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Smart Interval", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { isExpandedSmartInterval = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(smartInterval)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                    }
                                }
                                DropdownMenu(expanded = isExpandedSmartInterval, onDismissRequest = { isExpandedSmartInterval = false }, modifier = Modifier.background(SurfaceDark)) {
                                    listOf("1 minute", "5 minutes", "15 minutes").forEach { interval ->
                                        DropdownMenuItem(text = { Text(interval, color = TextLight) }, onClick = { smartInterval = interval; isExpandedSmartInterval = false })
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Smart Distance (meters)", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            TextField(value = smartDistance, onValueChange = { smartDistance = it }, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground, focusedTextColor = TextLight, unfocusedTextColor = TextLight), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
                            Text("The minimum distance change in meters to be considered for a smart position broadcast.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Device GPS Settings", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fixed Position", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Configure static stationary beacon mode.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = fixedPosition, onCheckedChange = { fixedPosition = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("GPS Mode (Physical Hardware)", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedGpsMode = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(gpsMode)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedGpsMode, onDismissRequest = { isExpandedGpsMode = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("ENABLED", "DISABLED", "NOT_PRESENT").forEach { mode ->
                                    DropdownMenuItem(text = { Text(mode, color = TextLight) }, onClick = { gpsMode = mode; isExpandedGpsMode = false })
                                }
                            }
                        }
                    }

                    "POWER" -> {
                        Text("Power Config", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Power saving mode
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enable power saving mode", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Will sleep everything as much as possible, for the tracker and sensor role this will also include the lora radio.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = powerSaveMode, onCheckedChange = { powerSaveMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Shutdown on power loss
                        Text("Shutdown on power loss", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedShutdown = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(shutdownPowerLoss)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedShutdown, onDismissRequest = { isExpandedShutdown = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("Unset", "1 minute", "5 minutes", "15 minutes").forEach { duration ->
                                    DropdownMenuItem(text = { Text(duration, color = TextLight) }, onClick = { shutdownPowerLoss = duration; isExpandedShutdown = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Wait for bluetooth duration
                        Text("Wait for Bluetooth duration", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedBleDur = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(waitBluetoothDuration)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedBleDur, onDismissRequest = { isExpandedBleDur = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("1 minute", "5 minutes", "10 minutes", "30 minutes").forEach { duration ->
                                    DropdownMenuItem(text = { Text(duration, color = TextLight) }, onClick = { waitBluetoothDuration = duration; isExpandedBleDur = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Super deep sleep
                        Text("Super deep sleep duration", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedDeepSleep = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(superDeepSleepDuration)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedDeepSleep, onDismissRequest = { isExpandedDeepSleep = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("Unset", "30 minutes", "1 hour", "3 hours", "6 hours").forEach { duration ->
                                    DropdownMenuItem(text = { Text(duration, color = TextLight) }, onClick = { superDeepSleepDuration = duration; isExpandedDeepSleep = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Minimum wake time
                        Text("Minimum wake time", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedMinWake = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(minWakeTime)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedMinWake, onDismissRequest = { isExpandedMinWake = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("10 seconds", "30 seconds", "1 minute", "5 minutes").forEach { duration ->
                                    DropdownMenuItem(text = { Text(duration, color = TextLight) }, onClick = { minWakeTime = duration; isExpandedMinWake = false })
                                }
                            }
                        }
                    }

                    "NETWORK" -> {
                        Text("Network Settings", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        // WiFi enabled
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("WiFi enabled", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Enabling WiFi will disable the bluetooth connection to the app.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = wifiEnabled, onCheckedChange = { wifiEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Ethernet enabled
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ethernet enabled", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Enabling Ethernet will disable the bluetooth connection to the app.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = ethernetEnabled, onCheckedChange = { ethernetEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Ethernet Options", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("NTP Server", color = TextLight, fontSize = 13.sp)
                        TextField(value = ntpServer, onValueChange = { ntpServer = it }, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground, focusedTextColor = TextLight, unfocusedTextColor = TextLight), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("UDP Broadcasting", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Enable broadcasting packets via UDP over the local network.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = udpBroadcasting, onCheckedChange = { udpBroadcasting = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("IPv4 Mode", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedIpv4 = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(ipv4Mode)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedIpv4, onDismissRequest = { isExpandedIpv4 = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("DHCP", "STATIC").forEach { mode ->
                                    DropdownMenuItem(text = { Text(mode, color = TextLight) }, onClick = { ipv4Mode = mode; isExpandedIpv4 = false })
                                }
                            }
                        }
                    }

                    "DISPLAY" -> {
                        Text("Device Display Options", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Carousel interval
                        Text("Carousel interval", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedCarousel = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(carouselInterval)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedCarousel, onDismissRequest = { isExpandedCarousel = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("Unset", "5 seconds", "10 seconds", "20 seconds").forEach { duration ->
                                    DropdownMenuItem(text = { Text(duration, color = TextLight) }, onClick = { carouselInterval = duration; isExpandedCarousel = false })
                                }
                            }
                        }
                        Text("Automatically toggles to the next page on the screen like a carousel, based the specified interval.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        // Wake on tap/motion
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wake on tap or motion", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Requires that there be an accelerometer on your device.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = wakeOnMotion, onCheckedChange = { wakeOnMotion = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        // Flip screen
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Flip screen", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Flip screen vertically.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = flipScreen, onCheckedChange = { flipScreen = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        // Always point north
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Always point north", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("The compass heading on the screen outside of the circle will always point north.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = alwaysPointNorth, onCheckedChange = { alwaysPointNorth = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        // 12h format
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Use 12h clock format", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("When enabled, the device will display the time in 12-hour format on screen.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = use12hClock, onCheckedChange = { use12hClock = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        // Bold Heading
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bold Heading", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Bold the heading text on the screen.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = boldHeading, onCheckedChange = { boldHeading = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display units
                        Text("Display units", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedUnits = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(displayUnits)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedUnits, onDismissRequest = { isExpandedUnits = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("METRIC", "IMPERIAL").forEach { unit ->
                                    DropdownMenuItem(text = { Text(unit, color = TextLight) }, onClick = { displayUnits = unit; isExpandedUnits = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Screen on for
                        Text("Screen on for", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedScreenOn = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(screenOnFor)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedScreenOn, onDismissRequest = { isExpandedScreenOn = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("1 minute", "5 minutes", "10 minutes", "30 minutes").forEach { duration ->
                                    DropdownMenuItem(text = { Text(duration, color = TextLight) }, onClick = { screenOnFor = duration; isExpandedScreenOn = false })
                                }
                            }
                        }
                    }

                    else -> {
                        Text("Bluetooth Settings", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bluetooth enabled", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Enables local BLE advertisement and pairing requests.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = bleEnabled, onCheckedChange = { bleEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Require PIN authentication", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Forces pairing passkey verification dialogue on first connection.", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(checked = blePincodeAuth, onCheckedChange = { blePincodeAuth = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f)))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Advertising Power Level", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isExpandedBlePower = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight), border = ButtonDefaults.outlinedButtonBorder) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(bleTxPower)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                                }
                            }
                            DropdownMenu(expanded = isExpandedBlePower, onDismissRequest = { isExpandedBlePower = false }, modifier = Modifier.background(SurfaceDark)) {
                                listOf("High", "Medium", "Low").forEach { power ->
                                    DropdownMenuItem(text = { Text(power, color = TextLight) }, onClick = { bleTxPower = power; isExpandedBlePower = false })
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save button for sub-portals
                Button(
                    onClick = {
                        // Persist to SharedPrefs for this specific node
                        nodePrefs.edit().apply {
                            putInt("lora_role", deviceRole)
                            putString("rebroadcast_mode", rebroadcastMode)
                            putString("node_info_interval", nodeInfoInterval)
                            putBoolean("double_tap_button", doubleTapButton)
                            putBoolean("triple_click_ping", tripleClickPing)
                            putString("button_gpio", buttonGpio)
                            putString("buzzer_gpio", buzzerGpio)

                            putString("pos_interval", posInterval)
                            putBoolean("smart_position", smartPosition)
                            putString("smart_interval", smartInterval)
                            putString("smart_distance", smartDistance)
                            putString("gps_mode", gpsMode)
                            putBoolean("fixed_position", fixedPosition)

                            putBoolean("power_save_mode", powerSaveMode)
                            putString("shutdown_power_loss", shutdownPowerLoss)
                            putString("wait_ble_duration", waitBluetoothDuration)
                            putString("deep_sleep_duration", superDeepSleepDuration)
                            putString("min_wake_time", minWakeTime)

                            putBoolean("wifi_enabled", wifiEnabled)
                            putBoolean("ethernet_enabled", ethernetEnabled)
                            putString("ntp_server", ntpServer)
                            putBoolean("udp_broadcasting", udpBroadcasting)
                            putString("ipv4_mode", ipv4Mode)

                            putString("carousel_interval", carouselInterval)
                            putBoolean("wake_on_motion", wakeOnMotion)
                            putBoolean("flip_screen", flipScreen)
                            putBoolean("always_point_north", alwaysPointNorth)
                            putBoolean("use_12h_clock", use12hClock)
                            putBoolean("bold_heading", boldHeading)
                            putString("display_units", displayUnits)
                            putString("screen_on_for", screenOnFor)

                            putBoolean("ble_enabled", bleEnabled)
                            putBoolean("ble_pincode_auth", blePincodeAuth)
                            putString("ble_tx_power", bleTxPower)
                            apply()
                        }
                        
                        // Sync role/telemetry if Device config modified
                        if (portalName == "DEVICE") {
                            viewModel.sendNodeConfig(
                                name = connectedNode?.name?.replace("AetherMesh-", "")?.replace("Node ", "") ?: "Wolf Base",
                                shortName = connectedNode?.shortName ?: "BASE",
                                sf = nodePrefs.getInt("lora_sf", 9),
                                bw = nodePrefs.getFloat("lora_bw", 125f),
                                txPower = nodePrefs.getInt("lora_tx_power", 22),
                                region = nodePrefs.getInt("region", 0),
                                role = deviceRole,
                                telemetryInterval = telemetryIntervalSecs
                            )
                        }

                        android.widget.Toast.makeText(context, "Configurations saved & synced successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                ) {
                    Text("Save Configuration", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
// Radio profiles: matched SF/BW presets so every node in the mesh can be flipped
// to the same configuration without slider mismatches. Link budget is relative
// to Fast (SF9); each SF step roughly doubles airtime.
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

fun exportRangeTestLogsToCsv(context: Context, logs: List<com.example.aethermesh.data.RangeTestLog>) {
    if (logs.isEmpty()) {
        android.widget.Toast.makeText(context, "No range test data to export yet.", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    // Machine-friendly CSV: epoch ms for tooling, ISO local time for humans,
    // raw lat/lon plus BOTH link directions for signal analysis:
    //   ping_* = signal of our ping as heard by the target (from the ACK payload)
    //   ack_*  = signal of the target's ACK as heard by our node
    // Signal columns are blank (not placeholder values) on timeouts/unreported.
    val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
    val csv = StringBuilder("timestamp_ms,datetime,target_id,latitude,longitude,speed_mps,gps_accuracy_m,ping_rssi_dbm,ping_snr_db,ack_rssi_dbm,ack_snr_db,success\n")
    logs.forEach {
        val ackRssi = if (it.success) "${it.rssi}" else ""
        val ackSnr = if (it.success) "${it.snr}" else ""
        val pingRssi = it.remoteRssi?.toString() ?: ""
        val pingSnr = it.remoteSnr?.toString() ?: ""
        val speed = it.speedMps?.toString() ?: ""
        val accuracy = it.gpsAccuracyM?.toString() ?: ""
        csv.append("${it.timestamp},${iso.format(java.util.Date(it.timestamp))},0x${it.targetId.toString(16).uppercase()},${it.latitude},${it.longitude},$speed,$accuracy,$pingRssi,$pingSnr,$ackRssi,$ackSnr,${it.success}\n")
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