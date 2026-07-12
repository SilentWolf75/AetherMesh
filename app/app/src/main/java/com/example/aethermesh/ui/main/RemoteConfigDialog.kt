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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import com.example.aethermesh.data.MeshNode
import com.example.aethermesh.ui.components.aetherTextFieldColors

@Composable
fun RemoteConfigDialog(
    node: MeshNode,
    viewModel: MainScreenViewModel,
    appLanguage: String = "English",
    useImperialUnits: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val spanish = appLanguage == "Spanish"
    // Prefill from the last settings this phone pushed to that node (if any),
    // so re-opening the dialog doesn't silently revert the node to defaults.
    val remotePrefs = remember(node.nodeId) {
        context.getSharedPreferences("node_settings_${node.nodeId}", Context.MODE_PRIVATE)
    }
    var remoteName by remember(node.nodeId) {
        mutableStateOf(remotePrefs.getString("node_name", null) ?: node.name)
    }
    var remotePassword by remember(node.nodeId) { mutableStateOf("") }
    var remoteSF by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("lora_sf", 9)) }
    var remoteBW by remember(node.nodeId) { mutableFloatStateOf(remotePrefs.getFloat("lora_bw", 125f)) }
    var remoteTxPower by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("lora_tx_power", 22)) }
    var remoteRegion by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("region", 0)) }
    var remoteRole by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("node_role", 0)) }
    var remoteTelemetryInterval by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("telemetry_interval", 60)) }
    var remotePositionPrecision by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("position_precision", 0)) }
    var remoteGpsEnabled by remember(node.nodeId) { mutableStateOf(remotePrefs.getInt("gps_mode", 0) == 0) }
    var remoteScreenTimeout by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("screen_timeout", 30)) }
    var remotePowerSave by remember(node.nodeId) { mutableStateOf(remotePrefs.getBoolean("power_save_mode", false)) }
    var remoteFixedPosition by remember(node.nodeId) { mutableStateOf(remotePrefs.getBoolean("fixed_position", false)) }
    var remoteFixedLat by remember(node.nodeId) { mutableFloatStateOf(remotePrefs.getFloat("fixed_latitude", 0f)) }
    var remoteFixedLon by remember(node.nodeId) { mutableFloatStateOf(remotePrefs.getFloat("fixed_longitude", 0f)) }
    var remoteFixedAlt by remember(node.nodeId) { mutableIntStateOf(remotePrefs.getInt("fixed_altitude", 0)) }
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
            screenTimeout = remoteScreenTimeout,
            powerSaveMode = remotePowerSave,
            positionPrecision = remotePositionPrecision,
            gpsMode = if (remoteGpsEnabled) 0 else 1,
            fixedPosition = remoteFixedPosition,
            fixedLatitude = remoteFixedLat,
            fixedLongitude = remoteFixedLon,
            fixedAltitude = remoteFixedAlt
        )
        if (success) {
            remotePrefs.edit().apply {
                putString("node_name", remoteName.trim())
                putInt("lora_sf", remoteSF)
                putFloat("lora_bw", remoteBW)
                putInt("lora_tx_power", remoteTxPower)
                putInt("region", remoteRegion)
                putInt("node_role", remoteRole)
                putInt("telemetry_interval", remoteTelemetryInterval)
                putInt("screen_timeout", remoteScreenTimeout)
                putBoolean("power_save_mode", remotePowerSave)
                putInt("position_precision", remotePositionPrecision)
                putInt("gps_mode", if (remoteGpsEnabled) 0 else 1)
                putBoolean("fixed_position", remoteFixedPosition)
                putFloat("fixed_latitude", remoteFixedLat)
                putFloat("fixed_longitude", remoteFixedLon)
                putInt("fixed_altitude", remoteFixedAlt)
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
        onDismiss()
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
        onDismissRequest = { onDismiss() },
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
            TextButton(onClick = { onDismiss() }) {
                Text(t("Cancel", appLanguage), color = TextMuted)
            }
        },
        containerColor = SurfaceDark
    )
}
