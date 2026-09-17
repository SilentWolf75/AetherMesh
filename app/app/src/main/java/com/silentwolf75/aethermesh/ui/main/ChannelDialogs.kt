package com.silentwolf75.aethermesh.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentwolf75.aethermesh.data.ChannelConfig
import com.silentwolf75.aethermesh.data.ChannelInviteLink
import com.silentwolf75.aethermesh.data.ChannelJoinApply
import com.silentwolf75.aethermesh.data.ChannelJoinKind
import com.silentwolf75.aethermesh.data.ChannelPskPolicy
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import com.silentwolf75.aethermesh.ui.components.aetherTextFieldColors

@Composable
fun ChannelSettingsDialogs(
    viewModel: MainScreenViewModel,
    appLanguage: String,
    spanishUi: Boolean,
    channelsList: List<ChannelConfig>,
    onChannelsChanged: (List<ChannelConfig>) -> Unit,
    showAddChannelDialog: Boolean,
    onDismissAdd: () -> Unit,
    showEditChannelDialog: Boolean,
    editingChannel: ChannelConfig?,
    onDismissEdit: () -> Unit,
    showImportChannelDialog: Boolean,
    importChannelLinkInput: String,
    onImportInputChange: (String) -> Unit,
    onDismissImport: () -> Unit,
    channelPendingDelete: ChannelConfig?,
    onDismissDelete: () -> Unit
) {
    channelPendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { onDismissDelete() },
            title = {
                Text(
                    if (appLanguage == "Spanish") "Eliminar canal" else "Delete channel",
                    color = TextLight,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (appLanguage == "Spanish")
                        "¿Eliminar «${pending.name}»? Los mensajes locales del canal no se borran."
                    else
                        "Remove “${pending.name}”? Local channel messages are kept.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChannel(pending.id)
                        onChannelsChanged(viewModel.getChannelsList())
                        onDismissDelete()
                        AppUiFeedback.show(t("Channel deleted.", appLanguage), duration = SnackbarDuration.Short)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextLight)
                ) {
                    Text(if (appLanguage == "Spanish") "Eliminar" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissDelete() }) {
                    Text(t("Cancel", appLanguage), color = TextLight)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showAddChannelDialog) {
        var newChannelName by remember { mutableStateOf("") }
        var newChannelPsk by remember { mutableStateOf(ChannelPskPolicy.generate()) }
        val nameValid = newChannelName.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = { onDismissAdd() },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.insertChannel(
                            ChannelConfig(
                                name = newChannelName.trim(),
                                psk = newChannelPsk,
                                isPrimary = false
                            )
                        )
                        onChannelsChanged(viewModel.getChannelsList())
                        onDismissAdd()
                        AppUiFeedback.show(t("Channel added.", appLanguage), duration = SnackbarDuration.Short)
                    },
                    enabled = nameValid
                ) { Text(t("Save", appLanguage), color = if (nameValid) AccentMint else TextMuted) }
            },
            dismissButton = {
                TextButton(onClick = { onDismissAdd() }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            title = { Text(t("Add Secondary Channel", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        if (appLanguage == "Spanish")
                            "Crea un canal secundario. Todos los nodos necesitan el mismo nombre y PSK."
                        else
                            "Creates a secondary channel. Every node needs the same name and PSK.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(t("Channel Name", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    TextField(
                        value = newChannelName,
                        onValueChange = { if (it.length <= ChannelPskPolicy.MAX_NAME) newChannelName = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                if (appLanguage == "Spanish") "p. ej. Equipo-Sendero" else "e.g. Trail-Crew",
                                color = TextMuted
                            )
                        },
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(t("PSK Key (Base64)", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            newChannelPsk,
                            color = AccentMint,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (appLanguage == "Spanish")
                                "Regenerar PSK"
                            else
                                "Regenerate PSK",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp).clickable {
                                newChannelPsk = ChannelPskPolicy.generate()
                            }
                        )
                    }
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showEditChannelDialog && editingChannel != null) {
        val channel = editingChannel
        var chanName by remember { mutableStateOf(channel.name) }
        var chanPsk by remember { mutableStateOf(channel.psk) }
        var uplink by remember { mutableStateOf(channel.uplinkEnabled) }
        var downlink by remember { mutableStateOf(channel.downlinkEnabled) }
        var position by remember { mutableStateOf(channel.positionEnabled) }
        var precise by remember { mutableStateOf(channel.preciseLocation) }
        var precision by remember { mutableStateOf(channel.precisionMiles) }
        
        AlertDialog(
            onDismissRequest = { onDismissEdit() },
            title = {
                Text(
                    text = t("Channels", appLanguage), 
                    color = TextLight, 
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(t("Channel Name", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    TextField(
                        value = chanName,
                        onValueChange = { if (it.length <= ChannelPskPolicy.MAX_NAME) chanName = it },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(t("PSK Key (Base64)", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = chanPsk,
                            color = AccentMint,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (appLanguage == "Spanish") "Regenerar PSK" else "Regenerate PSK",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp).clickable {
                                chanPsk = ChannelPskPolicy.generate()
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Switch Rows
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("Uplink enabled", appLanguage), color = TextLight, fontSize = 13.sp)
                        Switch(
                            checked = uplink,
                            onCheckedChange = { uplink = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = AccentMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("Downlink enabled", appLanguage), color = TextLight, fontSize = 13.sp)
                        Switch(
                            checked = downlink,
                            onCheckedChange = { downlink = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = AccentMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("Position enabled", appLanguage), color = TextLight, fontSize = 13.sp)
                        Switch(
                            checked = position,
                            onCheckedChange = { position = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = AccentMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }
                    
                    if (position) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t("Precise location", appLanguage), color = TextLight, fontSize = 13.sp)
                            Switch(
                                checked = precise,
                                onCheckedChange = { precise = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBackground,
                                    checkedTrackColor = AccentMint,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = BorderDark
                                )
                            )
                        }
                        
                        if (!precise) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = t("Location Fuzzing Precision", appLanguage),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = precision,
                                onValueChange = { precision = it },
                                valueRange = 0.5f..5.0f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentMint,
                                    activeTrackColor = AccentMint,
                                    inactiveTrackColor = BorderDark
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "± ${"%.1f".format(precision)} mi",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = channel.copy(
                            name = chanName.trim(),
                            psk = chanPsk,
                            uplinkEnabled = uplink,
                            downlinkEnabled = downlink,
                            positionEnabled = position,
                            preciseLocation = precise,
                            precisionMiles = if (position && !precise) precision else 0.0f
                        )
                        viewModel.updateChannel(updated)
                        onChannelsChanged(viewModel.getChannelsList())
                        onDismissEdit()
                    },
                    enabled = chanName.trim().isNotEmpty()
                ) {
                    Text(t("Save", appLanguage), color = AccentMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissEdit() }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showImportChannelDialog) {
    val parsedJoin = remember(importChannelLinkInput) {
        ChannelInviteLink.parse(importChannelLinkInput)
    }
        AlertDialog(
            onDismissRequest = { onDismissImport() },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val parsed = ChannelInviteLink.parse(importChannelLinkInput)
                                ?: throw Exception("Invalid URI scheme")
                            val plan = ChannelJoinApply.plan(channelsList, parsed)
                            val name = plan.config.name
                            if (plan.kind == ChannelJoinKind.PRIMARY_UPDATE) {
                                viewModel.updateChannel(plan.config)
                            } else {
                                viewModel.insertChannel(plan.config)
                            }
                            onChannelsChanged(viewModel.getChannelsList())
                            onDismissImport()
                            AppUiFeedback.show(
                                when (plan.kind) {
                                    ChannelJoinKind.PRIMARY_UPDATE ->
                                        if (spanishUi) "Canal principal «$name» actualizado (PSK/flags)"
                                        else "Primary channel \"$name\" updated (PSK/flags)"
                                    ChannelJoinKind.SECONDARY_UPDATE ->
                                        if (spanishUi) "Canal secundario «$name» actualizado"
                                        else "Secondary channel \"$name\" updated"
                                    ChannelJoinKind.SECONDARY_INSERT ->
                                        if (spanishUi) "Canal secundario «$name» añadido"
                                        else "Secondary channel \"$name\" added"
                                },
                                duration = SnackbarDuration.Short
                            )
                        } catch (e: Exception) {
                            AppUiFeedback.show(
                                if (spanishUi) "Enlace de canal no válido"
                                else "Invalid channel link",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    enabled = parsedJoin != null
                ) {
                    Text(
                        t("Join", appLanguage),
                        color = if (parsedJoin != null) AccentMint else TextMuted
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissImport() }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            title = { Text(t("Join Channel", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        if (spanishUi)
                            "Pega un enlace AetherMesh. Se añadirá como canal secundario."
                        else
                            "Paste an AetherMesh link. It will be added as a secondary channel.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(t("Paste AetherMesh Channel Link", appLanguage), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = importChannelLinkInput,
                        onValueChange = onImportInputChange,
                        singleLine = false,
                        maxLines = 3,
                        placeholder = {
                            Text(
                                if (spanishUi) "https://aethermesh.org/join#…"
                                else "https://aethermesh.org/join#...",
                                color = TextMuted
                            )
                        },
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    parsedJoin?.let { (name, psk, _) ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            if (spanishUi) "Vista previa" else "Preview",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (spanishUi) "Nombre: $name" else "Name: $name",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                        Text(
                            if (spanishUi)
                                "PSK: ${if (psk.isNotEmpty()) "presente (${psk.length} car.)" else "ninguna"}"
                            else
                                "PSK: ${if (psk.isNotEmpty()) "present (${psk.length} chars)" else "none"}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (importChannelLinkInput.trim().isNotEmpty() && parsedJoin == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (spanishUi) "Enlace no reconocido todavía"
                            else "Link not recognized yet",
                            color = AccentAmber,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            containerColor = SurfaceDark
        )
    }
}
