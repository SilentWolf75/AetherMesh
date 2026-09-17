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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentwolf75.aethermesh.data.ChannelConfig
import com.silentwolf75.aethermesh.data.ChannelInviteLink
import com.silentwolf75.aethermesh.data.ChannelPskPolicy
import com.silentwolf75.aethermesh.data.RadioRegionPolicy
import com.silentwolf75.aethermesh.ui.AppUiFeedback
import com.silentwolf75.aethermesh.ui.components.AetherSectionHeader

@Composable
fun ChannelSettings(
    privacyStatus: com.silentwolf75.aethermesh.data.ChannelPrivacyStatus,
    channelsList: List<ChannelConfig>,
    connectedNodeId: Long,
    appLanguage: String,
    onAddChannel: () -> Unit,
    onEditChannel: (ChannelConfig) -> Unit,
    onDeleteChannel: (ChannelConfig) -> Unit,
    onJoinChannel: () -> Unit
) {
    val context = LocalContext.current
    Text(
        text = privacyStatus.label(appLanguage == "Spanish"),
        color = if (privacyStatus == com.silentwolf75.aethermesh.data.ChannelPrivacyStatus.CONFIRMED) AccentMint else TextMuted,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )

    val currentRegion = remember(connectedNodeId) {
        val nodeKey = connectedNodeId
        val nPrefs = context.getSharedPreferences("node_settings_$nodeKey", Context.MODE_PRIVATE)
        nPrefs.getInt("region", 0)
    }
            val freqText = RadioRegionPolicy.frequencyCompact(currentRegion)

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AetherSectionHeader(
                    title = t("Channels", appLanguage),
                    trailing = "${channelsList.size}",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (appLanguage == "Spanish") "Frec: $freqText" else "Freq: $freqText",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (channelsList.isEmpty()) {
                        Text(
                            text = t("No channels configured yet.", appLanguage),
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        channelsList.forEachIndexed { index, channel ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onEditChannel(channel)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Slot number box
                                Box(
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E293B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = index.toString(),
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = channel.name,
                                            color = if (channel.isPrimary) AccentMint else TextLight,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (channel.isPrimary) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x204ADE80))
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Text(t("Primary", appLanguage), color = AccentMint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(
                                        "PSK: ${channel.psk.take(12)}${if (channel.psk.length > 12) "…" else ""}",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }

                                // Location status indicator
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = if (appLanguage == "Spanish")
                                        "Estado de ubicación"
                                    else
                                        "Location sharing status",
                                    tint = if (channel.positionEnabled) AccentCyan else TextMuted,
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                // Encryption status indicator
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = if (appLanguage == "Spanish")
                                        "Estado de cifrado"
                                    else
                                        "Encryption status",
                                    tint = if (ChannelPskPolicy.isCustom(channel.psk)) AccentMint else TextMuted,
                                    modifier = Modifier.size(18.dp).padding(end = 8.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                if (!channel.isPrimary) {
                                    IconButton(
                                        onClick = { onDeleteChannel(channel) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = if (appLanguage == "Spanish")
                                                "Eliminar canal"
                                            else
                                                "Delete channel",
                                            tint = AccentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (index < channelsList.lastIndex) {
                                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onAddChannel() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(t("Add Secondary Channel", appLanguage), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (appLanguage == "Spanish")
                        "Comparte el enlace del canal primario. Unirse añade un canal secundario con esa PSK."
                    else
                        "Share sends the primary channel link. Join adds it as a secondary channel with that PSK.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val primary = channelsList.firstOrNull { it.isPrimary }
                            if (primary != null) {
                                val shareText = ChannelInviteLink.buildShareUrl(primary)
                                try {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                        putExtra(
                                            android.content.Intent.EXTRA_SUBJECT,
                                            if (appLanguage == "Spanish") "Canal AetherMesh" else "AetherMesh Channel"
                                        )
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            send,
                                            if (appLanguage == "Spanish") "Compartir canal" else "Share channel"
                                        )
                                    )
                                } catch (_: Exception) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Channel Link", shareText))
                                    AppUiFeedback.show(if (appLanguage == "Spanish") "¡Enlace de canal copiado!" else "Channel link copied!", duration = SnackbarDuration.Short)
                                }
                            } else {
                                AppUiFeedback.show(if (appLanguage == "Spanish") "Crea un canal primario primero" else "Create a primary channel first", duration = SnackbarDuration.Short)
                            }
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = TextLight)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(t("Share Channel", appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = {
                            onJoinChannel()
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = TextLight)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(t("Join Channel", appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
}
