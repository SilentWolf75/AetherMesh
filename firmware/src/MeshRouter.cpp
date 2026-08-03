#include "MeshRouter.h"
#include "MeshMath.h"
#include "Version.h"
#include "pb_common.h"
#include "pb_encode.h"
#include "pb_decode.h"
#include <string.h>

static void terminateTextFields(aethermesh_TextMessage& text) {
    text.content[sizeof(text.content) - 1] = '\0';
    text.channel[sizeof(text.channel) - 1] = '\0';
}

static bool isRangeTestTextPacket(const aethermesh_MeshPacket& packet) {
    return packet.which_payload == aethermesh_MeshPacket_text_tag &&
           (strncmp(packet.payload.text.content, "PING_", 5) == 0 ||
            strncmp(packet.payload.text.content, "PONG_", 5) == 0);
}

// Keep remote-config control plane moving even during range-test quiet mode.
// Locally-originated ACKs must also drain — otherwise quiet mode holds channel
// HEARD receipts and the phone sticks on "waiting for hearers…".
static bool isUrgentControlPacket(const aethermesh_MeshPacket& packet) {
    if (isRangeTestTextPacket(packet)) return true;
    if (packet.which_payload == aethermesh_MeshPacket_ack_tag) return true;
    if (packet.which_payload == aethermesh_MeshPacket_config_result_tag) return true;
    if (packet.which_payload == aethermesh_MeshPacket_config_tag) {
        return packet.payload.config.report_only || packet.payload.config.request_report;
    }
    return false;
}

// startTransmit() returns as soon as TX begins. Schedule the next direct PONG
// copy only after the current one should have finished, plus a short gap.
static uint32_t directPongSpacingMs(RadioManager* radio) {
    uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
    uint32_t airtimeMs;
    if (sf >= 12) {
        airtimeMs = 2600;
    } else if (sf >= 11) {
        airtimeMs = 1600;
    } else if (sf >= 10) {
        airtimeMs = 900;
    } else if (sf >= 9) {
        airtimeMs = 500;
    } else {
        airtimeMs = 250;
    }
    return airtimeMs + DIRECT_PONG_RESEND_MS;
}

MeshRouter::MeshRouter(RadioManager* radioMgr) {
    radio = radioMgr;
    localNodeId = 0;
    packetSequenceCounter = 0;
    sessionId = 0;
    nodeRole = 0;
    defaultHopLimit = DEFAULT_HOP_LIMIT;
    rebroadcastTxdelayX100 = 100;
    seenPacketsIndex = 0;
    textCallback = nullptr;
    telemetryCallback = nullptr;
    configCallback = nullptr;
    deliveryStatusCallback = nullptr;
    relayedPackets = 0;
    retryPackets = 0;
    ackedPackets = 0;
    ackTimeouts = 0;
    duplicatePackets = 0;
    queueDrops = 0;
    routeChanges = 0;
    directedRelays = 0;
    suppressRelays = 0;
    floodUnicasts = 0;
    rreqSent = 0;
    earlyRepairs = 0;
    rangePingsRx = 0;
    rangePongsQueued = 0;
    rangePongsSent = 0;
    rangePongTxFailures = 0;
    quietMode = false;
    quietModeStartedAt = 0;
    
    // Clear tables
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        routingTable[i].active = false;
        routingTable[i].hasBackup = false;
    }
    for (int i = 0; i < MAX_SEEN_PACKETS_CACHE; i++) {
        seenPackets[i].senderId = 0;
        seenPackets[i].packetId = 0;
        seenPackets[i].retryCount = 0;
        seenPackets[i].timestamp = 0;
    }
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        pendingRebroadcasts[i].active = false;
    }
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        pendingAcks[i].active = false;
    }
    for (int i = 0; i < MAX_CHANNEL_RECEIPTS; i++) {
        channelReceipts[i].active = false;
        channelReceipts[i].heardCount = 0;
        channelReceipts[i].packetId = 0;
    }
    for (int i = 0; i < MAX_PENDING_PONGS; i++) {
        pendingPongs[i].active = false;
        pendingPongs[i].sendCount = 0;
        pendingPongs[i].firstQueuedMs = 0;
    }
    for (int i = 0; i < 6; i++) {
        routeDiscoveries[i].targetId = 0;
        routeDiscoveries[i].lastRequestMs = 0;
    }
    for (int i = 0; i < MAX_ROUTE_FAILURES; i++) {
        routeFailures[i].active = false;
        routeFailures[i].targetId = 0;
        routeFailures[i].failedAtMs = 0;
    }
    for (int i = 0; i < 6; i++) {
        floodDests[i].targetId = 0;
        floodDests[i].lastFloodMs = 0;
    }
    for (int i = 0; i < MAX_NEIGHBOR_QUALITY; i++) {
        neighborQuality[i].active = false;
        neighborQuality[i].neighborId = 0;
        neighborQuality[i].hopCost = 0;
        neighborQuality[i].samples = 0;
        neighborQuality[i].updatedAt = 0;
    }
    lastAnyDiscoveryMs = 0;
}

void MeshRouter::init(uint32_t localId) {
    localNodeId = localId;
    uint32_t entropy = ((uint32_t)random(1, 0x7FFFFFFF) << 1) ^ micros();
    packetSequenceCounter = meshmath::initialPacketSequence(localId, entropy);
    sessionId = ((uint64_t)meshmath::initialPacketSequence(entropy, localId) << 32) |
                meshmath::initialPacketSequence(localId ^ 0xA5A5A5A5u, micros());
    
    Serial.print("MeshRouter initialized. Local Node ID: 0x");
    Serial.println(localNodeId, HEX);
    Serial.printf("  Role: %u (%s) hop_limit=%u\n",
                  nodeRole,
                  nodeRole == 0 ? "Client/no-relay" : (nodeRole == 2 ? "Repeater" : "Router"),
                  defaultHopLimit);
}

void MeshRouter::setNodeRole(uint32_t role) {
    nodeRole = (role > 2) ? 0 : role;
    Serial.printf("MeshRouter role set to %u (%s)\n",
                  nodeRole,
                  canRelay() ? "relay enabled" : "Client — will not relay");
}

void MeshRouter::setDefaultHopLimit(uint8_t hops) {
    if (hops < 1) hops = 1;
    if (hops > 8) hops = 8;
    defaultHopLimit = hops;
}

void MeshRouter::setRebroadcastTxdelayX100(uint32_t x100) {
    if (x100 == 0) x100 = 100;
    if (x100 < 50) x100 = 50;
    if (x100 > 200) x100 = 200;
    rebroadcastTxdelayX100 = x100;
}

bool MeshRouter::shouldFloodUnknownUnicast(const aethermesh_MeshPacket& packet) const {
    // Diagnostics always flood; DMs/config/acks need flood-to-learn-path
    // on Router/Repeater roles (MeshCore-style discovery).
    switch (packet.which_payload) {
        case aethermesh_MeshPacket_text_tag:
        case aethermesh_MeshPacket_trace_route_tag:
        case aethermesh_MeshPacket_config_tag:
        case aethermesh_MeshPacket_config_result_tag:
        case aethermesh_MeshPacket_ack_tag:
        case aethermesh_MeshPacket_route_discovery_tag:
        case aethermesh_MeshPacket_telemetry_tag:
            return true;
        default:
            return false;
    }
}

void MeshRouter::markRouteFailed(uint32_t targetId) {
    if (targetId == 0 || targetId == 0xFFFFFFFFu) return;
    uint32_t now = millis();
    int slot = -1;
    for (int i = 0; i < MAX_ROUTE_FAILURES; i++) {
        if (routeFailures[i].active && routeFailures[i].targetId == targetId) {
            routeFailures[i].failedAtMs = now;
            return;
        }
        if (slot < 0 && !routeFailures[i].active) slot = i;
    }
    if (slot < 0) {
        // Evict oldest
        slot = 0;
        for (int i = 1; i < MAX_ROUTE_FAILURES; i++) {
            if (routeFailures[i].failedAtMs < routeFailures[slot].failedAtMs) {
                slot = i;
            }
        }
    }
    routeFailures[slot].targetId = targetId;
    routeFailures[slot].failedAtMs = now;
    routeFailures[slot].active = true;
}

static void clearRouteFailureSlot(RouteFailure* failures, uint32_t targetId) {
    for (int i = 0; i < MAX_ROUTE_FAILURES; i++) {
        if (failures[i].active && failures[i].targetId == targetId) {
            failures[i].active = false;
        }
    }
}

bool MeshRouter::isRouteFailedRecently(uint32_t targetId) const {
    uint32_t now = millis();
    for (int i = 0; i < MAX_ROUTE_FAILURES; i++) {
        if (routeFailures[i].active && routeFailures[i].targetId == targetId) {
            return (uint32_t)(now - routeFailures[i].failedAtMs) <= ROUTE_FAIL_FLOOD_MS;
        }
    }
    return false;
}

void MeshRouter::learnReverseRoute(uint32_t originId, uint32_t viaHopId, uint8_t lastHopCost) {
    if (originId == 0 || originId == localNodeId || viaHopId == 0) return;
    if (viaHopId == originId) {
        addRoute(originId, viaHopId, lastHopCost);
        return;
    }
    RouteEntry* existing = getRoute(originId);
    if (existing && existing->nextHopId == viaHopId) {
        // Same next hop: refresh without collapsing multi-hop cost to last-hop only.
        addRoute(originId, viaHopId, existing->metric);
    } else {
        addRoute(originId, viaHopId, meshmath::multiHopLearnedMetric(lastHopCost));
    }
}

void MeshRouter::applyDirectedNextHop(aethermesh_MeshPacket* packet,
                                      bool preferReturnPath) {
    if (packet == nullptr) return;
    if (packet->recipient_id == 0 || packet->recipient_id == 0xFFFFFFFFu) {
        packet->next_hop_id = 0;
        return;
    }
    RouteEntry* route = getRoute(packet->recipient_id);
    if (preferReturnPath) {
        // ACK / RREP / traceroute / config reply: stamp reverse next hop;
        // flood only if cold (ignore recent *forward* DM failure).
        const bool hasReverse =
            route != nullptr && meshmath::hasUsableDirectedHop(route->nextHopId);
        packet->next_hop_id = meshmath::restampNextHopId(
            hasReverse ? route->nextHopId : 0,
            !meshmath::shouldFloodReturnPath(hasReverse));
        return;
    }
    if (isRouteFailedRecently(packet->recipient_id)) {
        packet->next_hop_id = 0; // flood fallback after recent failure
        return;
    }
    const bool hasDirected =
        route != nullptr && meshmath::hasUsableDirectedHop(route->nextHopId);
    packet->next_hop_id = meshmath::restampNextHopId(
        hasDirected ? route->nextHopId : 0, hasDirected);
}

void MeshRouter::refreshPendingDirectedNextHop(uint32_t targetId,
                                               uint32_t newNextHop) {
    if (targetId == 0 || targetId == 0xFFFFFFFFu || newNextHop == 0) return;
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (!pendingAcks[i].active || pendingAcks[i].stored) continue;
        if (pendingAcks[i].packet.recipient_id != targetId) continue;
        if (!meshmath::shouldRetargetDirectedPending(
                pendingAcks[i].packet.next_hop_id, newNextHop,
                pendingAcks[i].earlyFloodDone)) {
            continue;
        }
        // Better / fresher directed hop appeared — retarget without burning
        // the backup-probe flag (genuine backup remains eligible).
        pendingAcks[i].packet.next_hop_id = newNextHop;
        Serial.printf("Retargeted pending packet %u -> 0x%08X via 0x%08X\n",
                      pendingAcks[i].packet.packet_id, targetId, newNextHop);
    }
}

uint8_t MeshRouter::countActiveRebroadcasts() const {
    uint8_t n = 0;
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        if (pendingRebroadcasts[i].active) n++;
    }
    return n;
}

uint8_t MeshRouter::countActivePendingAcks() const {
    uint8_t n = 0;
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (pendingAcks[i].active) n++;
    }
    return n;
}

uint8_t MeshRouter::currentCongestionScore() const {
    uint32_t recentAir = radio ? radio->getRecentAirtimeMs() : 0;
    return meshmath::congestionScore(
        countActiveRebroadcasts(), MAX_PENDING_REBROADCASTS,
        countActivePendingAcks(), MAX_PENDING_ACKS,
        recentAir, AIRTIME_CONGESTION_WINDOW_MS);
}

void MeshRouter::noteNeighborAckQuality(uint32_t neighborId, float ackSnr) {
    if (neighborId == 0 || neighborId == localNodeId || ackSnr == 0.0f) return;
    uint8_t sample = meshmath::hopCost(ackSnr);
    uint32_t now = millis();
    int slot = -1;
    int oldest = 0;
    for (int i = 0; i < MAX_NEIGHBOR_QUALITY; i++) {
        if (neighborQuality[i].active &&
            neighborQuality[i].neighborId == neighborId) {
            neighborQuality[i].hopCost =
                meshmath::smoothedRouteMetric(neighborQuality[i].hopCost, sample);
            if (neighborQuality[i].samples < 255) neighborQuality[i].samples++;
            neighborQuality[i].updatedAt = now;
            return;
        }
        if (!neighborQuality[i].active && slot < 0) slot = i;
        if (!neighborQuality[oldest].active ||
            neighborQuality[i].updatedAt < neighborQuality[oldest].updatedAt) {
            oldest = i;
        }
    }
    if (slot < 0) slot = oldest;
    neighborQuality[slot].neighborId = neighborId;
    neighborQuality[slot].hopCost = sample;
    neighborQuality[slot].samples = 1;
    neighborQuality[slot].updatedAt = now;
    neighborQuality[slot].active = true;
}

