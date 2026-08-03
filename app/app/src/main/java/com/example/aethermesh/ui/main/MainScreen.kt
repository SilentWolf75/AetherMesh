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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
import com.example.aethermesh.ui.AppUiFeedback
import com.example.aethermesh.ui.PermissionHealthBanner
import kotlinx.coroutines.launch
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

/** Infer ESP32 vs RAK OTA target from telemetry model and/or BLE / node names. */
fun isRakOtaTarget(vararg hints: String?): Boolean {
    val haystack = hints.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .joinToString(" ")
        .lowercase()
    if (haystack.isEmpty()) return false
    return haystack.contains("rak") ||
        haystack.contains("wisblock") ||
        haystack.contains("nrf52") ||
        haystack.contains("nrf52840")
}

fun isEspOtaTarget(vararg hints: String?): Boolean {
    val haystack = hints.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .joinToString(" ")
        .lowercase()
    if (haystack.isEmpty()) return false
    return haystack.contains("heltec") ||
        haystack.contains("t-deck") ||
        haystack.contains("tdeck") ||
        haystack.contains("crowpanel") ||
        haystack.contains("esp32")
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

fun localizeOtaStatus(status: String, appLanguage: String): String {
    if (appLanguage != "Spanish" || status.isBlank()) return status
    return when {
        status == "Not connected/authenticated" -> "No conectado/autenticado"
        status == "Preparing..." -> "Preparando…"
        status == "Retrying start..." -> "Reintentando inicio…"
        status == "Uploading..." -> "Subiendo…"
        status.startsWith("Uploading...") -> status.replace("Uploading...", "Subiendo…")
        status == "Verifying..." -> "Verificando…"
        status.startsWith("Update verified") -> "Actualización verificada — el nodo está reiniciando con el nuevo firmware"
        status == "Update cancelled" -> "Actualización cancelada"
        status.startsWith("Update failed:") -> status.replace("Update failed:", "Falló la actualización:")
        status.startsWith("DFU: connecting") -> "DFU: conectando al bootloader…"
        status.startsWith("DFU: starting") -> "DFU: iniciando transferencia…"
        status.startsWith("DFU: validating") -> "DFU: validando firmware…"
        status.startsWith("DFU uploading...") -> status.replace("DFU uploading...", "DFU subiendo…")
        status.startsWith("DFU complete") -> "DFU completo — el nodo reinicia con el nuevo firmware"
        status == "DFU cancelled" -> "DFU cancelado"
        status.startsWith("DFU failed:") -> status.replace("DFU failed:", "DFU falló:")
        status.startsWith("Rebooting node into DFU") -> "Reiniciando nodo en bootloader DFU…"
        status == "No device address" -> "Sin dirección del dispositivo"
        else -> status
    }
}

fun localizeOtaPickError(error: String, appLanguage: String): String {
    if (appLanguage != "Spanish") return error
    return when (error) {
        "Empty file" -> "Archivo vacío"
        "RAK updates need a .zip DFU package" -> "Las actualizaciones RAK requieren un paquete DFU .zip"
        "File too small to be a DFU package" -> "El archivo es demasiado pequeño para ser un paquete DFU"
        "Not a valid ZIP (DFU) package" -> "No es un paquete ZIP (DFU) válido"
        "Heltec updates need a .bin image (not .zip)" -> "Las actualizaciones Heltec requieren una imagen .bin (no .zip)"
        "Firmware image looks too small" -> "La imagen de firmware parece demasiado pequeña"
        "Does not look like an ESP32 .bin image" -> "No parece una imagen .bin de ESP32"
        else -> error
    }
}

fun localizeTraceRouteError(error: String?, appLanguage: String): String {
    val spanish = appLanguage == "Spanish"
    return when (error) {
        null -> if (spanish) "Traza fallida" else "Trace failed"
        "No route response received" -> if (spanish)
            "No se recibió respuesta de ruta"
        else
            "No route response received"
        "Cancelled" -> if (spanish) "Trazado cancelado" else "Traceroute cancelled"
        "Disconnected" -> if (spanish)
            "Desconectado — traza cancelada"
        else
            "Disconnected — traceroute cancelled"
        else -> error
    }
}

fun localizeChatPlaceholder(content: String, appLanguage: String): String {
    if (appLanguage != "Spanish") return content
    return when (content) {
        "[Encrypted Message - No Key Configured]" -> "[Mensaje cifrado — sin clave configurada]"
        "[Decryption Error - Invalid Message]" -> "[Error de descifrado — mensaje inválido]"
        "[Decryption Error - Bad Key or Context]" -> "[Error de descifrado — clave o contexto incorrecto]"
        "[Decryption Error - Bad Key]" -> "[Error de descifrado — clave incorrecta]"
        else -> content
    }
}


fun t(text: String, lang: String): String {
    if (lang != "Spanish") return text
    return when (text) {
        "Distance Units" -> "Unidades de Distancia"
        "Imperial" -> "Imperial"
        "Metric" -> "Métrico"
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
        "View raw system messages" -> "Vista previa de paquetes de chat recientes"
        "Recent chat packet sizes (not a full system log)" -> "Tamaños de paquetes de chat recientes (no es un registro completo del sistema)"
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
        "Mesh Routing" -> "Enrutamiento Mesh"
        "Live mesh health, quiet-mode status, and observed routes" -> "Salud del mesh en vivo, estado de modo silencioso y rutas observadas"
        "No routing paths observed yet.\nPaths are dynamically built as nodes transmit." -> "Aún no se han observado rutas.\nLas rutas se construyen dinámicamente a medida que transmiten los nodos."
        "Next Hop" -> "Siguiente Salto"
        "Hop" -> "Salto"
        "Hops" -> "Saltos"
        "ACTIVE" -> "ACTIVO"
        "Stop Test" -> "Detener Prueba"
        "Export CSV" -> "Exportar CSV"
        "Clear Logs" -> "Borrar Registros"
        "RSSI Signal Level History (dBm)" -> "Historial del Nivel de Señal RSSI (dBm)"
        "Rename Node" -> "Renombrar Nodo"
        "Long Name (max 16 chars)" -> "Nombre largo (máx. 16 caracteres)"
        "Short Name (max 4 chars)" -> "Nombre corto (máx. 4 caracteres)"
        "Firmware Update" -> "Actualización de Firmware"
        "Flash new firmware to the connected node over Bluetooth (BLE OTA)" -> "Flashea firmware nuevo al nodo conectado por Bluetooth (BLE OTA)"
        "Configure GPS enable, telemetry interval, and view satellite lock status" -> "Configura GPS (siempre / periódico / apagado), telemetría y estado de satélites"
        "Configure onboard GPS mode, telemetry interval, and satellite lock status" -> "Configura el GPS del nodo, telemetría y estado de satélites"
        "Phone GPS Sharing" -> "Compartir GPS del teléfono"
        "Position Configuration" -> "Configuración de posición"
        "GPS Status & Live Telemetry" -> "Estado GPS y telemetría en vivo"
        "GPS Lock Status" -> "Estado de bloqueo GPS"
        "LOCKED" -> "FIJADO"
        "WAITING FOR LOCK" -> "ESPERANDO FIJACIÓN"
        "PERIODIC SLEEP" -> "SUEÑO PERIÓDICO"
        "GPS OFF" -> "GPS APAGADO"
        "Node GPS" -> "GPS del nodo"
        "Backup Device Settings" -> "Respaldar Ajustes del Dispositivo"
        "Restore Device Settings" -> "Restaurar Ajustes del Dispositivo"
        "Data & Logs Management" -> "Gestión de Datos y Registros"
        "Hide" -> "Ocultar"
        "Show" -> "Mostrar"
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
        "Set language, theme, units, and background alerts" -> "Ajusta idioma, tema, unidades y alertas en segundo plano"
        "Hop limit, rebroadcast pace, and route health" -> "Límite de saltos, ritmo de rebroadcast y salud de rutas"
        "Developer & Diagnostics" -> "Desarrollo y Diagnósticos"
        "Live logs console, packet exports, and system database reset" -> "Consola de registros, exportación de paquetes y restablecimiento"
        "Keys copied to clipboard" -> "Claves copiadas al portapapeles"
        "Location loaded from phone GPS" -> "Ubicación cargada del GPS del teléfono"
        "No phone GPS location lock yet — open the Map tab briefly to acquire one" -> "Aún no hay GPS del teléfono — abre el mapa un momento para obtener una fijación"
        "Location permission needed" -> "Se necesita permiso de ubicación"
        "No channels configured yet." -> "Aún no hay canales configurados."
        "Node Short Name" -> "Nombre corto del nodo"
        "Connect to a hardware node via Bluetooth to configure LoRa radio settings." -> "Conéctate a un nodo por Bluetooth para configurar la radio LoRa."
        "Connect to a hardware node via Bluetooth to configure LoRa position interval." -> "Conéctate a un nodo por Bluetooth para configurar el intervalo de posición."
        "No nodes discovered yet. Waiting for telemetry..." -> "Aún no hay nodos. Esperando telemetría…"
        "Channel deleted." -> "Canal eliminado."
        "Channel added." -> "Canal añadido."
        "Keys regenerated." -> "Claves regeneradas."
        "Node renamed." -> "Nodo renombrado."
        "System" -> "Sistema"
        "Dark" -> "Oscuro"
        "Light" -> "Claro"
        "English" -> "Inglés"
        "Spanish" -> "Español"
        "Radio Profile" -> "Perfil de radio"
        "Primary" -> "Primario"
        "Client" -> "Cliente"
        "Router" -> "Router"
        "Low-Power Repeater" -> "Repetidor de bajo consumo"
        "This replaces your device keypair. Existing encrypted direct-message threads may become unreadable." ->
            "Esto reemplaza el par de claves del dispositivo. Los mensajes directos cifrados existentes pueden quedar ilegibles."
        else -> text
    }
}



enum class TabItem {
    CHATS, NODES, MAP, SETTINGS, CONNECTION
}

enum class SettingsCategory {
    CHANNELS, RADIO, POSITION, FIRMWARE, SECURITY, ROUTING, PREFERENCES, DEVELOPER
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

    // MeshCore-style: start on Connection until a radio is linked; then land on Chats.
    var activeTab by remember { mutableStateOf(TabItem.CONNECTION) }
    var previousTabBeforeConnection by remember { mutableStateOf(TabItem.CHATS) }
    var wasBleConnected by remember { mutableStateOf(false) }
    var pendingChatsAfterAuth by remember { mutableStateOf(false) }
    var fitTraceRouteToken by remember { mutableIntStateOf(0) }
    var pendingMapFocusNodeId by remember { mutableStateOf<Long?>(null) }
    var pendingSettingsCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var selectedNodeDetailsId by remember { mutableStateOf<Long?>(null) }
    var renamingDetailNode by remember { mutableStateOf<MeshNode?>(null) }

    val isDeviceAuthenticated by viewModel.isDeviceAuthenticated.collectAsStateWithLifecycle()
    val authenticationRequired by viewModel.authenticationRequired.collectAsStateWithLifecycle()
    val authFailureTick by viewModel.authFailureTick.collectAsStateWithLifecycle()
    val needsRegionSetup by viewModel.needsRegionSetup.collectAsStateWithLifecycle()

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            // Drop mesh tabs back to the scanner; allow Settings while offline.
            if (activeTab != TabItem.CONNECTION && activeTab != TabItem.SETTINGS) {
                previousTabBeforeConnection = activeTab
                activeTab = TabItem.CONNECTION
            }
            wasBleConnected = false
            pendingChatsAfterAuth = false
        } else if (!wasBleConnected) {
            // Stay on Connection until unlock; then land on Chats.
            wasBleConnected = true
            if (activeTab == TabItem.CONNECTION) {
                pendingChatsAfterAuth = true
            }
        }
    }

    LaunchedEffect(isDeviceAuthenticated, pendingChatsAfterAuth) {
        if (pendingChatsAfterAuth && isConnected && isDeviceAuthenticated) {
            activeTab = TabItem.CHATS
            pendingChatsAfterAuth = false
        }
    }

    fun openConnectionTab() {
        if (activeTab != TabItem.CONNECTION) {
            previousTabBeforeConnection = activeTab
        }
        activeTab = TabItem.CONNECTION
    }

    fun leaveConnectionTab() {
        activeTab = if (isConnected) previousTabBeforeConnection else TabItem.CONNECTION
        if (activeTab == TabItem.CONNECTION && isConnected) {
            activeTab = TabItem.CHATS
        }
    }

    var authPasswordInput by remember { mutableStateOf("") }
    var authConfirmPasswordInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf(false) }
    var authSendFailed by remember { mutableStateOf(false) }
    var setupRegion by remember { mutableIntStateOf(0) } // 0 = US915, 1 = EU868

    LaunchedEffect(authFailureTick) {
        if (authFailureTick > 0) authError = true
    }

    val sharedPrefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
    var appLanguage by remember { mutableStateOf(sharedPrefs.getString("app_language", "English") ?: "English") }
    var useImperialUnitsSetting by remember { mutableStateOf(sharedPrefs.getBoolean("use_imperial_units", true)) }

    LaunchedEffect(bleReconnectGaveUp, appLanguage) {
        if (bleReconnectGaveUp) {
            AppUiFeedback.show(
                text = if (appLanguage == "Spanish")
                    "Reconexión agotada. ¿Reintentar?"
                else
                    "Reconnect gave up. Retry?",
                actionLabel = if (appLanguage == "Spanish") "Reintentar" else "Retry"
            ) {
                viewModel.retryBleConnection()
            }
        }
    }

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
    val chatKeysRevision by viewModel.chatKeysRevision.collectAsStateWithLifecycle()

    val pendingOpenChats by viewModel.pendingOpenChatsTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenChats, isConnected) {
        if (viewModel.consumeOpenChatsTab()) {
            activeTab = if (isConnected) TabItem.CHATS else TabItem.CONNECTION
        }
    }
    val pendingOpenMap by viewModel.pendingOpenMapTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenMap, isConnected) {
        if (viewModel.consumeOpenMapTab()) {
            if (isConnected) {
                activeTab = TabItem.MAP
                pendingMapFocusNodeId = viewModel.consumeFocusNodeId()
            } else {
                activeTab = TabItem.CONNECTION
            }
        }
    }
    val pendingChatDeep by viewModel.pendingChatDeepLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingChatDeep, isConnected, isDeviceAuthenticated) {
        val link = pendingChatDeep ?: return@LaunchedEffect
        // Hold the deep-link until BLE is up and unlocked — cold-start notification
        // taps often arrive before connect/auth completes.
        if (!isConnected) {
            activeTab = TabItem.CONNECTION
            return@LaunchedEffect
        }
        if (!isDeviceAuthenticated) {
            return@LaunchedEffect
        }
        viewModel.consumeChatDeepLink() ?: return@LaunchedEffect
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
        authConfirmPasswordInput = ""
        authError = false
        authSendFailed = false
    }

    // Ensure map HTTP identity is set (also done in Application.onCreate).
    LaunchedEffect(Unit) {
        OsmMapConfig.configure(context)
    }

    // Global location update listener to share location whenever connected
    DisposableEffect(isConnected) {
        if (isConnected) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            var lastShareMs = 0L
            val minShareIntervalMs = 60_000L // mesh share at most once per minute
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    val gp = GeoPoint(location.latitude, location.longitude)
                    phoneLocation = gp
                    viewModel.updatePhoneLocation(gp.latitude, gp.longitude)
                    if (sharedPrefs.getBoolean("enable_phone_gps_sharing", true)) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastShareMs >= minShareIntervalMs) {
                            if (viewModel.sharePhoneLocation(gp.latitude, gp.longitude)) {
                                lastShareMs = now
                            }
                        }
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            try {
                // Battery-friendlier than 15s: 45s / 25m still keeps the map useful
                lm.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER,
                    45_000L,
                    25f,
                    listener
                )
                lm.requestLocationUpdates(
                    android.location.LocationManager.NETWORK_PROVIDER,
                    60_000L,
                    50f,
                    listener
                )
                val lastFix = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                if (lastFix != null) {
                    phoneLocation = GeoPoint(lastFix.latitude, lastFix.longitude)
                    viewModel.updatePhoneLocation(lastFix.latitude, lastFix.longitude)
                    if (sharedPrefs.getBoolean("enable_phone_gps_sharing", true)) {
                        if (viewModel.sharePhoneLocation(lastFix.latitude, lastFix.longitude)) {
                            lastShareMs = android.os.SystemClock.elapsedRealtime()
                        }
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
    
    val connectedNode = resolveConnectedMeshNode(
        nodes = nodes,
        connectedId = viewModel.connectedNodeId,
        deviceName = viewModel.connectedDeviceName
    )
    val connectedNodeName = connectedNode?.name ?: viewModel.connectedDeviceName
    val adaptive = rememberAdaptiveLayoutInfo()
    val constrainContentWidth = activeTab != TabItem.MAP

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appBackgroundBrush())
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.useNavigationRail) {
                AetherAppNavigation(
                    selectedTab = activeTab,
                    appLanguage = appLanguage,
                    useRail = true,
                    isConnected = isConnected,
                    linkedHighlightTab = previousTabBeforeConnection,
                    onTabSelected = { tab ->
                        if (tab == TabItem.CONNECTION) openConnectionTab() else activeTab = tab
                    }
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Header Bar
                HeaderBar(
                    title = headerTitle,
                    isConnected = isConnected,
                    isAuthenticated = isDeviceAuthenticated,
                    connectionPhase = blePhase,
                    reconnectAttempt = bleReconnectAttempt,
                    connectedNodeName = connectedNodeName,
                    appLanguage = appLanguage,
                    horizontalPadding = adaptive.horizontalPadding,
                    showBackFromConnection = isConnected && activeTab == TabItem.CONNECTION,
                    onConnectionClick = { openConnectionTab() },
                    onBackFromConnection = { leaveConnectionTab() }
                )

                PermissionHealthBanner(
                    appLanguage = appLanguage,
                    bgAlertsEnabled = sharedPrefs.getBoolean("bg_alerts_enabled", true),
                    onOpenSettings = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                )

                // Content Area based on Tab Selection
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .then(
                                if (constrainContentWidth) Modifier.adaptiveContentWidth(adaptive)
                                else Modifier
                            )
                    ) {
                if (!isConnected && activeTab != TabItem.CONNECTION) {
                    val bannerText = when {
                        bleReconnectGaveUp -> if (appLanguage == "Spanish")
                            "Reconexión agotada. Toque para reintentar o abrir Conexión."
                        else
                            "Reconnect gave up. Tap to retry or open Connection."
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
                            .clickable {
                                if (bleReconnectGaveUp) {
                                    viewModel.retryBleConnection()
                                    AppUiFeedback.show(
                                        if (appLanguage == "Spanish") "Reintentando conexión…"
                                        else "Retrying connection…"
                                    )
                                } else {
                                    activeTab = TabItem.CONNECTION
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = if (appLanguage == "Spanish") "Desconectado" else "Disconnected",
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
                        if (bleReconnectGaveUp) {
                            TextButton(onClick = { activeTab = TabItem.CONNECTION }) {
                                Text(
                                    if (appLanguage == "Spanish") "Conexión" else "Connection",
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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
                            isReconnecting = !isConnected && (
                                blePhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ||
                                    blePhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting
                            ),
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
                            deepLinkEpoch = chatDeepLinkEpoch,
                            chatKeysRevision = chatKeysRevision
                        )
                        TabItem.NODES -> {
                            val nodesTwoPane = adaptive.useTwoPane
                            if (nodesTwoPane) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                                        NodesView(
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
                                            onRangeTest = { nodeId -> viewModel.requestRangeTestDialog(nodeId) },
                                            onOpenNodeDetails = { nodeId -> selectedNodeDetailsId = nodeId },
                                            selectedNodeId = selectedNodeDetailsId,
                                            onRefresh = { viewModel.refresh() }
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(BorderDark.copy(alpha = 0.7f))
                                    )
                                    Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                                        val detailId = selectedNodeDetailsId
                                        val detailNode = detailId?.let { id ->
                                            nodes.find { it.nodeId == id }
                                                ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (id and 0xFFFFFFFFL) }
                                        }
                                        if (detailNode != null) {
                                            NodeDetailsScreen(
                                                node = detailNode,
                                                observedRoutes = observedRoutes,
                                                phoneLocation = phoneLocation,
                                                appLanguage = appLanguage,
                                                useImperialUnits = useImperialUnitsSetting,
                                                connectedNodeId = viewModel.connectedNodeId,
                                                getTelemetryHistory = { nodeId -> viewModel.getTelemetryHistory(nodeId) },
                                                onDismiss = { selectedNodeDetailsId = null },
                                                onMessage = {
                                                    viewModel.selectDirectMessage(detailNode.nodeId)
                                                    activeTab = TabItem.CHATS
                                                },
                                                onRename = { renamingDetailNode = detailNode },
                                                onTraceRoute = { viewModel.startTraceRoute(detailNode.nodeId) },
                                                onRemoteConfig = if (!sameMeshNodeId(detailNode.nodeId, viewModel.connectedNodeId)) {
                                                    { viewModel.requestRemoteConfig(detailNode.nodeId) }
                                                } else null,
                                                onViewOnMap = {
                                                    viewModel.requestOpenMapTab(focusNodeId = detailNode.nodeId)
                                                    activeTab = TabItem.MAP
                                                },
                                                onStartRangeTest = if (!sameMeshNodeId(detailNode.nodeId, viewModel.connectedNodeId)) {
                                                    { viewModel.requestRangeTestDialog(detailNode.nodeId) }
                                                } else null
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    if (appLanguage == "Spanish")
                                                        "Selecciona un nodo para ver detalles"
                                                    else
                                                        "Select a node to view details",
                                                    color = TextMuted,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                NodesView(
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
                                    onRangeTest = { nodeId -> viewModel.requestRangeTestDialog(nodeId) },
                                    onOpenNodeDetails = { nodeId ->
                                        onItemClick(com.example.aethermesh.NodeDetails(nodeId))
                                    },
                                    onRefresh = { viewModel.refresh() }
                                )
                            }
                        }
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
                            }
                        )
                        TabItem.SETTINGS -> SettingsView(
                            viewModel = viewModel,
                            isConnected = isConnected,
                            initialCategory = pendingSettingsCategory,
                            onInitialCategoryConsumed = { pendingSettingsCategory = null }
                        )
                        TabItem.CONNECTION -> ConnectionView(
                            viewModel = viewModel,
                            isConnected = isConnected,
                            nodes = nodes,
                            scannedDevices = scannedDevices,
                            appLanguage = appLanguage,
                            onOpenMeshRouting = {
                                activeTab = TabItem.SETTINGS
                                pendingSettingsCategory = SettingsCategory.ROUTING
                            },
                            onContinueToMesh = { leaveConnectionTab() }
                        )
                    }
                    }
                }
                }

                if (!adaptive.useNavigationRail) {
                    AetherAppNavigation(
                        selectedTab = activeTab,
                        appLanguage = appLanguage,
                        useRail = false,
                        isConnected = isConnected,
                        linkedHighlightTab = previousTabBeforeConnection,
                        onTabSelected = { tab ->
                            if (tab == TabItem.CONNECTION) openConnectionTab() else activeTab = tab
                        }
                    )
                }
            }
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
                onCancel = { viewModel.cancelTraceRoute() },
                onViewOnMap = {
                    viewModel.hideTraceRouteDialog()
                    activeTab = TabItem.MAP
                    fitTraceRouteToken++
                }
            )
        }

        if (renamingDetailNode != null) {
            RenameNodeDialog(
                node = renamingDetailNode!!,
                connectedNodeId = viewModel.connectedNodeId,
                appLanguage = appLanguage,
                onRename = { nodeId, longName, shortName, password ->
                    viewModel.renameNode(nodeId, longName, shortName, password)
                },
                onDismiss = { renamingDetailNode = null }
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
                                authSendFailed = false
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
                        if (isFirstTime) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = authConfirmPasswordInput,
                                onValueChange = {
                                    authConfirmPasswordInput = it
                                    authError = false
                                    authSendFailed = false
                                },
                                label = {
                                    Text(
                                        if (spanish) "Confirmar contraseña" else "Confirm password",
                                        color = TextMuted
                                    )
                                },
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
                        }
                        
                        if (authError || authSendFailed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when {
                                    authSendFailed && spanish ->
                                        "No se pudo enviar — espera a que Bluetooth esté listo e inténtalo de nuevo."
                                    authSendFailed ->
                                        "Could not send — wait for Bluetooth to be ready and try again."
                                    isFirstTime && spanish ->
                                        "Las contraseñas no coinciden."
                                    isFirstTime ->
                                        "Passwords do not match."
                                    spanish ->
                                        "Autenticación fallida. Contraseña incorrecta."
                                    else ->
                                        "Authentication failed. Incorrect password."
                                },
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
                            if (pass.isEmpty()) return@Button
                            if (isFirstTime && pass != authConfirmPasswordInput.trim()) {
                                authError = true
                                authSendFailed = false
                                return@Button
                            }
                            val sent = viewModel.sendAuthRequest(pass)
                            if (!sent) {
                                authSendFailed = true
                                authError = false
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

        // First-setup region wizard — shown after auth when the node has never
        // had a confirmed LoRa region (factory-fresh or wiped settings).
        if (isConnected && isDeviceAuthenticated && needsRegionSetup) {
            val spanish = appLanguage == "Spanish"
            val nodeKey = viewModel.connectedNodeId
            val nodePrefs = remember(nodeKey) {
                context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
            }
            AlertDialog(
                onDismissRequest = { /* Force region choice */ },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (spanish) "Elegir región LoRa" else "Choose LoRa Region",
                            color = TextLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            if (spanish)
                                "Cada país usa una frecuencia distinta. Elige la región correcta antes de usar el mesh. El radio usará alcance largo (SF11) por defecto. Esto solo configura el nodo conectado por BLE — los demás nodos deben igualarse con Configuración remota o conectándote a cada uno."
                            else
                                "Every country uses a different frequency. Pick the correct region before using the mesh. The radio defaults to Long range (SF11). This only configures the BLE-connected node — match other nodes via Remote Config or by connecting to each one.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        listOf(
                            0 to "US915 (North America)",
                            1 to "EU868 (Europe)"
                        ).forEach { (value, label) ->
                            val selected = setupRegion == value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AccentCyan.copy(alpha = 0.18f) else Color.Transparent)
                                    .clickable { setupRegion = value }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) AccentCyan else BorderDark)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label, color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            if (spanish)
                                "Al confirmar, el nodo reinicia con esta región."
                            else
                                "Confirming reboots the node with this region.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val name = nodePrefs.getString("node_name", "")
                                ?.takeIf { it.isNotBlank() }
                                ?: nodes.find { it.nodeId == nodeKey }?.name
                                    ?.replace("AetherMesh-", "")
                                    ?.replace("Node ", "")
                                ?: ""
                            val shortName = nodePrefs.getString("node_short_name", null)
                                ?: nodes.find { it.nodeId == nodeKey }?.shortName
                                ?: name.replace(Regex("[^a-zA-Z0-9]"), "").take(4).uppercase()
                                    .ifEmpty { String.format("%04X", (nodeKey and 0xFFFFL).toInt()) }
                            val sent = viewModel.sendNodeConfig(
                                name = name,
                                shortName = shortName,
                                sf = nodePrefs.getInt("lora_sf", 11),
                                bw = nodePrefs.getFloat("lora_bw", 125f),
                                txPower = nodePrefs.getInt("lora_tx_power", 22),
                                region = setupRegion,
                                role = nodePrefs.getInt("node_role", 0),
                                telemetryInterval = nodePrefs.getInt("telemetry_interval", 60),
                                screenTimeout = nodePrefs.getInt("screen_timeout", 30),
                                powerSaveMode = nodePrefs.getBoolean("power_save_mode", false),
                                positionPrecision = nodePrefs.getInt("position_precision", 0),
                                gpsMode = nodePrefs.getInt("gps_mode", 0).coerceIn(0, 2),
                                gpsDutyIntervalSecs = snapGpsDutyIntervalSecs(
                                    nodePrefs.getInt("gps_duty_interval_secs", 900)
                                ),
                                fixedPosition = nodePrefs.getBoolean("fixed_position", false),
                                fixedLatitude = nodePrefs.getFloat("fixed_latitude", 0f),
                                fixedLongitude = nodePrefs.getFloat("fixed_longitude", 0f),
                                fixedAltitude = nodePrefs.getInt("fixed_altitude", 0),
                                meshHopLimit = nodePrefs.getInt("mesh_hop_limit", 4).coerceIn(1, 8),
                                rebroadcastTxdelayX100 = nodePrefs.getInt("rebroadcast_txdelay_x100", 100).let {
                                    if (it <= 0) 100 else it.coerceIn(50, 200)
                                }
                            )
                            if (sent) {
                                nodePrefs.edit()
                                    .putInt("region", setupRegion)
                                    .putBoolean("region_configured", true)
                                    .apply()
                                viewModel.clearRegionSetupPrompt()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                    ) {
                        Text(if (spanish) "Confirmar región" else "Confirm Region")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.disconnect() }) {
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
    isAuthenticated: Boolean = true,
    connectionPhase: com.example.aethermesh.ble.BleConnectionPhase =
        com.example.aethermesh.ble.BleConnectionPhase.Disconnected,
    reconnectAttempt: Int = 0,
    connectedNodeName: String?,
    appLanguage: String = "English",
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    showBackFromConnection: Boolean = false,
    onConnectionClick: () -> Unit = {},
    onBackFromConnection: () -> Unit = {}
) {
    val spanish = appLanguage == "Spanish"
    val statusLabel = when {
        isConnected && !isAuthenticated -> if (spanish) "BLOQUEADO" else "LOCKED"
        isConnected -> if (spanish) "ENLACE" else "LINK UP"
        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ->
            if (spanish) "RECON $reconnectAttempt" else "RECONNECT $reconnectAttempt"
        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting ->
            if (spanish) "CONECTANDO" else "CONNECTING"
        else -> if (spanish) "SIN RED" else "OFFLINE"
    }
    val statusColor = when {
        isConnected && !isAuthenticated -> AccentAmber
        isConnected -> AccentMint
        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting ||
            connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting -> AccentAmber
        else -> AccentRed
    }
    val pulseActive = (isConnected && isAuthenticated) ||
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
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (showBackFromConnection) {
                    IconButton(onClick = onBackFromConnection) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (spanish) "Volver" else "Back",
                            tint = AccentCyan
                        )
                    }
                }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onConnectionClick)
                            .padding(vertical = 2.dp)
                    ) {
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

            if (isConnected && !isAuthenticated) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentAmber.copy(alpha = 0.2f))
                        .border(BorderStroke(1.dp, AccentAmber.copy(alpha = 0.55f)), RoundedCornerShape(10.dp))
                        .clickable(onClick = onConnectionClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (spanish) "DESBLOQ." else "UNLOCK",
                        color = AccentAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else if (isConnected && !connectedNodeName.isNullOrBlank()) {
                val shortName = getShortName(connectedNodeName, 0L)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(primaryButtonBrush())
                        .clickable(onClick = onConnectionClick)
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
            } else if (!isConnected) {
                val isConnecting =
                    connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Connecting ||
                        connectionPhase == com.example.aethermesh.ble.BleConnectionPhase.Reconnecting
                val chipColor = if (isConnecting) AccentAmber else AccentOrange
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(chipColor.copy(alpha = 0.2f))
                        .border(BorderStroke(1.dp, chipColor.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                        .clickable(onClick = onConnectionClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            isConnecting && spanish -> "Conectando…"
                            isConnecting -> "Connecting…"
                            spanish -> "Conectar"
                            else -> "Connect"
                        },
                        color = chipColor,
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


// Feature screens live in ChatScreen/NodesScreen/MapScreen/SettingsScreen/ConnectionScreen.

@Composable
fun AetherBottomNav(
    selectedTab: TabItem,
    appLanguage: String,
    onTabSelected: (TabItem) -> Unit
) {
    AetherAppNavigation(
        selectedTab = selectedTab,
        appLanguage = appLanguage,
        useRail = false,
        isConnected = true,
        onTabSelected = onTabSelected
    )
}

