package com.silentwolf75.aethermesh.ui.main

import android.content.SharedPreferences
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import com.silentwolf75.aethermesh.data.AppUiPrefs
import com.silentwolf75.aethermesh.data.ChatMessage
import com.silentwolf75.aethermesh.ui.components.AetherSectionHeader

@Composable
fun PreferencesSettings(
    viewModel: MainScreenViewModel,
    sharedPrefs: SharedPreferences,
    appLanguageState: MutableState<String>,
    appThemeState: MutableState<String>,
    useImperialUnitsSettingState: MutableState<Boolean>,
    consoleMessages: List<ChatMessage>,
    diagnosticLogs: List<String>
) {
    val context = LocalContext.current
    var appLanguage by appLanguageState
    var appTheme by appThemeState
    var useImperialUnitsSetting by useImperialUnitsSettingState
    var isExpandedTheme by remember { mutableStateOf(false) }
    var isExpandedLanguage by remember { mutableStateOf(false) }

            // --- 4. APP PREFERENCES CARD ---
            AetherSectionHeader(
                title = t("App Preferences", appLanguage),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Theme Choice
                Text(t("Theme", appLanguage), color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isExpandedTheme = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(t(appTheme, appLanguage))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                        }
                    }
                    DropdownMenu(expanded = isExpandedTheme, onDismissRequest = { isExpandedTheme = false }, modifier = Modifier.background(SurfaceDark)) {
                        AppUiPrefs.THEMES.forEach { theme ->
                            DropdownMenuItem(text = { Text(t(theme, appLanguage), color = TextLight) }, onClick = {
                                appTheme = theme
                                sharedPrefs.edit().putString(AppUiPrefs.THEME, theme).apply()
                                isExpandedTheme = false
                            })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Language Choice
                Text(t("Language", appLanguage), color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isExpandedLanguage = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = TextLight)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(t(appLanguage, appLanguage))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentCyan)
                        }
                    }
                    DropdownMenu(expanded = isExpandedLanguage, onDismissRequest = { isExpandedLanguage = false }, modifier = Modifier.background(SurfaceDark)) {
                        AppUiPrefs.LANGUAGES.forEach { lang ->
                            DropdownMenuItem(text = { Text(t(lang, appLanguage), color = TextLight) }, onClick = {
                                appLanguage = lang
                                sharedPrefs.edit().putString(AppUiPrefs.LANGUAGE, lang).apply()
                                isExpandedLanguage = false
                            })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(t("Distance Units", appLanguage), color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (useImperialUnitsSetting)
                        t("Imperial (Miles, Feet)", appLanguage)
                    else
                        t("Metric (Kilometers, Meters)", appLanguage),
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        true to t("Imperial", appLanguage),
                        false to t("Metric", appLanguage)
                    ).forEach { (imperial, label) ->
                        val selected = useImperialUnitsSetting == imperial
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
                                .clickable {
                                    useImperialUnitsSetting = imperial
                                    sharedPrefs.edit().putBoolean(AppUiPrefs.IMPERIAL, imperial).apply()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) AccentCyan else TextLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CSV Exports
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        exportRangeTestLogsToCsv(context, viewModel.getAllRangeTestLogs(), viewModel.nodes.value.associate { it.nodeId to (it.latitude.toDouble() to it.longitude.toDouble()) }, appLanguage)
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Export rangetest packets", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Export range pings to CSV and copy", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val allMsg = consoleMessages
                        exportAllPacketsToCsv(context, allMsg, appLanguage)
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t("Export all packets", appLanguage), color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Export full message list to CSV and copy", appLanguage), color = TextMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        exportAfterActionReport(
                            context = context,
                            messages = viewModel.getAllChatMessages(),
                            nodes = viewModel.nodes.value,
                            appLanguage = appLanguage,
                            diagnosticLines = diagnosticLogs
                        )
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = AccentMint, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (appLanguage == "Spanish") "Exportar post-evento (chat + nodos)"
                            else "Export after-action (chat + nodes)",
                            color = TextLight,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (appLanguage == "Spanish")
                                "Solo local / hoja de compartir — sin nube ni MQTT"
                            else
                                "Local share sheet only — no cloud upload, no MQTT",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
}