uint8_t MeshRouter::neighborLinkCost(uint32_t neighborId) const {
    if (neighborId == 0) return 0;
    uint32_t now = millis();
    for (int i = 0; i < MAX_NEIGHBOR_QUALITY; i++) {
        if (!neighborQuality[i].active) continue;
        if (neighborQuality[i].neighborId != neighborId) continue;
        if ((uint32_t)(now - neighborQuality[i].updatedAt) >
            NEIGHBOR_QUALITY_TIMEOUT_MS) {
            return 0;
        }
        // Need at least one ACK sample before biasing routes.
        return neighborQuality[i].samples > 0 ? neighborQuality[i].hopCost : 0;
    }
    return 0;
}

uint8_t MeshRouter::metricWithNeighborQuality(uint32_t nextHopId,
                                              uint8_t metric) const {
    uint8_t nq = neighborLinkCost(nextHopId);
    if (nq == 0) return metric;
    return meshmath::linkQualityAdjustedMetric(metric, nq);
}

void MeshRouter::retargetPendingAfterRelayLoss(uint32_t targetId,
                                               bool promotedBackup,
                                               uint32_t newNextHop) {
    if (targetId == 0 || targetId == 0xFFFFFFFFu) return;
    uint32_t now = millis();
    uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
    bool hasPending = false;
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (!pendingAcks[i].active) continue;
        if (pendingAcks[i].packet.recipient_id != targetId) continue;
        hasPending = true;
        if (!meshmath::shouldRetargetAfterRelayLoss(pendingAcks[i].earlyFloodDone,
                                                    pendingAcks[i].stored)) {
            continue;
        }
        if (promotedBackup && newNextHop != 0) {
            pendingAcks[i].packet.next_hop_id = newNextHop;
            // Fresh primary after promote — allow another backup probe later
            // if this hop also fails (do not leave earlyBackupDone sticky).
            pendingAcks[i].earlyBackupDone = false;
            pendingAcks[i].cadFailStreak = 0;
        } else {
            // Cold table / flood fallback — restamp via applyDirectedNextHop.
            applyDirectedNextHop(&pendingAcks[i].packet);
        }
        uint32_t delay =
            meshmath::relayLossRetargetDelayMs(sf, (uint32_t)random(0, 101));
        // Congestion: directed retarget stays lighter than flood rediscovery.
        uint8_t cong = currentCongestionScore();
        delay += meshmath::congestionDeferMs(cong, false, sf, (uint32_t)random(0, 101));
        if ((int32_t)(pendingAcks[i].nextRetryTime - now) > (int32_t)delay) {
            pendingAcks[i].nextRetryTime = now + delay;
        }
        Serial.printf("Relay-loss retarget packet %u -> 0x%08X via 0x%08X\n",
                      pendingAcks[i].packet.packet_id, targetId,
                      pendingAcks[i].packet.next_hop_id);
    }
    if (meshmath::shouldScheduleRediscoveryOnInvalidate(promotedBackup,
                                                        hasPending)) {
        // Coalesced by sendRouteRequest per-target / global pacing.
        sendRouteRequest(targetId);
    }
    if (promotedBackup && newNextHop != 0) {
        wakeStoredPendingForTarget(targetId);
    }
}

void MeshRouter::wakeStoredPendingForTarget(uint32_t targetId) {
    if (targetId == 0 || targetId == 0xFFFFFFFFu) return;
    if (quietMode) return;
    RouteEntry* route = getRoute(targetId);
    const bool hasRoute = route != nullptr && route->nextHopId != 0;
    if (!hasRoute) return;

    uint32_t now = millis();
    uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
    uint8_t cong = currentCongestionScore();
    // Phase 6: under congestion, skip wake this sighting so we do not pile
    // CAD failures — leave storedWakeDone clear for a later quieter pass.
    if (meshmath::shouldDeferCongestedStoredWake(cong)) {
        Serial.printf("Deferring stored wake for 0x%08X (congestion=%u)\n",
                      targetId, cong);
        return;
    }
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (!pendingAcks[i].active) continue;
        if (pendingAcks[i].packet.recipient_id != targetId) continue;
        if (!meshmath::shouldWakeStoredPending(pendingAcks[i].stored,
                                               pendingAcks[i].storedWakeDone,
                                               hasRoute)) {
            continue;
        }
        // Stamp directed next hop now; short SF-scaled delay clears the
        // observation airtime, then the normal retry path TXes once.
        applyDirectedNextHop(&pendingAcks[i].packet);
        uint32_t delay =
            meshmath::storeForwardWakeDelayMs(sf, (uint32_t)random(0, 201));
        // Soft elevated congestion still adds light backoff (directed-scale).
        delay += meshmath::congestionDeferMs(cong, true, sf, (uint32_t)random(0, 101));
        pendingAcks[i].nextRetryTime = now + delay;
        pendingAcks[i].storedWakeDone = true;
        Serial.printf("Waking stored packet %u for 0x%08X via 0x%08X\n",
                      pendingAcks[i].packet.packet_id, targetId,
                      pendingAcks[i].packet.next_hop_id);
    }
}

void MeshRouter::reinforceRouteOnDelivery(uint32_t destId, float ackSnr) {
    if (destId == 0 || destId == 0xFFFFFFFFu || destId == localNodeId) return;
    RouteEntry* route = getRoute(destId);
    if (route == nullptr || route->nextHopId == 0) return;

    uint8_t metric = route->metric;
    if (ackSnr != 0.0f) {
        uint8_t sample = meshmath::hopCost(ackSnr);
        // For multi-hop routes keep cumulative cost from rising to a
        // single-hop sample; only smooth when already near 1 hop.
        if (route->metric <= sample + 2) {
            metric = meshmath::smoothedRouteMetric(route->metric, sample);
        }
    }
    // Refresh primary (clears failure + soft-age); nudge backup soft-age.
    // Neighbor ACK quality is noted on RX via immediateSender (Phase 6).
    uint32_t hop = route->nextHopId;
    bool hadBackup = route->hasBackup;
    uint32_t backupHop = route->backupNextHopId;
    uint8_t backupMetric = route->backupMetric;
    uint32_t backupTs = route->backupTimestamp;
    addRoute(destId, hop, metric);
    RouteEntry* refreshed = getRoute(destId);
    if (refreshed == nullptr) return;
    if (hadBackup && backupHop != 0 && backupHop != refreshed->nextHopId) {
        uint32_t now = millis();
        if (!refreshed->hasBackup || refreshed->backupNextHopId != backupHop) {
            refreshed->backupNextHopId = backupHop;
            refreshed->backupMetric = backupMetric;
            refreshed->backupTimestamp = backupTs;
            refreshed->hasBackup = true;
        }
        refreshed->backupTimestamp = meshmath::nudgedSoftAgeTimestamp(
            now, refreshed->backupTimestamp, ROUTE_SOFT_AGE_MS);
    }
}

void MeshRouter::maybeSoftDemoteRoute(RouteEntry* route) {
    if (route == nullptr || !route->hasBackup) return;
    uint32_t now = millis();
    uint32_t primaryAge = now - route->timestamp;
    uint32_t backupAge = now - route->backupTimestamp;
    if (!meshmath::backupRouteIsUsable(now, route->backupTimestamp, ROUTE_TIMEOUT_MS)) {
        return;
    }
    if (!meshmath::shouldDemoteStalePrimary(primaryAge, route->metric, backupAge,
                                            route->backupMetric, ROUTE_SOFT_AGE_MS,
                                            true)) {
        return;
    }
    // Swap: fresher backup becomes primary; demoted primary retained as backup.
    uint32_t oldHop = route->nextHopId;
    uint8_t oldMetric = route->metric;
    uint32_t oldTs = route->timestamp;
    route->nextHopId = route->backupNextHopId;
    route->metric = route->backupMetric;
    route->timestamp = route->backupTimestamp;
    route->backupNextHopId = oldHop;
    route->backupMetric = oldMetric;
    route->backupTimestamp = oldTs;
    route->hasBackup = true;
    routeChanges++;
    Serial.printf("Soft-demoted stale route to 0x%08X; now via 0x%08X (was 0x%08X)\n",
                  route->targetId, route->nextHopId, oldHop);
    refreshPendingDirectedNextHop(route->targetId, route->nextHopId);
}

bool MeshRouter::tryEarlyPathRepair(PendingAck& pending, uint32_t now) {
    // Phase 5 guardrail: stored / flood-done packets never re-enter repair.
    if (!pending.active || pending.retriesLeft == 0) return false;
    if (!meshmath::mayEarlyLimitedFlood(pending.earlyFloodDone, pending.stored)) {
        return false;
    }
    const uint32_t dest = pending.packet.recipient_id;
    if (dest == 0 || dest == 0xFFFFFFFFu) return false;

    // Only repair directed unicasts (or CAD-stuck directed attempts). Never
    // re-flood a packet that already left as a cold-table flood.
    const bool directed = pending.packet.next_hop_id != 0;
    const bool cadStuck = directed && meshmath::shouldAbandonDirectedOnCad(
        pending.cadFailStreak, EARLY_CAD_FAIL_THRESHOLD);
    if (!directed && !pending.earlyBackupDone) return false;

    RouteEntry* route = getRoute(dest);
    uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
    uint8_t routeMetric = route ? route->metric : 0;
    uint32_t routeAge = route ? (now - route->timestamp) : ROUTE_TIMEOUT_MS;
    uint32_t waited = now - pending.trackedAt;

    // Fresher directed hop landed while we were waiting — use it instead of
    // probing backup / flooding (preserves genuine backup for later).
    if (route != nullptr && route->nextHopId != 0 &&
        !isRouteFailedRecently(dest) &&
        meshmath::shouldRetargetDirectedPending(pending.packet.next_hop_id,
                                                route->nextHopId,
                                                pending.earlyFloodDone)) {
        pending.packet.next_hop_id = route->nextHopId;
        pending.cadFailStreak = 0;
        pending.nextRetryTime = now;
        Serial.printf("Early retarget for packet %u -> 0x%08X via 0x%08X\n",
                      pending.packet.packet_id, dest, route->nextHopId);
        return true;
    }

    uint32_t probeAfter = meshmath::earlyBackupProbeDelayMs(
        sf, routeMetric, routeAge, ROUTE_SOFT_AGE_MS, 0);
    uint32_t floodAfter = meshmath::earlyFloodDelayMs(
        sf, routeMetric, routeAge, ROUTE_SOFT_AGE_MS, 0);

    const bool hasUsableBackup =
        route != nullptr && route->hasBackup &&
        meshmath::backupRouteIsUsable(now, route->backupTimestamp, ROUTE_TIMEOUT_MS) &&
        route->backupNextHopId != 0 &&
        route->backupNextHopId != route->nextHopId &&
        // Do not "probe" a backup that is already the pending next hop.
        route->backupNextHopId != pending.packet.next_hop_id;

    // 1) Backup-next-hop probe (timer or CAD-stuck) before full ACK timeout.
    const bool canProbe = meshmath::mayEarlyBackupProbe(
        pending.earlyBackupDone, pending.earlyFloodDone, pending.stored);
    const bool probeDue =
        canProbe &&
        (meshmath::shouldEarlyBackupProbe(waited, probeAfter,
                                          pending.earlyBackupDone, true) ||
         cadStuck);
    if (probeDue && (hasUsableBackup || cadStuck)) {
        if (hasUsableBackup) {
            // Promote backup without marking flood-failed yet. Keep the old
            // primary as backup so a genuine alternate path is not discarded.
            uint32_t oldHop = route->nextHopId;
            uint8_t oldMetric = route->metric;
            uint32_t oldTs = route->timestamp;
            route->nextHopId = route->backupNextHopId;
            route->metric = route->backupMetric;
            route->timestamp = now;
            route->backupNextHopId = oldHop;
            route->backupMetric = oldMetric;
            route->backupTimestamp = oldTs;
            route->hasBackup = (oldHop != 0 && oldHop != route->nextHopId);
            routeChanges++;
            clearRouteFailureSlot(routeFailures, dest);
            pending.packet.next_hop_id = route->nextHopId;
            pending.earlyBackupDone = true;
            pending.cadFailStreak = 0;
            pending.nextRetryTime = now; // send on this loop pass
            earlyRepairs++;
            Serial.printf("Early backup probe for packet %u -> 0x%08X via 0x%08X\n",
                          pending.packet.packet_id, dest, route->nextHopId);
            // Phase 6: other pending DMs to same dest follow the promoted hop.
            refreshPendingDirectedNextHop(dest, route->nextHopId);
            return true;
        }
        // CAD-stuck with no backup: fall through to limited flood below.
        pending.earlyBackupDone = true;
    }

    // 2) Timed limited flood when backup is unavailable / already probed.
    // Never flood while a backup probe is still the armed next hop.
    const bool backupUnavailable = pending.earlyBackupDone || !hasUsableBackup;
    if (meshmath::shouldEarlyLimitedFlood(waited, floodAfter,
                                          pending.earlyFloodDone,
                                          backupUnavailable) ||
        (cadStuck && !pending.earlyFloodDone && !hasUsableBackup)) {
        uint8_t cong = currentCongestionScore();
        // Phase 6: defer repair floods under congestion instead of piling CAD.
        if (meshmath::shouldDeferCongestedFlood(cong)) {
            uint32_t defer = meshmath::congestionDeferMs(
                cong, true, sf, (uint32_t)random(0, 201));
            if (defer > 0 &&
                (int32_t)(pending.nextRetryTime - now) < (int32_t)defer) {
                pending.nextRetryTime = now + defer;
            }
            Serial.printf("Deferring early flood for packet %u (congestion=%u)\n",
                          pending.packet.packet_id, cong);
            return false;
        }
        if (!noteFloodDest(dest)) {
            // Cooldown: do not storm; wait for the normal ACK retry path.
            return false;
        }
        markRouteFailed(dest);
        pending.packet.next_hop_id = 0;
        pending.earlyFloodDone = true;
        pending.earlyBackupDone = true;
        pending.cadFailStreak = 0;
        pending.nextRetryTime = now;
        earlyRepairs++;
        Serial.printf("Early limited flood for packet %u -> 0x%08X\n",
                      pending.packet.packet_id, dest);
        return true;
    }
    return false;
}

