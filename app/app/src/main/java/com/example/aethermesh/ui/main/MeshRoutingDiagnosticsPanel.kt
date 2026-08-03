package com.example.aethermesh.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aethermesh.data.MeshNode
import com.example.aethermesh.ui.components.AetherSectionHeader
import com.example.aethermesh.ui.AppUiFeedback
import androidx.compose.material3.SnackbarDuration

@Composable
fun MeshRoutingDiagnosticsPanel(
    viewModel: MainScreenViewModel,
    nodes: List<MeshNode>,
    isConnected: Boolean,
    isDeviceAuthenticated: Boolean,
    appLanguage: String = "English",
    onUnlockDevice: (() -> Unit)? = null,
    meshHopLimit: Int = 4,
    onMeshHopLimitChange: (Int) -> Unit = {},
    rebroadcastTxdelayX100: Int = 100,
    onRebroadcastTxdelayChange: (Int) -> Unit = {},
    onApplyRoutingSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val observedRoutes by viewModel.observedRoutes.collectAsStateWithLifecycle()
    val meshDiagnostics by viewModel.meshDiagnostics.collectAsStateWithLifecycle()
    val spanish = appLanguage == "Spanish"

    if (isConnected && !isDeviceAuthenticated) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (spanish) "Herramientas bloqueadas" else "Tools locked",
                    color = AccentAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (spanish)
                        "Autentica el nodo conectado para ver diagnósticos en vivo. El modo silencioso es solo estado (se activa en prueba de rango)."
                    else
                        "Authenticate the connected node to view live diagnostics. Quiet mode is status-only (enabled during range test).",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                if (onUnlockDevice != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onUnlockDevice,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (spanish) "Desbloquear dispositivo" else "Unlock device",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        return
    }

    if (!isConnected) {
        Text(
            if (appLanguage == "Spanish")
                "Conéctate a un nodo para ver diagnósticos de enrutamiento en vivo."
            else
                "Connect to a node to view live mesh routing diagnostics.",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }

    AetherSectionHeader(
        title = t("Mesh Routing Diagnostics", appLanguage),
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Text(
        if (spanish)
            "Cliente no reenvía LoRa. Router/Repetidor reenvían y descubren rutas (flood → camino directo). Las rutas observadas abajo se aprenden de ACKs y traceroute."
        else
            "Client nodes do not relay LoRa. Router/Repeater nodes forward and discover paths (flood → direct). Observed routes below are learned from ACKs and traceroute.",
        color = TextMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    if (isConnected && isDeviceAuthenticated && onApplyRoutingSettings != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (spanish) "Ajustes de enrutamiento" else "Routing settings",
                    color = TextLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (spanish)
                        "Afectan Router/Repetidor. Aplicar reinicia el nodo."
                    else
                        "Applies to Router/Repeater nodes. Apply reboots the node.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (spanish) "Límite de saltos" else "Hop limit",
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("$meshHopLimit", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = meshHopLimit.toFloat(),
                    onValueChange = { onMeshHopLimitChange(it.toInt().coerceIn(1, 8)) },
                    valueRange = 1f..8f,
                    steps = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (spanish) "Ritmo de reenvío" else "Rebroadcast pace",
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            rebroadcastTxdelayX100 <= 70 -> if (spanish) "Rápido" else "Fast"
                            rebroadcastTxdelayX100 >= 140 -> if (spanish) "Lento" else "Slow"
                            else -> if (spanish) "Normal" else "Normal"
                        } + " (${rebroadcastTxdelayX100}%)",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = rebroadcastTxdelayX100.toFloat(),
                    onValueChange = { onRebroadcastTxdelayChange(it.toInt().coerceIn(50, 200)) },
                    valueRange = 50f..200f,
                    steps = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (spanish)
                        "Más lento = menos colisiones en mallas densas (estilo MeshCore txdelay)."
                    else
                        "Slower = fewer collisions on dense meshes (MeshCore-style txdelay).",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onApplyRoutingSettings()
                        AppUiFeedback.show(
                            if (spanish)
                                "Ajustes de enrutamiento enviados. El nodo se reiniciará."
                            else
                                "Routing settings sent. Node will reboot.",
                            duration = SnackbarDuration.Long
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (spanish) "Aplicar al nodo" else "Apply to node",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            meshDiagnostics?.let { diagnostics ->
                val deliveryAttempts = diagnostics.ackedPackets + diagnostics.ackTimeouts
                val deliveryLabel = if (deliveryAttempts > 0) {
                    "${diagnostics.ackedPackets * 100 / deliveryAttempts}%"
                } else {
                    if (appLanguage == "Spanish") "n/d" else "n/a"
                }
                val deliveryColor = when {
                    deliveryAttempts == 0L -> TextMuted
                    diagnostics.ackTimeouts == 0L -> AccentMint
                    diagnostics.ackedPackets * 100 / deliveryAttempts >= 70L -> AccentMint
                    else -> AccentAmber
                }
                val ageLabel = formatRelativeAge(diagnostics.timestamp, appLanguage)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCard(
                        if (spanish) "TX / RX" else "TX / RX",
                        "${diagnostics.txPackets} / ${diagnostics.rxPackets}",
                        AccentCyan,
                        Modifier.weight(1f),
                        compact = true
                    )
                    DiagnosticCard(
                        "ACK %",
                        deliveryLabel,
                        deliveryColor,
                        Modifier.weight(1f),
                        compact = true
                    )
                }
                if (deliveryAttempts == 0L) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (spanish)
                            "ACK % solo cuenta DMs con recibo — no pruebas de rango ni canales."
                        else
                            "ACK % counts DMs with receipts — not range tests or channel chats.",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (spanish)
                            "ACK ${diagnostics.ackedPackets} · timeout ${diagnostics.ackTimeouts}"
                        else
                            "ACK ${diagnostics.ackedPackets} · timeout ${diagnostics.ackTimeouts}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCard(
                        if (spanish) "TX fallos" else "TX fail",
                        "${diagnostics.txFailures}",
                        TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                    DiagnosticCard(
                        if (spanish) "Caídas" else "Drops",
                        "${diagnostics.queueDrops}",
                        TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCard(
                        if (spanish) "CAD ocupado" else "CAD busy",
                        "${diagnostics.cadBusyEvents}",
                        TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                    DiagnosticCard(
                        if (spanish) "Cola ACK" else "ACK Q",
                        "${diagnostics.pendingAckDepth}",
                        TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCard(
                        if (spanish) "Cola rebroadcast" else "Rebroadcast Q",
                        "${diagnostics.rebroadcastQueueDepth}",
                        TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                    DiagnosticCard(
                        if (spanish) "Silencio" else "Quiet",
                        if (diagnostics.quietMode) {
                            if (spanish) "ON" else "ON"
                        } else {
                            if (spanish) "off" else "off"
                        },
                        if (diagnostics.quietMode) AccentMint else TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    buildString {
                        append(if (spanish) "Reenviados " else "Relayed ")
                        append(diagnostics.relayedPackets)
                        append(if (spanish) "  ·  Reintentos " else "  ·  Retries ")
                        append(diagnostics.retries)
                        append(if (spanish) "  ·  Rutas " else "  ·  Routes ")
                        append(diagnostics.activeRoutes)
                        append(if (spanish) "  ·  Cambios " else "  ·  Changes ")
                        append(diagnostics.routeChanges)
                        append(if (spanish) "  ·  Aire " else "  ·  Airtime ")
                        append(diagnostics.airtimeMs / 1000)
                        append(if (spanish) "s  ·  Activo ${diagnostics.uptimeSeconds}s  ·  V" else "s  ·  Up ${diagnostics.uptimeSeconds}s  ·  V")
                        append(diagnostics.protocolVersion)
                    },
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildString {
                        append(if (spanish) "Dirigidos " else "Directed ")
                        append(diagnostics.directedRelays)
                        append(if (spanish) "  ·  Suprimidos " else "  ·  Suppress ")
                        append(diagnostics.suppressRelays)
                        append(if (spanish) "  ·  Flood " else "  ·  Flood ")
                        append(diagnostics.floodUnicasts)
                        append("  ·  RREQ ")
                        append(diagnostics.rreqSent)
                        append(if (spanish) "  ·  Reparos " else "  ·  Early ")
                        append(diagnostics.earlyRepairs)
                    },
                    color = TextMuted,
                    fontSize = 11.sp
                )
                if (diagnostics.quietMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (spanish)
                            "Modo silencioso: estado del nodo (p. ej. durante prueba de rango), no un interruptor de la app."
                        else
                            "Quiet mode: node status (e.g. during range test), not an app toggle.",
                        color = AccentMint,
                        fontSize = 10.sp
                    )
                }
                Text(
                    if (spanish) "Actualizado: $ageLabel" else "Updated: $ageLabel",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (diagnostics.rangePingsRx > 0L || diagnostics.rangePongsSent > 0L || diagnostics.quietMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        buildString {
                            if (diagnostics.quietMode) {
                                append(if (spanish) "Modo silencioso activo  ·  " else "Quiet mode active  ·  ")
                            }
                            append(
                                if (spanish) "Rango RX ${diagnostics.rangePingsRx}"
                                else "Range RX ${diagnostics.rangePingsRx}"
                            )
                            append(
                                if (spanish)
                                    "  ·  PONGs cola/env/fallo ${diagnostics.rangePongsQueued}/${diagnostics.rangePongsSent}/${diagnostics.rangePongTxFailures}"
                                else
                                    "  ·  PONGs queued/sent/fail ${diagnostics.rangePongsQueued}/${diagnostics.rangePongsSent}/${diagnostics.rangePongTxFailures}"
                            )
                        },
                        color = if (diagnostics.quietMode) AccentMint else TextMuted,
                        fontSize = 11.sp
                    )
                }
                TextButton(
                    onClick = {
                        exportMeshDiagnosticsToCsv(context, viewModel.getMeshDiagnosticsHistory(), appLanguage)
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (appLanguage == "Spanish") "Exportar salud mesh CSV" else "Export mesh health CSV",
                        fontSize = 11.sp
                    )
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))
            } ?: Text(
                if (appLanguage == "Spanish") "Esperando telemetría del mesh…" else "Waiting for mesh telemetry…",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (observedRoutes.isEmpty()) {
                Text(
                    text = if (spanish)
                        "Aún no hay rutas aprendidas.\nEnvía un DM o ejecuta traceroute; los relays descubren el camino y luego usan enrutamiento directo."
                    else
                        "No learned paths yet.\nSend a DM or run traceroute; relays flood to discover, then use direct routing.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (appLanguage == "Spanish") "Destino" else "Target",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(t("Next Hop", appLanguage), color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text(t("Hops", appLanguage), color = TextMuted, fontSize = 10.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    observedRoutes.values.forEach { route ->
                        val targetNode = nodes.find {
                            it.nodeId == route.targetId || (it.nodeId and 0xFFFFL) == (route.targetId and 0xFFFFL)
                        }
                        val nextHopNode = nodes.find {
                            it.nodeId == route.nextHopId || (it.nodeId and 0xFFFFL) == (route.nextHopId and 0xFFFFL)
                        }
                        val targetName = targetNode?.name
                            ?: if (spanish)
                                "Nodo ${String.format("%04X", (route.targetId and 0xFFFFL).toInt())}"
                            else
                                "Node ${String.format("%04X", (route.targetId and 0xFFFFL).toInt())}"
                        val nextHopName = nextHopNode?.name
                            ?: if (spanish)
                                "Nodo ${String.format("%04X", (route.nextHopId and 0xFFFFL).toInt())}"
                            else
                                "Node ${String.format("%04X", (route.nextHopId and 0xFFFFL).toInt())}"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(targetName, color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (spanish) "Siguiente: $nextHopName" else "Via: $nextHopName",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceDark)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${route.hops} ${if (route.hops == 1) t("Hop", appLanguage) else t("Hops", appLanguage)}",
                                        color = AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (isConnected && isDeviceAuthenticated) {
                                    TextButton(
                                        onClick = { viewModel.startTraceRoute(route.targetId) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            if (spanish) "Redescubrir" else "Rediscover",
                                            color = AccentMint,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
