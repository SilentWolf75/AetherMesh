package com.silentwolf75.aethermesh.ui.main

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.silentwolf75.aethermesh.data.AppUiPrefs
import com.silentwolf75.aethermesh.data.ChatMessage
import com.silentwolf75.aethermesh.data.NotificationGatePolicy
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import com.silentwolf75.aethermesh.ui.components.AetherSectionHeader

@Composable
fun DeveloperSettings(
    viewModel: MainScreenViewModel,
    sharedPrefs: SharedPreferences,
    appLanguage: String,
    consoleMessages: List<ChatMessage>,
    diagnosticLogs: List<String>,
    onBackupSettings: () -> Unit,
    onRestoreSettings: () -> Unit,
    onExportMigration: () -> Unit,
    onImportMigration: () -> Unit
) {
    val context = LocalContext.current
    var bgAlertsEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(AppUiPrefs.BG_ALERTS, true))
    }
    var showConsoleLogs by remember { mutableStateOf(false) }
    var showIntroDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showResetNodesDialog by remember { mutableStateOf(false) }

            // --- 5. DATA & LOGS MANAGEMENT CARD ---
            AetherSectionHeader(
                title = t("Data & Logs Management", appLanguage),
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
                    modifier = Modifier.fillMaxWidth().clickable { onBackupSettings() }.padding(vertical = 10.dp),
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

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onRestoreSettings()
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Restore Device Settings", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Import configuration from JSON file", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onExportMigration()
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            t("Export App Data (package migration)", appLanguage),
                            color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            t("Messages, nodes, passwords — before uninstalling old app", appLanguage),
                            color = TextMuted, fontSize = 11.sp
                        )
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onImportMigration()
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            t("Import App Data (package migration)", appLanguage),
                            color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            t("Restore from JSON after installing com.silentwolf75.aethermesh", appLanguage),
                            color = TextMuted, fontSize = 11.sp
                        )
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
                    NotificationGatePolicy.canPost(
                        android.os.Build.VERSION.SDK_INT,
                        ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                var notifPermGranted by remember { mutableStateOf(notificationsGranted) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            notifPermGranted = NotificationGatePolicy.canPost(
                                android.os.Build.VERSION.SDK_INT,
                                ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            )
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
                            sharedPrefs.edit().putBoolean(AppUiPrefs.BG_ALERTS, isChecked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentCyan,
                            checkedTrackColor = AccentCyan.copy(alpha = 0.5f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BorderDark
                        )
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceRaised)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            if (appLanguage == "Spanish")
                                "Sesión BLE"
                            else
                                "BLE session",
                            color = TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (appLanguage == "Spanish")
                                "Solo un teléfono debe controlar la radio por Bluetooth. El teléfono enlazado aparece en Conexión como el dueño de la sesión."
                            else
                                "Only one phone should control the radio over Bluetooth. The linked phone is shown on Connection as the BLE session owner.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
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
                            Text(
                                t("Recent chat packet sizes (not a full system log)", appLanguage),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
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
                            .height(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (diagnosticLogs.isEmpty() && consoleMessages.isEmpty()) {
                            Text(
                                if (appLanguage == "Spanish")
                                    "Sin eventos recientes. Errores BLE/malla y paquetes de chat aparecen aquí."
                                else
                                    "No recent events. BLE/mesh errors and chat packets appear here.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        } else {
                            diagnosticLogs.takeLast(30).forEach { line ->
                                Text(line, color = AccentAmber, fontSize = 11.sp)
                            }
                            consoleMessages.takeLast(12).forEach { msg ->
                                Text(
                                    if (appLanguage == "Spanish")
                                        "Paquete de 0x${msg.senderId.toString(16).uppercase()}: ${msg.content.length} bytes"
                                    else
                                        "Packet from 0x${msg.senderId.toString(16).uppercase()}: ${msg.content.length} bytes",
                                    color = TextLight,
                                    fontSize = 11.sp
                                )
                            }
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
                        Text(
                            "v${com.silentwolf75.aethermesh.BuildConfig.VERSION_NAME} (${com.silentwolf75.aethermesh.BuildConfig.VERSION_CODE})",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

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
                        AppUiFeedback.show(if (appLanguage == "Spanish") "Historial de chat borrado" else "Chat history cleared", duration = SnackbarDuration.Short)
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
                        AppUiFeedback.show(if (appLanguage == "Spanish") "Directorio de nodos reiniciado" else "Nodes directory reset", duration = SnackbarDuration.Short)
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
}