void MeshRouter::installReplyPath(uint32_t targetId, uint32_t viaHopId, uint8_t metric) {
    if (!meshmath::shouldInstallReplyPath(targetId, localNodeId)) return;
    if (viaHopId == 0) return;
    addRoute(targetId, viaHopId, metric);
}

bool MeshRouter::noteFloodDest(uint32_t targetId) {
    uint32_t now = millis();
    int slot = -1;
    int oldest = 0;
    for (int i = 0; i < 6; i++) {
        if (floodDests[i].targetId == targetId) {
            if (meshmath::floodDestCooldownActive(now, floodDests[i].lastFloodMs,
                                                 FLOOD_DEST_COOLDOWN_MS)) {
                return false;
            }
            floodDests[i].lastFloodMs = now;
            return true;
        }
        if (floodDests[i].targetId == 0 && slot < 0) slot = i;
        if (floodDests[i].lastFloodMs < floodDests[oldest].lastFloodMs) oldest = i;
    }
    if (slot < 0) slot = oldest;
    floodDests[slot].targetId = targetId;
    floodDests[slot].lastFloodMs = now;
    return true;
}

void MeshRouter::loop() {
    uint32_t now = millis();
    // Leave-behind safeguard: never stay quiet indefinitely if the phone
    // disconnects without sending STOP (or the app crashes mid-test).
    if (quietMode && quietModeStartedAt != 0 &&
        (uint32_t)(now - quietModeStartedAt) >= QUIET_MODE_MAX_MS) {
        Serial.println("Range-test quiet mode auto-cleared after timeout.");
        setQuietMode(false);
    }
    drainPendingPongReplies();
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        if (pendingRebroadcasts[i].active &&
            (int32_t)(now - pendingRebroadcasts[i].transmitTime) >= 0) {
            bool urgent = isUrgentControlPacket(pendingRebroadcasts[i].packet);
            // Quiet mode: only emit range-test / remote-config control; hold other relays.
            if (quietMode && !urgent) {
                pendingRebroadcasts[i].transmitTime = now + 1000;
                continue;
            }
            const bool isLocalAck =
                pendingRebroadcasts[i].packet.which_payload == aethermesh_MeshPacket_ack_tag &&
                pendingRebroadcasts[i].packet.sender_id == localNodeId;
            // Capture relay intent before serializeAndSend restamps next_hop.
            const bool queuedDirected =
                pendingRebroadcasts[i].packet.next_hop_id != 0;
            const bool queuedUnicast =
                pendingRebroadcasts[i].packet.recipient_id != 0 &&
                pendingRebroadcasts[i].packet.recipient_id != 0xFFFFFFFFu;
            if (serializeAndSend(&pendingRebroadcasts[i].packet, urgent)) {
                if (pendingRebroadcasts[i].packet.sender_id == localNodeId) {
                    // Local insurance retries count; locally-originated ACKs do not.
                    if (!isLocalAck) {
                        retryPackets++;
                    }
                } else {
                    relayedPackets++;
                    if (queuedUnicast) {
                        if (queuedDirected) directedRelays++;
                        else floodUnicasts++;
                    }
                }
                pendingRebroadcasts[i].active = false;
                if (isLocalAck) {
                    Serial.printf("Transmitted queued ACK for packet %u to 0x%08X\n",
                                  pendingRebroadcasts[i].packet.payload.ack.acked_packet_id,
                                  pendingRebroadcasts[i].packet.recipient_id);
                } else {
                    Serial.printf("Transmitted queued rebroadcast for packet %u from sender 0x%08X\n",
                                  pendingRebroadcasts[i].packet.packet_id,
                                  pendingRebroadcasts[i].packet.sender_id);
                }
            } else {
                uint32_t holdMs = isLocalAck ? ACK_QUEUE_TTL_MS : 5000u;
                if (now - pendingRebroadcasts[i].queuedAtTime > holdMs) {
                    // Radio stayed busy past the hold window; give up.
                    pendingRebroadcasts[i].active = false;
                    queueDrops++;
                    Serial.printf("Dropping queued %s for packet %u (radio busy too long)\n",
                                  isLocalAck ? "ACK" : "rebroadcast",
                                  isLocalAck
                                      ? pendingRebroadcasts[i].packet.payload.ack.acked_packet_id
                                      : pendingRebroadcasts[i].packet.packet_id);
                } else if (isLocalAck) {
                    // CAD-busy: restagger with the *other* mixer so same-slot
                    // primary colliders do not retry on top of each other again.
                    uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
                    uint32_t slot = (pendingRebroadcasts[i].packet.retry_count == 0)
                        ? meshmath::channelAckAltSlotIndex(localNodeId)
                        : meshmath::channelAckSlotIndex(localNodeId);
                    pendingRebroadcasts[i].transmitTime = now +
                        meshmath::channelAckBusyRetryDelayMs(sf, (uint32_t)random(0, 401)) +
                        meshmath::channelAckSlotWidthMs(sf) * slot;
                } else {
                    pendingRebroadcasts[i].transmitTime = now +
                        meshmath::radioBusyRetryDelayMs(random(0, 181));
                }
            }
        }
    }

    // Retransmit locally-originated want_ack packets that haven't been ACKed.
    // Same packet_id on purpose: the recipient's dedup cache prevents double
    // delivery, and duplicates addressed to it trigger a fresh ACK.
    // Quiet mode (phone range test): pause store-forward / ACK retries so
    // CAD-busy contention does not eat direct PONG airtime.
    if (quietMode) {
        drainPendingPongReplies();
        return;
    }

    // Phase 3: early backup probe / limited flood before full ACK timeout when
    // a directed next hop looks stuck (timer or CAD streak). Accelerates
    // nextRetryTime; the send path below performs the actual TX.
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (pendingAcks[i].active && !pendingAcks[i].stored) {
            tryEarlyPathRepair(pendingAcks[i], now);
        }
    }

    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (pendingAcks[i].active && (int32_t)(now - pendingAcks[i].nextRetryTime) >= 0) {
            if ((int32_t)(now - pendingAcks[i].expiresAt) >= 0) {
                pendingAcks[i].active = false;
                emitDeliveryStatus(
                    pendingAcks[i].packet.packet_id,
                    pendingAcks[i].packet.recipient_id,
                    aethermesh_DeliveryStatus_State_EXPIRED,
                    aethermesh_DeliveryStatus_Reason_MESSAGE_EXPIRED,
                    pendingAcks[i].packet.retry_count
                );
                continue;
            }
            if (pendingAcks[i].retriesLeft == 0) {
                if (!pendingAcks[i].stored) {
                    pendingAcks[i].stored = true;
                    pendingAcks[i].storedWakeDone = false;
                    // Leaving repair phases — stored wake / RREP owns recovery.
                    pendingAcks[i].earlyBackupDone = true;
                    pendingAcks[i].earlyFloodDone = true;
                    ackTimeouts++;
                    invalidateRoute(pendingAcks[i].packet.recipient_id);
                    sendRouteRequest(pendingAcks[i].packet.recipient_id);
                    emitDeliveryStatus(
                        pendingAcks[i].packet.packet_id,
                        pendingAcks[i].packet.recipient_id,
                        aethermesh_DeliveryStatus_State_STORED,
                        aethermesh_DeliveryStatus_Reason_ACK_TIMEOUT,
                        pendingAcks[i].packet.retry_count
                    );
                    pendingAcks[i].nextRetryTime = now + STORE_FORWARD_RETRY_MS + random(0, 5000);
                    Serial.printf("Stored packet %u for later delivery.\n",
                                  pendingAcks[i].packet.packet_id);
                    continue;
                }
                pendingAcks[i].retriesLeft = 1;
            }
            // Path repair: promote backup, else invalidate + mark failed (flood).
            // When Phase 3 early repair already promoted backup or armed a
            // limited flood, do not immediately invalidate again — that would
            // burn the backup or double-mark failure before the probe TX.
            // Stored retries never invalidate (route may have just woken).
            const bool skipInvalidateForEarly =
                pendingAcks[i].earlyFloodDone ||
                (pendingAcks[i].earlyBackupDone &&
                 pendingAcks[i].packet.next_hop_id != 0);
            if (!pendingAcks[i].stored && !skipInvalidateForEarly) {
                invalidateRoute(pendingAcks[i].packet.recipient_id);
            }
            pendingAcks[i].packet.retry_count++;
            // Prefer directed next hop; applyDirectedNextHop floods if failed.
            applyDirectedNextHop(&pendingAcks[i].packet);
            if (serializeAndSend(&pendingAcks[i].packet)) {
                retryPackets++;
                pendingAcks[i].retriesLeft--;
                pendingAcks[i].cadFailStreak = 0;
                uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
                // Phase 4/5: coalesce rediscovery — only when the path is cold /
                // failed / early-flooded, skip flood piggyback, and never emit
                // a fighting RREQ for a directed stored wake that has a route.
                const bool hasRoute =
                    getRoute(pendingAcks[i].packet.recipient_id) != nullptr;
                const bool failedRecently =
                    isRouteFailedRecently(pendingAcks[i].packet.recipient_id);
                const bool needsRediscovery =
                    !pendingAcks[i].stored &&
                    (meshmath::shouldRediscoverAfterRouteFail(hasRoute, failedRecently) ||
                     pendingAcks[i].earlyFloodDone);
                const bool payloadIsFlood =
                    pendingAcks[i].packet.next_hop_id == 0;
                if (meshmath::shouldEmitRouteRequest(needsRediscovery, payloadIsFlood)) {
                    sendRouteRequest(pendingAcks[i].packet.recipient_id);
                }
                RouteEntry* retryRoute = getRoute(pendingAcks[i].packet.recipient_id);
                uint8_t retryMetric = retryRoute ? retryRoute->metric : 0;
                // Use aged metric so stale paths back off / rediscover sooner.
                if (retryRoute) {
                    retryMetric = meshmath::agedRouteMetric(
                        retryMetric, now - retryRoute->timestamp, ROUTE_SOFT_AGE_MS);
                }
                if (pendingAcks[i].stored) {
                    // Next long store-forward cycle may wake again on route sighting.
                    pendingAcks[i].storedWakeDone = false;
                    pendingAcks[i].nextRetryTime =
                        now + STORE_FORWARD_RETRY_MS + random(0, 5000);
                } else {
                    pendingAcks[i].nextRetryTime =
                        now + meshmath::ackRetryDelayMs(
                            sf, pendingAcks[i].packet.retry_count, retryMetric,
                            random(0, 500));
                }
                Serial.printf("Retransmitting packet %u retry %u (retries left: %u)\n",
                              pendingAcks[i].packet.packet_id,
                              pendingAcks[i].packet.retry_count,
                              pendingAcks[i].retriesLeft);
                emitDeliveryStatus(
                    pendingAcks[i].packet.packet_id,
                    pendingAcks[i].packet.recipient_id,
                    aethermesh_DeliveryStatus_State_RETRYING,
                    aethermesh_DeliveryStatus_Reason_REASON_UNSPECIFIED,
                    pendingAcks[i].packet.retry_count
                );
            } else {
                pendingAcks[i].packet.retry_count--;
                pendingAcks[i].cadFailStreak++;
                // CAD-busy on a directed unicast: shorten deferral and let
                // tryEarlyPathRepair abandon the next hop on the next loop.
                uint32_t defer = meshmath::radioBusyRetryDelayMs(random(0, 181));
                if (pendingAcks[i].packet.next_hop_id != 0 &&
                    meshmath::shouldAbandonDirectedOnCad(
                        pendingAcks[i].cadFailStreak, EARLY_CAD_FAIL_THRESHOLD)) {
                    defer = 40 + (uint32_t)random(0, 80);
                }
                pendingAcks[i].nextRetryTime = now + defer;
            }
        }
    }

    // Expire channel receipt aggregation windows (no FAILED — broadcast is best-effort).
    for (int i = 0; i < MAX_CHANNEL_RECEIPTS; i++) {
        if (channelReceipts[i].active && (int32_t)(now - channelReceipts[i].expiresAt) >= 0) {
            Serial.printf("Channel receipt window closed for packet %u (heard=%u).\n",
                          channelReceipts[i].packetId, channelReceipts[i].heardCount);
            channelReceipts[i].active = false;
        }
    }
}

