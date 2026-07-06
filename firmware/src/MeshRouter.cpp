#include "MeshRouter.h"
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
}

void MeshRouter::init(uint32_t localId) {
    localNodeId = localId;
    packetSequenceCounter = random(1, 10000);
    
    Serial.print("MeshRouter initialized. Local Node ID: 0x");
    Serial.println(localNodeId, HEX);
}

void MeshRouter::loop() {
    uint32_t now = millis();
    drainPendingPongReplies();
    for (int i = 0; i < MAX_PENDING_REBROADCASTS; i++) {
        if (pendingRebroadcasts[i].active && now >= pendingRebroadcasts[i].transmitTime) {
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
                pendingAcks[i].nextRetryTime = now + ACK_RETRY_INTERVAL_MS + random(0, 500);
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
        // Update if new metric is better or equal
        if (metric <= existing->metric || (now - existing->timestamp) > 30000) {
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

bool MeshRouter::hasSeenPacketId(uint32_t senderId, uint32_t packetId) {
    for (int i = 0; i < MAX_SEEN_PACKETS_CACHE; i++) {
        if (seenPackets[i].senderId == senderId && seenPackets[i].packetId == packetId) {
            return true;
        }
    }
    return false;
}

bool MeshRouter::isDuplicatePacket(uint32_t senderId, uint32_t packetId, uint32_t retryCount) {
    for (int i = 0; i < MAX_SEEN_PACKETS_CACHE; i++) {
        if (seenPackets[i].senderId == senderId && seenPackets[i].packetId == packetId) {
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
            maybeQueuePongForPingText(packet);
        }
        return;
    }
    markPacketAsSeen(packet.sender_id, packet.packet_id, packet.retry_count);
    
    // 3. Update routing table
    // If prev_hop_id is set, it's the node that directly relayed it to us.
    // If not, it's the original sender.
    uint32_t immediateSender = (packet.prev_hop_id != 0) ? packet.prev_hop_id : packet.sender_id;
    
    // Calculate SNR-weighted hop cost
    float constrainedSnr = constrain(snr, -20.0f, 10.0f);
    uint8_t hopCost = (uint8_t)(1.0f + (10.0f - constrainedSnr) * (24.0f / 30.0f));
    
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
        maybeQueuePongForPingText(packet);

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
            
            // Queue rebroadcast with SNR-based delay
            float constrainedSnr = constrain(snr, -20.0f, 10.0f);
            uint32_t delayMs = (uint32_t)(500.0f + (10.0f - constrainedSnr) * (1500.0f / 30.0f));
            queueRebroadcast(packet, millis() + delayMs);
        }
    } else {
        // Unicast packet for someone else (we are a relay node)
        if (packet.hop_limit >= 1) {
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
            } else if (packet.which_payload == aethermesh_MeshPacket_text_tag &&
                       (strncmp(packet.payload.text.content, "PING_", 5) == 0 ||
                        strncmp(packet.payload.text.content, "PONG_", 5) == 0)) {
                // Range-test ping/pong with no route: flood anyway (telemetry uses
                // broadcast and often gets through when routed unicast would drop here).
                Serial.print("No route to 0x");
                Serial.print(packet.recipient_id, HEX);
                Serial.println("; flooding range-test PING/PONG.");
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

bool MeshRouter::sendTextNoAck(uint32_t recipientId, const char* text, bool urgent) {
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    packet.sender_id = localNodeId;
    packet.recipient_id = recipientId;
    packet.packet_id = ++packetSequenceCounter;
    packet.hop_limit = DEFAULT_HOP_LIMIT;
    packet.want_ack = false;
    packet.prev_hop_id = localNodeId;
    packet.which_payload = aethermesh_MeshPacket_text_tag;

    strncpy(packet.payload.text.content, text, sizeof(packet.payload.text.content) - 1);
    packet.payload.text.content[sizeof(packet.payload.text.content) - 1] = '\0';
    packet.payload.text.channel[0] = '\0';
    packet.payload.text.is_encrypted = false;

    if (recipientId != 0xFFFFFFFF && getRoute(recipientId) == nullptr) {
        sendRouteRequest(recipientId);
    }

    return serializeAndSend(&packet, urgent);
}

bool MeshRouter::sendTelemetry(uint32_t recipientId, uint8_t battery, float lat, float lon, bool charging) {
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
    packet.payload.telemetry.uptime_seconds = (uint32_t)(millis() / 1000);
    strncpy(packet.payload.telemetry.firmware_version, AETHERMESH_FW_VERSION,
            sizeof(packet.payload.telemetry.firmware_version) - 1);

#if defined(HELTEC_V4)
    strcpy(packet.payload.telemetry.node_model, "Heltec V4");
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
        if (route) {
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

void MeshRouter::maybeQueuePongForPingText(const aethermesh_MeshPacket& packet) {
    if (packet.which_payload != aethermesh_MeshPacket_text_tag) {
        return;
    }
    // Range test uses mesh ACKs (want_ack=true). PONG replies are only for
    // want_ack=false pings and would just congest the channel otherwise.
    if (packet.want_ack) {
        return;
    }
    if (strncmp(packet.payload.text.content, "PING_", 5) != 0) {
        return;
    }
    const char* pingId = packet.payload.text.content + 5;
    if (pingId[0] != '\0' && strlen(pingId) < 8) {
        queuePongReply(packet.sender_id, pingId);
    }
}

void MeshRouter::queuePongReply(uint32_t recipientId, const char* pingId) {
    char pongContent[24];
    snprintf(pongContent, sizeof(pongContent), "PONG_%s", pingId);

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
        if (sendTextNoAck(pendingPongs[i].recipientId, pendingPongs[i].content, true)) {
            pendingPongs[i].sendCount++;
            Serial.printf("Sent range-test %s (attempt %u)\n",
                          pendingPongs[i].content, pendingPongs[i].sendCount);
            pendingPongs[i].sendAtMs = now + PONG_RESEND_INTERVAL_MS + random(0, 300);
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
        // If queue is full, overwrite the slot with the earliest transmit time
        uint32_t earliestTime = pendingRebroadcasts[0].transmitTime;
        emptySlot = 0;
        for (int i = 1; i < MAX_PENDING_REBROADCASTS; i++) {
            if (pendingRebroadcasts[i].transmitTime < earliestTime) {
                earliestTime = pendingRebroadcasts[i].transmitTime;
                emptySlot = i;
            }
        }
        Serial.printf("Rebroadcast queue full. Overwriting slot %d (packet_id: %u)\n", 
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
    pendingAcks[slot].nextRetryTime = millis() + ACK_RETRY_INTERVAL_MS + random(0, 500);
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
