#include "MeshRouter.h"
#include "MeshMath.h"
#include "Version.h"
#include "pb_common.h"
#include "pb_encode.h"
#include "pb_decode.h"

static void terminateTextFields(aethermesh_TextMessage& text) {
    text.content[sizeof(text.content) - 1] = '\0';
    text.channel[sizeof(text.channel) - 1] = '\0';
}

static bool isRangeTestTextPacket(const aethermesh_MeshPacket& packet) {
    return packet.which_payload == aethermesh_MeshPacket_text_tag &&
           (strncmp(packet.payload.text.content, "PING_", 5) == 0 ||
            strncmp(packet.payload.text.content, "PONG_", 5) == 0);
}

MeshRouter::MeshRouter(RadioManager* radioMgr) {
    radio = radioMgr;
    localNodeId = 0;
    packetSequenceCounter = 0;
    seenPacketsIndex = 0;
    textCallback = nullptr;
    telemetryCallback = nullptr;
    configCallback = nullptr;
    deliveryStatusCallback = nullptr;
    
    // Clear tables
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        routingTable[i].active = false;
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
    for (int i = 0; i < MAX_PENDING_PONGS; i++) {
        pendingPongs[i].active = false;
        pendingPongs[i].sendCount = 0;
        pendingPongs[i].firstQueuedMs = 0;
    }
    for (int i = 0; i < 6; i++) {
        routeDiscoveries[i].targetId = 0;
        routeDiscoveries[i].lastRequestMs = 0;
    }
}

void MeshRouter::init(uint32_t localId) {
    localNodeId = localId;
    uint32_t entropy = ((uint32_t)random(1, 0x7FFFFFFF) << 1) ^ micros();
    packetSequenceCounter = meshmath::initialPacketSequence(localId, entropy);
    
    Serial.print("MeshRouter initialized. Local Node ID: 0x");
    Serial.println(localNodeId, HEX);
}

void MeshRouter::loop() {
    uint32_t now = millis();
    drainPendingPongReplies();
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        if (pendingRebroadcasts[i].active &&
            (int32_t)(now - pendingRebroadcasts[i].transmitTime) >= 0) {
            bool urgent = isRangeTestTextPacket(pendingRebroadcasts[i].packet);
            if (serializeAndSend(&pendingRebroadcasts[i].packet, urgent)) {
                pendingRebroadcasts[i].active = false;
                Serial.printf("Transmitted queued rebroadcast for packet %u from sender 0x%08X\n",
                              pendingRebroadcasts[i].packet.packet_id, pendingRebroadcasts[i].packet.sender_id);
            } else if (now - pendingRebroadcasts[i].transmitTime > 5000) {
                // Radio stayed busy for 5s past the scheduled time; give up.
                pendingRebroadcasts[i].active = false;
                Serial.printf("Dropping queued rebroadcast for packet %u (radio busy too long)\n",
                              pendingRebroadcasts[i].packet.packet_id);
            }
            // else: radio busy (e.g. mid-transmit) — slot stays active, retry next loop
        }
    }

    // Retransmit locally-originated want_ack packets that haven't been ACKed.
    // Same packet_id on purpose: the recipient's dedup cache prevents double
    // delivery, and duplicates addressed to it trigger a fresh ACK.
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (pendingAcks[i].active && (int32_t)(now - pendingAcks[i].nextRetryTime) >= 0) {
            if (pendingAcks[i].retriesLeft == 0) {
                pendingAcks[i].active = false;
                Serial.printf("No ACK for packet %u after all retries. Giving up.\n",
                              pendingAcks[i].packet.packet_id);
                emitDeliveryStatus(
                    pendingAcks[i].packet.packet_id,
                    pendingAcks[i].packet.recipient_id,
                    aethermesh_DeliveryStatus_State_FAILED,
                    aethermesh_DeliveryStatus_Reason_ACK_TIMEOUT,
                    pendingAcks[i].packet.retry_count
                );
                invalidateRoute(pendingAcks[i].packet.recipient_id);
                continue;
            }
            // If we still have no route, ask again while retrying (the direct
            // transmission below may still reach a 1-hop recipient).
            if (getRoute(pendingAcks[i].packet.recipient_id) == nullptr) {
                sendRouteRequest(pendingAcks[i].packet.recipient_id);
            }
            pendingAcks[i].packet.retry_count++;
            if (serializeAndSend(&pendingAcks[i].packet)) {
                pendingAcks[i].retriesLeft--;
                RouteEntry* retryRoute = getRoute(pendingAcks[i].packet.recipient_id);
                uint8_t retryMetric = retryRoute ? retryRoute->metric : 0;
                pendingAcks[i].nextRetryTime = now + meshmath::ackRetryDelayMs(
                    pendingAcks[i].packet.retry_count, retryMetric, random(0, 500));
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
            }
            // Radio busy: leave the slot as-is and try again next loop pass
        }
    }
}