void MeshRouter::addRoute(uint32_t targetId, uint32_t nextHopId, uint8_t metric) {
    if (targetId == localNodeId) return;
    
    // Phase 6: demote multi-hop paths through flaky ACK-history neighbors.
    // Direct neighbor rows already carry live RX hopCost — do not double-penalize.
    if (targetId != nextHopId) {
        metric = metricWithNeighborQuality(nextHopId, metric);
    }

    uint32_t now = millis();
    // A fresh observation clears flood-fallback for this destination.
    clearRouteFailureSlot(routeFailures, targetId);
    RouteEntry* existing = getRoute(targetId);
    
    if (existing) {
        if (existing->nextHopId == nextHopId) {
            existing->metric = meshmath::smoothedRouteMetric(existing->metric, metric);
            existing->timestamp = now;
            // Recipient / next hop still alive — wake any STORED want_ack.
            wakeStoredPendingForTarget(targetId);
            return;
        }
        if (existing->hasBackup && existing->backupNextHopId == nextHopId) {
            existing->backupMetric = meshmath::smoothedRouteMetric(existing->backupMetric, metric);
            existing->backupTimestamp = now;
        }
        // Refresh the active next hop, accept a genuinely better / fresher path
        // (aged metric), or replace when old enough to be suspect. Soft-age
        // decay prefers fresher SNR without waiting for hard timeout.
        if (meshmath::shouldReplaceRoute(existing->nextHopId, existing->metric,
                                         now - existing->timestamp, nextHopId,
                                         metric, ROUTE_TIMEOUT_MS, ROUTE_SOFT_AGE_MS)) {
            const bool hopChanged = existing->nextHopId != nextHopId;
            if (hopChanged) routeChanges++;
            existing->backupNextHopId = existing->nextHopId;
            existing->backupMetric = existing->metric;
            existing->backupTimestamp = existing->timestamp;
            existing->hasBackup = true;
            existing->nextHopId = nextHopId;
            existing->metric = metric;
            existing->timestamp = now;
            
            Serial.print("Route updated: Target 0x");
            Serial.print(targetId, HEX);
            Serial.print(" via NextHop 0x");
            Serial.print(nextHopId, HEX);
            Serial.print(" Hops: ");
            Serial.println(metric);
            if (hopChanged) {
                refreshPendingDirectedNextHop(targetId, nextHopId);
            }
            wakeStoredPendingForTarget(targetId);
        } else if (!existing->hasBackup ||
                   !meshmath::backupRouteIsUsable(now, existing->backupTimestamp, ROUTE_TIMEOUT_MS) ||
                   metric + 2u < meshmath::agedRouteMetric(
                       existing->backupMetric, now - existing->backupTimestamp,
                       ROUTE_SOFT_AGE_MS)) {
            existing->backupNextHopId = nextHopId;
            existing->backupMetric = metric;
            existing->backupTimestamp = now;
            existing->hasBackup = true;
            Serial.printf("Backup route: Target 0x%08X via 0x%08X metric %u\n",
                          targetId, nextHopId, metric);
            // Neighbor activity near the dest — still a wake signal.
            wakeStoredPendingForTarget(targetId);
        }
        return;
    }
    
    // Find empty slot
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (!routingTable[i].active) {
            routingTable[i].targetId = targetId;
            routingTable[i].nextHopId = nextHopId;
            routingTable[i].metric = metric;
            routingTable[i].timestamp = now;
            routingTable[i].hasBackup = false;
            routingTable[i].active = true;
            
            Serial.print("New Route added: Target 0x");
            Serial.print(targetId, HEX);
            Serial.print(" via NextHop 0x");
            Serial.print(nextHopId, HEX);
            Serial.print(" Hops: ");
            Serial.println(metric);
            wakeStoredPendingForTarget(targetId);
            return;
        }
    }
    
    // Evict oldest if full
    int oldestIdx = 0;
    uint32_t oldestTime = routingTable[0].timestamp;
    for (int i = 1; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (routingTable[i].timestamp < oldestTime) {
            oldestTime = routingTable[i].timestamp;
            oldestIdx = i;
        }
    }
    
    routingTable[oldestIdx].targetId = targetId;
    routingTable[oldestIdx].nextHopId = nextHopId;
    routingTable[oldestIdx].metric = metric;
    routingTable[oldestIdx].timestamp = now;
    routingTable[oldestIdx].hasBackup = false;
    routingTable[oldestIdx].active = true;
    
    Serial.print("Route table full. Evicted oldest. Added: Target 0x");
    Serial.print(targetId, HEX);
    Serial.print(" via NextHop 0x");
    Serial.println(nextHopId, HEX);
    wakeStoredPendingForTarget(targetId);
}

RouteEntry* MeshRouter::getRoute(uint32_t targetId) {
    uint32_t now = millis();
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (routingTable[i].active && routingTable[i].targetId == targetId) {
            // Check timeout
            if (now - routingTable[i].timestamp > ROUTE_TIMEOUT_MS) {
                if (routingTable[i].hasBackup && meshmath::backupRouteIsUsable(
                        now, routingTable[i].backupTimestamp, ROUTE_TIMEOUT_MS)) {
                    routingTable[i].nextHopId = routingTable[i].backupNextHopId;
                    routingTable[i].metric = routingTable[i].backupMetric;
                    routingTable[i].timestamp = routingTable[i].backupTimestamp;
                    routingTable[i].hasBackup = false;
                    routeChanges++;
                } else {
                    routingTable[i].active = false;
                    continue;
                }
            }
            // Soft-demote stale primary to a fresher backup before hard expiry.
            maybeSoftDemoteRoute(&routingTable[i]);
            return &routingTable[i];
        }
    }
    return nullptr;
}

void MeshRouter::invalidateRoute(uint32_t targetId) {
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (routingTable[i].active && routingTable[i].targetId == targetId) {
            uint32_t now = millis();
            if (routingTable[i].hasBackup && meshmath::backupRouteIsUsable(
                    now, routingTable[i].backupTimestamp, ROUTE_TIMEOUT_MS)) {
                routingTable[i].nextHopId = routingTable[i].backupNextHopId;
                routingTable[i].metric = routingTable[i].backupMetric;
                routingTable[i].timestamp = now;
                routingTable[i].hasBackup = false;
                routeChanges++;
                // Backup is usable — keep directed routing (do not force flood).
                clearRouteFailureSlot(routeFailures, targetId);
                Serial.printf("Promoted backup route to 0x%08X via 0x%08X.\n",
                              targetId, routingTable[i].nextHopId);
                // Phase 6: promptly retarget pending want_ack + wake stored.
                retargetPendingAfterRelayLoss(targetId, true,
                                              routingTable[i].nextHopId);
            } else {
                routingTable[i].active = false;
                markRouteFailed(targetId);
                Serial.printf("Invalidated failed route to 0x%08X.\n", targetId);
                // Phase 6: restamp pending toward flood/rediscovery once.
                retargetPendingAfterRelayLoss(targetId, false, 0);
            }
        }
    }
}

bool MeshRouter::hasSeenPacketId(uint32_t senderId, uint32_t packetId) {
    uint32_t now = millis();
    for (int i = 0; i < MAX_SEEN_PACKETS_CACHE; i++) {
        if (seenPackets[i].senderId == senderId && seenPackets[i].packetId == packetId &&
            meshmath::seenEntryIsFresh(now, seenPackets[i].timestamp, SEEN_PACKET_TIMEOUT_MS)) {
            return true;
        }
    }
    return false;
}

bool MeshRouter::isDuplicatePacket(uint32_t senderId, uint32_t packetId, uint32_t retryCount) {
    uint32_t now = millis();
    for (int i = 0; i < MAX_SEEN_PACKETS_CACHE; i++) {
        if (seenPackets[i].senderId == senderId && seenPackets[i].packetId == packetId &&
            meshmath::seenEntryIsFresh(now, seenPackets[i].timestamp, SEEN_PACKET_TIMEOUT_MS)) {
            return retryCount <= seenPackets[i].retryCount;
        }
    }
    return false;
}

void MeshRouter::markPacketAsSeen(uint32_t senderId, uint32_t packetId, uint32_t retryCount) {
    for (int i = 0; i < MAX_SEEN_PACKETS_CACHE; i++) {
        if (seenPackets[i].senderId == senderId && seenPackets[i].packetId == packetId) {
            seenPackets[i].retryCount = retryCount;
            seenPackets[i].timestamp = millis();
            return;
        }
    }

    seenPackets[seenPacketsIndex].senderId = senderId;
    seenPackets[seenPacketsIndex].packetId = packetId;
    seenPackets[seenPacketsIndex].retryCount = retryCount;
    seenPackets[seenPacketsIndex].timestamp = millis();
    
    seenPacketsIndex = (seenPacketsIndex + 1) % MAX_SEEN_PACKETS_CACHE;
}

