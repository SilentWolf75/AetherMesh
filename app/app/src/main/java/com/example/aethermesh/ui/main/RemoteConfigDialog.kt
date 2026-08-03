package com.example.aethermesh.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aethermesh.data.ConfigApplyMask
import com.example.aethermesh.data.MeshNode
import com.example.aethermesh.proto.ConfigResult
import com.example.aethermesh.proto.NodeConfig
import com.example.aethermesh.ui.AppUiFeedback
import com.example.aethermesh.ui.components.aetherTextFieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

private data class RemoteBaseline(
    val name: String,
    val sf: Int,
    val bw: Float,
    val txPower: Int,
    val region: Int,
    val role: Int,
    val telemetry: Int,
    val screen: Int,
    val powerSave: Boolean,
    val posPrec: Int,
    val gpsMode: Int,
    val gpsDutySecs: Int,
    val fixed: Boolean,
    val lat: Float,
    val lon: Float,
    val alt: Int,
    val hop: Int,
    val txdelay: Int
)

@Composable
fun RemoteConfigDialog(
    node: MeshNode,
    viewModel: MainScreenViewModel,
    appLanguage: String = "English",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val spanish = appLanguage == "Spanish"
    val remotePrefs = remember(node.nodeId) {
        context.getSharedPreferences("node_settings_${node.nodeId}", Context.MODE_PRIVATE)
    }

    var remoteName by remember(node.nodeId) {
        mutableStateOf(remotePrefs.getString("node_name", null) ?: node.name)
    }
    var remotePassword by remember(node.nodeId) { mutableStateOf("") }
    var remoteSF by remember(node.nodeId) {
        mutableIntStateOf(
            when {
                remotePrefs.contains("lora_sf") -> remotePrefs.getInt("lora_sf", 11)
                node.loraSf in 7..12 -> node.loraSf
                else -> 11
            }
        )
    }
    var remoteBW by remember(node.nodeId) { mutableFloatStateOf(remotePrefs.getFloat("lora_bw", 125f)) }
    var remoteTxPower by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("lora_tx_power", 22)) }
    var remoteRegion by remember(node.nodeId) {
        mutableIntStateOf(
            when {
                remotePrefs.contains("region") -> remotePrefs.getInt("region", 0)
                node.region >= 0 -> node.region
                else -> 0
            }
        )
    }
    var remoteRole by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("node_role", 0).coerceIn(0, 1)) }
    var remoteTelemetryInterval by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("telemetry_interval", 60)) }
    var remotePositionPrecision by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("position_precision", 0)) }
    var remoteGpsMode by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("gps_mode", 0).coerceIn(0, 2)) }
    var remoteGpsDutySecs by remember(node.nodeId) {
        mutableIntStateOf(snapGpsDutyIntervalSecs(remotePrefs.getInt("gps_duty_interval_secs", 900)))
    }
    var remoteScreenTimeout by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("screen_timeout", 30)) }
    var remotePowerSave by remember(node.nodeId) { mutableStateOf(remotePrefs.getBoolean("power_save_mode", false)) }
    var remoteFixedPosition by remember(node.nodeId) { mutableStateOf(remotePrefs.getBoolean("fixed_position", false)) }
    var remoteFixedLat by remember(node.nodeId) { mutableFloatStateOf(remotePrefs.getFloat("fixed_latitude", 0f)) }
    var remoteFixedLon by remember(node.nodeId) { mutableFloatStateOf(remotePrefs.getFloat("fixed_longitude", 0f)) }
    var remoteFixedAlt by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("fixed_altitude", 0)) }
    var remoteHop by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("mesh_hop_limit", 4).coerceIn(1, 8)) }
    var remoteTxdelay by remember(node.nodeId) {
        mutableIntStateOf(remotePrefs.getInt("rebroadcast_txdelay_x100", 100).coerceIn(50, 200))
    }

    var baseline by remember(node.nodeId) { mutableStateOf<RemoteBaseline?>(null) }
    var statusText by remember(node.nodeId) {
        mutableStateOf(
            if (spanish) "Introduce la contraseña y carga los ajustes del nodo."
            else "Enter password and load live settings from the node."
        )
    }
    var busy by remember(node.nodeId) { mutableStateOf(false) }
    var pendingPacketId by remember(node.nodeId) { mutableIntStateOf(0) }
    var awaitingReport by remember(node.nodeId) { mutableStateOf(false) }
    var awaitingApply by remember(node.nodeId) { mutableStateOf(false) }

    fun hydrateFromConfig(cfg: NodeConfig) {
        remoteName = cfg.nodeName.ifBlank { remoteName }
        if (cfg.loraSf in 7..12) remoteSF = cfg.loraSf
        if (cfg.loraBw > 0f) remoteBW = cfg.loraBw
        if (cfg.loraTxPower != 0) remoteTxPower = cfg.loraTxPower
        remoteRegion = cfg.region
        remoteRole = cfg.nodeRole.coerceIn(0, 1)
        if (cfg.telemetryInterval > 0) remoteTelemetryInterval = cfg.telemetryInterval
        remoteScreenTimeout = cfg.screenTimeoutSecs
        remotePowerSave = cfg.powerSaveMode
        remotePositionPrecision = cfg.positionPrecision
        remoteGpsMode = cfg.gpsMode.coerceIn(0, 2)
        remoteGpsDutySecs = snapGpsDutyIntervalSecs(cfg.gpsDutyIntervalSecs)
        remoteFixedPosition = cfg.fixedPosition
        remoteFixedLat = cfg.fixedLatitude
        remoteFixedLon = cfg.fixedLongitude
        remoteFixedAlt = cfg.fixedAltitude
        remoteHop = if (cfg.meshHopLimit in 1..8) cfg.meshHopLimit else 4
        remoteTxdelay = when {
            cfg.rebroadcastTxdelayX100 in 50..200 -> cfg.rebroadcastTxdelayX100
            else -> 100
        }
        baseline = RemoteBaseline(
            name = remoteName,
            sf = remoteSF,
            bw = remoteBW,
            txPower = remoteTxPower,
            region = remoteRegion,
            role = remoteRole,
            telemetry = remoteTelemetryInterval,
            screen = remoteScreenTimeout,
            powerSave = remotePowerSave,
            posPrec = remotePositionPrecision,
            gpsMode = remoteGpsMode,
            gpsDutySecs = remoteGpsDutySecs,
            fixed = remoteFixedPosition,
            lat = remoteFixedLat,
            lon = remoteFixedLon,
            alt = remoteFixedAlt,
            hop = remoteHop,
            txdelay = remoteTxdelay
        )
    }

    fun computeMask(base: RemoteBaseline): Int {
        var mask = 0
        if (remoteName.trim() != base.name) mask = mask or ConfigApplyMask.NAME
        if (remoteSF != base.sf) mask = mask or ConfigApplyMask.SF
        if (remoteBW != base.bw) mask = mask or ConfigApplyMask.BW
        if (remoteTxPower != base.txPower) mask = mask or ConfigApplyMask.TX
        if (remoteRegion != base.region) mask = mask or ConfigApplyMask.REGION
        if (remoteRole != base.role) mask = mask or ConfigApplyMask.ROLE
        if (remoteTelemetryInterval != base.telemetry) mask = mask or ConfigApplyMask.TELEMETRY
        if (remoteScreenTimeout != base.screen) mask = mask or ConfigApplyMask.SCREEN
        if (remotePowerSave != base.powerSave) mask = mask or ConfigApplyMask.POWER_SAVE
        if (remotePositionPrecision != base.posPrec) mask = mask or ConfigApplyMask.POS_PREC
        if (remoteGpsMode != base.gpsMode || remoteGpsDutySecs != base.gpsDutySecs) {
            mask = mask or ConfigApplyMask.GPS_MODE
        }
        if (remoteFixedPosition != base.fixed || remoteFixedLat != base.lat ||
            remoteFixedLon != base.lon || remoteFixedAlt != base.alt
        ) {
            mask = mask or ConfigApplyMask.FIXED
        }
        if (remoteHop != base.hop) mask = mask or ConfigApplyMask.HOP
        if (remoteTxdelay != base.txdelay) mask = mask or ConfigApplyMask.TXDELAY
        return mask
    }

    fun requestLive() {
        if (remotePassword.isBlank()) {
            AppUiFeedback.show(
                if (spanish) "Contraseña requerida" else "Password required"
            )
            return
        }
        busy = true
        awaitingReport = true
        awaitingApply = false
        statusText = if (spanish) "Solicitando ajustes al nodo…" else "Requesting settings from node…"
        val id = viewModel.requestRemoteConfigReport(node.nodeId, remotePassword.trim())
        if (id == null) {
            busy = false
            awaitingReport = false
            statusText = if (spanish) "No se pudo enviar la solicitud." else "Could not send request."
        } else {
            pendingPacketId = id
        }
    }

    fun applyRemoteConfig() {
        val base = baseline
        if (base == null) {
            AppUiFeedback.show(
                if (spanish) "Carga primero los ajustes del nodo."
                else "Load live settings from the node first."
            )
            return
        }
        val roleToSend = remoteRole.coerceIn(0, 1)
        val latLonOk = remoteFixedLat in -90f..90f &&
            remoteFixedLon in -180f..180f &&
            !(remoteFixedLat == 0f && remoteFixedLon == 0f)
        if (remoteFixedPosition && !latLonOk) {
            AppUiFeedback.show(
                if (spanish) "Posición fija inválida — usa coordenadas reales (no 0,0)."
                else "Invalid fixed position — use real coordinates (not 0,0)."
            )
            return
        }
        val mask = computeMask(base)
        if (mask == 0) {
            AppUiFeedback.show(
                if (spanish) "Sin cambios que aplicar." else "No changes to apply."
            )
            return
        }
        busy = true
        awaitingApply = true
        awaitingReport = false
        statusText = if (spanish) "Enviando cambios…" else "Sending changes…"
        val id = viewModel.sendRemoteConfig(
            nodeId = node.nodeId,
            name = remoteName.trim(),
            password = remotePassword.trim(),
            sf = remoteSF,
            bw = remoteBW,
            txPower = remoteTxPower,
            region = remoteRegion,
            role = roleToSend,
            telemetryInterval = remoteTelemetryInterval,
            screenTimeout = remoteScreenTimeout,
            powerSaveMode = remotePowerSave,
            positionPrecision = remotePositionPrecision,
            gpsMode = remoteGpsMode,
            gpsDutyIntervalSecs = remoteGpsDutySecs,
            fixedPosition = remoteFixedPosition,
            fixedLatitude = remoteFixedLat,
            fixedLongitude = remoteFixedLon,
            fixedAltitude = remoteFixedAlt,
            meshHopLimit = remoteHop,
            rebroadcastTxdelayX100 = remoteTxdelay,
            applyMask = mask
        )
        if (id == null) {
            busy = false
            awaitingApply = false
            statusText = if (spanish) "No se pudo enviar." else "Could not send."
        } else {
            pendingPacketId = id
            // Prefs are written only after ConfigResult APPLIED / APPLIED_REBOOTING.
        }
    }

    fun persistRemotePrefs() {
        remotePrefs.edit().apply {
            putString("node_name", remoteName.trim())
            putInt("lora_sf", remoteSF)
            putFloat("lora_bw", remoteBW)
            putInt("lora_tx_power", remoteTxPower)
            putInt("region", remoteRegion)
            putInt("node_role", remoteRole.coerceIn(0, 1))
            putInt("telemetry_interval", remoteTelemetryInterval)
            putInt("screen_timeout", remoteScreenTimeout)
            putBoolean("power_save_mode", remotePowerSave)
            putInt("position_precision", remotePositionPrecision)
            putInt("gps_mode", remoteGpsMode)
            putInt("gps_duty_interval_secs", remoteGpsDutySecs)
            putBoolean("fixed_position", remoteFixedPosition)
            putFloat("fixed_latitude", remoteFixedLat)
            putFloat("fixed_longitude", remoteFixedLon)
            putInt("fixed_altitude", remoteFixedAlt)
            putInt("mesh_hop_limit", remoteHop)
            putInt("rebroadcast_txdelay_x100", remoteTxdelay)
            apply()
        }
    }

    LaunchedEffect(node.nodeId) {
        viewModel.remoteConfigReport
            .filter { it.nodeId == node.nodeId }
            .collect { report ->
                hydrateFromConfig(report.config)
                busy = false
                awaitingReport = false
                statusText = if (spanish) "Ajustes cargados del nodo." else "Live settings loaded."
            }
    }

    LaunchedEffect(node.nodeId) {
        viewModel.remoteConfigResult
            .filter { it.nodeId == node.nodeId }
            .collect { event ->
                if (pendingPacketId != 0 && event.requestPacketId != 0 &&
                    event.requestPacketId != pendingPacketId
                ) {
                    return@collect
                }
                busy = false
                awaitingReport = false
                awaitingApply = false
                statusText = when (event.status) {
                    ConfigResult.Status.REPORT_OK ->
                        if (spanish) "Informe enviado por el nodo…" else "Node sent report…"
                    ConfigResult.Status.APPLIED ->
                        if (spanish) "Aplicado (sin reinicio)." else "Applied (no reboot)."
                    ConfigResult.Status.APPLIED_REBOOTING ->
                        if (spanish) "Aplicado — el nodo se reinicia." else "Applied — node rebooting."
                    ConfigResult.Status.AUTH_FAILED ->
                        if (spanish) "Autenticación fallida." else "Authentication failed."
                    ConfigResult.Status.REJECTED_ROLE2 ->
                        if (spanish) "Rol Repetidor rechazado (sin BLE)." else "Repeater role rejected (no BLE)."
                    ConfigResult.Status.REJECTED_FIXED_POS ->
                        if (spanish) "Posición fija rechazada." else "Fixed position rejected."
                    else -> event.message.ifBlank {
                        if (spanish) "Respuesta: ${event.status}" else "Result: ${event.status}"
                    }
                }
                if (event.status == ConfigResult.Status.APPLIED_REBOOTING ||
                    event.status == ConfigResult.Status.APPLIED
                ) {
                    persistRemotePrefs()
                    AppUiFeedback.show(statusText)
                } else if (event.status == ConfigResult.Status.AUTH_FAILED ||
                    event.status == ConfigResult.Status.REJECTED_ROLE2 ||
                    event.status == ConfigResult.Status.REJECTED_FIXED_POS
                ) {
                    AppUiFeedback.show(statusText)
                }
            }
    }

    LaunchedEffect(busy, awaitingReport, awaitingApply, pendingPacketId) {
        if (!busy) return@LaunchedEffect
        delay(45_000)
        if (busy && (awaitingReport || awaitingApply)) {
            busy = false
            awaitingReport = false
            awaitingApply = false
            statusText = if (spanish)
                "Tiempo agotado — sin respuesta del nodo."
            else
                "Timed out — no response from node."
            AppUiFeedback.show(statusText)
        }
    }

    @Composable
    fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) AccentCyan else SurfaceDark)
                .clickable(enabled = !busy, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                label,
                color = if (selected) DarkBackground else TextLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = AccentCyan
                        )
                    }
                    Text(statusText, color = TextMuted, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    if (spanish) "Contraseña de admin" else "Admin password",
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { requestLive() },
                    enabled = !busy && remotePassword.isNotEmpty()
                ) {
                    Text(
                        if (spanish) "Cargar ajustes del nodo" else "Load settings from node",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (baseline == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (spanish)
                            "Los campos se habilitan tras cargar los ajustes en vivo."
                        else
                            "Fields unlock after live settings are loaded.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    return@Column
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (spanish) "Nombre" else "Custom Name", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = remoteName,
                    onValueChange = { if (it.length <= 16) remoteName = it },
                    singleLine = true,
                    colors = aetherTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (spanish) "Perfil de radio" else "Radio Profile", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(7 to 500f, 8 to 250f, 9 to 125f, 10 to 125f, 11 to 125f, 12 to 125f)) { (sf, bw) ->
                        val selected = remoteSF == sf && remoteBW == bw
                        Chip("SF$sf/${bw.toInt()}", selected) {
                            remoteSF = sf
                            remoteBW = bw
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (spanish) "Potencia TX" else "TX Power", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(14, 17, 20, 22).forEach { p ->
                        Chip("${p}dBm", remoteTxPower == p) { remoteTxPower = p }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (spanish) "Región" else "Region", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("US915", remoteRegion == 0) { remoteRegion = 0 }
                    Chip("EU868", remoteRegion == 1) { remoteRegion = 1 }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (spanish) "Rol" else "Role", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (spanish) "Cliente" else "Client", remoteRole == 0) { remoteRole = 0 }
                    Chip("Router", remoteRole == 1) { remoteRole = 1 }
                }
                Text(
                    if (spanish)
                        "Repetidor (sin BLE) solo por USB/Bluetooth local."
                    else
                        "Repeater (no BLE) only via USB / local Bluetooth.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (spanish) "Telemetría" else "Telemetry", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 120, 300).forEach { interval ->
                        Chip("${interval}s", remoteTelemetryInterval == interval) {
                            remoteTelemetryInterval = interval
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (spanish) "Límite de saltos" else "Hop limit", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 6, 8).forEach { h ->
                        Chip("$h", remoteHop == h) { remoteHop = h }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (spanish) "Ritmo de rebroadcast" else "Rebroadcast pace",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 150, 200).forEach { d ->
                        Chip("${d}%", remoteTxdelay == d) { remoteTxdelay = d }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (spanish) "GPS del nodo" else "Node GPS", color = TextMuted, fontSize = 11.sp)
                Text(
                    if (spanish)
                        "Periódico: enciende, obtiene ubicación, apaga. Ideal para nodos en el campo."
                    else
                        "Periodic: wake, get a fix, sleep. Best for leave-behind nodes.",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (spanish) "Siempre" else "On", remoteGpsMode == 0) { remoteGpsMode = 0 }
                    Chip(if (spanish) "Periódico" else "Periodic", remoteGpsMode == 2) { remoteGpsMode = 2 }
                    Chip(if (spanish) "Apagado" else "Off", remoteGpsMode == 1) { remoteGpsMode = 1 }
                }
                if (remoteGpsMode == 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (spanish) "Intervalo de despertar" else "Wake interval",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(300 to "5m", 900 to "15m", 1800 to "30m", 3600 to "60m").forEach { (secs, label) ->
                            Chip(label, remoteGpsDutySecs == secs) { remoteGpsDutySecs = secs }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (spanish) "Ahorro de energía" else "Power save", color = TextMuted, fontSize = 11.sp)
                    Switch(
                        checked = remotePowerSave,
                        onCheckedChange = { remotePowerSave = it },
                        enabled = !busy,
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (spanish) "Posición fija" else "Fixed position", color = TextMuted, fontSize = 11.sp)
                    Switch(
                        checked = remoteFixedPosition,
                        onCheckedChange = { remoteFixedPosition = it },
                        enabled = !busy,
                        modifier = Modifier.scale(0.8f)
                    )
                }
                if (remoteFixedPosition) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextField(
                        value = remoteFixedLat.toString(),
                        onValueChange = { remoteFixedLat = it.toFloatOrNull() ?: remoteFixedLat },
                        label = { Text(if (spanish) "Latitud" else "Latitude", color = TextMuted) },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextField(
                        value = remoteFixedLon.toString(),
                        onValueChange = { remoteFixedLon = it.toFloatOrNull() ?: remoteFixedLon },
                        label = { Text(if (spanish) "Longitud" else "Longitude", color = TextMuted) },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { applyRemoteConfig() },
                enabled = !busy && remotePassword.isNotEmpty() && baseline != null
            ) {
                Text(
                    if (spanish) "Aplicar cambios" else "Apply changes",
                    color = if (!busy && remotePassword.isNotEmpty() && baseline != null) AccentMint else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) {
                Text(t("Cancel", appLanguage), color = TextMuted)
            }
        },
        containerColor = SurfaceDark
    )
}
