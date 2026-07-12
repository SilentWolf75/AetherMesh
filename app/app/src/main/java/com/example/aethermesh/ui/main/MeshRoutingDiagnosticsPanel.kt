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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

@Composable
fun MeshRoutingDiagnosticsPanel(
    viewModel: MainScreenViewModel,
    nodes: List<MeshNode>,
    isConnected: Boolean,
    isDeviceAuthenticated: Boolean,
    appLanguage: String = "English"
) {
    val context = LocalContext.current
    val observedRoutes by viewModel.observedRoutes.collectAsStateWithLifecycle()
    val meshDiagnostics by viewModel.meshDiagnostics.collectAsStateWithLifecycle()

    if (isConnected && !isDeviceAuthenticated) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (appLanguage == "Spanish") "Herramientas bloqueadas"
                    else "Tools locked",
                    color = AccentAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (appLanguage == "Spanish")
                        "Autentica el nodo conectado para usar modo silencioso y diagnósticos en vivo."
                    else
                        "Authenticate the connected node to use quiet mode and live diagnostics.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
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
                val ageSec = ((System.currentTimeMillis() - diagnostics.timestamp) / 1000L).coerceAtLeast(0L)
                val ageLabel = when {
                    ageSec < 5L -> if (appLanguage == "Spanish") "ahora" else "just now"
                    ageSec < 60L -> "${ageSec}s"
                    else -> "${ageSec / 60}m"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCard("TX / RX", "${diagnostics.txPackets} / ${diagnostics.rxPackets}", AccentCyan, Modifier.weight(1f), compact = true)
                    DiagnosticCard(
                        if (appLanguage == "Spanish") "ACK %" else "ACK %",
                        deliveryLabel,
                        deliveryColor,
                        Modifier.weight(1f),
                        compact = true
                    )
                }
                if (deliveryAttempts == 0L) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (appLanguage == "Spanish")
                            "ACK % solo cuenta DMs con recibo — no pruebas de rango ni canales."
                        else
                            "ACK % counts DMs with receipts — not range tests or channel chats.",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
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
                        if (appLanguage == "Spanish") "TX fallos" else "TX fail",
                        "${diagnostics.txFailures}",
                        TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                    DiagnosticCard(
                        if (appLanguage == "Spanish") "Caídas" else "Drops",
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
                        if (appLanguage == "Spanish") "CAD ocupado" else "CAD busy",
                        "${diagnostics.cadBusyEvents}",
                        TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                    DiagnosticCard("ACK Q", "${diagnostics.pendingAckDepth}", TextMuted, Modifier.weight(1f), compact = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCard(
                        if (appLanguage == "Spanish") "Cola rebroadcast" else "Rebroadcast Q",
                        "${diagnostics.rebroadcastQueueDepth}",
                        TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                    DiagnosticCard(
                        if (appLanguage == "Spanish") "Silencio" else "Quiet",
                        if (diagnostics.quietMode) "ON" else "off",
                        if (diagnostics.quietMode) AccentMint else TextMuted,
                        Modifier.weight(1f),
                        compact = true
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    buildString {
                        append(if (appLanguage == "Spanish") "Reenviados " else "Relayed ")
                        append(diagnostics.relayedPackets)
                        append(if (appLanguage == "Spanish") "  ·  Reintentos " else "  ·  Retries ")
                        append(diagnostics.retries)
                        append(if (appLanguage == "Spanish") "  ·  Aire " else "  ·  Airtime ")
                        append(diagnostics.airtimeMs / 1000)
                        append("s  ·  Up ${diagnostics.uptimeSeconds}s  ·  V${diagnostics.protocolVersion}")
                    },
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    if (appLanguage == "Spanish") "Actualizado: $ageLabel" else "Updated: $ageLabel",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (diagnostics.rangePingsRx > 0L || diagnostics.rangePongsSent > 0L || diagnostics.quietMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        buildString {
                            if (diagnostics.quietMode) {
                                append(if (appLanguage == "Spanish") "Modo silencioso activo  ·  " else "Quiet mode active  ·  ")
                            }
                            append(
                                if (appLanguage == "Spanish") "Rango RX ${diagnostics.rangePingsRx}"
                                else "Range RX ${diagnostics.rangePingsRx}"
                            )
                            append(
                                if (appLanguage == "Spanish")
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
                    text = t("No routing paths observed yet.\nPaths are dynamically built as nodes transmit.", appLanguage),
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
                            ?: "Node ${String.format("%04X", (route.targetId and 0xFFFFL).toInt())}"
                        val nextHopName = nextHopNode?.name
                            ?: "Node ${String.format("%04X", (route.nextHopId and 0xFFFFL).toInt())}"

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
                                Text(nextHopName, color = TextMuted, fontSize = 11.sp)
                            }
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
                        }
                    }
                }
            }
        }
    }
}