void MeshRouter::processIncomingPacket(uint8_t* data, size_t len, float rssi, float snr) {
    // Check for raw diagnostic beacon to prevent decoding errors
    if (len == 12 && data[0] == 'A' && data[1] == 'M' && data[2] == 'T' && data[3] == 'E') {
        uint32_t beaconSender = ((uint32_t)data[4] << 24) | ((uint32_t)data[5] << 16) | ((uint32_t)data[6] << 8) | (uint32_t)data[7];
        uint32_t beaconSeq = ((uint32_t)data[8] << 24) | ((uint32_t)data[9] << 16) | ((uint32_t)data[10] << 8) | (uint32_t)data[11];
        Serial.printf("Raw Diagnostic Beacon received: Sender=0x%08X, Seq=%u\n", beaconSender, beaconSeq);
        return;
    }

    // 1. Deserialize Protobuf
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    pb_istream_t stream = pb_istream_from_buffer(data, len);
    
    if (!pb_decode(&stream, aethermesh_MeshPacket_fields, &packet)) {
        Serial.println("Error decoding incoming packet protobuf.");
        return;
    }

    if (packet.which_payload == aethermesh_MeshPacket_text_tag) {
        terminateTextFields(packet.payload.text);
    }
    
    // Ignore loopback reflections of packets originally generated by us
    if (packet.sender_id == localNodeId) {
        return;
    }
    
    // 2. Filter duplicate attempts. A higher retry_count for the same packet_id
    // is a real retransmit that relays should forward again, while the final
    // recipient should re-ACK without delivering the payload twice.
    bool packetIdSeenBefore = hasSeenPacketId(packet.sender_id, packet.packet_id);
    if (isDuplicatePacket(packet.sender_id, packet.packet_id, packet.retry_count)) {
        duplicatePackets++;
        // Cancel pending rebroadcast if we hear a duplicate
        cancelRebroadcast(packet.sender_id, packet.packet_id, packet.retry_count);
        // A duplicate unicast addressed to us means the sender is retransmitting
        // because our ACK was lost — re-ACK it (without re-delivering the payload).
        if (packet.recipient_id == localNodeId && packet.want_ack &&
            packet.which_payload != aethermesh_MeshPacket_ack_tag) {
            Serial.printf("Duplicate of packet %u for us; re-sending ACK.\n", packet.packet_id);
            sendAck(packet.sender_id, packet.packet_id, rssi, snr);
        }
        // Range-test PING retries (same packet_id) mean the sender never got our
        // PONG — queue another reply without re-displaying the ping on screen.
        if (packet.recipient_id == localNodeId) {
            maybeQueuePongForPingText(packet, rssi, snr);
        }
        return;
    }
    markPacketAsSeen(packet.sender_id, packet.packet_id, packet.retry_count);
    
    // 3. Update routing table
    // If prev_hop_id is set, it's the node that directly relayed it to us.
    // If not, it's the original sender.
    uint32_t immediateSender = (packet.prev_hop_id != 0) ? packet.prev_hop_id : packet.sender_id;
    
    // Calculate SNR-weighted hop cost (pure math in MeshMath.h, host-tested)
    uint8_t hopCost = meshmath::hopCost(snr);

    // Always add/update route to the immediate sender (neighbor)
    addRoute(immediateSender, immediateSender, hopCost);
    
    // If it's a Route Discovery packet, increment the metric immediately after decoding
    if (packet.which_payload == aethermesh_MeshPacket_route_discovery_tag) {
        packet.payload.route_discovery.metric += hopCost;

        // RREQ: learn reverse path to the originator at every hop so the RREP
        // can travel directed back. RREP sender is often the target itself.
        addRoute(packet.sender_id, immediateSender, packet.payload.route_discovery.metric);

        // Phase 2 path splice: every hearer of an RREP installs a forward
        // route to the discovered target via the transmitting hop. This is
        // what makes multi-hop directed DM work after discovery — not only
        // the RREQ originator gets the route (critical for proxy RREPs too).
        if (packet.payload.route_discovery.type == aethermesh_RouteDiscovery_Type_REPLY) {
            installReplyPath(packet.payload.route_discovery.target_id,
                             immediateSender,
                             packet.payload.route_discovery.metric);
        }
    } else {
        // ACKs, telemetry, text, etc. teach the reverse path (1-hop exact,
        // multi-hop conservative) so later unicasts can go directed.
        learnReverseRoute(packet.sender_id, immediateSender, hopCost);
    }
    
    // Traceroute packets carry the route they actually traversed. Learn the
    // reverse direction at every hop and append this receiver before relaying.
    if (packet.which_payload == aethermesh_MeshPacket_trace_route_tag) {
        aethermesh_TraceRoute& trace = packet.payload.trace_route;
        bool returning = trace.type == aethermesh_TraceRoute_Type_RESPONSE;
        appendTraceHop(trace, returning, rssi, snr);
        addRoute(
            returning ? trace.target_id : trace.origin_id,
            immediateSender,
            traceMetric(trace, returning)
        );

        if (!returning && trace.target_id == localNodeId) {
            Serial.printf("Traceroute %u reached target; returning observed path.\n", trace.trace_id);
            sendTraceResponse(trace);
            return;
        }
    }

    // 4. Handle recipient logic
    if (packet.recipient_id == localNodeId) {
        // Packet addressed to US
        Serial.print("Unicast packet received for local node from 0x");
        Serial.println(packet.sender_id, HEX);

        // ACK on receipt, BEFORE processing. A config packet reboots the node
        // inside its callback, so a post-processing ACK would never be sent and
        // the sender would retransmit (rebooting us again on each retry).
        if (packet.want_ack && packet.which_payload != aethermesh_MeshPacket_ack_tag) {
            sendAck(packet.sender_id, packet.packet_id, rssi, snr);
        }

        // Always queue PONG for range-test pings, even if this is a retry duplicate.
        maybeQueuePongForPingText(packet, rssi, snr);

        if (packetIdSeenBefore && packet.which_payload != aethermesh_MeshPacket_ack_tag) {
            Serial.printf("Retry %u of packet %u for us; ACKed without duplicate delivery.\n",
                          packet.retry_count, packet.packet_id);
            return;
        }

        switch (packet.which_payload) {
            case aethermesh_MeshPacket_text_tag:
                // Higher retry_count is forwarded again for coverage, but it is
                // still one logical chat message and must not be displayed twice.
                if (!packetIdSeenBefore && textCallback) {
                    textCallback(packet.sender_id, packet.payload.text.content);
                }
                break;
            case aethermesh_MeshPacket_telemetry_tag:
                if (telemetryCallback) {
                    telemetryCallback(packet.sender_id, 
                                      packet.payload.telemetry.battery_level,
                                      packet.payload.telemetry.latitude,
                                      packet.payload.telemetry.longitude);
                }
                break;
            case aethermesh_MeshPacket_route_discovery_tag:
                if (packet.payload.route_discovery.type == aethermesh_RouteDiscovery_Type_REQUEST) {
                    handleRouteRequest(packet.sender_id, immediateSender, packet.payload.route_discovery);
                } else {
                    handleRouteReply(packet.sender_id, immediateSender, packet.payload.route_discovery);
                }
                break;
            case aethermesh_MeshPacket_ack_tag:
                Serial.print("Received ACK for packet_id: ");
                Serial.println(packet.payload.ack.acked_packet_id);
                // Phase 6: RX SNR of the ACK is last-hop neighbor quality.
                noteNeighborAckQuality(immediateSender, snr);
                clearPendingAck(packet.payload.ack.acked_packet_id, rssi, snr);
                noteChannelHearing(packet.payload.ack.acked_packet_id, packet.sender_id, rssi, snr);
                break;
            case aethermesh_MeshPacket_config_tag:
                if (configCallback) {
                    configCallback(packet);
                }
                break;
            case aethermesh_MeshPacket_trace_route_tag:
                Serial.printf("Traceroute %u response reached origin.\n", packet.payload.trace_route.trace_id);
                break;
        }
        // (ACK already sent above, before processing)
    } else if (packet.recipient_id == 0xFFFFFFFF) {
        // Broadcast packet
        Serial.print("Broadcast packet received from 0x");
        Serial.println(packet.sender_id, HEX);
        
        bool shouldRebroadcast = true;
        switch (packet.which_payload) {
            case aethermesh_MeshPacket_text_tag:
                // Channel receipts: each hearer ACKs want_ack broadcasts.
                // Insurance retries share packet_id but bump retry_count and
                // reach here with packetIdSeenBefore=true — re-ACK those so a
                // first ACK lost while the originator was TX/deaf (insurance
                // window) can still recover. Exact same-retry duplicates are
                // filtered earlier (no ACK storm on relay echoes). sendAck
                // coalesces bursts onto one queued outbound ACK.
                if (packet.want_ack) {
                    sendAck(packet.sender_id, packet.packet_id, rssi, snr, true);
                }
                if (!packetIdSeenBefore && textCallback) {
                    textCallback(packet.sender_id, packet.payload.text.content);
                }
                break;
            case aethermesh_MeshPacket_telemetry_tag:
                if (telemetryCallback) {
                    telemetryCallback(packet.sender_id, 
                                      packet.payload.telemetry.battery_level,
                                      packet.payload.telemetry.latitude,
                                      packet.payload.telemetry.longitude);
                }
                break;
            case aethermesh_MeshPacket_route_discovery_tag:
                if (packet.payload.route_discovery.type == aethermesh_RouteDiscovery_Type_REQUEST) {
                    bool resolved = handleRouteRequest(packet.sender_id, immediateSender, packet.payload.route_discovery);
                    if (resolved) {
                        shouldRebroadcast = false;
                    }
                }
                break;
        }
        
        // Rebroadcast only on Router/Repeater roles (Client = companion, no relay).
        if (canRelay() && shouldRebroadcast && packet.hop_limit > 1) {
            packet.hop_limit--;
            packet.prev_hop_id = localNodeId;
            
            // Queue rebroadcast with SNR-based delay (pure math in MeshMath.h)
            queueRebroadcast(packet, millis() + meshmath::rebroadcastDelayMs(snr, rebroadcastTxdelayX100));
        } else if (!canRelay() && packet.hop_limit > 1) {
            // Still learn topology from broadcasts we hear, but do not forward.
        }
    } else {
        // Unicast packet for someone else — only Router/Repeater relay.
        if (!canRelay()) {
            // Clients may still learn a route to the sender from the RF hop.
            return;
        }

        // Directed next-hop: if the packet names a forwarder and it is not us,
        // do not rebroadcast (cancel any pending flood of the same attempt).
        if (!meshmath::shouldRelayAsNextHop(packet.next_hop_id, localNodeId)) {
            cancelRebroadcast(packet.sender_id, packet.packet_id, packet.retry_count);
            suppressRelays++;
            Serial.printf("Suppress relay of packet %u (next_hop 0x%08X)\n",
                          packet.packet_id, packet.next_hop_id);
            return;
        }

        if (packet.hop_limit > 1) {
            RouteEntry* route = getRoute(packet.recipient_id);
            const bool failedRecently = isRouteFailedRecently(packet.recipient_id);
            const bool hasDirected =
                route != nullptr && meshmath::hasUsableDirectedHop(route->nextHopId);
            const bool useDirected =
                hasDirected &&
                !meshmath::shouldFloodUnicast(true, failedRecently);

            // Metric for RouteDiscovery is already accumulated once on decode.
            // Do not add hopCost again here (Phase 1 left a double-count on RREP).

            // If this ACK is passing through us, its target has already answered —
            // drop any still-queued relay of the packet it acknowledges.
            if (packet.which_payload == aethermesh_MeshPacket_ack_tag) {
                cancelRebroadcast(packet.recipient_id, packet.payload.ack.acked_packet_id);
            }

            if (useDirected) {
                // Restamp next_hop to *our* next hop — never leave the
                // originator's next_hop sticky across hops.
                Serial.printf("Directed relay of packet %u for 0x%08X via 0x%08X\n",
                              packet.packet_id, packet.recipient_id, route->nextHopId);
                packet.hop_limit--;
                packet.prev_hop_id = localNodeId;
                packet.next_hop_id = meshmath::restampNextHopId(route->nextHopId, true);
                // Prefer SNR-weighted delay so the best hearer among equals
                // transmits first; duplicates cancel the rest.
                uint32_t delay = meshmath::rebroadcastDelayMs(snr, rebroadcastTxdelayX100);
                if (delay > 900) delay = 900; // keep directed relays snappy
                uint32_t jitter = 80 + (uint32_t)random(0, 200);
                queueRebroadcast(packet, millis() + delay / 2 + jitter);
            } else if (shouldFloodUnknownUnicast(packet) &&
                       meshmath::shouldFloodUnicast(hasDirected, failedRecently)) {
                // Flood only when cold or recently failed. SNR-weighted delay +
                // duplicate cancel keep the storm small; RREQ has its own cooldown.
                noteFloodDest(packet.recipient_id);
                Serial.printf("Flooding unicast packet %u for 0x%08X (no/failed route)\n",
                              packet.packet_id, packet.recipient_id);
                packet.hop_limit--;
                packet.prev_hop_id = localNodeId;
                packet.next_hop_id = meshmath::restampNextHopId(0, false);
                // Longer SNR backoff for floods so one strong relay leads.
                uint32_t delay = meshmath::rebroadcastDelayMs(snr, rebroadcastTxdelayX100);
                uint32_t jitter = 200 + (uint32_t)random(0, 500);
                queueRebroadcast(packet, millis() + delay + jitter);
            } else {
                Serial.printf("No route to 0x%08X; dropping packet %u\n",
                              packet.recipient_id, packet.packet_id);
            }
        }
    }
}

bool MeshRouter::sendText(uint32_t recipientId, const char* text) {
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    packet.sender_id = localNodeId;
    packet.recipient_id = recipientId;
    packet.packet_id = ++packetSequenceCounter;
    packet.hop_limit = defaultHopLimit;
    // Channel broadcasts need want_ack too: hearers ACK so a connected phone
    // (or local DeliveryStatus) can leave "waiting for hearers…".
    packet.want_ack = true;
    packet.prev_hop_id = localNodeId;
    packet.which_payload = aethermesh_MeshPacket_text_tag;
    
    strncpy(packet.payload.text.content, text, sizeof(packet.payload.text.content) - 1);
    strncpy(packet.payload.text.channel, "General", sizeof(packet.payload.text.channel) - 1);
    terminateTextFields(packet.payload.text);
    
    // Send the payload first. Starting route discovery here would occupy the
    // asynchronous radio and make this payload attempt fail.
    if (recipientId != 0xFFFFFFFF && getRoute(recipientId) == nullptr) {
        Serial.print("No route to recipient 0x");
        Serial.print(recipientId, HEX);
        Serial.println(". Sending; relays will flood-discover if needed...");
    }

    applyDirectedNextHop(&packet);
    if (recipientId == 0xFFFFFFFFu) {
        trackChannelReceipt(packet.packet_id);
    } else {
        trackForAck(packet);
    }
    bool sent = serializeAndSend(&packet);
    // Mirror sendRawPacket: one spaced insurance TX for channel text coverage.
    if (sent && recipientId == 0xFFFFFFFFu && !isRangeTestTextPacket(packet)) {
        aethermesh_MeshPacket retry = packet;
        retry.retry_count = 1;
        retry.prev_hop_id = localNodeId;
        uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
        queueRebroadcast(retry, millis() + meshmath::channelInsuranceDelayMs(sf, (uint32_t)random(0, 2000)));
    }
    // After the payload is on the air, ask the mesh for a path (flood-then-direct).
    if (sent && recipientId != 0xFFFFFFFF && getRoute(recipientId) == nullptr) {
        sendRouteRequest(recipientId);
    }
    return sent;
}

bool MeshRouter::sendTextNoAck(uint32_t recipientId, const char* text, bool urgent, uint8_t hopLimit) {
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    packet.sender_id = localNodeId;
    packet.recipient_id = recipientId;
    packet.packet_id = ++packetSequenceCounter;
    packet.hop_limit = hopLimit;
    packet.want_ack = false;
    packet.prev_hop_id = localNodeId;
    packet.which_payload = aethermesh_MeshPacket_text_tag;

    strncpy(packet.payload.text.content, text, sizeof(packet.payload.text.content) - 1);
    packet.payload.text.content[sizeof(packet.payload.text.content) - 1] = '\0';
    packet.payload.text.channel[0] = '\0';
    packet.payload.text.is_encrypted = false;

    return serializeAndSend(&packet, urgent);
}

