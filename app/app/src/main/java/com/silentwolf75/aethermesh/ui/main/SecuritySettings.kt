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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentwolf75.aethermesh.data.EcdhKeyPolicy
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import com.silentwolf75.aethermesh.ui.components.AetherSectionHeader

@Composable
fun SecuritySettings(
    viewModel: MainScreenViewModel,
    appLanguage: String
) {
    val context = LocalContext.current
    var ecdhKeys by remember { mutableStateOf(viewModel.getOrCreateEcdhKeys()) }
    var showPrivateKey by remember { mutableStateOf(false) }
    var showRegenKeysDialog by remember { mutableStateOf(false) }
    var peerPublicKey by remember { mutableStateOf("") }
    val keyFingerprint = remember(ecdhKeys.first) { EcdhKeyPolicy.fingerprint(ecdhKeys.first) }
    val matchCode = remember(ecdhKeys.first, peerPublicKey) {
        EcdhKeyPolicy.verificationCode(ecdhKeys.first, peerPublicKey)
    }

    // --- 3. SECURITY & DM KEYS CARD ---
    AetherSectionHeader(
        title = t("Security & DM Keys", appLanguage),
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
            if (keyFingerprint.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    t("Key check code", appLanguage) + ": " + keyFingerprint,
                    color = TextMuted,
                    fontSize = 11.sp
                )
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

            Text(t("Their Public Key (Base64)", appLanguage), color = TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = peerPublicKey,
                onValueChange = { peerPublicKey = it },
                placeholder = {
                    Text(t("Paste a contact's public key to get a matching check code.", appLanguage), color = TextMuted, fontSize = 11.sp)
                },
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
            if (matchCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    t("Matching check code", appLanguage) + ": " + matchCode,
                    color = AccentMint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
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
                        val shareTxt = EcdhKeyPolicy.exportText(ecdhKeys.first, ecdhKeys.second)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Security Keys Export", shareTxt))
                        AppUiFeedback.show(t("Keys copied to clipboard", appLanguage), duration = SnackbarDuration.Short)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(t("Export Keys", appLanguage), fontSize = 11.sp)
                }
            }
        }
    }

    if (showRegenKeysDialog) {
        AlertDialog(
            onDismissRequest = { showRegenKeysDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    ecdhKeys = viewModel.regenerateEcdhKeys()
                    showPrivateKey = false
                    showRegenKeysDialog = false
                    AppUiFeedback.show(t("Keys regenerated.", appLanguage), duration = SnackbarDuration.Short)
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
}
