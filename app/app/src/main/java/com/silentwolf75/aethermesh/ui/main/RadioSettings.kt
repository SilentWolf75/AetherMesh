package com.silentwolf75.aethermesh.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentwolf75.aethermesh.data.RadioRegionPolicy
import com.silentwolf75.aethermesh.ui.components.AetherSectionHeader
import com.silentwolf75.aethermesh.ui.components.aetherTextFieldColors

@Composable
fun RadioSettings(
    isConnected: Boolean,
    appLanguage: String,
    nodeNameState: MutableState<String>,
    nodeShortNameState: MutableState<String>,
    sfState: MutableIntState,
    bwState: MutableFloatState,
    txPowerState: MutableIntState,
    regionState: MutableIntState,
    roleState: MutableIntState,
    nodeGpsModeState: MutableIntState,
    gpsDutyIntervalSecsState: MutableIntState,
    telemetryIntervalSecsState: MutableIntState,
    screenTimeoutSecsState: MutableIntState,
    powerSaveModeEnabledState: MutableState<Boolean>,
    connectedModel: String? = null,
    onApply: () -> Unit,
    onRequestRepeaterConfirm: () -> Unit,
    onRequestDeployConfirm: (DeployProfile) -> Unit,
    onRequestPasswordChange: () -> Unit
) {
    var nodeName by nodeNameState
    var nodeShortName by nodeShortNameState
    var sf by sfState
    var bw by bwState
    var txPower by txPowerState
    var region by regionState
    var role by roleState
    var nodeGpsMode by nodeGpsModeState
    var gpsDutyIntervalSecs by gpsDutyIntervalSecsState
    var telemetryIntervalSecs by telemetryIntervalSecsState
    var screenTimeoutSecs by screenTimeoutSecsState
    var powerSaveModeEnabled by powerSaveModeEnabledState
    var isExpandedSF by remember { mutableStateOf(false) }
    var isExpandedBW by remember { mutableStateOf(false) }
    var isExpandedRegion by remember { mutableStateOf(false) }
    var isExpandedRole by remember { mutableStateOf(false) }

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
                                Text(RadioRegionPolicy.labelWithFrequency(region))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                            }
                        }
                        DropdownMenu(
                            expanded = isExpandedRegion,
                            onDismissRequest = { isExpandedRegion = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        RadioRegionPolicy.labelWithFrequency(RadioRegionPolicy.US915),
                                        color = TextLight
                                    )
                                },
                                onClick = {
                                    region = RadioRegionPolicy.US915
                                    isExpandedRegion = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        RadioRegionPolicy.labelWithFrequency(RadioRegionPolicy.EU868),
                                        color = TextLight
                                    )
                                },
                                onClick = {
                                    region = RadioRegionPolicy.EU868
                                    isExpandedRegion = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Deploy role presets (Leave-behind ≈ serial DEPLOY_LB)
                    Text(
                        text = if (appLanguage == "Spanish") "Perfil de despliegue" else "Deploy profile",
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (appLanguage == "Spanish")
                            "Ajusta rol, GPS, ahorro y telemetría. Pulsa Aplicar para enviar (el nodo reinicia). DEPLOY_LB por USB sigue disponible."
                        else
                            "Sets role, GPS, power-save, and telemetry. Tap Apply to send (node reboots). Serial DEPLOY_LB still works.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DEPLOY_PROFILES.forEach { profile ->
                            val selected = role == profile.role &&
                                nodeGpsMode == profile.gpsMode &&
                                powerSaveModeEnabled == profile.powerSave &&
                                gpsDutyIntervalSecs == profile.gpsDutyIntervalSecs &&
                                telemetryIntervalSecs == profile.telemetryIntervalSecs &&
                                (profile.txPower == null || txPower == profile.txPower)
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
                                    .clickable {
                                        role = profile.role
                                        nodeGpsMode = profile.gpsMode
                                        gpsDutyIntervalSecs = profile.gpsDutyIntervalSecs
                                        powerSaveModeEnabled = profile.powerSave
                                        telemetryIntervalSecs = profile.telemetryIntervalSecs
                                        profile.txPower?.let { txPower = it }
                                        if (profile.powerSave && screenTimeoutSecs > 10) {
                                            screenTimeoutSecs = 10
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (appLanguage == "Spanish") profile.labelEs else profile.labelEn,
                                    color = if (selected) AccentMint else TextLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    val activeDeploy = DEPLOY_PROFILES.firstOrNull { p ->
                        role == p.role && nodeGpsMode == p.gpsMode && powerSaveModeEnabled == p.powerSave
                    }
                    if (activeDeploy != null) {
                        Text(
                            text = if (appLanguage == "Spanish") activeDeploy.hintEs else activeDeploy.hintEn,
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (powerSaveModeEnabled || role == 1 || role == 2) {
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Tras reinicio con Ahorro / dejar atrás: pulsa el botón del nodo y luego escanea — BLE anuncia ~5 min."
                            else
                                "After reboot with Battery Saver / leave-behind: press the device button, then scan — BLE advertises ~5 min.",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
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
                    val txMax = com.silentwolf75.aethermesh.data.TxPowerPolicy.maxDbm(connectedModel, region)
                    val txMin = com.silentwolf75.aethermesh.data.TxPowerPolicy.MIN_DBM
                    Text(
                        text = if (appLanguage == "Spanish")
                            "Potencia en la antena. Máximo $txMax dBm para esta placa y región."
                        else
                            "Power at the antenna. Up to $txMax dBm for this board and region.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = txPower.coerceIn(txMin, txMax).toFloat(),
                        onValueChange = { txPower = it.toInt() },
                        valueRange = txMin.toFloat()..txMax.toFloat(),
                        steps = (txMax - txMin - 1).coerceAtLeast(0),
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
                            val matched = DEPLOY_PROFILES.firstOrNull { p ->
                                role == p.role &&
                                    nodeGpsMode == p.gpsMode &&
                                    powerSaveModeEnabled == p.powerSave &&
                                    gpsDutyIntervalSecs == p.gpsDutyIntervalSecs &&
                                    telemetryIntervalSecs == p.telemetryIntervalSecs &&
                                    (p.txPower == null || txPower == p.txPower)
                            }
                            when {
                                matched != null -> {
                                    onRequestDeployConfirm(matched)
                                }
                                role == 2 -> onRequestRepeaterConfirm()
                                else -> onApply()
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
                                onRequestPasswordChange()
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
