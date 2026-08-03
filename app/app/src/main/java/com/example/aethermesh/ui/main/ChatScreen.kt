package com.example.aethermesh.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aethermesh.data.ChatMessage
import com.example.aethermesh.data.ChannelConfig
import com.example.aethermesh.data.MeshNode
import com.example.aethermesh.data.TraceRouteState
import com.example.aethermesh.data.takeUtf8Bytes
import com.example.aethermesh.ui.AppUiFeedback
import com.example.aethermesh.ui.components.*
import com.example.aethermesh.theme.AccentCyanDim
import com.example.aethermesh.theme.AccentSteel
import com.example.aethermesh.theme.AccentSteelDim
import com.example.aethermesh.theme.appBackgroundBrush
import com.example.aethermesh.theme.headerBarBrush
import com.example.aethermesh.theme.primaryButtonBrush
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@Composable
fun ChatView(
    messages: List<ChatMessage>,
    channels: List<String>,
    selectedChannel: String,
    localNodeId: Long,
    activeChatId: Long?,
    nodes: List<MeshNode>,
    appLanguage: String = "English",
    isConnected: Boolean = true,
    isAuthenticated: Boolean = true,
    isReconnecting: Boolean = false,
    onSelectChannel: (String) -> Unit,
    onSelectDirectMessage: (Long) -> Unit,
    onCreateChannel: (String) -> Unit,
    onSendMessage: (String) -> com.example.aethermesh.data.SendMessageResult,
    onRetryMessage: (ChatMessage) -> Unit,
    getChatKey: (String) -> String?,
    saveChatKey: (String, String) -> Unit,
    channelPreviews: Map<String, com.example.aethermesh.data.ChatInboxPreview> = emptyMap(),
    dmPreviews: Map<Long, com.example.aethermesh.data.ChatInboxPreview> = emptyMap(),
    onGoToConnection: () -> Unit = {},
    deepLinkEpoch: Int = 0,
    chatKeysRevision: Int = 0
) {
    var textState by remember { mutableStateOf("") }
    var sendError by remember { mutableStateOf<String?>(null) }
    var showNewChannelDialog by remember { mutableStateOf(false) }
    var inThread by remember { mutableStateOf(false) }
    var stickToBottom by remember { mutableStateOf(true) }
    val chatTwoPane = rememberAdaptiveLayoutInfo().useTwoPane
    BackHandler(enabled = inThread && !chatTwoPane) { inThread = false }
    val listState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    val canSend = isConnected && isAuthenticated
    val spanish = appLanguage == "Spanish"
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val draftPrefs = remember {
        context.getSharedPreferences("chat_drafts", Context.MODE_PRIVATE)
    }
    // Bump when drafts change so inbox rows refresh "Draft:" snippets.
    var draftsRevision by remember { mutableIntStateOf(0) }
    val showJumpToBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isNotEmpty() && lastVisible < messages.lastIndex - 1
        }
    }

    fun chatDraftKey(): String =
        if (activeChatId == null) "CHANNEL_$selectedChannel" else "DM_$activeChatId"

    fun formatInboxTime(ts: Long): String {
        if (ts <= 0L) return ""
        val msgCal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        val nowCal = java.util.Calendar.getInstance()
        val sameDay = msgCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            msgCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)
        return if (sameDay) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
        } else {
            SimpleDateFormat("M/d h:mm a", Locale.getDefault()).format(Date(ts))
        }
    }

    fun previewSnippet(raw: String): String {
        val trimmed = raw.trim().replace('\n', ' ')
        return if (trimmed.length <= 48) trimmed else trimmed.take(45) + "…"
    }

    fun inboxPreviewLine(preview: com.example.aethermesh.data.ChatInboxPreview): String {
        val body = previewSnippet(preview.snippet)
        val failed = preview.status in setOf("FAILED", "EXPIRED")
        val fromMe = localNodeId != 0L && preview.senderId == localNodeId
        val prefix = when {
            failed && spanish -> "Falló · "
            failed -> "Failed · "
            fromMe && spanish -> "Tú: "
            fromMe -> "You: "
            else -> ""
        }
        return prefix + body
    }

    fun draftSnippetFor(key: String): String? {
        val draft = draftPrefs.getString(key, null)?.trim().orEmpty()
        return draft.takeIf { it.isNotEmpty() }?.let { previewSnippet(it) }
    }

    LaunchedEffect(activeChatId) {
        if (activeChatId != null && activeChatId != 0L) inThread = true
    }
    LaunchedEffect(deepLinkEpoch) {
        if (deepLinkEpoch > 0) inThread = true
    }

    // Load draft when switching threads (MeshCore-style).
    LaunchedEffect(activeChatId, selectedChannel) {
        textState = draftPrefs.getString(chatDraftKey(), "") ?: ""
        sendError = null
    }
    // Persist draft while typing (debounced).
    LaunchedEffect(textState, activeChatId, selectedChannel) {
        kotlinx.coroutines.delay(350)
        val key = chatDraftKey()
        val trimmed = textState
        if (trimmed.isBlank()) {
            if (draftPrefs.contains(key)) {
                draftPrefs.edit().remove(key).apply()
                draftsRevision++
            }
        } else if (draftPrefs.getString(key, null) != trimmed) {
            draftPrefs.edit().putString(key, trimmed).apply()
            draftsRevision++
        }
    }

    if (showNewChannelDialog) {
        NewChannelDialog(
            appLanguage = appLanguage,
            onCreate = {
                onCreateChannel(it)
                showNewChannelDialog = false
                onSelectChannel(it)
                inThread = true
            },
            onDismiss = { showNewChannelDialog = false }
        )
    }

    val selectedNode = nodes.find { it.nodeId == activeChatId }
    val threadTitle = if (activeChatId == null) {
        "#$selectedChannel"
    } else {
        selectedNode?.name ?: "Node 0x${activeChatId.toString(16).uppercase()}"
    }
    val isChannelThread = activeChatId == null

    LaunchedEffect(activeChatId, selectedChannel, inThread) {
        stickToBottom = true
        if (inThread && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(messages.size) {
        if (inThread && messages.isNotEmpty() && stickToBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            stickToBottom = messages.isEmpty() || lastVisible >= messages.lastIndex - 1
        }
    }

    @Composable
    fun ChatInboxPane() {
        val relativeTick = rememberRelativeTimeTick()
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val dmNodes = nodes.filter { it.nodeId != localNodeId }
            val sortedChannels = remember(channels, channelPreviews) {
                channels.sortedByDescending { channelPreviews[it]?.timestamp ?: 0L }
            }
            val sortedDmNodes = remember(dmNodes, dmPreviews, relativeTick) {
                dmNodes.sortedByDescending { node ->
                    dmPreviews[node.nodeId]?.timestamp?.takeIf { it > 0L } ?: node.lastActive
                }
            }
            if (!canSend) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentAmber.copy(alpha = 0.15f))
                        .clickable { onGoToConnection() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            !isConnected && isReconnecting -> if (spanish)
                                "Reconectando… toca para abrir Conexión."
                            else
                                "Reconnecting… tap to open Connection."
                            !isConnected -> if (spanish)
                                "Conecta una radio para enviar y recibir mensajes."
                            else
                                "Link a radio to send and receive messages."
                            else -> if (spanish)
                                "Autentica el nodo para chatear."
                            else
                                "Unlock the node to chat."
                        },
                        color = TextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AetherSectionHeader(
                            title = t("Channels", appLanguage),
                            trailing = "${channels.size}",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showNewChannelDialog = true },
                            enabled = canSend
                        ) {
                            Text(
                                if (spanish) "+ Canal" else "+ Channel",
                                color = if (canSend) AccentCyan else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (sortedChannels.isEmpty()) {
                    item {
                        Text(
                            if (spanish)
                                "Aún no hay canales. Crea uno o espera a que tu nodo sincronice."
                            else
                                "No channels yet. Create one or wait for your node to sync.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(sortedChannels) { channel ->
                    val preview = channelPreviews[channel]
                    val selected = activeChatId == null && channel == selectedChannel && (chatTwoPane || inThread)
                    val draft = remember(draftsRevision, channel) { draftSnippetFor("CHANNEL_$channel") }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) AccentCyan.copy(alpha = 0.16f) else SurfaceDark
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (selected) AccentCyan.copy(alpha = 0.45f) else BorderDark
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelectChannel(channel)
                                inThread = true
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentCyanDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("#", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                channel,
                                color = TextLight,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    draft != null ->
                                        (if (spanish) "Borrador: " else "Draft: ") + draft
                                    preview != null -> inboxPreviewLine(preview)
                                    else -> if (spanish) "Chat de canal" else "Channel chat"
                                },
                                color = when {
                                    draft != null -> AccentAmber
                                    preview?.status in setOf("FAILED", "EXPIRED") -> AccentRed
                                    else -> TextMuted
                                },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (preview != null && preview.timestamp > 0L) {
                                Text(formatInboxTime(preview.timestamp), color = TextMuted, fontSize = 11.sp)
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    AetherSectionHeader(
                        title = if (spanish) "Mensajes directos" else "Direct Messages",
                        trailing = "${sortedDmNodes.size}",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (sortedDmNodes.isEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                if (spanish)
                                    "Sin contactos aún. Los nodos aparecen aquí cuando se oyen en la malla."
                                else
                                    "No contacts yet. Nodes appear here when heard on the mesh.",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (spanish)
                                    "También puedes abrir Nodos → mensaje en un contacto."
                                else
                                    "Or open Nodes → message on a contact.",
                                color = TextMuted.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(sortedDmNodes) { node ->
                        val shortName = node.shortName.ifEmpty { getShortName(node.name, node.nodeId) }
                        val stale = isNodeStale(node.lastActive)
                        val preview = dmPreviews[node.nodeId]
                        val selected = activeChatId != null && activeChatId == node.nodeId && (chatTwoPane || inThread)
                        val draft = remember(draftsRevision, node.nodeId) { draftSnippetFor("DM_${node.nodeId}") }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        selected -> AccentMint.copy(alpha = 0.16f)
                                        stale -> SurfaceDark.copy(alpha = 0.55f)
                                        else -> SurfaceDark
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (selected) AccentMint.copy(alpha = 0.45f) else BorderDark
                                    ),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectDirectMessage(node.nodeId)
                                    inThread = true
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NodeBadge(shortName = shortName, color = getBadgeColor(node.name), muted = stale)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    node.name,
                                    color = if (stale) TextMuted else TextLight,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val heardLabel = remember(node.lastActive, relativeTick, appLanguage) {
                                    formatLastHeard(node.lastActive, appLanguage)
                                }
                                Text(
                                    when {
                                        draft != null ->
                                            (if (spanish) "Borrador: " else "Draft: ") + draft
                                        preview != null -> inboxPreviewLine(preview)
                                        stale -> if (spanish)
                                            "Último aviso $heardLabel"
                                        else
                                            "Last heard $heardLabel"
                                        else -> if (spanish) "Mensaje directo" else "Direct message"
                                    },
                                    color = when {
                                        draft != null -> AccentAmber
                                        preview?.status in setOf("FAILED", "EXPIRED") -> AccentRed
                                        else -> TextMuted
                                    },
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (preview != null && preview.timestamp > 0L) {
                                    Text(formatInboxTime(preview.timestamp), color = TextMuted, fontSize = 11.sp)
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ChatThreadPane() {
        Column(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (!chatTwoPane) {
                    IconButton(onClick = { inThread = false }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (spanish) "Atrás" else "Back",
                            tint = TextLight
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(threadTitle, color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isChannelThread) {
                            if (spanish) "Canal" else "Channel"
                        } else {
                            if (spanish) "Mensaje directo" else "Direct message"
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        Spacer(modifier = Modifier.height(8.dp))

        if (!canSend) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentAmber.copy(alpha = 0.15f))
                    .clickable { onGoToConnection() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !isConnected && isReconnecting -> if (spanish)
                            "Reconectando BLE… toca para Conexión."
                        else
                            "Reconnecting BLE… tap for Connection."
                        !isConnected -> if (spanish)
                            "Sin conexión BLE. Toca para ir a Conexión."
                        else
                            "Not connected. Tap to open Connection."
                        else -> if (spanish)
                            "Dispositivo bloqueado. Autentica en Conexión."
                        else
                            "Device locked. Authenticate on Connection."
                    },
                    color = TextLight,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (activeChatId == null || activeChatId != 0L) {
            val chatIdentifier = if (activeChatId == null) "CHANNEL_$selectedChannel" else "DM_$activeChatId"
            var passcode by remember(chatIdentifier) { mutableStateOf(getChatKey(chatIdentifier)) }
            var showPasscodeDialog by remember { mutableStateOf(false) }
            LaunchedEffect(chatIdentifier, chatKeysRevision) {
                passcode = getChatKey(chatIdentifier)
            }

            if (showPasscodeDialog) {
                PasscodeEntryDialog(
                    title = if (activeChatId == null) {
                        if (spanish) "Clave del canal (#$selectedChannel)" else "Channel Key (#$selectedChannel)"
                    } else {
                        if (spanish)
                            "Clave directa (Nodo 0x${activeChatId.toString(16).uppercase()})"
                        else
                            "Direct Key (Node 0x${activeChatId.toString(16).uppercase()})"
                    },
                    initialPasscode = passcode ?: "",
                    appLanguage = appLanguage,
                    onSave = { newKey ->
                        saveChatKey(chatIdentifier, newKey)
                        passcode = newKey.takeIf { it.isNotEmpty() }
                        showPasscodeDialog = false
                        AppUiFeedback.show(
                            when {
                                newKey.isEmpty() && spanish -> "Clave borrada"
                                newKey.isEmpty() -> "Key cleared"
                                spanish -> "Clave guardada"
                                else -> "Key saved"
                            }
                        )
                    },
                    onDismiss = { showPasscodeDialog = false }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                    .clickable { showPasscodeDialog = true }
                    .semantics {
                        contentDescription = if (!passcode.isNullOrEmpty()) {
                            if (spanish) "Cifrado — tocar para editar clave" else "Encrypted — tap to edit key"
                        } else {
                            if (spanish) "Texto claro — tocar para clave" else "Cleartext — tap to set key"
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (!passcode.isNullOrEmpty()) AccentMint else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!passcode.isNullOrEmpty()) {
                            if (spanish) "Cifrado" else "Encrypted"
                        } else {
                            if (spanish) "Texto claro — toca para clave" else "Cleartext — tap to set key"
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                if (!passcode.isNullOrEmpty()) {
                    SecureChip()
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeChatId != null && activeChatId == 0L) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (spanish)
                        "No hay nodos para chat privado.\nEspera a que otros nodos emitan telemetría."
                    else
                        "No nodes available for private chat.\nWait for other nodes to broadcast telemetries.",
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = false
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when {
                                        !canSend && spanish ->
                                            "Aún no hay mensajes. Desbloquea el nodo para enviar."
                                        !canSend ->
                                            "No messages yet. Unlock the node to send."
                                        spanish -> "Aún no hay mensajes. Escribe el primero."
                                        else -> "No messages yet. Say hello."
                                    },
                                    color = TextMuted,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        val senderNode = nodes.find { it.nodeId == message.senderId }
                        val senderLabel = when {
                            !isChannelThread -> null
                            localNodeId != 0L && message.senderId == localNodeId -> null
                            senderNode != null -> senderNode.shortName.ifEmpty {
                                getShortName(senderNode.name, senderNode.nodeId)
                            }
                            message.senderId != 0L -> getShortName("", message.senderId)
                            else -> null
                        }
                        MessageBubble(
                            message = message,
                            localNodeId = localNodeId,
                            onRetryMessage = onRetryMessage,
                            senderLabel = senderLabel,
                            appLanguage = appLanguage
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                if (showJumpToBottom) {
                    SmallFloatingActionButton(
                        onClick = {
                            stickToBottom = true
                            chatScope.launch {
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.lastIndex)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        containerColor = SurfaceRaised,
                        contentColor = AccentCyan
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = if (spanish) "Ir al final" else "Jump to latest"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeChatId == null || activeChatId != 0L) {
            val placeholderText = when {
                !isConnected && isReconnecting && spanish -> "Reconectando…"
                !isConnected && isReconnecting -> "Reconnecting…"
                !isConnected && spanish -> "Conecta una radio para escribir…"
                !isConnected -> "Connect a radio to type…"
                !isAuthenticated && spanish -> "Desbloquea el nodo para enviar…"
                !isAuthenticated -> "Unlock the node to send…"
                activeChatId == null && spanish -> "Mensaje #$selectedChannel…"
                activeChatId == null -> "Message #$selectedChannel..."
                spanish -> "Mensaje a ${selectedNode?.name ?: "nodo"}…"
                else -> "Message ${selectedNode?.name ?: "node"}..."
            }
            val chatIdForLimit = if (activeChatId == null) "CHANNEL_$selectedChannel" else "DM_$activeChatId"
            val encryptedChat = !getChatKey(chatIdForLimit).isNullOrEmpty()
            val maxUtf8 = if (encryptedChat) CHAT_MAX_ENCRYPTED_UTF8_BYTES else CHAT_MAX_PLAIN_UTF8_BYTES
            LaunchedEffect(maxUtf8) {
                val clipped = textState.takeUtf8Bytes(maxUtf8)
                if (clipped != textState) textState = clipped
            }
            val usedUtf8 = remember(textState) { textState.toByteArray(Charsets.UTF_8).size }
            val hasText = textState.trim().isNotEmpty()
            val canTapSend = canSend && hasText
            fun trySend() {
                if (!canSend) {
                    val msg = if (!isConnected) {
                        if (spanish) "Conecta y autentica el nodo primero."
                        else "Connect and authenticate the node first."
                    } else {
                        if (spanish) "Autentica el dispositivo para enviar."
                        else "Authenticate the device to send."
                    }
                    sendError = msg
                    return
                }
                if (!hasText) return
                when (onSendMessage(textState)) {
                    com.example.aethermesh.data.SendMessageResult.Sent -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val key = chatDraftKey()
                        textState = ""
                        sendError = null
                        stickToBottom = true
                        keyboardController?.hide()
                        if (draftPrefs.contains(key)) {
                            draftPrefs.edit().remove(key).apply()
                            draftsRevision++
                        }
                    }
                    com.example.aethermesh.data.SendMessageResult.EncryptFailed -> {
                        val msg = if (spanish)
                            "No se pudo cifrar el mensaje. Revisa la clave del chat."
                        else
                            "Could not encrypt the message. Check the chat passcode."
                        sendError = msg
                    }
                    com.example.aethermesh.data.SendMessageResult.NotReady -> {
                        val msg = if (spanish)
                            "No se envió. Revisa conexión BLE y autenticación."
                        else
                            "Message not sent. Check BLE connection and authentication."
                        sendError = msg
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textState,
                    onValueChange = {
                        textState = it.takeUtf8Bytes(maxUtf8)
                        if (sendError != null) sendError = null
                    },
                    enabled = canSend,
                    placeholder = { Text(placeholderText, color = TextMuted) },
                    colors = aetherFilledFieldColors(),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { trySend() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { trySend() },
                    enabled = canTapSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canTapSend) AccentCyan else TextMuted.copy(alpha = 0.35f))
                        .semantics {
                            contentDescription = when {
                                !canSend && spanish -> "Enviar desactivado — desbloquea el nodo"
                                !canSend -> "Send disabled — unlock the node"
                                !hasText && spanish -> "Enviar desactivado — escribe un mensaje"
                                !hasText -> "Send disabled — type a message"
                                spanish -> "Enviar"
                                else -> "Send"
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = DarkBackground
                    )
                }
            }
            if (canSend && (usedUtf8 >= (maxUtf8 * 3 / 4) || encryptedChat)) {
                Text(
                    text = when {
                        spanish && encryptedChat ->
                            "$usedUtf8 / $maxUtf8 bytes (cifrado)"
                        spanish ->
                            "$usedUtf8 / $maxUtf8 bytes"
                        encryptedChat ->
                            "$usedUtf8 / $maxUtf8 bytes (encrypted)"
                        else ->
                            "$usedUtf8 / $maxUtf8 bytes"
                    },
                    color = if (usedUtf8 >= maxUtf8) AccentAmber else TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                )
            }
            sendError?.let {
                Text(
                    text = it,
                    color = AccentRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp, start = 4.dp)
                )
            }
        }
        }
    }

    if (chatTwoPane) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
            ) {
                ChatInboxPane()
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(BorderDark.copy(alpha = 0.7f))
            )
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
            ) {
                if (inThread) {
                    ChatThreadPane()
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (spanish) "Selecciona un canal o mensaje directo"
                            else "Select a channel or direct message",
                            color = TextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }
    } else if (!inThread) {
        ChatInboxPane()
    } else {
        ChatThreadPane()
    }
}

@Composable
fun NewChannelDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    appLanguage: String = "English"
) {
    var name by remember { mutableStateOf("") }
    val spanish = appLanguage == "Spanish"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (name.trim().isNotEmpty()) onCreate(name.trim()) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(
                    if (spanish) "Crear" else "Create",
                    color = if (name.trim().isNotEmpty()) AccentMint else TextMuted
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (spanish) "Cancelar" else "Cancel", color = TextMuted)
            }
        },
        title = {
            Text(
                if (spanish) "Nuevo canal" else "New Channel",
                color = TextLight,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    if (spanish)
                        "Los mensajes se emiten en este nombre de canal. Solo los nodos sintonizados al mismo canal los verán."
                    else
                        "Messages you send here are broadcast on this channel name. Only nodes tuned to the same channel will display them.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = name,
                    onValueChange = { if (it.length <= 16) name = it.filterNot { c -> c.isWhitespace() } },
                    singleLine = true,
                    placeholder = {
                        Text(
                            if (spanish) "p. ej. Equipo-Sendero" else "e.g. Trail-Crew",
                            color = TextMuted
                        )
                    },
                    colors = aetherTextFieldColors(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun PasscodeEntryDialog(
    title: String,
    initialPasscode: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    var keyState by remember { mutableStateOf(initialPasscode) }
    val hadKey = initialPasscode.isNotEmpty()
    val willEncrypt = keyState.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                if (hadKey || willEncrypt) {
                    TextButton(onClick = { onSave("") }) {
                        Text(
                            if (spanish) "Borrar clave" else "Clear key",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                TextButton(
                    onClick = { onSave(keyState.trim()) },
                    enabled = willEncrypt || hadKey
                ) {
                    Text(
                        when {
                            willEncrypt && spanish -> "Guardar clave"
                            willEncrypt -> "Save key"
                            spanish -> "Guardar"
                            else -> "Save"
                        },
                        color = if (willEncrypt || hadKey) AccentMint else TextMuted
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (spanish) "Cancelar" else "Cancel", color = TextMuted)
            }
        },
        title = { Text(title, color = TextLight, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    when {
                        willEncrypt && spanish -> "Estado: cifrado AES-256 activo al guardar"
                        willEncrypt -> "Status: AES-256 encryption on after save"
                        hadKey && spanish -> "Estado: hay una clave — borrar o reemplazar abajo"
                        hadKey -> "Status: key set — clear or replace below"
                        spanish -> "Estado: texto claro (sin clave)"
                        else -> "Status: cleartext (no key)"
                    },
                    color = if (willEncrypt || hadKey) AccentMint else AccentAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (spanish)
                        "Todos los mensajes de este chat se cifran y descifran con AES-256 usando la clave de abajo. Manténla en secreto y compártela fuera de banda con los demás."
                    else
                        "All messages in this chat will be encrypted and decrypted using AES-256 with the key below. Keep this key secret and share it off-grid with other participants.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = keyState,
                    onValueChange = { keyState = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            if (spanish) "Introduce la clave (p. ej. secreto123)" else "Enter passcode (e.g. secret123)",
                            color = TextMuted
                        )
                    },
                    colors = aetherTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (spanish)
                        "Usa «Borrar clave» o deja el campo vacío y guarda para volver a texto claro."
                    else
                        "Use Clear key, or leave blank and save, to return to cleartext.",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    localNodeId: Long,
    onRetryMessage: (ChatMessage) -> Unit,
    senderLabel: String? = null,
    appLanguage: String = "English"
) {
    val isMe = localNodeId != 0L && message.senderId == localNodeId
    val canRetry = isMe && message.status in setOf("FAILED", "EXPIRED")
    val spanish = appLanguage == "Spanish"
    // Direct messages: PENDING → DELIVERED when the recipient *node* ACKs.
    // Channel: SENT → HEARD as unique hearer ACKs arrive ("heard by N").
    val isChannel = message.channel.isNotEmpty() ||
        (message.recipientId and 0xFFFFFFFFL) == 0xFFFFFFFFL
    val statusIcon = when (message.status) {
        "DELIVERED" -> "✓✓"
        "HEARD" -> "✓✓"
        "PENDING", "QUEUED" -> "…"
        "FAILED", "EXPIRED" -> "!"
        "RETRIED" -> "↻"
        "SENT" -> if (isChannel) "…" else "✓"
        else -> "✓"
    }
    val statusColor = when (message.status) {
        "DELIVERED", "HEARD" -> AccentMint
        "SENT" -> if (isChannel) AccentAmber else TextMuted
        "FAILED", "EXPIRED" -> AccentRed
        "PENDING", "QUEUED" -> AccentAmber
        else -> TextMuted
    }
    val statusText = when (message.status) {
        "EXPIRED" -> if (spanish) "$statusIcon sin ACK · tocar para reintentar" else "$statusIcon no ACK · tap to retry"
        "FAILED" -> if (spanish) "$statusIcon sin respuesta · tocar para reintentar" else "$statusIcon no reply · tap to retry"
        "PENDING", "QUEUED" -> if (spanish) "$statusIcon esperando recepción…" else "$statusIcon waiting for receipt…"
        "RETRIED" -> if (spanish) "$statusIcon reenviado" else "$statusIcon resent"
        "DELIVERED" -> if (spanish) "$statusIcon recibido" else "$statusIcon received"
        "HEARD" -> if (spanish) {
            "$statusIcon oído por ${message.heardCount}"
        } else {
            "$statusIcon heard by ${message.heardCount}"
        }
        "SENT" -> if (isChannel) {
            if (spanish) "$statusIcon Esperando ser oído…" else "$statusIcon Waiting to be heard…"
        } else {
            if (spanish) "$statusIcon enviado" else "$statusIcon sent"
        }
        else -> statusIcon
    }
    val statusDescription = when (message.status) {
        "EXPIRED" -> if (spanish)
            "Sin confirmación del nodo. Toca el mensaje para reintentar; también se reintenta automáticamente."
        else
            "No node acknowledgment. Tap message to retry; auto-retry also runs in the background."
        "FAILED" -> if (spanish)
            "Sin respuesta del nodo en 45s. Toca para reintentar."
        else
            "No node reply within 45s. Tap to retry."
        "PENDING", "QUEUED" -> if (spanish)
            "Enviado. Esperando que el nodo destino confirme la recepción."
        else
            "Sent. Waiting for the destination node to confirm receipt."
        "RETRIED" -> if (spanish) "Reenviado automáticamente." else "Automatically resent."
        "DELIVERED" -> if (spanish)
            "Recibido por el nodo destino (confirmación de malla)."
        else
            "Received by the destination node (mesh acknowledgment)."
        "HEARD" -> if (spanish)
            "Confirmado por ${message.heardCount} nodo(s) de la malla que oyeron el mensaje de canal."
        else
            "Confirmed by ${message.heardCount} mesh node(s) that heard this channel message."
        "SENT" -> if (isChannel) {
            if (spanish)
                "Transmitido al canal. Esperando ser oído por nodos de la malla."
            else
                "Broadcast on the channel. Waiting to be heard."
        } else {
            if (spanish) "Enviado." else "Sent."
        }
        else -> if (spanish) "Enviado." else "Sent."
    }
    val time = run {
        val msgCal = java.util.Calendar.getInstance().apply { timeInMillis = message.timestamp }
        val nowCal = java.util.Calendar.getInstance()
        val sameDay = msgCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            msgCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "h:mm a" else "M/d h:mm a"
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (!senderLabel.isNullOrBlank() && !isMe) {
            Text(
                senderLabel,
                color = AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
            )
        }
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    )
                )
                .then(
                    if (isMe) Modifier.background(primaryButtonBrush())
                    else Modifier.background(SurfaceDark)
                )
                .clickable(enabled = canRetry) { onRetryMessage(message) }
                .semantics {
                    if (isMe) {
                        contentDescription = statusDescription
                        if (canRetry) {
                            onClick(label = if (spanish) "Reintentar envío" else "Retry send") {
                                onRetryMessage(message)
                                true
                            }
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = localizeChatPlaceholder(message.content, appLanguage),
                color = if (isMe) Color(0xFF061018) else TextLight,
                fontSize = 15.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(time, color = TextMuted, fontSize = 10.sp)
            if (isMe) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = statusDescription }
                )
            }
        }
    }
}

@Composable
fun TraceRouteResultDialog(
    state: TraceRouteState,
    nodes: List<MeshNode>,
    connectedNodeId: Long,
    onOk: () -> Unit,
    onCancel: () -> Unit = onOk,
    onViewOnMap: () -> Unit,
    appLanguage: String = "English"
) {
    val spanish = appLanguage == "Spanish"
    val snrOrange = Color(0xFFFF9800)
    val haptic = LocalHapticFeedback.current

    fun displayName(id: Long): String {
        val node = nodes.find { it.nodeId == id }
            ?: nodes.find { (it.nodeId and 0xFFFFFFFFL) == (id and 0xFFFFFFFFL) }
        val longName = node?.name?.takeIf { it.isNotBlank() }
            ?: "0x${id.toString(16).uppercase()}"
        val short = node?.shortName?.takeIf { it.isNotBlank() }
            ?: getShortName(longName, id)
        return "$longName ($short)"
    }

    @Composable
    fun HopSnr(snr: Float, rssi: Int) {
        val unknown = snr == 0f && rssi == 0
        Text(
            text = if (unknown) "? dB" else "%.2f dB".format(snr),
            color = if (unknown) TextMuted else snrOrange,
            fontSize = 13.sp,
            fontWeight = if (unknown) FontWeight.Normal else FontWeight.SemiBold
        )
    }

    @Composable
    fun RoutePath(
        title: String,
        startId: Long,
        endLabel: String?,
        hops: List<com.example.aethermesh.data.TraceHop>,
        truncated: Boolean
    ) {
        Text(title, color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("■", color = TextLight, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(displayName(startId), color = TextLight, fontSize = 14.sp)
        }
        if (hops.isEmpty()) {
            Text(
                if (spanish) "↳ Directo (sin repetidores)" else "↳ Direct (no relays)",
                color = AccentMint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 4.dp)
            )
            endLabel?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("■", color = TextLight, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(it, color = TextLight, fontSize = 14.sp)
                }
            }
        } else {
            hops.forEach { hop ->
                Row(
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⇊", color = TextMuted, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    HopSnr(hop.snr, hop.rssi)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("■", color = TextLight, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(displayName(hop.nodeId), color = TextLight, fontSize = 14.sp)
                }
            }
        }
        if (truncated) {
            Text(
                if (spanish) "La ruta superó el límite de 8 saltos" else "Path exceeded the 8-hop capture limit",
                color = snrOrange,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (state.active) onCancel() else onOk()
        },
        containerColor = SurfaceDark,
        title = {
            Text(
                if (spanish) "Trazado de ruta" else "Traceroute",
                color = TextLight,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (state.targetId != 0L) {
                    Text(
                        if (spanish) "Destino: ${displayName(state.targetId)}"
                        else "Target: ${displayName(state.targetId)}",
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                when {
                    state.active -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentMint,
                            trackColor = BorderDark
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (spanish)
                                "Trazando ruta… puedes cancelar si tarda demasiado."
                            else
                                "Tracing route… you can cancel if this takes too long.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    state.error != null -> {
                        Text(
                            localizeTraceRouteError(state.error, appLanguage),
                            color = if (state.error == "Cancelled") AccentAmber else Color(0xFFF87171),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    else -> {
                        val outHops = state.forward.size
                        val backHops = state.returning.size
                        Text(
                            if (spanish)
                                "Resumen: $outHops salto(s) de ida · $backHops de vuelta"
                            else
                                "Summary: $outHops hop(s) out · $backHops back",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        RoutePath(
                            title = if (spanish) "Ruta hacia el destino:" else "Route traced toward destination:",
                            startId = connectedNodeId,
                            endLabel = displayName(state.targetId),
                            hops = state.forward,
                            truncated = state.forwardTruncated
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        RoutePath(
                            title = if (spanish) "Ruta de vuelta:" else "Route traced back to us:",
                            startId = state.targetId,
                            endLabel = displayName(connectedNodeId),
                            hops = state.returning,
                            truncated = state.returnTruncated
                        )
                        state.durationSeconds?.let { secs ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (spanish) "Duración: ${"%.1f".format(secs)} s" else "Duration: ${"%.1f".format(secs)} s",
                                color = TextLight,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.active) {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    }
                ) {
                    Text(
                        if (spanish) "Cancelar" else "Cancel",
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row {
                    TextButton(onClick = onOk) {
                        Text(if (spanish) "Aceptar" else "OK", color = TextLight, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.error == null && state.targetId != 0L) {
                        TextButton(onClick = onViewOnMap) {
                            Text(
                                if (spanish) "Ver en mapa" else "View on map",
                                color = TextLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    )
}