void MeshRouter::addRoute(uint32_t targetId, uint32_t nextHopId, uint8_t metric) {
    if (targetId == localNodeId) return;
    
    uint32_t now = millis();
    RouteEntry* existing = getRoute(targetId);
    
    if (existing) {
        // Refresh the active next hop, accept a genuinely better path, or replace
        // a route only when it is old enough to be suspect. This prevents a noisy
        // late discovery reply from making a healthy route flap every 30 seconds.
        if (meshmath::shouldReplaceRoute(existing->nextHopId, existing->metric,
                                         now - existing->timestamp, nextHopId,
                                         metric, ROUTE_TIMEOUT_MS)) {
            existing->nextHopId = nextHopId;
            existing->metric = metric;
            existing->timestamp = now;
            
            Serial.print("Route updated: Target 0x");
            Serial.print(targetId, HEX);
            Serial.print(" via NextHop 0x");
            Serial.print(nextHopId, HEX);
            Serial.print(" Hops: ");
            Serial.println(metric);
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
            routingTable[i].active = true;
            
            Serial.print("New Route added: Target 0x");
            Serial.print(targetId, HEX);
            Serial.print(" via NextHop 0x");
            Serial.print(nextHopId, HEX);
            Serial.print(" Hops: ");
            Serial.println(metric);
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
    routingTable[oldestIdx].active = true;
    
    Serial.print("Route table full. Evicted oldest. Added: Target 0x");
    Serial.print(targetId, HEX);
    Serial.print(" via NextHop 0x");
    Serial.println(nextHopId, HEX);
}

RouteEntry* MeshRouter::getRoute(uint32_t targetId) {
    uint32_t now = millis();
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (routingTable[i].active && routingTable[i].targetId == targetId) {
            // Check timeout
            if (now - routingTable[i].timestamp > ROUTE_TIMEOUT_MS) {
                routingTable[i].active = false;
                continue;
            }
            return &routingTable[i];
        }
    }
    return nullptr;
}

void MeshRouter::invalidateRoute(uint32_t targetId) {
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (routingTable[i].active && routingTable[i].targetId == targetId) {
            routingTable[i].active = false;
            Serial.printf("Invalidated failed route to 0x%08X.\n", targetId);
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
        
        // Add route to originator using the cumulative metric
        addRoute(packet.sender_id, immediateSender, packet.payload.route_discovery.metric);
    } else if (immediateSender == packet.sender_id) {
        // Direct 1-hop link for other packet types
        addRoute(packet.sender_id, immediateSender, hopCost);
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
                if (textCallback) {
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
                clearPendingAck(packet.payload.ack.acked_packet_id, rssi, snr);
                break;
            case aethermesh_MeshPacket_config_tag:
                if (configCallback) {
                    configCallback(packet.sender_id, packet.payload.config);
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
                if (textCallback) {
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
        
        // Rebroadcast if hops remaining and not resolved
        if (shouldRebroadcast && packet.hop_limit > 1) {
            packet.hop_limit--;
            packet.prev_hop_id = localNodeId;
            
            // Queue rebroadcast with SNR-based delay (pure math in MeshMath.h)
            queueRebroadcast(packet, millis() + meshmath::rebroadcastDelayMs(snr));
        }
    } else {
        // Unicast packet for someone else (we are a relay node)
        if (packet.hop_limit > 1) {
            RouteEntry* route = getRoute(packet.recipient_id);
            if (route) {
                Serial.print("Relaying unicast packet for 0x");
                Serial.print(packet.recipient_id, HEX);
                Serial.print(" via next hop 0x");
                Serial.println(route->nextHopId, HEX);
                
                packet.hop_limit--;
                packet.prev_hop_id = localNodeId;

                // If the payload is a RouteReply, we must increment the metric by the incoming link's hopCost
                if (packet.which_payload == aethermesh_MeshPacket_route_discovery_tag &&
                    packet.payload.route_discovery.type == aethermesh_RouteDiscovery_Type_REPLY) {
                    packet.payload.route_discovery.metric += hopCost;
                }

                // If this ACK is passing through us, its target has already answered —
                // drop any still-queued relay of the packet it acknowledges.
                if (packet.which_payload == aethermesh_MeshPacket_ack_tag) {
                    cancelRebroadcast(packet.recipient_id, packet.payload.ack.acked_packet_id);
                }

                // Never relay immediately: the recipient's ACK transmits the moment
                // the original packet lands, so an instant relay from a third node
                // collides with that ACK at the sender every time (all responders
                // are triggered by the same packet). Wait out the ACK airtime with
                // jitter; if a duplicate arrives meanwhile, the relay is cancelled.
                queueRebroadcast(packet, millis() + random(300, 700));
            } else if ((packet.which_payload == aethermesh_MeshPacket_text_tag &&
                        (strncmp(packet.payload.text.content, "PING_", 5) == 0 ||
                         strncmp(packet.payload.text.content, "PONG_", 5) == 0)) ||
                       packet.which_payload == aethermesh_MeshPacket_trace_route_tag) {
                // Diagnostics must still discover a path when the route table is
                // cold. Duplicate suppression and jitter bound this fallback flood.
                Serial.print("No route to 0x");
                Serial.print(packet.recipient_id, HEX);
                Serial.println("; flooding diagnostic packet.");
                packet.hop_limit--;
                packet.prev_hop_id = localNodeId;
                queueRebroadcast(packet, millis() + random(300, 700));
            } else {
                Serial.print("No route to 0x");
                Serial.print(packet.recipient_id, HEX);
                Serial.println(". Dropping packet.");
            }
        }
    }
}

bool MeshRouter::sendText(uint32_t recipientId, const char* text) {
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    packet.sender_id = localNodeId;
    packet.recipient_id = recipientId;
    packet.packet_id = ++packetSequenceCounter;
    packet.hop_limit = DEFAULT_HOP_LIMIT;
    packet.want_ack = (recipientId != 0xFFFFFFFF);
    packet.prev_hop_id = localNodeId;
    packet.which_payload = aethermesh_MeshPacket_text_tag;
    
    strncpy(packet.payload.text.content, text, sizeof(packet.payload.text.content) - 1);
    strncpy(packet.payload.text.channel, "General", sizeof(packet.payload.text.channel) - 1);
    terminateTextFields(packet.payload.text);
    
    // If unicast with no route, kick off discovery but still transmit — a 1-hop
    // recipient hears us regardless, and the retransmit queue covers the rest.
    if (recipientId != 0xFFFFFFFF && getRoute(recipientId) == nullptr) {
        Serial.print("No route to recipient 0x");
        Serial.print(recipientId, HEX);
        Serial.println(". Sending anyway + requesting route discovery...");
        sendRouteRequest(recipientId);
    }

    trackForAck(packet);
    return serializeAndSend(&packet);
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

    if (hopLimit > 1 && recipientId != 0xFFFFFFFF && getRoute(recipientId) == nullptr) {
        sendRouteRequest(recipientId);
    }

    return serializeAndSend(&packet, urgent);
}

bool MeshRouter::sendTelemetry(uint32_t recipientId, uint8_t battery, float lat, float lon, const char* nodeName, bool charging, float voltage, uint32_t positionPrecision) {
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
    // Update routing entry to targets
    addRoute(rrep.target_id, prevHopId, rrep.metric);
    Serial.print("RREP received for target node 0x");
    Serial.println(rrep.target_id, HEX);
}

void MeshRouter::sendRouteRequest(uint32_t targetId) {
    uint32_t now = millis();
    int slot = -1;
    int oldest = 0;
    for (int i = 0; i < 6; i++) {
        if (routeDiscoveries[i].targetId == targetId) {
            if (now - routeDiscoveries[i].lastRequestMs < ROUTE_DISCOVERY_COOLDOWN_MS) {
                return;
            }
            slot = i;
            break;
        }
        if (routeDiscoveries[i].targetId == 0 && slot < 0) slot = i;
        if (routeDiscoveries[i].lastRequestMs < routeDiscoveries[oldest].lastRequestMs) oldest = i;
    }
    if (slot < 0) slot = oldest;
    routeDiscoveries[slot].targetId = targetId;
    routeDiscoveries[slot].lastRequestMs = now;

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
    
    serializeAndSend(&packet);
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
    uint8_t buffer[256];
    pb_ostream_t stream = pb_ostream_from_buffer(buffer, sizeof(buffer));
    
    if (!pb_encode(&stream, aethermesh_MeshPacket_fields, packet)) {
        Serial.println("Error encoding packet protobuf.");
        return false;
    }
    
    return radio->sendPacket(buffer, stream.bytes_written, urgent);
}

void MeshRouter::onReceivedTextMessage(void (*callback)(uint32_t senderId, const char* text)) {
    textCallback = callback;
}

void MeshRouter::onReceivedTelemetry(void (*callback)(uint32_t senderId, uint8_t battery, float lat, float lon)) {
    telemetryCallback = callback;
}

void MeshRouter::onReceivedConfig(void (*callback)(uint32_t senderId, const aethermesh_NodeConfig& config)) {
    configCallback = callback;
}

void MeshRouter::onDeliveryStatus(void (*callback)(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr)) {
    deliveryStatusCallback = callback;
}

void MeshRouter::printRoutingTable() {
    Serial.println("--- ROUTING TABLE ---");
    for (int i = 0; i < MAX_ROUTE_TABLE_ENTRIES; i++) {
        if (routingTable[i].active) {
            Serial.print("Target: 0x");
            Serial.print(routingTable[i].targetId, HEX);
            Serial.print(" | NextHop: 0x");
            Serial.print(routingTable[i].nextHopId, HEX);
            Serial.print(" | Hops: ");
            Serial.print(routingTable[i].metric);
            Serial.print(" | Age: ");
            Serial.print((millis() - routingTable[i].timestamp) / 1000);
            Serial.println("s");
        }
    }
    Serial.println("---------------------");
}

bool MeshRouter::sendRawPacket(aethermesh_MeshPacket* packet, bool urgent) {
    // Only track for ACK/retransmit when the sender requested it (DMs, etc.).
    // Range-test PINGs set want_ack=false and are scored via PONG replies.
    if (packet->want_ack) {
        trackForAck(*packet);
    }
    return serializeAndSend(packet, urgent);
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
    if (idLength > 0 && idLength < 8) {
        char pingId[8];
        memcpy(pingId, encoded, idLength);
        pingId[idLength] = '\0';
        queuePongReply(packet.sender_id, pingId, rssi, snr, directOnly);
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

    // Coalesce duplicate PING retries for the same id.
    for (int i = 0; i < MAX_PENDING_PONGS; i++) {
        if (pendingPongs[i].active &&
            pendingPongs[i].recipientId == recipientId &&
            strcmp(pendingPongs[i].content, pongContent) == 0) {
            pendingPongs[i].sendAtMs = millis() + 100 + random(0, 100);
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

    // A new ping id supersedes stale PONG retries — stop flooding the channel
    // with PONG replies for the previous ping while the next one is in flight.
    for (int i = 0; i < MAX_PENDING_PONGS; i++) {
        if (pendingPongs[i].active &&
            pendingPongs[i].recipientId == recipientId &&
            strcmp(pendingPongs[i].content, pongContent) != 0) {
            Serial.printf("Cancelled stale %s (new ping queued)\n", pendingPongs[i].content);
            pendingPongs[i].active = false;
        }
    }

    strncpy(pendingPongs[slot].content, pongContent, sizeof(pendingPongs[slot].content) - 1);
    pendingPongs[slot].content[sizeof(pendingPongs[slot].content) - 1] = '\0';
    pendingPongs[slot].recipientId = recipientId;
    pendingPongs[slot].hopLimit = directOnly ? 1 : DEFAULT_HOP_LIMIT;
    pendingPongs[slot].sendAtMs = millis() + 100 + random(0, 100);
    pendingPongs[slot].firstQueuedMs = millis();
    pendingPongs[slot].sendCount = 0;
    pendingPongs[slot].active = true;
    Serial.printf("Queued %s to 0x%08X (slot %d)\n", pongContent, recipientId, slot);
    drainPendingPongReplies();
}

void MeshRouter::drainPendingPongReplies() {
    uint32_t now = millis();
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
        // Only the FIRST send skips CAD (reply latency matters). Retries are
        // blind repeats of a possibly-already-delivered PONG: transmitting them
        // without listening deafened the node to the pinger's NEXT ping and
        // caused back-to-back failures in field tests. Retries also back off
        // (1.5s, 3s, 4.5s...) instead of hammering a flat 1.5s cadence.
        bool firstAttempt = (pendingPongs[i].sendCount == 0);
        if (sendTextNoAck(
                pendingPongs[i].recipientId,
                pendingPongs[i].content,
                firstAttempt,
                pendingPongs[i].hopLimit
            )) {
            pendingPongs[i].sendCount++;
            Serial.printf("Sent range-test %s (attempt %u)\n",
                          pendingPongs[i].content, pendingPongs[i].sendCount);
            pendingPongs[i].sendAtMs = now +
                PONG_RESEND_INTERVAL_MS * pendingPongs[i].sendCount + random(0, 400);
        }
        // Radio busy: leave slot active and retry next loop pass until window expires.
    }
}

void MeshRouter::queueRebroadcast(const aethermesh_MeshPacket& packet, uint32_t transmitTime) {
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
        uint32_t farthestTime = pendingRebroadcasts[0].transmitTime;
        int farthestSlot = 0;
        for (int i = 1; i < MAX_PENDING_REBROADCASTS; i++) {
            if (meshmath::deadlineBefore(farthestTime, pendingRebroadcasts[i].transmitTime, now)) {
                farthestTime = pendingRebroadcasts[i].transmitTime;
                farthestSlot = i;
            }
        }
        if (!meshmath::deadlineBefore(transmitTime, farthestTime, now)) {
            Serial.printf("Rebroadcast queue full. Dropping later packet %u.\n", packet.packet_id);
            return;
        }
        emptySlot = farthestSlot;
        Serial.printf("Rebroadcast queue full. Replacing farthest slot %d (packet_id: %u)\n",
                      emptySlot, pendingRebroadcasts[emptySlot].packet.packet_id);
    }
    
    pendingRebroadcasts[emptySlot].packet = packet;
    pendingRebroadcasts[emptySlot].transmitTime = transmitTime;
    pendingRebroadcasts[emptySlot].active = true;
    
    Serial.printf("Queued rebroadcast for packet %u from sender 0x%08X in %u ms\n", 
                  packet.packet_id, packet.sender_id, (transmitTime - millis()));
}

void MeshRouter::sendAck(uint32_t recipientId, uint32_t ackedPacketId, float rssi, float snr) {
    aethermesh_MeshPacket ackPacket = aethermesh_MeshPacket_init_zero;
    ackPacket.sender_id = localNodeId;
    ackPacket.recipient_id = recipientId;
    ackPacket.packet_id = ++packetSequenceCounter;
    ackPacket.hop_limit = DEFAULT_HOP_LIMIT;
    ackPacket.want_ack = false;
    ackPacket.prev_hop_id = localNodeId;
    ackPacket.which_payload = aethermesh_MeshPacket_ack_tag;
    ackPacket.payload.ack.acked_packet_id = ackedPacketId;
    // Report how we heard the packet, so the sender learns the
    // outbound link quality (not just the ACK's return signal)
    ackPacket.payload.ack.acked_rx_rssi = rssi;
    ackPacket.payload.ack.acked_rx_snr = snr;

    serializeAndSend(&ackPacket);
}

void MeshRouter::emitDeliveryStatus(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr) {
    if (deliveryStatusCallback) {
        deliveryStatusCallback(packetId, recipientId, state, reason, retryCount, ackRssi, ackSnr);
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
    RouteEntry* route = getRoute(packet.recipient_id);
    uint8_t routeMetric = route ? route->metric : 0;
    pendingAcks[slot].nextRetryTime = millis() +
        meshmath::ackRetryDelayMs(0, routeMetric, random(0, 500));
    pendingAcks[slot].active = true;
}

void MeshRouter::clearPendingAck(uint32_t ackedPacketId, float ackRssi, float ackSnr) {
    for (int i = 0; i < MAX_PENDING_ACKS; i++) {
        if (pendingAcks[i].active && pendingAcks[i].packet.packet_id == ackedPacketId) {
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
            Serial.printf("Packet %u acknowledged; retransmit tracking cleared.\n", ackedPacketId);
        }
    }
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