bool MeshRouter::sendTelemetry(uint32_t recipientId, uint8_t battery, float lat, float lon, const char* nodeName, bool charging, float voltage, uint32_t positionPrecision, uint32_t loraSf, uint32_t region) {
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    packet.sender_id = localNodeId;
    packet.recipient_id = recipientId;
    packet.packet_id = ++packetSequenceCounter;
    packet.hop_limit = DEFAULT_HOP_LIMIT;
    packet.want_ack = false;
    packet.prev_hop_id = localNodeId;
    packet.which_payload = aethermesh_MeshPacket_telemetry_tag;

    packet.payload.telemetry.battery_level = battery;
    packet.payload.telemetry.latitude = lat;
    packet.payload.telemetry.longitude = lon;
    packet.payload.telemetry.altitude = 0;
    packet.payload.telemetry.is_charging = charging;
    packet.payload.telemetry.battery_voltage = voltage;
    packet.payload.telemetry.position_precision = positionPrecision;
    packet.payload.telemetry.uptime_seconds = (uint32_t)(millis() / 1000);
    packet.payload.telemetry.lora_sf = loraSf;
    packet.payload.telemetry.region = region;
    strncpy(packet.payload.telemetry.firmware_version, AETHERMESH_FW_VERSION,
            sizeof(packet.payload.telemetry.firmware_version) - 1);
    if (nodeName != nullptr) {
        strncpy(packet.payload.telemetry.node_name, nodeName,
                sizeof(packet.payload.telemetry.node_name) - 1);
        packet.payload.telemetry.node_name[sizeof(packet.payload.telemetry.node_name) - 1] = '\0';
    }

#if defined(HELTEC_V4)
    strcpy(packet.payload.telemetry.node_model, "Heltec V4");
#elif defined(LILYGO_T_DECK)
    strcpy(packet.payload.telemetry.node_model, "T-Deck");
#elif defined(ELECROW_CROWPANEL_35)
    strcpy(packet.payload.telemetry.node_model, "CrowPanel 3.5");
#elif defined(LILYGO_T_ECHO)
    strcpy(packet.payload.telemetry.node_model, "T-Echo");
#elif defined(RAK4631)
    strcpy(packet.payload.telemetry.node_model, "RAK4631");
#elif defined(RAK3401_1W)
    strcpy(packet.payload.telemetry.node_model, "RAK 1W");
#else
    strcpy(packet.payload.telemetry.node_model, "Generic Node");
#endif

    return serializeAndSend(&packet);
}

bool MeshRouter::handleRouteRequest(uint32_t senderId, uint32_t prevHopId, const aethermesh_RouteDiscovery& rreq) {
    if (rreq.target_id == localNodeId) {
        Serial.print("RREQ matches us! Sending Route Reply back to 0x");
        Serial.println(senderId, HEX);
        sendRouteReply(senderId, localNodeId, 0);
        return true;
    } else {
        // If we have a route to the target, we can send RREP on target's behalf (Gratuitous RREP)
        RouteEntry* route = getRoute(rreq.target_id);
        if (route && meshmath::proxyRouteIsFresh(millis() - route->timestamp, PROXY_ROUTE_MAX_AGE_MS)) {
            Serial.print("We know a route to target. Sending proxy RREP back to 0x");
            Serial.println(senderId, HEX);
            sendRouteReply(senderId, rreq.target_id, route->metric);
            return true;
        }
    }
    return false;
}

void MeshRouter::handleRouteReply(uint32_t senderId, uint32_t prevHopId, const aethermesh_RouteDiscovery& rrep) {
    // Originator path install (also done for all hearers via installReplyPath).
    installReplyPath(rrep.target_id, prevHopId, rrep.metric);
    Serial.printf("RREP for target 0x%08X via 0x%08X metric %u (from 0x%08X)\n",
                  rrep.target_id, prevHopId, rrep.metric, senderId);
}

bool MeshRouter::sendRouteRequest(uint32_t targetId) {
    uint32_t now = millis();
    uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
    const uint32_t perTargetCooldown = meshmath::routeDiscoveryCooldownMs(sf);
    const uint32_t globalGap = meshmath::routeDiscoveryGlobalGapMs(sf);

    // Phase 4: pace RREQs across destinations so multi-DM failures do not
    // emit a discovery storm in one loop.
    if (meshmath::rediscoveryGlobalPacingActive(now, lastAnyDiscoveryMs, globalGap)) {
        return false;
    }

    int slot = -1;
    int oldest = 0;
    for (int i = 0; i < 6; i++) {
        if (routeDiscoveries[i].targetId == targetId) {
            if (meshmath::floodDestCooldownActive(
                    now, routeDiscoveries[i].lastRequestMs, perTargetCooldown)) {
                return false;
            }
            slot = i;
            break;
        }
        if (routeDiscoveries[i].targetId == 0 && slot < 0) slot = i;
        if (routeDiscoveries[i].lastRequestMs < routeDiscoveries[oldest].lastRequestMs) oldest = i;
    }
    if (slot < 0) slot = oldest;

    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    packet.sender_id = localNodeId;
    packet.recipient_id = 0xFFFFFFFF; // Broadcast
    packet.packet_id = ++packetSequenceCounter;
    packet.hop_limit = DEFAULT_HOP_LIMIT;
    packet.want_ack = false;
    packet.prev_hop_id = localNodeId;
    packet.which_payload = aethermesh_MeshPacket_route_discovery_tag;
    
    packet.payload.route_discovery.type = aethermesh_RouteDiscovery_Type_REQUEST;
    packet.payload.route_discovery.target_id = targetId;
    packet.payload.route_discovery.metric = 0;
    
    bool sent = serializeAndSend(&packet);
    if (sent) {
        routeDiscoveries[slot].targetId = targetId;
        routeDiscoveries[slot].lastRequestMs = now;
        lastAnyDiscoveryMs = now;
        rreqSent++;
    }
    return sent;
}

void MeshRouter::sendRouteReply(uint32_t recipientId, uint32_t targetId, uint8_t metric) {
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    packet.sender_id = localNodeId;
    packet.recipient_id = recipientId;
    packet.packet_id = ++packetSequenceCounter;
    packet.hop_limit = DEFAULT_HOP_LIMIT;
    packet.want_ack = false;
    packet.prev_hop_id = localNodeId;
    packet.which_payload = aethermesh_MeshPacket_route_discovery_tag;
    
    packet.payload.route_discovery.type = aethermesh_RouteDiscovery_Type_REPLY;
    packet.payload.route_discovery.target_id = targetId;
    packet.payload.route_discovery.metric = metric;

    // Stamp reverse next hop toward the RREQ originator (learned on the RREQ
    // flood). Prefer return path — do not force-flood on a prior forward fail.
    applyDirectedNextHop(&packet, true);

    // A broadcast RREQ triggers the target AND every proxy that knows a route
    // to reply at the same instant — jitter the RREP so they don't collide.
    queueRebroadcast(packet, millis() + random(100, 400));
}

void MeshRouter::appendTraceHop(aethermesh_TraceRoute& trace, bool returning, float rssi, float snr) {
    pb_size_t& nodeCount = returning ? trace.return_node_ids_count : trace.forward_node_ids_count;
    pb_size_t& rssiCount = returning ? trace.return_rssi_count : trace.forward_rssi_count;
    pb_size_t& snrCount = returning ? trace.return_snr_quarter_db_count : trace.forward_snr_quarter_db_count;
    uint32_t* nodeIds = returning ? trace.return_node_ids : trace.forward_node_ids;
    int32_t* rssiValues = returning ? trace.return_rssi : trace.forward_rssi;
    int32_t* snrValues = returning ? trace.return_snr_quarter_db : trace.forward_snr_quarter_db;

    if (nodeCount > 0 && nodeIds[nodeCount - 1] == localNodeId) {
        return;
    }
    if (nodeCount >= 8 || rssiCount >= 8 || snrCount >= 8) {
        if (returning) trace.return_truncated = true;
        else trace.forward_truncated = true;
        return;
    }

    nodeIds[nodeCount++] = localNodeId;
    rssiValues[rssiCount++] = (int32_t)roundf(rssi);
    snrValues[snrCount++] = (int32_t)roundf(snr * 4.0f);
}

uint8_t MeshRouter::traceMetric(const aethermesh_TraceRoute& trace, bool returning) const {
    pb_size_t count = returning ? trace.return_snr_quarter_db_count : trace.forward_snr_quarter_db_count;
    const int32_t* values = returning ? trace.return_snr_quarter_db : trace.forward_snr_quarter_db;
    uint16_t metric = 0;
    for (pb_size_t i = 0; i < count; ++i) {
        metric += meshmath::hopCost(values[i] / 4.0f);
    }
    return metric > 255 ? 255 : (uint8_t)metric;
}

void MeshRouter::sendTraceResponse(const aethermesh_TraceRoute& request) {
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    packet.sender_id = localNodeId;
    packet.recipient_id = request.origin_id;
    packet.packet_id = ++packetSequenceCounter;
    packet.hop_limit = DEFAULT_HOP_LIMIT;
    packet.want_ack = false;
    packet.prev_hop_id = localNodeId;
    packet.which_payload = aethermesh_MeshPacket_trace_route_tag;
    packet.payload.trace_route = request;
    packet.payload.trace_route.type = aethermesh_TraceRoute_Type_RESPONSE;
    packet.payload.trace_route.return_node_ids_count = 0;
    packet.payload.trace_route.return_rssi_count = 0;
    packet.payload.trace_route.return_snr_quarter_db_count = 0;
    packet.payload.trace_route.return_truncated = false;
    queueRebroadcast(packet, millis() + random(120, 320));
}

bool MeshRouter::serializeAndSend(aethermesh_MeshPacket* packet, bool urgent) {
    if (packet->sender_id == localNodeId && packet->protocol_version == 0) {
        packet->protocol_version = 2;
        packet->session_id = sessionId;
    }
    // Stamp directed next hop for unicasts when a fresh route is known.
    // Return-path control (ACK / config result / RREP / traceroute response)
    // keeps reverse next hop even after a forward DM failure. Forward config
    // requests still flood-on-failure like other data unicasts.
    const bool preferReturnPath = meshmath::shouldPreferReturnPathNextHop(
        packet->which_payload == aethermesh_MeshPacket_ack_tag,
        packet->which_payload == aethermesh_MeshPacket_config_result_tag,
        packet->which_payload == aethermesh_MeshPacket_route_discovery_tag &&
            packet->payload.route_discovery.type ==
                aethermesh_RouteDiscovery_Type_REPLY,
        packet->which_payload == aethermesh_MeshPacket_trace_route_tag &&
            packet->payload.trace_route.type ==
                aethermesh_TraceRoute_Type_RESPONSE);
    applyDirectedNextHop(packet, preferReturnPath);
    const bool localUnicastFlood =
        packet->sender_id == localNodeId &&
        packet->recipient_id != 0 &&
        packet->recipient_id != 0xFFFFFFFFu &&
        packet->next_hop_id == 0;
    uint8_t buffer[256];
    pb_ostream_t stream = pb_ostream_from_buffer(buffer, sizeof(buffer));
    
    if (!pb_encode(&stream, aethermesh_MeshPacket_fields, packet)) {
        Serial.println("Error encoding packet protobuf.");
        return false;
    }
    
    bool sent = radio->sendPacket(buffer, stream.bytes_written, urgent);
    // Count airtime floods only (CAD/busy failures do not inflate the metric).
    if (sent && localUnicastFlood) {
        floodUnicasts++;
    }
    return sent;
}

void MeshRouter::onReceivedTextMessage(void (*callback)(uint32_t senderId, const char* text)) {
    textCallback = callback;
}

void MeshRouter::onReceivedTelemetry(void (*callback)(uint32_t senderId, uint8_t battery, float lat, float lon)) {
    telemetryCallback = callback;
}

void MeshRouter::onReceivedConfig(void (*callback)(const aethermesh_MeshPacket& packet)) {
    configCallback = callback;
}

void MeshRouter::onDeliveryStatus(void (*callback)(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr, uint32_t heardCount, uint32_t fromNodeId)) {
    deliveryStatusCallback = callback;
}

void MeshRouter::printRoutingTable() {
    Serial.println("--- ROUTING TABLE ---");
    uint32_t now = millis();
    uint8_t active = 0;
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (routingTable[i].active) {
            active++;
            uint32_t age = now - routingTable[i].timestamp;
            uint8_t aged = meshmath::agedRouteMetric(
                routingTable[i].metric, age, ROUTE_SOFT_AGE_MS);
            Serial.printf("Target: 0x%08X | NextHop: 0x%08X | metric %u (aged %u) | Age: %us",
                          routingTable[i].targetId, routingTable[i].nextHopId,
                          routingTable[i].metric, aged, age / 1000);
            if (routingTable[i].hasBackup) {
                Serial.printf(" | backup 0x%08X m=%u",
                              routingTable[i].backupNextHopId,
                              routingTable[i].backupMetric);
            }
            Serial.println();
        }
    }
    Serial.printf(
        "Smart routing: routes=%u directed=%lu suppress=%lu flood=%lu rreq=%lu early=%lu changes=%lu\n",
        active,
        (unsigned long)directedRelays,
        (unsigned long)suppressRelays,
        (unsigned long)floodUnicasts,
        (unsigned long)rreqSent,
        (unsigned long)earlyRepairs,
        (unsigned long)routeChanges);
    Serial.println("---------------------");
}

