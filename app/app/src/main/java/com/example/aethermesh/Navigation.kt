package com.example.aethermesh

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.aethermesh.ui.main.AccentCyan
import com.example.aethermesh.ui.main.AccentMint
import com.example.aethermesh.ui.main.MainScreen
import com.example.aethermesh.ui.main.MainScreenViewModel
import com.example.aethermesh.ui.main.NodeDetailsScreen
import com.example.aethermesh.ui.main.RangeTestDialog
import com.example.aethermesh.ui.main.RemoteConfigDialog
import com.example.aethermesh.ui.main.SurfaceDark
import com.example.aethermesh.ui.main.TextLight
import com.example.aethermesh.ui.main.TextMuted
import com.example.aethermesh.ui.main.getShortName
import com.example.aethermesh.ui.main.t
import com.example.aethermesh.ui.components.aetherTextFieldColors

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)
    val context = LocalContext.current
    val app = context.applicationContext as AetherMeshApplication
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(app.repository) }
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val pendingRangeTestId by viewModel.pendingRangeTestTargetId.collectAsStateWithLifecycle()
    val pendingRemoteConfigId by viewModel.pendingRemoteConfigNodeId.collectAsStateWithLifecycle()
    val isRangeTestActive by viewModel.isRangeTestActive.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
    val appLanguage = prefs.getString("app_language", "English") ?: "English"
    val useImperialUnits = prefs.getBoolean("use_imperial_units", true)
    val spanish = appLanguage == "Spanish"
    val activeRangeTarget = nodes.find { it.nodeId == viewModel.rangeTestTargetId }
        ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (viewModel.rangeTestTargetId and 0xFFFFFFFFL) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isRangeTestActive && pendingRangeTestId == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentCyan.copy(alpha = 0.18f))
                        .clickable {
                            val id = viewModel.rangeTestTargetId
                            if (id != 0L) viewModel.requestRangeTestDialog(id)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .safeDrawingPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentMint)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                if (spanish) "Prueba de rango activa" else "Range test active",
                                color = TextLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                activeRangeTarget?.name
                                    ?: "0x${viewModel.rangeTestTargetId.toString(16).uppercase()}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.stopRangeTest() }) {
                        Text(if (spanish) "Detener" else "Stop", color = AccentMint, fontSize = 12.sp)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider =
                        entryProvider {
                            entry<Main> {
                                MainScreen(
                                    onItemClick = { navKey -> backStack.add(navKey) },
                                    viewModel = viewModel,
                                    modifier = Modifier.safeDrawingPadding().padding(16.dp)
                                )
                            }
                            entry<NodeDetails> { key ->
                                Box(modifier = Modifier.safeDrawingPadding()) {
                                    NodeDetailsRoute(
                                        nodeId = key.nodeId,
                                        viewModel = viewModel,
                                        onBack = { backStack.removeLastOrNull() }
                                    )
                                }
                            }
                        },
                )
            }
        }

        pendingRangeTestId?.let { targetId ->
            val target = nodes.find { it.nodeId == targetId }
                ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (targetId and 0xFFFFFFFFL) }
            if (target != null) {
                RangeTestDialog(
                    targetNode = target,
                    viewModel = viewModel,
                    appLanguage = appLanguage,
                    onDismiss = { viewModel.dismissRangeTestDialog() }
                )
            } else {
                LaunchedEffect(targetId) {
                    viewModel.dismissRangeTestDialog()
                }
            }
        }

        pendingRemoteConfigId?.let { targetId ->
            val target = nodes.find { it.nodeId == targetId }
                ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (targetId and 0xFFFFFFFFL) }
            if (target != null) {
                RemoteConfigDialog(
                    node = target,
                    viewModel = viewModel,
                    appLanguage = appLanguage,
                    useImperialUnits = useImperialUnits,
                    onDismiss = { viewModel.dismissRemoteConfigDialog() }
                )
            } else {
                LaunchedEffect(targetId) {
                    viewModel.dismissRemoteConfigDialog()
                }
            }
        }
    }
}

@Composable
private fun NodeDetailsRoute(
    nodeId: Long,
    viewModel: MainScreenViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val observedRoutes by viewModel.observedRoutes.collectAsStateWithLifecycle()
    val phoneLocation by viewModel.phoneLocation.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
    val appLanguage = prefs.getString("app_language", "English") ?: "English"
    val useImperialUnits = prefs.getBoolean("use_imperial_units", true)

    val node = nodes.find { it.nodeId == nodeId }
        ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (nodeId and 0xFFFFFFFFL) }

    var renaming by remember { mutableStateOf(false) }

    if (node == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (appLanguage == "Spanish") "Nodo no encontrado" else "Node not found",
                color = TextLight,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onBack) {
                Text(if (appLanguage == "Spanish") "Volver" else "Back", color = AccentMint)
            }
        }
        return
    }

    if (renaming) {
        var longName by remember(node.nodeId) { mutableStateOf(node.name) }
        var shortName by remember(node.nodeId) {
            mutableStateOf(node.shortName.ifEmpty { getShortName(node.name, node.nodeId) })
        }
        var adminPassword by remember(node.nodeId) { mutableStateOf("") }
        val isRemote = node.nodeId != viewModel.connectedNodeId
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(t("Rename Node", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t("Long Name (max 16 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = longName,
                        onValueChange = { if (it.length <= 16) longName = it },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(t("Short Name (max 4 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = shortName,
                        onValueChange = { if (it.length <= 4) shortName = it.uppercase() },
                        singleLine = true,
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isRemote) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (appLanguage == "Spanish")
                                "Contraseña del nodo (para guardar en el mesh)"
                            else
                                "Node admin password (to store on the mesh)",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = adminPassword,
                            onValueChange = { adminPassword = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = aetherTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val persisted = viewModel.renameNode(
                            node.nodeId,
                            longName.trim(),
                            shortName.trim(),
                            adminPassword
                        )
                        if (!persisted && isRemote) {
                            android.widget.Toast.makeText(
                                context,
                                if (appLanguage == "Spanish")
                                    "Nombre guardado solo en el teléfono. Conéctate al nodo o usa Config remota."
                                else
                                    "Name saved on phone only. Connect to that node or use Remote Config.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        renaming = false
                    }
                ) {
                    Text(t("Save", appLanguage), color = AccentMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) {
                    Text(t("Cancel", appLanguage), color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    NodeDetailsScreen(
        node = node,
        observedRoutes = observedRoutes,
        phoneLocation = phoneLocation,
        appLanguage = appLanguage,
        useImperialUnits = useImperialUnits,
        connectedNodeId = viewModel.connectedNodeId,
        getTelemetryHistory = { viewModel.getTelemetryHistory(it) },
        onDismiss = onBack,
        onMessage = {
            viewModel.selectDirectMessage(node.nodeId)
            viewModel.requestOpenChatsTab()
            onBack()
        },
        onRename = { renaming = true },
        onTraceRoute = {
            if (viewModel.startTraceRoute(node.nodeId)) onBack()
        },
        onRemoteConfig = if (node.nodeId != viewModel.connectedNodeId) {
            { viewModel.requestRemoteConfig(node.nodeId) }
        } else null,
        onViewOnMap = {
            viewModel.requestOpenMapTab(focusNodeId = node.nodeId)
            onBack()
        },
        onStartRangeTest = if (node.nodeId != viewModel.connectedNodeId) {
            { viewModel.requestRangeTestDialog(node.nodeId) }
        } else null
    )
}
