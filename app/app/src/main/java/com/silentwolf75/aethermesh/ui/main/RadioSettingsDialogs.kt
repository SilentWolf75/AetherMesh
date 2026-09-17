package com.silentwolf75.aethermesh.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentwolf75.aethermesh.data.AuthRequestApply
import com.silentwolf75.aethermesh.ui.AppUiFeedback

@Composable
fun RadioSettingsDialogs(
    viewModel: MainScreenViewModel,
    appLanguage: String,
    spanishUi: Boolean,
    showChangePasswordDialog: Boolean,
    onDismissPassword: () -> Unit,
    showRepeaterConfirmDialog: Boolean,
    onDismissRepeater: () -> Unit,
    onConfirmRepeater: () -> Unit,
    showDeployConfirmDialog: Boolean,
    deployConfirmProfile: DeployProfile?,
    onDismissDeploy: () -> Unit,
    onConfirmDeploy: () -> Unit,
    role: Int,
    nodeGpsMode: Int,
    gpsDutyIntervalSecs: Int,
    powerSaveModeEnabled: Boolean,
    telemetryIntervalSecs: Int,
    txPower: Int
) {
    if (showChangePasswordDialog) {
        var currentPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            viewModel.passwordChangeResult.collect { ok ->
                if (ok) {
                    onDismissPassword()
                    AppUiFeedback.show(
                        if (appLanguage == "Spanish") "Contraseña del dispositivo actualizada"
                        else "Device password updated",
                        duration = SnackbarDuration.Short
                    )
                } else {
                    passwordError = true
                }
            }
        }
        AlertDialog(
            onDismissRequest = onDismissPassword,
            title = { Text(t("Change Device Password", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        if (appLanguage == "Spanish")
                            "Ingrese la contraseña actual y una nueva contraseña para este nodo de hardware."
                        else
                            "Enter current password and a new password for this hardware node.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            passwordError = false
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
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            passwordError = false
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
                    if (passwordError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (appLanguage == "Spanish")
                                "Contraseña actual incorrecta o error al actualizar."
                            else
                                "Incorrect current password or update failed.",
                            color = AccentRed,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val curr = currentPassword.trim()
                        val new = newPassword.trim()
                        if (AuthRequestApply.canChangePassword(curr, new)) {
                            passwordError = false
                            if (!viewModel.changeDevicePassword(curr, new)) {
                                passwordError = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                ) {
                    Text(t("Change", appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPassword) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showRepeaterConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissRepeater,
            title = { Text(t("Enable Repeater Mode?", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
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
                    onClick = onConfirmRepeater,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextLight)
                ) {
                    Text(t("Apply & Disconnect", appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRepeater) {
                    Text(t("Cancel", appLanguage), color = TextLight)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showDeployConfirmDialog) {
        val profile = deployConfirmProfile
        AlertDialog(
            onDismissRequest = onDismissDeploy,
            title = {
                Text(
                    if (spanishUi) "Confirmar perfil de despliegue" else "Confirm deploy profile",
                    color = TextLight,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (profile != null) {
                            if (spanishUi) profile.labelEs else profile.labelEn
                        } else "",
                        color = AccentMint,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        DeployConfirmCopy.summary(
                            spanish = spanishUi,
                            role = role,
                            gpsMode = nodeGpsMode,
                            dutySecs = gpsDutyIntervalSecs,
                            powerSave = powerSaveModeEnabled,
                            telemetrySecs = telemetryIntervalSecs,
                            txPower = txPower
                        ),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (profile != null) {
                            if (spanishUi) profile.hintEs else profile.hintEn
                        } else "",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    if (role == 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (spanishUi)
                                "El repetidor apaga BLE tras aplicar; perderás la conexión."
                            else
                                "Repeater turns BLE off after apply; you will lose the connection.",
                            color = AccentAmber,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (spanishUi) "El nodo se reiniciará al aplicar." else "The node will reboot on apply.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeploy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (role == 2) AccentRed else AccentCyan,
                        contentColor = if (role == 2) TextLight else DarkBackground
                    )
                ) {
                    Text(
                        if (role == 2) t("Apply & Disconnect", appLanguage)
                        else t("Apply Settings", appLanguage)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeploy) {
                    Text(t("Cancel", appLanguage), color = TextLight)
                }
            },
            containerColor = SurfaceDark
        )
    }
}