bool MeshRouter::sendRawPacket(aethermesh_MeshPacket* packet, bool urgent) {
    // Phone/BLE-originated unicasts need the same directed next-hop stamp as
    // locally built packets. serializeAndSend applies it; stamp early so the
    // PendingAck snapshot (if any) also carries the chosen next hop.
    applyDirectedNextHop(packet);

    // Only track for ACK/retransmit when the sender requested it (DMs, etc.).
    // Range-test PINGs set want_ack=false and are scored via PONG replies.
    // Broadcast want_ack text uses channel receipt aggregation (no retransmit).
    if (packet->want_ack) {
        if (packet->recipient_id == 0xFFFFFFFFu &&
            packet->which_payload == aethermesh_MeshPacket_text_tag) {
            trackChannelReceipt(packet->packet_id);
        } else {
            trackForAck(*packet);
        }
    }
    bool sent = serializeAndSend(packet, urgent);

    // Give locally-originated broadcast text one spaced insurance transmission
    // so a single CAD collision does not make the message disappear. Schedule
    // it AFTER the hearer ACK window — overlapping the old 1.8–3.2s retry with
    // SF11/SF12 ACK jitter left the originator half-duplex/deaf when ACKs
    // arrived, and hearers did not re-ACK (same packet_id already seen).
    // Receivers suppress duplicate app/display delivery by (sender_id,
    // packet_id) while still re-ACKing insurance retries for HEARD recovery.
    if (packet->recipient_id == 0xFFFFFFFFu &&
        packet->which_payload == aethermesh_MeshPacket_text_tag &&
        packet->retry_count == 0 && !isRangeTestTextPacket(*packet)) {
        aethermesh_MeshPacket retry = *packet;
        retry.retry_count = 1;
        retry.prev_hop_id = localNodeId;
        uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
        queueRebroadcast(retry, millis() + meshmath::channelInsuranceDelayMs(sf, (uint32_t)random(0, 2000)));
    }
    return sent;
}

void MeshRouter::maybeQueuePongForPingText(const aethermesh_MeshPacket& packet, float rssi, float snr) {
    if (packet.which_payload != aethermesh_MeshPacket_text_tag) {
        return;
    }
    // Range tests use want_ack=false PING/PONG control traffic so normal
    // message retransmission does not collide with the reply window.
    if (packet.want_ack) {
        return;
    }
    if (strncmp(packet.payload.text.content, "PING_", 5) != 0) {
        return;
    }
    const char* encoded = packet.payload.text.content + 5;
    const char* suffix = strchr(encoded, '_');
    size_t idLength = suffix ? (size_t)(suffix - encoded) : strlen(encoded);
    bool directOnly = suffix != nullptr && strcmp(suffix, "_D") == 0;
    // App packet IDs are uint32 decimal strings (up to 10 digits). The old
    // "< 8" guard silently dropped most range-test pings from PacketIdGenerator.
    if (idLength > 0 && idLength <= 10) {
        char pingId[11];
        memcpy(pingId, encoded, idLength);
        pingId[idLength] = '\0';
        rangePingsRx++;
        queuePongReply(packet.sender_id, pingId, rssi, snr, directOnly);
    } else if (idLength > 10) {
        Serial.printf("Ignoring oversized range-test ping id (%u digits)\n", (unsigned)idLength);
    }
}

void MeshRouter::queuePongReply(uint32_t recipientId, const char* pingId, float rssi, float snr, bool directOnly) {
    char pongContent[32];
    int rssiDbm = (int)lroundf(rssi);
    int snrQuarterDb = (int)lroundf(snr * 4.0f);
    if (directOnly) {
        snprintf(pongContent, sizeof(pongContent), "PONG_%s_%d_%d_D", pingId, rssiDbm, snrQuarterDb);
    } else {
        // Preserve the legacy reply shape for older app builds.
        snprintf(pongContent, sizeof(pongContent), "PONG_%s", pingId);
    }

    // Coalesce by ping id (not full content): RSSI/SNR in the direct reply
    // string would otherwise create duplicate slots for the same ping.
    for (int i = 0; i < MAX_PENDING_PONGS; i++) {
        if (pendingPongs[i].active &&
            pendingPongs[i].recipientId == recipientId &&
            strcmp(pendingPongs[i].pingId, pingId) == 0) {
            strncpy(pendingPongs[i].content, pongContent, sizeof(pendingPongs[i].content) - 1);
            pendingPongs[i].content[sizeof(pendingPongs[i].content) - 1] = '\0';
            pendingPongs[i].hopLimit = directOnly ? 1 : DEFAULT_HOP_LIMIT;
            pendingPongs[i].directOnly = directOnly;
            // Clear PING airtime on the pinger before the first reply.
            pendingPongs[i].sendAtMs = millis() + DIRECT_PONG_INITIAL_DELAY_MS + random(0, 150);
            pendingPongs[i].firstQueuedMs = millis();
            pendingPongs[i].sendCount = 0;
            Serial.printf("Refreshed queued %s to 0x%08X (slot %d)\n", pongContent, recipientId, i);
            drainPendingPongReplies();
            return;
        }
    }

    int slot = -1;
    for (int i = 0; i < MAX_PENDING_PONGS; i++) {
        if (!pendingPongs[i].active) {
            slot = i;
            break;
        }
    }
    if (slot == -1) {
        uint32_t earliest = pendingPongs[0].sendAtMs;
        slot = 0;
        for (int i = 1; i < MAX_PENDING_PONGS; i++) {
            if (pendingPongs[i].active && pendingPongs[i].sendAtMs < earliest) {
                earliest = pendingPongs[i].sendAtMs;
                slot = i;
            }
        }
    }

    // Do not cancel other ping-ids for this recipient. The app matches any
    // outstanding id within RANGE_PING_TIMEOUT; killing an unsent PONG when
    // the next ping arrives was a major source of close-range misses.

    strncpy(pendingPongs[slot].content, pongContent, sizeof(pendingPongs[slot].content) - 1);
    pendingPongs[slot].content[sizeof(pendingPongs[slot].content) - 1] = '\0';
    strncpy(pendingPongs[slot].pingId, pingId, sizeof(pendingPongs[slot].pingId) - 1);
    pendingPongs[slot].pingId[sizeof(pendingPongs[slot].pingId) - 1] = '\0';
    pendingPongs[slot].recipientId = recipientId;
    pendingPongs[slot].hopLimit = directOnly ? 1 : DEFAULT_HOP_LIMIT;
    pendingPongs[slot].directOnly = directOnly;
    pendingPongs[slot].sendAtMs = millis() + DIRECT_PONG_INITIAL_DELAY_MS + random(0, 150);
    pendingPongs[slot].firstQueuedMs = millis();
    pendingPongs[slot].sendCount = 0;
    pendingPongs[slot].active = true;
    rangePongsQueued++;
    Serial.printf("Queued %s to 0x%08X (slot %d)\n", pongContent, recipientId, slot);
    drainPendingPongReplies();
}

void MeshRouter::drainPendingPongReplies() {
    uint32_t now = millis();
    const uint32_t directSpacing = directPongSpacingMs(radio);
    for (int i = 0; i < MAX_PENDING_PONGS; i++) {
        if (!pendingPongs[i].active || (int32_t)(now - pendingPongs[i].sendAtMs) < 0) {
            continue;
        }
        if (now - pendingPongs[i].firstQueuedMs > PONG_RETRY_WINDOW_MS) {
            Serial.printf("Dropping PONG %s after %ums of retries\n",
                          pendingPongs[i].content, PONG_RETRY_WINDOW_MS);
            pendingPongs[i].active = false;
            continue;
        }
        // Direct-range replies always skip CAD: channel politeness here only
        // produced CAD-busy / tx_failures while the phone scored a miss.
        // Multi-hop legacy PONGs still skip CAD on the first attempt only.
        bool skipCad = pendingPongs[i].directOnly || (pendingPongs[i].sendCount == 0);
        if (sendTextNoAck(
                pendingPongs[i].recipientId,
                pendingPongs[i].content,
                skipCad,
                pendingPongs[i].hopLimit
            )) {
            pendingPongs[i].sendCount++;
            rangePongsSent++;
            Serial.printf("Sent range-test %s (attempt %u, next in %lums)\n",
                          pendingPongs[i].content, pendingPongs[i].sendCount,
                          (unsigned long)directSpacing);
            if (pendingPongs[i].directOnly) {
                // startTransmit success != pinger RX. Spaced copies cover
                // half-duplex deaf windows without colliding with our own TX.
                if (pendingPongs[i].sendCount >= DIRECT_PONG_MAX_ATTEMPTS) {
                    pendingPongs[i].active = false;
                } else {
                    pendingPongs[i].sendAtMs =
                        now + directSpacing + random(0, 150);
                }
                continue;
            }
            pendingPongs[i].sendAtMs = now +
                PONG_RESEND_INTERVAL_MS * pendingPongs[i].sendCount + random(0, 400);
        } else {
            rangePongTxFailures++;
            // Radio still busy (often previous PONG still on air at SF11+).
            // Wait out airtime instead of a sub-second busy spin.
            uint32_t busyWait = pendingPongs[i].directOnly
                ? (directSpacing / 2)
                : meshmath::radioBusyRetryDelayMs(random(0, 181));
            if (busyWait < 200) busyWait = 200;
            pendingPongs[i].sendAtMs = now + busyWait;
        }
    }
}

void MeshRouter::queueRebroadcast(const aethermesh_MeshPacket& packet, uint32_t transmitTime) {
    uint8_t priority = packetPriority(packet);
    int emptySlot = -1;
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        if (!pendingRebroadcasts[i].active) {
            emptySlot = i;
            break;
        }
    }
    
    if (emptySlot == -1) {
        // Preserve work that is closest to transmission. A new urgent relay may
        // replace the farthest deadline; otherwise reject it without disturbing
        // the queue that is already draining.
        uint32_t now = millis();
        uint8_t lowestPriority = pendingRebroadcasts[0].priority;
        uint32_t farthestTime = pendingRebroadcasts[0].transmitTime;
        int farthestSlot = 0;
        for (int i = 1; i < MAX_PENDING_REBROADCASTS; i++) {
            if (pendingRebroadcasts[i].priority < lowestPriority ||
                (pendingRebroadcasts[i].priority == lowestPriority &&
                 meshmath::deadlineBefore(farthestTime, pendingRebroadcasts[i].transmitTime, now))) {
                lowestPriority = pendingRebroadcasts[i].priority;
                farthestTime = pendingRebroadcasts[i].transmitTime;
                farthestSlot = i;
            }
        }
        if (priority < lowestPriority ||
            (priority == lowestPriority && !meshmath::deadlineBefore(transmitTime, farthestTime, now))) {
            Serial.printf("Rebroadcast queue full. Dropping later packet %u.\n", packet.packet_id);
            queueDrops++;
            return;
        }
        queueDrops++;
        emptySlot = farthestSlot;
        Serial.printf("Rebroadcast queue full. Replacing farthest slot %d (packet_id: %u)\n",
                      emptySlot, pendingRebroadcasts[emptySlot].packet.packet_id);
    }
    
    pendingRebroadcasts[emptySlot].packet = packet;
    pendingRebroadcasts[emptySlot].transmitTime = transmitTime;
    pendingRebroadcasts[emptySlot].queuedAtTime = millis();
    pendingRebroadcasts[emptySlot].priority = priority;
    pendingRebroadcasts[emptySlot].active = true;
    
    if (packet.which_payload == aethermesh_MeshPacket_ack_tag &&
        packet.sender_id == localNodeId) {
        Serial.printf("Queued ACK for packet %u to 0x%08X in %u ms\n",
                      packet.payload.ack.acked_packet_id, packet.recipient_id,
                      (transmitTime - millis()));
    } else {
        Serial.printf("Queued rebroadcast for packet %u from sender 0x%08X in %u ms\n",
                      packet.packet_id, packet.sender_id, (transmitTime - millis()));
    }
}

uint8_t MeshRouter::packetPriority(const aethermesh_MeshPacket& packet) const {
    if (packet.which_payload == aethermesh_MeshPacket_ack_tag) return 5;
    if (packet.which_payload == aethermesh_MeshPacket_route_discovery_tag &&
        packet.payload.route_discovery.type == aethermesh_RouteDiscovery_Type_REPLY) return 4;
    if (packet.recipient_id != 0xFFFFFFFFu &&
        packet.which_payload == aethermesh_MeshPacket_text_tag) return 4;
    if (packet.which_payload == aethermesh_MeshPacket_trace_route_tag) return 3;
    if (packet.which_payload == aethermesh_MeshPacket_route_discovery_tag) return 2;
    if (packet.which_payload == aethermesh_MeshPacket_text_tag) return 1;
    return 0;
}

