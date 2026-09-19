package com.silentwolf75.aethermesh.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silentwolf75.aethermesh.data.FirmwareCatalog
import com.silentwolf75.aethermesh.data.FirmwareFreshnessPolicy
import com.silentwolf75.aethermesh.data.MeshNode
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import kotlinx.coroutines.launch

@Composable
fun FirmwareUpdateSettings(
    viewModel: MainScreenViewModel,
    isConnected: Boolean,
    isDeviceAuthenticated: Boolean,
    connectedNode: MeshNode?,
    appLanguage: String
) {
    val context = LocalContext.current
            // --- FIRMWARE UPDATE VIEW ---
            // Heltec/ESP32: our in-app chunked OTA of a .bin into the inactive slot.
            // RAK/nRF52: node reboots into its DFU bootloader and the Nordic DFU
            // library streams the .zip package to it (Meshtastic-style).
            val otaState by viewModel.otaState.collectAsStateWithLifecycle()
            val firmwareFreshness by viewModel.firmwareFreshness.collectAsStateWithLifecycle()
            var otaFileBytes by remember { mutableStateOf<ByteArray?>(null) }
            var otaFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var otaFileName by remember { mutableStateOf("") }
            var otaPickError by remember { mutableStateOf<String?>(null) }
            var showOtaWarning by remember { mutableStateOf(false) }
            val otaModelHint = connectedNode?.model
                ?: viewModel.connectedDeviceName
                ?: connectedNode?.name
            val expectedBoardId = FirmwareCatalog.boardIdForModel(connectedNode?.model)
            val boardLabel = expectedBoardId
                ?: connectedNode?.model?.takeIf { it.isNotBlank() }
                ?: (if (appLanguage == "Spanish") "desconocido" else "unknown")
            val isRakNode = isRakOtaTarget(
                connectedNode?.model,
                viewModel.connectedDeviceName,
                connectedNode?.name
            )
            val isEspOtaNode = isEspOtaTarget(
                connectedNode?.model,
                viewModel.connectedDeviceName,
                connectedNode?.name
            )
            // Connected radios that haven't reported a model yet still get the OTA UI;
            // only truly unknown / unsupported boards are blocked.
            val otaSupported = isEspOtaNode || isRakNode ||
                (isConnected && connectedNode?.model.isNullOrBlank()) ||
                (isConnected && connectedNode?.model.equals("Unknown", ignoreCase = true) == true) ||
                (isConnected && connectedNode == null)
            val githubArtifact by viewModel.githubFirmware.collectAsStateWithLifecycle()
            val githubStatus by viewModel.githubFirmwareStatus.collectAsStateWithLifecycle()
            val githubBusy by viewModel.githubFirmwareBusy.collectAsStateWithLifecycle()
            val githubProgress by viewModel.githubDownloadProgress.collectAsStateWithLifecycle()
            val firmwareChannel by viewModel.firmwareChannel.collectAsStateWithLifecycle()
            val firmwareScope = rememberCoroutineScope()
            LaunchedEffect(otaModelHint, isConnected, otaSupported, firmwareChannel) {
                if (isConnected && otaSupported) {
                    val online = FirmwareCatalog.isPhoneDataAvailable(context)
                    viewModel.refreshGithubFirmware(otaModelHint, networkAvailable = online)
                }
            }
            val otaFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "firmware"
                        if (bytes != null && bytes.isNotEmpty()) {
                            val treatAsRak = isRakNode
                            val err = isValidOtaPayload(bytes, name, treatAsRak, expectedBoardId)
                            if (err != null) {
                                otaFileBytes = null
                                otaFileUri = null
                                otaFileName = ""
                                otaPickError = localizeOtaPickError(err, appLanguage)
                                AppUiFeedback.show(localizeOtaPickError(err, appLanguage), duration = SnackbarDuration.Long)
                            } else {
                                otaFileBytes = bytes
                                otaFileUri = uri
                                otaFileName = name
                                otaPickError = null
                                viewModel.resetOtaState()
                            }
                        }
                    } catch (e: Exception) {
                        AppUiFeedback.show(
                            if (appLanguage == "Spanish") "No se pudo leer el archivo: ${e.message}"
                            else "Could not read file: ${e.message}",
                            duration = SnackbarDuration.Short
                        )
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
                    val awaitingFw = FirmwareFreshnessPolicy.isChecking(
                        firmwareFreshness, connectedNode?.nodeId
                    )
                    Text(
                        text = formatInstalledFirmwareLabel(
                            connectedNode?.firmwareVersion,
                            awaitingFw,
                            appLanguage
                        ),
                        color = if (awaitingFw) AccentAmber else TextMuted,
                        fontSize = 12.sp
                    )
                    if (otaState.suspectRollback ||
                        (otaState.error && otaState.status.startsWith("Update may not have applied"))
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            localizeOtaStatus(otaState.status, appLanguage),
                            color = AccentAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val installedFw = if (awaitingFw) "" else connectedNode?.firmwareVersion.orEmpty()
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
                        Text(localizeOtaStatus(otaState.status, appLanguage), color = TextLight, fontSize = 12.sp)
                        if (otaState.expectedVersion.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (appLanguage == "Spanish")
                                    "Destino: ${otaState.expectedVersion}"
                                else
                                    "Target: ${otaState.expectedVersion}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
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
                                localizeOtaStatus(otaState.status, appLanguage),
                                color = when {
                                    otaState.suspectRollback -> AccentAmber
                                    otaState.error -> AccentRed
                                    otaState.done -> AccentMint
                                    else -> TextMuted
                                },
                                fontSize = 12.sp,
                                fontWeight = if (otaState.done || otaState.error) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (otaState.done && otaState.expectedVersion.isNotBlank() && !otaState.error) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (appLanguage == "Spanish")
                                        "Esperado: ${otaState.expectedVersion}"
                                    else
                                        "Expected: ${otaState.expectedVersion}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (otaState.error) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    otaRollbackGuidance(isRakNode, appLanguage == "Spanish"),
                                    color = AccentAmber,
                                    fontSize = 11.sp
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
                                        if (appLanguage == "Spanish") "Abrir flasher web (USB)" else "Open web flasher (USB)",
                                        color = AccentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else if (!otaSupported) {
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Este modelo de nodo no soporta OTA inalámbrica. Usa el flasher web por USB."
                            else
                                "This node model doesn't support wireless OTA. Use the web flasher over USB.",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp
                        )
                    } else {
                        if (connectedNode?.model.isNullOrBlank() && !isRakNode && !isEspOtaNode) {
                            Text(
                                text = if (appLanguage == "Spanish")
                                    "Esperando el modelo del nodo… puedes elegir un .bin/.zip mientras tanto."
                                else
                                    "Waiting for node model… you can still pick a .bin/.zip meanwhile.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        // Stable = GitHub Releases; Latest = Pages ota-manifest
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Catálogo GitHub (opcional): necesita datos del teléfono. La malla BLE/LoRa sigue local en modo avión. También puedes elegir un .bin/.zip guardado."
                            else
                                "GitHub catalog (optional): needs phone data. BLE/LoRa mesh stays local in airplane mode. You can always pick a saved .bin/.zip.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (appLanguage == "Spanish") "Canal de firmware" else "Firmware channel",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = if (appLanguage == "Spanish")
                                "Estable: probado en hardware. Beta: las correcciones más nuevas, aún en pruebas."
                            else
                                "Stable: tested on hardware. Beta: the newest fixes, still being tested.",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val releaseSelected = firmwareChannel == FirmwareCatalog.Channel.RELEASE
                            val betaSelected = firmwareChannel == FirmwareCatalog.Channel.BETA
                            OutlinedButton(
                                onClick = {
                                    viewModel.setFirmwareChannel(FirmwareCatalog.Channel.RELEASE)
                                    viewModel.refreshGithubFirmware(
                                        otaModelHint,
                                        networkAvailable = FirmwareCatalog.isPhoneDataAvailable(context)
                                    )
                                },
                                enabled = !githubBusy && !otaState.active,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (releaseSelected) AccentCyan.copy(alpha = 0.18f) else DarkBackground,
                                    contentColor = TextLight
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (releaseSelected) AccentCyan else BorderDark
                                )
                            ) {
                                Text(
                                    if (appLanguage == "Spanish") "Estable" else "Stable",
                                    fontSize = 12.sp,
                                    fontWeight = if (releaseSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.setFirmwareChannel(FirmwareCatalog.Channel.BETA)
                                    viewModel.refreshGithubFirmware(
                                        otaModelHint,
                                        networkAvailable = FirmwareCatalog.isPhoneDataAvailable(context)
                                    )
                                },
                                enabled = !githubBusy && !otaState.active,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (betaSelected) AccentAmber.copy(alpha = 0.18f) else DarkBackground,
                                    contentColor = TextLight
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (betaSelected) AccentAmber else BorderDark
                                )
                            ) {
                                Text(
                                    "Beta",
                                    fontSize = 12.sp,
                                    fontWeight = if (betaSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.refreshGithubFirmware(
                                    otaModelHint,
                                    networkAvailable = FirmwareCatalog.isPhoneDataAvailable(context)
                                )
                            },
                            enabled = !githubBusy && !otaState.active,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkBackground,
                                contentColor = TextLight
                            )
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (appLanguage == "Spanish") "Buscar actualizaciones (necesita datos)"
                                else "Check for updates (needs phone data)",
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(FirmwareCatalog.GITHUB_RELEASES_WEB)
                                        )
                                    )
                                } catch (_: Exception) { }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (appLanguage == "Spanish") "Abrir Releases en GitHub"
                                else "Open GitHub Releases",
                                color = AccentCyan,
                                fontSize = 11.sp
                            )
                        }
                        if (githubStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val offlineStatus = githubStatus == FirmwareCatalog.OFFLINE_CATALOG_STATUS ||
                                githubStatus.startsWith("Offline")
                            Text(
                                localizeGithubFirmwareStatus(githubStatus, appLanguage),
                                color = if (offlineStatus) AccentAmber else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (githubBusy && githubProgress in 1..99) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { githubProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = AccentMint,
                                trackColor = BorderDark
                            )
                        }
                        val artifact = githubArtifact
                        if (artifact != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val availableLabel = artifact.displayVersion?.let { ver ->
                                if (appLanguage == "Spanish") "Disponible en catálogo: $ver"
                                else "Available in catalog: $ver"
                            }
                            if (availableLabel != null) {
                                Text(
                                    availableLabel,
                                    color = AccentMint,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (appLanguage == "Spanish")
                                        "No instalado hasta que termine OTA/DFU y se confirme la versión."
                                    else
                                        "Not installed until OTA/DFU finishes and the version is confirmed.",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                artifact.name,
                                color = TextLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${artifact.file} · ${artifact.size / 1024} kB",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    firmwareScope.launch {
                                        val result = viewModel.downloadGithubFirmware(context, artifact)
                                        if (result != null) {
                                            val treatAsRak = isRakNode
                                            val err = isValidOtaPayload(
                                                result.bytes,
                                                result.fileName,
                                                treatAsRak,
                                                expectedBoardId
                                            )
                                            if (err != null) {
                                                otaPickError = localizeOtaPickError(err, appLanguage)
                                                AppUiFeedback.show(
                                                    localizeOtaPickError(err, appLanguage),
                                                    duration = SnackbarDuration.Long
                                                )
                                            } else {
                                                otaFileBytes = result.bytes
                                                otaFileUri = result.cacheUri
                                                otaFileName = result.fileName
                                                otaPickError = null
                                                viewModel.resetOtaState()
                                                AppUiFeedback.show(
                                                    if (appLanguage == "Spanish")
                                                        "Firmware de GitHub listo para flashear."
                                                    else
                                                        "GitHub firmware ready to flash."
                                                )
                                            }
                                        } else {
                                            AppUiFeedback.show(
                                                localizeGithubFirmwareStatus(
                                                    githubStatus.ifBlank { "Download failed" },
                                                    appLanguage
                                                ),
                                                duration = SnackbarDuration.Long
                                            )
                                        }
                                    }
                                },
                                enabled = !githubBusy && !otaState.active && isDeviceAuthenticated,
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan,
                                    contentColor = DarkBackground,
                                    disabledContainerColor = BorderDark,
                                    disabledContentColor = TextMuted
                                )
                            ) {
                                Text(
                                    if (appLanguage == "Spanish")
                                        "Descargar desde GitHub"
                                    else
                                        "Download from GitHub",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
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
                                    when {
                                        isRakNode ->
                                            if (appLanguage == "Spanish") "Elegir paquete .zip (DFU)" else "Choose firmware .zip (DFU package)"
                                        isEspOtaNode ->
                                            if (appLanguage == "Spanish") "Elegir archivo .bin" else "Choose firmware .bin"
                                        else ->
                                            if (appLanguage == "Spanish") "Elegir .bin o .zip" else "Choose firmware .bin or .zip"
                                    }
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
                                localizeOtaStatus(otaState.status, appLanguage),
                                color = when {
                                    otaState.suspectRollback -> AccentAmber
                                    otaState.error -> AccentRed
                                    otaState.done -> AccentMint
                                    else -> TextMuted
                                },
                                fontSize = 12.sp,
                                fontWeight = if (otaState.done || otaState.error) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (otaState.done && otaState.expectedVersion.isNotBlank() && !otaState.error) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (appLanguage == "Spanish")
                                        "Esperado: ${otaState.expectedVersion}"
                                    else
                                        "Expected: ${otaState.expectedVersion}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (otaState.error) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    otaRollbackGuidance(isRakNode, appLanguage == "Spanish"),
                                    color = AccentAmber,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = if (appLanguage == "Spanish")
                    "El primer firmware con OTA debe instalarse por USB; después es inalámbrico. Heltec/T-Deck/CrowPanel usan .bin (nunca .zip DFU); RAK usa el paquete .zip (nunca .bin ESP). Estable y Beta vienen de GitHub y necesitan datos del teléfono; en modo avión usa un archivo local. Se rechazan placas/archivos cruzados y se verifica tamaño/SHA-256 cuando hay catálogo."
                else
                    "The first OTA-capable firmware must be flashed over USB; after that, updates are wireless. Heltec/T-Deck/CrowPanel take the .bin (never a Nordic DFU .zip); RAK takes the .zip DFU package (never an ESP .bin). Stable and Beta both come from GitHub and need phone data; in airplane mode use a local file. Cross-board/wrong-format packages are refused; size/SHA-256 are checked when the catalog provides them.",
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
                            Text(
                                if (appLanguage == "Spanish")
                                    "• Asegúrate de que el nodo esté cargado o con USB."
                                else
                                    "• Make sure the node is charged or on USB.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                if (appLanguage == "Spanish")
                                    "• Mantén el nodo cerca del teléfono."
                                else
                                    "• Keep the node close to your phone.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                if (appLanguage == "Spanish")
                                    "• No cierres la app durante la actualización."
                                else
                                    "• Do not close the app during the update.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                if (appLanguage == "Spanish")
                                    "• Verifica que este build coincida con el hardware ($boardLabel)."
                                else
                                    "• Verify this build matches the hardware ($boardLabel).",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                if (appLanguage == "Spanish")
                                    "La imagen se verifica (tamaño / SHA-256 cuando está disponible) antes de reiniciar. Si falla la transferencia, el nodo suele conservar el firmware actual; si no responde, recupera por USB."
                                else
                                    "The image is verified (size / SHA-256 when available) before reboot. If the transfer fails, the node usually keeps its current firmware; if it will not reconnect, recover over USB.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showOtaWarning = false
                            val expected = githubArtifact?.expectedFirmwareVersionLabel()
                                ?: otaFileName.takeIf { it.isNotBlank() }?.let { name ->
                                    // Local pick: best-effort from filename (…-1.3.0-b75ad7c.zip)
                                    val m = Regex(
                                        """(\d+\.\d+\.\d+)-([0-9a-f]{7})""",
                                        RegexOption.IGNORE_CASE
                                    ).find(name)
                                    m?.value
                                }
                            val useRakDfu = isRakNode
                            if (useRakDfu) {
                                otaFileUri?.let { viewModel.startRakDfuUpdate(it, expected) }
                            } else {
                                otaFileBytes?.let { viewModel.startFirmwareUpdate(it, expected) }
                            }
                        }) {
                            Text(
                                if (appLanguage == "Spanish") "Sé lo que hago." else "I know what I'm doing.",
                                color = AccentMint,
                                fontWeight = FontWeight.Bold
                            )
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
