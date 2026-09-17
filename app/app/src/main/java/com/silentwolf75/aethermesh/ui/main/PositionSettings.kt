package com.silentwolf75.aethermesh.ui.main

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentwolf75.aethermesh.data.MeshNode
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import com.silentwolf75.aethermesh.ui.components.AetherSectionHeader

@Composable
fun PositionSettings(
    connectedNode: MeshNode?,
    isConnected: Boolean,
    appLanguage: String,
    useImperialUnitsSetting: Boolean,
    nodeGpsModeState: MutableIntState,
    gpsDutyIntervalSecsState: MutableIntState,
    telemetryIntervalSecsState: MutableIntState,
    positionPrecisionMState: MutableIntState,
    screenTimeoutSecsState: MutableIntState,
    powerSaveModeEnabledState: MutableState<Boolean>,
    fixedPositionEnabledState: MutableState<Boolean>,
    fixedLatInputState: MutableState<String>,
    fixedLonInputState: MutableState<String>,
    fixedAltInputState: MutableState<String>,
    enablePhoneGpsSharingState: MutableState<Boolean>,
    onApply: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE)
    var nodeGpsMode by nodeGpsModeState
    var gpsDutyIntervalSecs by gpsDutyIntervalSecsState
    var telemetryIntervalSecs by telemetryIntervalSecsState
    var positionPrecisionM by positionPrecisionMState
    var screenTimeoutSecs by screenTimeoutSecsState
    var powerSaveModeEnabled by powerSaveModeEnabledState
    var fixedPositionEnabled by fixedPositionEnabledState
    var fixedLatInput by fixedLatInputState
    var fixedLonInput by fixedLonInputState
    var fixedAltInput by fixedAltInputState
    var enablePhoneGpsSharing by enablePhoneGpsSharingState
    var isExpandedTelemetry by remember { mutableStateOf(false) }
    var isExpandedPosPrecision by remember { mutableStateOf(false) }
    var isExpandedScreenTimeout by remember { mutableStateOf(false) }
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

                    val spanishGps = appLanguage == "Spanish"
                    val hasLock = connectedNode != null && hasValidPosition(connectedNode.latitude, connectedNode.longitude)
                    val nodeGps = connectedNode?.gps ?: com.silentwolf75.aethermesh.data.TelemetryGps()
                    val gpsBadge = com.silentwolf75.aethermesh.data.GpsStatusPolicy.badge(nodeGps.state, hasLock, nodeGpsMode)
                    val statusLabel = com.silentwolf75.aethermesh.data.GpsStatusPolicy.badgeLabel(
                        gpsBadge, nodeGps.satellitesUsed, spanishGps
                    )
                    val statusOk = gpsBadge == com.silentwolf75.aethermesh.data.GpsBadge.FIX
                    val statusMuted = gpsBadge == com.silentwolf75.aethermesh.data.GpsBadge.OFF ||
                        gpsBadge == com.silentwolf75.aethermesh.data.GpsBadge.SLEEPING ||
                        gpsBadge == com.silentwolf75.aethermesh.data.GpsBadge.NO_MODULE ||
                        gpsBadge == com.silentwolf75.aethermesh.data.GpsBadge.LAST_KNOWN
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

                    // GNSS rows (firmware with telemetry GPS fields only)
                    val satellitesText = com.silentwolf75.aethermesh.data.GpsStatusPolicy.satellitesText(
                        nodeGps.state, nodeGps.satellitesUsed, nodeGps.satellitesInView, spanishGps
                    )
                    val hdopText = com.silentwolf75.aethermesh.data.GpsStatusPolicy.hdopText(nodeGps.hdopX10)
                    val sourceText = com.silentwolf75.aethermesh.data.GpsStatusPolicy.sourceText(
                        com.silentwolf75.aethermesh.data.GpsStatusPolicy.source(nodeGps.state, nodeGps.positionSource),
                        nodeGps.fixAgeSecs,
                        spanishGps
                    )
                    listOfNotNull(
                        satellitesText?.let { (if (spanishGps) "Satélites" else "Satellites") to it },
                        hdopText?.let { (if (spanishGps) "Precisión (HDOP)" else "Accuracy (HDOP)") to it },
                        sourceText?.let { (if (spanishGps) "Origen de posición" else "Position source") to it }
                    ).forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = label,
                                color = TextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = value,
                                color = TextLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

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
                                        "Pantalla máx. 10s; telemetría más lenta; apaga el anuncio BLE tras 5 min sin conexión. Pulsa el botón del nodo, luego escanea — BLE anuncia ~5 min."
                                    else
                                        "Caps screen to 10s, slows telemetry, stops BLE advertising after 5 min idle. Press the device button, then scan — BLE advertises ~5 min.",
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
                                onApply()
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

