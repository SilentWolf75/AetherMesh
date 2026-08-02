package com.example.aethermesh.ui.main

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
import com.example.aethermesh.theme.AccentAmber
import com.example.aethermesh.theme.AccentCyan
import com.example.aethermesh.theme.AccentMint
import com.example.aethermesh.theme.AccentOrange
import com.example.aethermesh.theme.AccentSteel
import com.example.aethermesh.theme.BorderDark
import com.example.aethermesh.theme.SurfaceRaised
import com.example.aethermesh.theme.TextMuted

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
                        .background(if (selected) item.color.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onTabSelected(item.tab) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = t(item.labelKey, appLanguage),
                        tint = if (selected) item.color else item.color.copy(alpha = 0.42f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = t(item.labelKey, appLanguage),
                        color = if (selected) item.color else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
                .background(SurfaceRaised)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderDark.copy(alpha = 0.7f))
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
                            .background(if (selected) item.color.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { onTabSelected(item.tab) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = t(item.labelKey, appLanguage),
                                tint = if (selected) item.color else item.color.copy(alpha = 0.45f),
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
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = t(item.labelKey, appLanguage),
                            color = if (selected) item.color else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
