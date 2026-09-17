package com.silentwolf75.aethermesh.ui.main

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silentwolf75.aethermesh.data.ConfigApplyMask
import com.silentwolf75.aethermesh.data.LocalConfigSavePolicy
import com.silentwolf75.aethermesh.data.MeshNode
import com.silentwolf75.aethermesh.data.MeshReplyPolicy
import com.silentwolf75.aethermesh.data.NodeSettingsPrefs
import com.silentwolf75.aethermesh.data.RadioRegionPolicy
import com.silentwolf75.aethermesh.data.RemoteConfigHydratePolicy
import com.silentwolf75.aethermesh.data.RemoteConfigResultAction
import com.silentwolf75.aethermesh.data.RemoteConfigResultPolicy
import com.silentwolf75.aethermesh.data.RemoteConfigSnapshot
import com.silentwolf75.aethermesh.proto.NodeConfig
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import com.silentwolf75.aethermesh.ui.components.aetherTextFieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

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
        context.getSharedPreferences(NodeSettingsPrefs.prefsName(node.nodeId), Context.MODE_PRIVATE)
    }

    var remoteName by remember(node.nodeId) {
        mutableStateOf(remotePrefs.getString(NodeSettingsPrefs.KEY_NODE_NAME, null) ?: node.name)
    }
    var remotePassword by remember(node.nodeId) { mutableStateOf("") }
    var remoteSF by remember(node.nodeId) {
        mutableIntStateOf(
            when {
                remotePrefs.contains(NodeSettingsPrefs.KEY_LORA_SF) ->
                    NodeSettingsPrefs.readLoraSf(remotePrefs)
                node.loraSf in 7..12 -> node.loraSf
                else -> NodeSettingsPrefs.DEFAULT_SF
            }
        )
    }
    var remoteBW by remember(node.nodeId) {
        mutableFloatStateOf(remotePrefs.getFloat(NodeSettingsPrefs.KEY_LORA_BW, NodeSettingsPrefs.DEFAULT_BW))
    }
    var remoteTxPower by remember(node.nodeId) {
        mutableIntStateOf(
            remotePrefs.getInt(NodeSettingsPrefs.KEY_LORA_TX_POWER, NodeSettingsPrefs.DEFAULT_TX_POWER)
        )
    }
    var remoteRegion by remember(node.nodeId) {
        mutableIntStateOf(
            when {
                remotePrefs.contains(NodeSettingsPrefs.KEY_REGION) ->
                    remotePrefs.getInt(NodeSettingsPrefs.KEY_REGION, 0)
                node.region >= 0 -> node.region
                else -> 0
            }
        )
    }
    var remoteRole by remember(node.nodeId) {
        mutableIntStateOf(remotePrefs.getInt(NodeSettingsPrefs.KEY_NODE_ROLE, 0).coerceIn(0, 1))
    }
    var remoteTelemetryInterval by remember(node.nodeId) {
        mutableIntStateOf(
            remotePrefs.getInt(
                NodeSettingsPrefs.KEY_TELEMETRY_INTERVAL,
                NodeSettingsPrefs.DEFAULT_TELEMETRY_SECS
            )
        )
    }
    var remotePositionPrecision by remember(node.nodeId) {
        mutableIntStateOf(remotePrefs.getInt(NodeSettingsPrefs.KEY_POSITION_PRECISION, 0))
    }
    var remoteGpsMode by remember(node.nodeId) {
        mutableIntStateOf(remotePrefs.getInt(NodeSettingsPrefs.KEY_GPS_MODE, 0).coerceIn(0, 2))
    }
    var remoteGpsDutySecs by remember(node.nodeId) {
        mutableIntStateOf(
            snapGpsDutyIntervalSecs(
                remotePrefs.getInt(NodeSettingsPrefs.KEY_GPS_DUTY_SECS, NodeSettingsPrefs.DEFAULT_GPS_DUTY_SECS)
            )
        )
    }
    var remoteScreenTimeout by remember(node.nodeId) {
        mutableIntStateOf(
            remotePrefs.getInt(NodeSettingsPrefs.KEY_SCREEN_TIMEOUT, NodeSettingsPrefs.DEFAULT_SCREEN_TIMEOUT)
        )
    }
    var remotePowerSave by remember(node.nodeId) {
        mutableStateOf(remotePrefs.getBoolean(NodeSettingsPrefs.KEY_POWER_SAVE, false))
    }
    var remoteFixedPosition by remember(node.nodeId) {
        mutableStateOf(remotePrefs.getBoolean(NodeSettingsPrefs.KEY_FIXED_POSITION, false))
    }
    var remoteFixedLat by remember(node.nodeId) {
        mutableFloatStateOf(remotePrefs.getFloat(NodeSettingsPrefs.KEY_FIXED_LAT, 0f))
    }
    var remoteFixedLon by remember(node.nodeId) {
        mutableFloatStateOf(remotePrefs.getFloat(NodeSettingsPrefs.KEY_FIXED_LON, 0f))
    }
    var remoteFixedAlt by remember(node.nodeId) {
        mutableIntStateOf(remotePrefs.getInt(NodeSettingsPrefs.KEY_FIXED_ALT, 0))
    }
    var remoteHop by remember(node.nodeId) {
        mutableIntStateOf(
            remotePrefs.getInt(NodeSettingsPrefs.KEY_MESH_HOP_LIMIT, NodeSettingsPrefs.DEFAULT_MESH_HOPS)
                .coerceIn(1, NodeSettingsPrefs.readMaxHopLimit(remotePrefs))
        )
    }
    var remoteTxdelay by remember(node.nodeId) {
        mutableIntStateOf(
            remotePrefs.getInt(
                NodeSettingsPrefs.KEY_REBROADCAST_TXDELAY,
                NodeSettingsPrefs.DEFAULT_TXDELAY_X100
            ).coerceIn(50, 200)
        )
    }

    var baseline by remember(node.nodeId) { mutableStateOf<RemoteConfigSnapshot?>(null) }
    var statusText by remember(node.nodeId) {
        mutableStateOf(RemoteConfigResultPolicy.promptEnterPassword(spanish))
    }
    var busy by remember(node.nodeId) { mutableStateOf(false) }
    var pendingPacketId by remember(node.nodeId) { mutableIntStateOf(0) }
    var awaitingReport by remember(node.nodeId) { mutableStateOf(false) }
    var awaitingApply by remember(node.nodeId) { mutableStateOf(false) }
    val isBleConnected by viewModel.isBleConnected.collectAsStateWithLifecycle()

    fun currentSnapshot(trimName: Boolean = false): RemoteConfigSnapshot = RemoteConfigSnapshot(
        name = if (trimName) remoteName.trim() else remoteName,
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

    fun hydrateFromConfig(cfg: NodeConfig) {
        val next = RemoteConfigHydratePolicy.merge(currentSnapshot(), cfg)
        remoteName = next.name
        remoteSF = next.sf
        remoteBW = next.bw
        remoteTxPower = next.txPower
        remoteRegion = next.region
        remoteRole = next.role
        remoteTelemetryInterval = next.telemetry
        remoteScreenTimeout = next.screen
        remotePowerSave = next.powerSave
        remotePositionPrecision = next.posPrec
        remoteGpsMode = next.gpsMode
        remoteGpsDutySecs = next.gpsDutySecs
        remoteFixedPosition = next.fixed
        remoteFixedLat = next.lat
        remoteFixedLon = next.lon
        remoteFixedAlt = next.alt
        remoteHop = next.hop
        remoteTxdelay = next.txdelay
        baseline = next
    }

    fun requestLive() {
        if (remotePassword.isBlank()) {
            AppUiFeedback.show(RemoteConfigResultPolicy.passwordRequired(spanish))
            return
        }
        busy = true
        awaitingReport = true
        awaitingApply = false
        statusText = RemoteConfigResultPolicy.requestingSettings(spanish)
        val id = viewModel.requestRemoteConfigReport(node.nodeId, remotePassword.trim())
        if (id == null) {
            busy = false
            awaitingReport = false
            statusText = RemoteConfigResultPolicy.requestSendFailed(spanish)
        } else {
            pendingPacketId = id
        }
    }

    fun applyRemoteConfig() {
        val base = baseline
        if (base == null) {
            AppUiFeedback.show(RemoteConfigResultPolicy.loadBaselineFirst(spanish))
            return
        }
        val roleToSend = remoteRole.coerceIn(0, 1)
        if (!LocalConfigSavePolicy.isAllowed(remoteFixedPosition, remoteFixedLat, remoteFixedLon)) {
            AppUiFeedback.show(LocalConfigSavePolicy.invalidFixedMessage(spanish))
            return
        }
        val mask = ConfigApplyMask.diff(base, currentSnapshot(trimName = true))
        if (mask == 0) {
            AppUiFeedback.show(RemoteConfigResultPolicy.noChanges(spanish))
            return
        }
        busy = true
        awaitingApply = true
        awaitingReport = false
        statusText = RemoteConfigResultPolicy.sendingChanges(spanish)
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
            statusText = RemoteConfigResultPolicy.applySendFailed(spanish)
        } else {
            pendingPacketId = id
            // Prefs are written only after ConfigResult APPLIED / APPLIED_REBOOTING.
        }
    }

    fun persistRemotePrefs() {
        remotePrefs.edit().apply {
            putString(NodeSettingsPrefs.KEY_NODE_NAME, remoteName.trim())
            putInt(NodeSettingsPrefs.KEY_LORA_SF, remoteSF)
            putFloat(NodeSettingsPrefs.KEY_LORA_BW, remoteBW)
            putInt(NodeSettingsPrefs.KEY_LORA_TX_POWER, remoteTxPower)
            putInt(NodeSettingsPrefs.KEY_REGION, remoteRegion)
            putInt(NodeSettingsPrefs.KEY_NODE_ROLE, remoteRole.coerceIn(0, 1))
            putInt(NodeSettingsPrefs.KEY_TELEMETRY_INTERVAL, remoteTelemetryInterval)
            putInt(NodeSettingsPrefs.KEY_SCREEN_TIMEOUT, remoteScreenTimeout)
            putBoolean(NodeSettingsPrefs.KEY_POWER_SAVE, remotePowerSave)
            putInt(NodeSettingsPrefs.KEY_POSITION_PRECISION, remotePositionPrecision)
            putInt(NodeSettingsPrefs.KEY_GPS_MODE, remoteGpsMode)
            putInt(NodeSettingsPrefs.KEY_GPS_DUTY_SECS, remoteGpsDutySecs)
            putBoolean(NodeSettingsPrefs.KEY_FIXED_POSITION, remoteFixedPosition)
            putFloat(NodeSettingsPrefs.KEY_FIXED_LAT, remoteFixedLat)
            putFloat(NodeSettingsPrefs.KEY_FIXED_LON, remoteFixedLon)
            putInt(NodeSettingsPrefs.KEY_FIXED_ALT, remoteFixedAlt)
            putInt(NodeSettingsPrefs.KEY_MESH_HOP_LIMIT, remoteHop)
            putInt(NodeSettingsPrefs.KEY_REBROADCAST_TXDELAY, remoteTxdelay)
            apply()
        }
    }

    LaunchedEffect(node.nodeId) {
        viewModel.remoteConfigReport
            .filter { it.nodeId == node.nodeId }
            .collect { report ->
                if (!RemoteConfigResultPolicy.acceptLiveReport(
                        awaitingReport = awaitingReport,
                        reportNodeId = report.nodeId,
                        expectedNodeId = node.nodeId
                    )
                ) {
                    return@collect
                }
                hydrateFromConfig(report.config)
                busy = false
                awaitingReport = false
                statusText = RemoteConfigResultPolicy.liveSettingsLoaded(spanish)
            }
    }

    LaunchedEffect(node.nodeId) {
        viewModel.remoteConfigResult
            .filter { it.nodeId == node.nodeId }
            .collect { event ->
                when (
                    val action = RemoteConfigResultPolicy.decide(
                        pendingPacketId = pendingPacketId,
                        requestPacketId = event.requestPacketId,
                        status = event.status,
                        message = event.message,
                        spanish = spanish
                    )
                ) {
                    RemoteConfigResultAction.IgnoreStale -> return@collect
                    is RemoteConfigResultAction.Handle -> {
                        busy = false
                        awaitingReport = false
                        awaitingApply = false
                        statusText = action.statusText
                        if (action.persistBaseline) {
                            // Capture applied values as the new baseline so a second
                            // Apply doesn't re-send the same mask after success.
                            baseline = currentSnapshot(trimName = true)
                            persistRemotePrefs()
                        }
                        if (action.showFeedback) {
                            AppUiFeedback.show(statusText)
                        }
                    }
                }
            }
    }

    LaunchedEffect(isBleConnected) {
        if (!isBleConnected && busy) {
            busy = false
            awaitingReport = false
            awaitingApply = false
            statusText = RemoteConfigResultPolicy.disconnectCancelled(spanish)
        }
    }

    LaunchedEffect(busy, awaitingReport, awaitingApply, pendingPacketId, remoteSF) {
        if (!busy) return@LaunchedEffect
        delay(MeshReplyPolicy.timeoutMs(remoteSF))
        if (busy && (awaitingReport || awaitingApply)) {
            busy = false
            awaitingReport = false
            awaitingApply = false
            statusText = MeshReplyPolicy.remoteTimedOut(spanish)
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
                    Chip(
                        RadioRegionPolicy.shortLabel(RadioRegionPolicy.US915),
                        remoteRegion == RadioRegionPolicy.US915
                    ) { remoteRegion = RadioRegionPolicy.US915 }
                    Chip(
                        RadioRegionPolicy.shortLabel(RadioRegionPolicy.EU868),
                        remoteRegion == RadioRegionPolicy.EU868
                    ) { remoteRegion = RadioRegionPolicy.EU868 }
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