void MeshRouter::sendAck(uint32_t recipientId, uint32_t ackedPacketId, float rssi, float snr,
                         bool scheduleRecovery) {
    uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
    // Primary: node-id slot + small random. Recovery: alternate mixer after the
    // insurance window — same-slot hidden-terminal colliders separate there.
    // Unique-hearer aggregation makes a duplicate ACK harmless.
    uint32_t primaryDelayMs =
        meshmath::channelAckDelayMs(sf, localNodeId, (uint32_t)random(0, 256));
    uint32_t recoveryDelayMs = scheduleRecovery
        ? meshmath::channelAckRecoveryDelayMs(sf, localNodeId, (uint32_t)random(0, 256))
        : 0;

    bool havePrimary = false;
    bool haveRecovery = false;
    uint32_t now = millis();

    // Coalesce duplicate ACK requests (unicast retransmit / insurance re-ACK /
    // relay echo) onto the existing primary + recovery slots. Do not reshuffle
    // future schedules — that re-collapses hearers into one window.
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        if (!pendingRebroadcasts[i].active) continue;
        if (pendingRebroadcasts[i].packet.which_payload != aethermesh_MeshPacket_ack_tag) continue;
        if (pendingRebroadcasts[i].packet.sender_id != localNodeId) continue;
        if (pendingRebroadcasts[i].packet.payload.ack.acked_packet_id != ackedPacketId) continue;

        const bool isRecovery = pendingRebroadcasts[i].packet.retry_count != 0;
        if (isRecovery) {
            haveRecovery = true;
        } else {
            havePrimary = true;
        }

        pendingRebroadcasts[i].packet.recipient_id = recipientId;
        pendingRebroadcasts[i].packet.payload.ack.acked_rx_rssi = rssi;
        pendingRebroadcasts[i].packet.payload.ack.acked_rx_snr = snr;
        applyDirectedNextHop(&pendingRebroadcasts[i].packet, true);
        pendingRebroadcasts[i].priority = packetPriority(pendingRebroadcasts[i].packet);

        if ((int32_t)(now - pendingRebroadcasts[i].transmitTime) >= 0) {
            uint32_t delayMs = isRecovery ? recoveryDelayMs : primaryDelayMs;
            if (isRecovery && !scheduleRecovery) {
                // Unicast path should not keep a stale recovery ACK around.
                pendingRebroadcasts[i].active = false;
                continue;
            }
            pendingRebroadcasts[i].transmitTime = now + delayMs;
            pendingRebroadcasts[i].queuedAtTime = now;
            Serial.printf("Rescheduled queued %s ACK for packet %u to 0x%08X in %u ms\n",
                          isRecovery ? "recovery" : "primary",
                          ackedPacketId, recipientId, delayMs);
        } else {
            Serial.printf("Kept slotted %s ACK for packet %u to 0x%08X (due in %u ms)\n",
                          isRecovery ? "recovery" : "primary",
                          ackedPacketId, recipientId,
                          (uint32_t)(pendingRebroadcasts[i].transmitTime - now));
        }
    }

    // Queue with node-id slotting. Immediate serializeAndSend from
    // processIncomingPacket loses CAD at SF11/SF12 with no retry — channel
    // chat then sticks on "waiting for hearers…". Clients still emit: loop()
    // drains this queue regardless of canRelay().
    if (!havePrimary) {
        aethermesh_MeshPacket ackPacket = aethermesh_MeshPacket_init_zero;
        ackPacket.sender_id = localNodeId;
        ackPacket.recipient_id = recipientId;
        ackPacket.packet_id = ++packetSequenceCounter;
        ackPacket.hop_limit = DEFAULT_HOP_LIMIT;
        ackPacket.want_ack = false;
        ackPacket.retry_count = 0;
        ackPacket.prev_hop_id = localNodeId;
        ackPacket.which_payload = aethermesh_MeshPacket_ack_tag;
        ackPacket.payload.ack.acked_packet_id = ackedPacketId;
        ackPacket.payload.ack.acked_rx_rssi = rssi;
        ackPacket.payload.ack.acked_rx_snr = snr;
        // Prefer reverse route now; serializeAndSend restamps at TX time.
        applyDirectedNextHop(&ackPacket, true);
        queueRebroadcast(ackPacket, now + primaryDelayMs);
        Serial.printf("Queued primary ACK for packet %u to 0x%08X via 0x%08X in %u ms (slot %u)\n",
                      ackedPacketId, recipientId, ackPacket.next_hop_id, primaryDelayMs,
                      meshmath::channelAckSlotIndex(localNodeId));
    }
    if (scheduleRecovery && !haveRecovery) {
        aethermesh_MeshPacket ackPacket = aethermesh_MeshPacket_init_zero;
        ackPacket.sender_id = localNodeId;
        ackPacket.recipient_id = recipientId;
        ackPacket.packet_id = ++packetSequenceCounter;
        ackPacket.hop_limit = DEFAULT_HOP_LIMIT;
        ackPacket.want_ack = false;
        ackPacket.retry_count = 1; // marks collision-recovery wave
        ackPacket.prev_hop_id = localNodeId;
        ackPacket.which_payload = aethermesh_MeshPacket_ack_tag;
        ackPacket.payload.ack.acked_packet_id = ackedPacketId;
        ackPacket.payload.ack.acked_rx_rssi = rssi;
        ackPacket.payload.ack.acked_rx_snr = snr;
        applyDirectedNextHop(&ackPacket, true);
        queueRebroadcast(ackPacket, now + recoveryDelayMs);
        Serial.printf("Queued recovery ACK for packet %u to 0x%08X via 0x%08X in %u ms (alt slot %u)\n",
                      ackedPacketId, recipientId, ackPacket.next_hop_id, recoveryDelayMs,
                      meshmath::channelAckAltSlotIndex(localNodeId));
    }
}

void MeshRouter::emitDeliveryStatus(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr, uint32_t heardCount, uint32_t fromNodeId) {
    if (deliveryStatusCallback) {
        deliveryStatusCallback(packetId, recipientId, state, reason, retryCount, ackRssi, ackSnr, heardCount, fromNodeId);
    }
}

void MeshRouter::trackChannelReceipt(uint32_t packetId) {
    if (packetId == 0) return;
    int slot = -1;
    for (int i = 0; i < MAX_CHANNEL_RECEIPTS; i++) {
        if (channelReceipts[i].active && channelReceipts[i].packetId == packetId) {
            channelReceipts[i].expiresAt = millis() + CHANNEL_RECEIPT_TTL_MS;
            return;
        }
        if (slot == -1 && !channelReceipts[i].active) slot = i;
    }
    if (slot == -1) {
        // Evict the oldest window.
        slot = 0;
        for (int i = 1; i < MAX_CHANNEL_RECEIPTS; i++) {
            if ((int32_t)(channelReceipts[i].expiresAt - channelReceipts[slot].expiresAt) < 0) {
                slot = i;
            }
        }
    }
    channelReceipts[slot].packetId = packetId;
    channelReceipts[slot].heardCount = 0;
    memset(channelReceipts[slot].hearers, 0, sizeof(channelReceipts[slot].hearers));
    channelReceipts[slot].expiresAt = millis() + CHANNEL_RECEIPT_TTL_MS;
    channelReceipts[slot].active = true;
}

void MeshRouter::noteChannelHearing(uint32_t ackedPacketId, uint32_t fromNodeId, float ackRssi, float ackSnr) {
    if (ackedPacketId == 0 || fromNodeId == 0 || fromNodeId == localNodeId) return;
    for (int i = 0; i < MAX_CHANNEL_RECEIPTS; i++) {
        if (!channelReceipts[i].active || channelReceipts[i].packetId != ackedPacketId) {
            continue;
        }
        for (uint8_t h = 0; h < channelReceipts[i].heardCount; h++) {
            if (channelReceipts[i].hearers[h] == fromNodeId) {
                return; // already counted
            }
        }
        if (channelReceipts[i].heardCount >= MAX_CHANNEL_HEARERS) {
            return;
        }
        channelReceipts[i].hearers[channelReceipts[i].heardCount++] = fromNodeId;
        Serial.printf("Channel packet %u heard by 0x%08X (total %u).\n",
                      ackedPacketId, fromNodeId, channelReceipts[i].heardCount);
        emitDeliveryStatus(
            ackedPacketId,
            0xFFFFFFFFu,
            aethermesh_DeliveryStatus_State_HEARD,
            aethermesh_DeliveryStatus_Reason_REASON_UNSPECIFIED,
            0,
            ackRssi,
            ackSnr,
            channelReceipts[i].heardCount,
            fromNodeId
        );
        return;
    }
}

void MeshRouter::trackForAck(const aethermesh_MeshPacket& packet) {
    if (!packet.want_ack || packet.recipient_id == 0xFFFFFFFF) {
        return;
    }
    // Reuse an existing slot for the same packet, else take a free one, else
    // overwrite the entry with the fewest retries left (closest to giving up).
    int slot = -1;
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (pendingAcks[i].active && pendingAcks[i].packet.packet_id == packet.packet_id) {
            return; // already tracked
        }
        if (slot == -1 && !pendingAcks[i].active) {
            slot = i;
        }
    }
    if (slot == -1) {
        slot = 0;
        for (int i = 1; i < MAX_PENDING_ACKS; i++) {
            if (pendingAcks[i].retriesLeft < pendingAcks[slot].retriesLeft) {
                slot = i;
            }
        }
        Serial.printf("Pending-ACK queue full. Evicting packet %u.\n",
                      pendingAcks[slot].packet.packet_id);
        queueDrops++;
        emitDeliveryStatus(
            pendingAcks[slot].packet.packet_id,
            pendingAcks[slot].packet.recipient_id,
            aethermesh_DeliveryStatus_State_FAILED,
            aethermesh_DeliveryStatus_Reason_QUEUE_EVICTED,
            pendingAcks[slot].packet.retry_count
        );
    }
    pendingAcks[slot].packet = packet;
    pendingAcks[slot].retriesLeft = ACK_MAX_RETRIES;
    pendingAcks[slot].stored = false;
    pendingAcks[slot].storedWakeDone = false;
    pendingAcks[slot].earlyBackupDone = false;
    pendingAcks[slot].earlyFloodDone = false;
    pendingAcks[slot].cadFailStreak = 0;
    uint32_t now = millis();
    pendingAcks[slot].trackedAt = now;
    pendingAcks[slot].expiresAt = now + STORE_FORWARD_TTL_MS;
    RouteEntry* route = getRoute(packet.recipient_id);
    uint8_t routeMetric = route ? route->metric : 0;
    if (route) {
        routeMetric = meshmath::agedRouteMetric(
            routeMetric, now - route->timestamp, ROUTE_SOFT_AGE_MS);
    }
    uint8_t sf = radio ? radio->getSpreadingFactor() : 11;
    pendingAcks[slot].nextRetryTime = now +
        meshmath::ackRetryDelayMs(sf, 0, routeMetric, random(0, 500));
    pendingAcks[slot].active = true;
}

void MeshRouter::clearPendingAck(uint32_t ackedPacketId, float ackRssi, float ackSnr) {
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (pendingAcks[i].active && pendingAcks[i].packet.packet_id == ackedPacketId) {
            // Phase 5: reinforce the successful reverse path (primary refresh +
            // soft-age nudge on backup). Immediate hop already learned on RX.
            reinforceRouteOnDelivery(pendingAcks[i].packet.recipient_id, ackSnr);
            emitDeliveryStatus(
                pendingAcks[i].packet.packet_id,
                pendingAcks[i].packet.recipient_id,
                aethermesh_DeliveryStatus_State_DELIVERED,
                aethermesh_DeliveryStatus_Reason_REASON_UNSPECIFIED,
                pendingAcks[i].packet.retry_count,
                ackRssi,
                ackSnr
            );
            pendingAcks[i].active = false;
            ackedPackets++;
            Serial.printf("Packet %u acknowledged; retransmit tracking cleared.\n", ackedPacketId);
        }
    }
}

void MeshRouter::getDiagnostics(aethermesh_MeshDiagnostics& diagnostics) const {
    diagnostics = aethermesh_MeshDiagnostics_init_zero;
    diagnostics.tx_packets = radio->getTxPackets();
    diagnostics.tx_failures = radio->getTxFailures();
    diagnostics.rx_packets = radio->getRxPackets();
    diagnostics.relayed_packets = relayedPackets;
    diagnostics.retries = retryPackets;
    diagnostics.acked_packets = ackedPackets;
    diagnostics.ack_timeouts = ackTimeouts;
    diagnostics.duplicate_packets = duplicatePackets;
    diagnostics.cad_busy_events = radio->getCadBusyEvents();
    diagnostics.queue_drops = queueDrops;
    diagnostics.route_changes = routeChanges;
    diagnostics.airtime_ms = radio->getAirtimeMs();
    diagnostics.uptime_seconds = millis() / 1000;
    diagnostics.protocol_version = 2;
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (routingTable[i].active) diagnostics.active_routes++;
    }
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        if (pendingRebroadcasts[i].active) diagnostics.rebroadcast_queue_depth++;
    }
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (pendingAcks[i].active) diagnostics.pending_ack_depth++;
    }
    diagnostics.range_pings_rx = rangePingsRx;
    diagnostics.range_pongs_queued = rangePongsQueued;
    diagnostics.range_pongs_sent = rangePongsSent;
    diagnostics.range_pong_tx_failures = rangePongTxFailures;
    diagnostics.quiet_mode = quietMode;
    diagnostics.directed_relays = directedRelays;
    diagnostics.suppress_relays = suppressRelays;
    diagnostics.flood_unicasts = floodUnicasts;
    diagnostics.rreq_sent = rreqSent;
    diagnostics.early_repairs = earlyRepairs;
}

void MeshRouter::setQuietMode(bool enabled) {
    quietMode = enabled;
    quietModeStartedAt = enabled ? millis() : 0;
    Serial.printf("Range-test quiet mode %s\n", enabled ? "ON" : "OFF");
}

void MeshRouter::resetRangeTestCounters() {
    rangePingsRx = 0;
    rangePongsQueued = 0;
    rangePongsSent = 0;
    rangePongTxFailures = 0;
}

void MeshRouter::cancelRebroadcast(uint32_t senderId, uint32_t packetId, uint32_t retryCount) {
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        if (pendingRebroadcasts[i].active && 
            pendingRebroadcasts[i].packet.sender_id == senderId && 
            pendingRebroadcasts[i].packet.packet_id == packetId &&
            pendingRebroadcasts[i].packet.retry_count <= retryCount) {
            pendingRebroadcasts[i].active = false;
            Serial.printf("Cancelled pending rebroadcast for packet %u retry %u from sender 0x%08X\n",
                          packetId, pendingRebroadcasts[i].packet.retry_count, senderId);
        }
    }
}
