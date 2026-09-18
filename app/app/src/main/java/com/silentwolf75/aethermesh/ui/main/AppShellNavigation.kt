package com.silentwolf75.aethermesh.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentwolf75.aethermesh.theme.AccentAmber
import com.silentwolf75.aethermesh.theme.AccentCyanDim
import com.silentwolf75.aethermesh.theme.AccentRed
import com.silentwolf75.aethermesh.theme.TextLight
import com.silentwolf75.aethermesh.theme.AccentCyan
import com.silentwolf75.aethermesh.theme.AccentMint
import com.silentwolf75.aethermesh.theme.AccentOrange
import com.silentwolf75.aethermesh.theme.AccentSteel
import com.silentwolf75.aethermesh.theme.BorderDark
import com.silentwolf75.aethermesh.theme.SurfaceRaised
import com.silentwolf75.aethermesh.theme.TextMuted

/**
 * Primary navigation modeled on Meshtastic (Chats / Nodes / Map / Settings)
 * with MeshCore-style connect-first: when offline, only Connection (+ Settings) appear.
 */
@Composable
fun AetherAppNavigation(
    selectedTab: TabItem,
    appLanguage: String,
    useRail: Boolean,
    isConnected: Boolean,
    /** When Connection is open while linked, keep this primary tab visually selected. */
    linkedHighlightTab: TabItem = TabItem.CHATS,
    chatsUnreadCount: Int = 0,
    onTabSelected: (TabItem) -> Unit
) {
    data class NavTab(
        val tab: TabItem,
        val icon: ImageVector,
        val labelKey: String,
        val color: Color
    )

    val tabs = if (isConnected) {
        listOf(
            NavTab(TabItem.CHATS, Icons.AutoMirrored.Filled.Chat, "Chats", AccentCyan),
            NavTab(TabItem.NODES, Icons.Default.Hub, "Nodes", AccentMint),
            NavTab(TabItem.MAP, Icons.Default.Map, "Map", AccentSteel),
            NavTab(TabItem.SETTINGS, Icons.Default.Settings, "Settings", AccentAmber)
        )
    } else {
        // MeshCore scanner-first: get online before mesh screens.
        listOf(
            NavTab(TabItem.CONNECTION, Icons.Default.SettingsInputAntenna, "Connection", AccentOrange),
            NavTab(TabItem.SETTINGS, Icons.Default.Settings, "Settings", AccentAmber)
        )
    }

    // Connection-while-linked is a utility overlay: keep the previous primary tab highlighted.
    val selectedForBar = when {
        !isConnected -> selectedTab
        selectedTab == TabItem.CONNECTION ->
            linkedHighlightTab.takeUnless { it == TabItem.CONNECTION } ?: TabItem.CHATS
        else -> selectedTab
    }

    if (useRail) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(92.dp)
                .background(SurfaceRaised)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { item ->
                val selected = selectedForBar == item.tab
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) AccentCyanDim else Color.Transparent)
                        .clickable { onTabSelected(item.tab) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = t(item.labelKey, appLanguage),
                            tint = if (selected) AccentCyan else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        if (item.tab == TabItem.CHATS && chatsUnreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(start = 14.dp, bottom = 14.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    if (chatsUnreadCount > 9) "9+" else "$chatsUnreadCount",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = t(item.labelKey, appLanguage),
                        color = if (selected) TextLight else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(com.silentwolf75.aethermesh.theme.SurfaceDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderDark.copy(alpha = 0.5f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { item ->
                    val selected = selectedForBar == item.tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onTabSelected(item.tab) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 60.dp, height = 32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) AccentCyanDim else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = t(item.labelKey, appLanguage),
                                tint = if (selected) AccentCyan else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            if (item.tab == TabItem.CONNECTION && !isConnected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(start = 14.dp, bottom = 14.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(AccentAmber)
                                )
                            }
                            if (item.tab == TabItem.CHATS && chatsUnreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(start = 12.dp, bottom = 12.dp)
                                        .clip(CircleShape)
                                        .background(AccentRed)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        if (chatsUnreadCount > 9) "9+" else "$chatsUnreadCount",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = t(item.labelKey, appLanguage),
                            color = if (selected) TextLight else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
