#include "MeshRouter.h"
#include "MeshIngress.h"
#include "TraceHops.h"
#include "pb_encode.h"
#include "pb_decode.h"
#include <vector>
#include <memory>
#include <stdexcept>
#include <iostream>
uint32_t simulatedMillis = 1000;
HostSerial Serial;
static int received = 0;
static std::vector<aethermesh_DeliveryStatus_State> statuses;
#define REQUIRE(value) do { if (!(value)) throw std::runtime_error(#value); } while (0)
struct FakeRadio : MeshRadio {
    bool busy = false;
    std::vector<std::vector<uint8_t>> frames;
    bool sendPacket(uint8_t* data, size_t size, bool = false) override {
        if (busy) return false;
        frames.emplace_back(data, data + size);
        return true;
    }
    uint8_t getSpreadingFactor() override { return 7; }
    uint32_t getTxPackets() const override { return (uint32_t)frames.size(); }
    uint32_t getTxFailures() const override { return 0; }
    uint32_t getRxPackets() const override { return 0; }
    uint32_t getCadBusyEvents() const override { return 0; }
    uint32_t getAirtimeMs() const override { return 0; }
    uint32_t getRecentAirtimeMs() const override { return 0; }
};
static void delivered(uint32_t, uint32_t, aethermesh_DeliveryStatus_State state,
                      aethermesh_DeliveryStatus_Reason, uint32_t, float, float, uint32_t, uint32_t) {
    statuses.push_back(state);
}
static void receiveText(uint32_t, const char*) { ++received; }
static aethermesh_MeshPacket packet(uint32_t id = 42) {
    aethermesh_MeshPacket p = aethermesh_MeshPacket_init_zero;
    p.sender_id = 1; p.recipient_id = 3; p.prev_hop_id = 1;
    p.packet_id = id; p.want_ack = true; p.hop_limit = 4;
    p.which_payload = aethermesh_MeshPacket_text_tag;
    strcpy(p.payload.text.content, "test");
    return p;
}
static std::vector<uint8_t> encode(aethermesh_MeshPacket p) {
    std::vector<uint8_t> data(256);
    auto stream = pb_ostream_from_buffer(data.data(), data.size());
    REQUIRE(pb_encode(&stream, aethermesh_MeshPacket_fields, &p));
    data.resize(stream.bytes_written);
    return data;
}
static aethermesh_MeshPacket decode(const std::vector<uint8_t>& data) {
    aethermesh_MeshPacket p = aethermesh_MeshPacket_init_zero;
    auto stream = pb_istream_from_buffer(data.data(), data.size());
    REQUIRE(pb_decode(&stream, aethermesh_MeshPacket_fields, &p));
    return p;
}
static void feed(MeshRouter& router, std::vector<uint8_t> data) {
    router.processIncomingPacket(data.data(), data.size(), -70, 8);
}
static void duplicateDelivery() {
    FakeRadio radio; MeshRouter target(&radio); target.init(3);
    target.onReceivedTextMessage(receiveText); received = 0;
    auto p = packet(); feed(target, encode(p)); feed(target, encode(p));
    p.retry_count = 1; feed(target, encode(p));
    REQUIRE(received == 1);
}
static void boundedQueuesAndExpiry() {
    FakeRadio radio; MeshRouter source(&radio); source.init(1);
    source.onDeliveryStatus(delivered); statuses.clear();
    for (uint32_t id = 1; id <= MAX_PENDING_ACKS + 2; ++id) {
        auto p = packet(id); source.sendRawPacket(&p);
    }
    aethermesh_MeshDiagnostics diagnostics = aethermesh_MeshDiagnostics_init_zero;
    source.getDiagnostics(diagnostics);
    REQUIRE(diagnostics.pending_ack_depth == MAX_PENDING_ACKS);
    REQUIRE(std::count(statuses.begin(), statuses.end(), aethermesh_DeliveryStatus_State_FAILED) >= 2);
    for (unsigned i = 0; i < 3600; ++i) { simulatedMillis += 1000; source.loop(); }
    source.getDiagnostics(diagnostics);
    REQUIRE(diagnostics.pending_ack_depth == 0);
    REQUIRE(std::count(statuses.begin(), statuses.end(), aethermesh_DeliveryStatus_State_EXPIRED) > 0);
}
static void multiHopWithLostAck() {
    FakeRadio radios[3];
    MeshRouter a(&radios[0]), b(&radios[1]), c(&radios[2]);
    MeshRouter* nodes[] = {&a, &b, &c};
    for (unsigned i = 0; i < 3; ++i) { nodes[i]->init(i + 1); nodes[i]->setNodeRole(1); }
    a.onDeliveryStatus(delivered); c.onReceivedTextMessage(receiveText);
    statuses.clear(); received = 0;
    auto p = packet(); REQUIRE(a.sendRawPacket(&p));
    size_t consumed[3] = {}; bool droppedAck = false;
    for (unsigned tick = 0; tick < 1800; ++tick) {
        simulatedMillis += 100;
        for (auto node : nodes) node->loop();
        for (unsigned sender = 0; sender < 3; ++sender) {
            const size_t end = radios[sender].frames.size();
            while (consumed[sender] < end) {
                auto frame = radios[sender].frames[consumed[sender]++];
                auto decoded = decode(frame);
                if (sender == 2 && decoded.which_payload == aethermesh_MeshPacket_ack_tag && !droppedAck) {
                    droppedAck = true; continue;
                }
                if (sender > 0) feed(*nodes[sender - 1], frame);
                if (sender < 2) feed(*nodes[sender + 1], frame);
            }
        }
    }
    REQUIRE(droppedAck);
    REQUIRE(received == 1);
    REQUIRE(std::count(statuses.begin(), statuses.end(), aethermesh_DeliveryStatus_State_DELIVERED) >= 1);
    aethermesh_MeshDiagnostics diagnostics = aethermesh_MeshDiagnostics_init_zero;
    a.getDiagnostics(diagnostics);
    REQUIRE(diagnostics.pending_ack_depth == 0);
}
static void busyRadioRecovers() {
    FakeRadio radio; radio.busy = true;
    MeshRouter source(&radio); source.init(1);
    auto p = packet(); source.sendRawPacket(&p);
    REQUIRE(radio.frames.empty());
    radio.busy = false;
    for (unsigned tick = 0; tick < 100; ++tick) { simulatedMillis += 100; source.loop(); }
    REQUIRE(!radio.frames.empty());
}
static void alternateRouteRepair() {
    FakeRadio radio; MeshRouter source(&radio); source.init(1);
    auto route = packet(800);
    route.sender_id = 3; route.prev_hop_id = 2; route.recipient_id = 1;
    route.which_payload = aethermesh_MeshPacket_route_discovery_tag;
    route.payload.route_discovery.type = aethermesh_RouteDiscovery_Type_REPLY;
    route.payload.route_discovery.target_id = 3;
    route.payload.route_discovery.metric = 1;
    feed(source, encode(route));
    route.packet_id = 801; route.prev_hop_id = 4;
    route.payload.route_discovery.metric = 8;
    feed(source, encode(route));
    radio.frames.clear();
    auto outgoing = packet(802); source.sendRawPacket(&outgoing);
    REQUIRE(decode(radio.frames.front()).next_hop_id == 2);
    // The preferred relay never ACKs. The router must try its learned backup.
    for (unsigned tick = 0; tick < 300; ++tick) { simulatedMillis += 100; source.loop(); }
    bool usedBackup = false;
    for (const auto& frame : radio.frames) {
        auto p = decode(frame);
        if (p.packet_id == 802 && p.next_hop_id == 4) usedBackup = true;
    }
    REQUIRE(usedBackup);
}
static void echoLoopDoesNotDeliver() {
    FakeRadio radio; MeshRouter target(&radio); target.init(1);
    target.setNodeRole(1);
    target.onReceivedTextMessage(receiveText); received = 0;
    auto p = packet();
    p.sender_id = 2; p.recipient_id = 0xFFFFFFFFu; p.prev_hop_id = 1; p.hop_limit = 3;
    p.want_ack = false;
    feed(target, encode(p));
    REQUIRE(received == 0);
    p.packet_id = 99; p.prev_hop_id = 2; p.hop_limit = 17; // above the 16-hop ceiling
    feed(target, encode(p));
    REQUIRE(received == 0);
    p.packet_id = 100; p.hop_limit = 4;
    feed(target, encode(p));
    REQUIRE(received == 1);
}
static void shorterPathDuplicateStillAcks() {
    FakeRadio radio; MeshRouter target(&radio); target.init(3);
    target.onReceivedTextMessage(receiveText); received = 0;
    auto p = packet(12345); p.hop_limit = 2; p.prev_hop_id = 2;
    feed(target, encode(p));
    radio.frames.clear();
    p.hop_limit = 4; p.prev_hop_id = 1;
    feed(target, encode(p));
    REQUIRE(received == 1);
    // ACKs are slotted onto the rebroadcast queue — drain before asserting.
    bool acked = false;
    for (unsigned i = 0; i < 50 && !acked; ++i) {
        simulatedMillis += 100;
        target.loop();
        for (const auto& f : radio.frames) {
            auto d = decode(f);
            if (d.which_payload == aethermesh_MeshPacket_ack_tag &&
                d.payload.ack.acked_packet_id == p.packet_id) acked = true;
        }
    }
    REQUIRE(acked);
}
static void hopInflationDoesNotRedeliverOrRerelay() {
    FakeRadio radio; MeshRouter relay(&radio); relay.init(2); relay.setNodeRole(1);
    relay.onReceivedTextMessage(receiveText); received = 0;
    auto p = packet(22222);
    p.recipient_id = 0xFFFFFFFFu;
    p.want_ack = false;
    p.hop_limit = 3;
    p.prev_hop_id = 1;
    feed(relay, encode(p));
    // Drain any scheduled flood copy from the first sighting.
    for (unsigned i = 0; i < 40; ++i) { simulatedMillis += 100; relay.loop(); }
    const size_t afterFirst = radio.frames.size();
    REQUIRE(afterFirst >= 1);
    // Same attempt with a higher hop_limit (rewound TTL) must not deliver again
    // and must not enqueue another relay TX.
    p.hop_limit = 7;
    p.prev_hop_id = 9;
    feed(relay, encode(p));
    for (unsigned i = 0; i < 40; ++i) { simulatedMillis += 100; relay.loop(); }
    REQUIRE(received == 1);
    REQUIRE(radio.frames.size() == afterFirst);
}
static void phoneRetryAdvancesFirmwareAttempts() {
    FakeRadio radio; MeshRouter source(&radio); source.init(1);
    auto p = packet(67890); REQUIRE(source.sendRawPacket(&p));
    p.retry_count = 64; REQUIRE(source.sendRawPacket(&p));
    radio.frames.clear();
    for (unsigned i = 0; i < 1200; ++i) { simulatedMillis += 100; source.loop(); }
    unsigned retries = 0;
    for (const auto& frame : radio.frames) {
        auto sent = decode(frame);
        if (sent.packet_id != p.packet_id) continue;
        REQUIRE(sent.retry_count >= 64);
        if (sent.retry_count > 64) ++retries;
    }
    REQUIRE(retries > 0);
}
static void radioCannotInjectLocalControls() {
    const pb_size_t tags[] = {
        aethermesh_MeshPacket_auth_request_tag, aethermesh_MeshPacket_auth_response_tag,
        aethermesh_MeshPacket_delivery_status_tag, aethermesh_MeshPacket_ota_control_tag,
        aethermesh_MeshPacket_ota_data_tag, aethermesh_MeshPacket_ota_status_tag,
        aethermesh_MeshPacket_diagnostics_tag, aethermesh_MeshPacket_range_test_control_tag,
        aethermesh_MeshPacket_position_privacy_tag
    };
    FakeRadio radio; MeshRouter relay(&radio); relay.init(2); relay.setNodeRole(1);
    for (pb_size_t tag : tags) {
        aethermesh_MeshPacket p = aethermesh_MeshPacket_init_zero;
        p.sender_id = 1; p.prev_hop_id = 1; p.recipient_id = 3;
        p.packet_id = 90000 + tag; p.hop_limit = 4; p.which_payload = tag;
        REQUIRE(!meshingress::acceptRadio(p, 2));
        feed(relay, encode(p));
        REQUIRE(!relay.hasSeen(p.sender_id, p.packet_id));
        for (unsigned i = 0; i < 20; ++i) { simulatedMillis += 100; relay.loop(); }
        REQUIRE(radio.frames.empty());
        // Even a local send API must not accidentally put these on air.
        p.sender_id = 2;
        REQUIRE(!relay.sendRawPacket(&p));
    }
}
static void radioTelemetryHonorsChannelPrivacy() {
    FakeRadio radio; MeshRouter source(&radio); source.init(1);
    const float lat = 35.1234f, lon = -106.4567f;
    for (uint32_t recipient : {3u, 0xFFFFFFFFu}) {
        source.setPositionPrivacy(positionprivacy::record(true, 0));
        REQUIRE(source.sendTelemetry(recipient, 80, lat, lon, "test"));
        auto hidden = decode(radio.frames.back()).payload.telemetry;
        REQUIRE(hidden.latitude == 0 && hidden.longitude == 0 && hidden.altitude == 0);
        REQUIRE(hidden.battery_level == 80);
        source.setPositionPrivacy(positionprivacy::record(false, 1609));
        REQUIRE(source.sendTelemetry(recipient, 80, lat, lon, "test", false, 4.0f, 100));
        auto blurred = decode(radio.frames.back()).payload.telemetry;
        float expectedLat, expectedLon;
        meshmath::blurPosition(lat, lon, 1609, expectedLat, expectedLon);
        REQUIRE(blurred.latitude == expectedLat && blurred.longitude == expectedLon);
        REQUIRE(blurred.position_precision == 1609);
        REQUIRE(source.sendTelemetry(recipient, 80, lat, lon, "test", false, 4.0f, 5000));
        REQUIRE(decode(radio.frames.back()).payload.telemetry.position_precision == 5000);
    }
    // Direct/raw telemetry must not bypass the same final-transmission rule.
    auto raw = packet(99000);
    raw.which_payload = aethermesh_MeshPacket_telemetry_tag;
    raw.payload.telemetry = aethermesh_Telemetry_init_zero;
    raw.payload.telemetry.latitude = lat;
    raw.payload.telemetry.longitude = lon;
    raw.payload.telemetry.altitude = 1000;
    source.setPositionPrivacy(positionprivacy::record(true, 0));
    REQUIRE(source.sendRawPacket(&raw));
    auto hidden = decode(radio.frames.back()).payload.telemetry;
    REQUIRE(hidden.latitude == 0 && hidden.longitude == 0 && hidden.altitude == 0);
    REQUIRE(positionprivacy::validateRecord(0x7FFFFFFFu) == positionprivacy::Disabled);
    REQUIRE(!positionprivacy::allowInheritedFix(positionprivacy::Disabled));
    REQUIRE(positionprivacy::allowInheritedFix(positionprivacy::record(false, 1609)));
    REQUIRE(positionprivacy::allowInheritedFix(0));
}

// Linear chain: each frame is heard only by the adjacent nodes.
static void runChain(std::vector<FakeRadio>& radios, std::vector<MeshRouter*>& nodes, unsigned ticks) {
    std::vector<size_t> consumed(nodes.size(), 0);
    for (unsigned tick = 0; tick < ticks; ++tick) {
        simulatedMillis += 100;
        for (auto node : nodes) node->loop();
        for (size_t sender = 0; sender < nodes.size(); ++sender) {
            while (consumed[sender] < radios[sender].frames.size()) {
                auto frame = radios[sender].frames[consumed[sender]++];
                if (sender > 0) feed(*nodes[sender - 1], frame);
                if (sender + 1 < nodes.size()) feed(*nodes[sender + 1], frame);
            }
        }
    }
}

// Older firmware sent every ACK with hop_limit 4, so a DM that needed more
// hops arrived but its ACK died on the way back and the sender reported failure.
static void longChainAckReturnsWithinOriginatorBudget() {
    const unsigned count = 10;
    std::vector<FakeRadio> radios(count);
    std::vector<std::unique_ptr<MeshRouter>> owned;
    std::vector<MeshRouter*> nodes;
    for (unsigned i = 0; i < count; ++i) {
        owned.emplace_back(new MeshRouter(&radios[i]));
        nodes.push_back(owned.back().get());
        nodes[i]->init(i + 1);
        nodes[i]->setNodeRole(1);
        nodes[i]->setDefaultHopLimit(12);
    }
    nodes[0]->onDeliveryStatus(delivered);
    nodes[count - 1]->onReceivedTextMessage(receiveText);
    statuses.clear(); received = 0;

    auto p = packet(); p.recipient_id = count; p.hop_limit = 12;
    REQUIRE(nodes[0]->sendRawPacket(&p));
    runChain(radios, nodes, 3000);

    REQUIRE(received == 1);
    REQUIRE(std::count(statuses.begin(), statuses.end(), aethermesh_DeliveryStatus_State_DELIVERED) >= 1);
}

static void hopStartIsStampedByOriginatorAndKeptByRelays() {
    FakeRadio radios[2];
    MeshRouter a(&radios[0]), b(&radios[1]);
    a.init(1); b.init(2); b.setNodeRole(1);
    auto p = packet(); p.recipient_id = 0xFFFFFFFFu; p.want_ack = false; p.hop_limit = 9;
    REQUIRE(a.sendRawPacket(&p));
    auto sent = decode(radios[0].frames.front());
    REQUIRE(sent.hop_start == 9 && sent.hop_limit == 9);

    feed(b, radios[0].frames.front());
    for (unsigned tick = 0; tick < 100 && radios[1].frames.empty(); ++tick) { simulatedMillis += 100; b.loop(); }
    REQUIRE(!radios[1].frames.empty());
    auto relayed = decode(radios[1].frames.front());
    REQUIRE(relayed.hop_start == 9 && relayed.hop_limit == 8);
}

static void rewoundHopStartIsDropped() {
    FakeRadio radio; MeshRouter target(&radio); target.init(3);
    target.onReceivedTextMessage(receiveText); received = 0;
    auto p = packet(); p.hop_start = 4; p.hop_limit = 6;
    feed(target, encode(p));
    REQUIRE(received == 0);
    p.packet_id = 43; p.hop_limit = 16; p.hop_start = 16;
    feed(target, encode(p));
    REQUIRE(received == 1);
    p.packet_id = 44; p.hop_limit = 17; p.hop_start = 17;
    feed(target, encode(p));
    REQUIRE(received == 1);
}

static aethermesh_MeshPacket traceRequest(uint32_t hopStart) {
    aethermesh_MeshPacket p = aethermesh_MeshPacket_init_zero;
    p.sender_id = 1; p.recipient_id = 9; p.prev_hop_id = 1; p.packet_id = 77;
    p.hop_limit = hopStart; p.hop_start = hopStart;
    p.which_payload = aethermesh_MeshPacket_trace_route_tag;
    p.payload.trace_route.type = aethermesh_TraceRoute_Type_REQUEST;
    p.payload.trace_route.trace_id = 5; p.payload.trace_route.origin_id = 1;
    p.payload.trace_route.target_id = 9;
    return p;
}

// Legacy-range traces keep the repeated fields older relays append to; only
// extended-range traces (hop_start > 8) switch to the compact path.
static void traceFormatFollowsHopStart() {
    for (uint32_t hopStart : {4u, 12u}) {
        FakeRadio radio; MeshRouter relay(&radio); relay.init(2); relay.setNodeRole(1);
        feed(relay, encode(traceRequest(hopStart)));
        for (unsigned tick = 0; tick < 100 && radio.frames.empty(); ++tick) { simulatedMillis += 100; relay.loop(); }
        REQUIRE(!radio.frames.empty());
        auto trace = decode(radio.frames.front()).payload.trace_route;
        if (hopStart > 8) {
            REQUIRE(trace.forward_hops.size == tracehops::HOP_BYTES);
            REQUIRE(tracehops::nodeAt(trace.forward_hops.bytes, 0) == 2);
            REQUIRE(tracehops::snrQuarterDbAt(trace.forward_hops.bytes, 0) == 32);
            REQUIRE(trace.forward_node_ids_count == 0);
        } else {
            REQUIRE(trace.forward_node_ids_count == 1 && trace.forward_node_ids[0] == 2);
            REQUIRE(trace.forward_hops.size == 0);
        }
    }
}

// SX1262 frames carry at most 255 bytes. A full 16-hop-each-way response with
// every header field at its largest realistic encoding must still fit.
static void fullExtendedTraceFitsOneLoRaFrame() {
    aethermesh_MeshPacket p = traceRequest(16);
    p.sender_id = 0xFFFFFFF0u; p.recipient_id = 0xFFFFFFF1u; p.prev_hop_id = 0xFFFFFFF2u;
    p.next_hop_id = 0xFFFFFFF3u; p.packet_id = 0xFFFFFFF4u; p.retry_count = 3; p.want_ack = true;
    p.protocol_version = 2; p.session_id = 0xFFFFFFFFFFFFFFFFull;
    // rx_rssi/rx_snr are only set on BLE copies to the phone, never on air.
    auto& trace = p.payload.trace_route;
    trace.type = aethermesh_TraceRoute_Type_RESPONSE;
    trace.trace_id = 0xFFFFFFF5u; trace.origin_id = 0xFFFFFFF6u; trace.target_id = 0xFFFFFFF7u;
    trace.forward_truncated = true; trace.return_truncated = true;
    for (uint32_t hop = 0; hop < tracehops::MAX_HOPS; ++hop) {
        REQUIRE(tracehops::append(trace.forward_hops.bytes, trace.forward_hops.size,
                                  sizeof(trace.forward_hops.bytes), 0xFFFFFF00u + hop, -32));
        REQUIRE(tracehops::append(trace.return_hops.bytes, trace.return_hops.size,
                                  sizeof(trace.return_hops.bytes), 0xFFFFFE00u + hop, -32));
    }
    REQUIRE(!tracehops::append(trace.forward_hops.bytes, trace.forward_hops.size,
                               sizeof(trace.forward_hops.bytes), 1, 0));
    std::vector<uint8_t> data(512);
    auto stream = pb_ostream_from_buffer(data.data(), data.size());
    REQUIRE(pb_encode(&stream, aethermesh_MeshPacket_fields, &p));
    std::cout << "  full 16+16 hop trace frame: " << stream.bytes_written << " bytes\n";
    REQUIRE(stream.bytes_written <= 255);
}

// LoRa telemetry must stay within one SX1262 frame with every string field full
// and the GNSS snapshot fields at their largest values.
static void worstCaseTelemetryFitsOneLoRaFrame() {
    FakeRadio radio; MeshRouter source(&radio); source.init(0xFFFFFFF0u);
    source.setDefaultHopLimit(16);
    aethermesh_Telemetry gps = aethermesh_Telemetry_init_zero;
    gps.gps_state = aethermesh_Telemetry_GpsState_GPS_STATE_FIX;
    gps.position_source = aethermesh_Telemetry_PositionSource_POSITION_SOURCE_FIXED;
    gps.gps_satellites_used = 60; gps.gps_satellites_in_view = 120;
    gps.gps_hdop_x10 = 9999; gps.gps_fix_age_secs = 0xFFFFFFFFu;
    REQUIRE(source.sendTelemetry(0xFFFFFFFFu, 100, -89.999f, -179.999f, "Sixteen chars!!!", true, 4.2f,
                                 0xFFFFFFFFu, 12, 1, &gps));
    REQUIRE(!radio.frames.empty());
    auto sent = decode(radio.frames.back());
    REQUIRE(sent.payload.telemetry.gps_satellites_in_view == 120);
    std::cout << "  worst-case telemetry frame: " << radio.frames.back().size() << " bytes\n";
    REQUIRE(radio.frames.back().size() <= 255);
}

// A relay replays stored channel text to a neighbor that was offline. The
// replay gets the relay's own hop budget; it must not keep the originator's
// smaller hop_start, or new firmware drops it as a rewound hop_limit.
static void channelCatchupReplayPassesHopStartCheck() {
    FakeRadio radio; MeshRouter relay(&radio); relay.init(2);
    relay.setNodeRole(1);
    relay.setDefaultHopLimit(8);

    auto hello = [&](uint32_t id) {
        aethermesh_MeshPacket p = aethermesh_MeshPacket_init_zero;
        p.sender_id = 5; p.recipient_id = 0xFFFFFFFFu; p.prev_hop_id = 5;
        p.packet_id = id; p.hop_limit = 1; p.hop_start = 1;
        p.which_payload = aethermesh_MeshPacket_telemetry_tag;
        feed(relay, encode(p));
    };
    hello(900);                                   // neighbor 5 first seen
    simulatedMillis += CHANNEL_NEIGHBOR_OFFLINE_MS + 1000;

    aethermesh_MeshPacket text = aethermesh_MeshPacket_init_zero;
    text.sender_id = 7; text.recipient_id = 0xFFFFFFFFu; text.prev_hop_id = 7;
    text.packet_id = 901; text.hop_limit = 4; text.hop_start = 4;
    text.which_payload = aethermesh_MeshPacket_text_tag;
    strcpy(text.payload.text.content, "missed while offline");
    strcpy(text.payload.text.channel, "General");
    feed(relay, encode(text));                    // stored for catch-up
    simulatedMillis += 1000;
    hello(902);                                   // neighbor 5 is back

    bool found = false;
    for (unsigned tick = 0; tick < 1200 && !found; ++tick) {
        simulatedMillis += 100;
        relay.loop();
        for (const auto& frame : radio.frames) {
            auto p = decode(frame);
            if (p.which_payload == aethermesh_MeshPacket_text_tag && p.recipient_id == 5 &&
                p.packet_id == 901) {
                REQUIRE(p.hop_limit == 8);
                REQUIRE(meshingress::acceptRadio(p, 5));
                found = true;
            }
        }
    }
    REQUIRE(found);
}

// The ACK timing budget assumes an encoded ACK is at most CHANNEL_ACK_FRAME_BYTES.
static void worstCaseAckFitsTimingBudget() {
    aethermesh_MeshPacket p = aethermesh_MeshPacket_init_zero;
    p.sender_id = 0xFFFFFFF0u; p.recipient_id = 0xFFFFFFF1u; p.prev_hop_id = 0xFFFFFFF2u;
    p.next_hop_id = 0xFFFFFFF3u; p.packet_id = 0xFFFFFFF4u; p.hop_limit = 16; p.hop_start = 16;
    p.retry_count = 3; p.protocol_version = 2; p.session_id = 0xFFFFFFFFFFFFFFFFull;
    p.which_payload = aethermesh_MeshPacket_ack_tag;
    p.payload.ack.acked_packet_id = 0xFFFFFFF5u;
    p.payload.ack.acked_rx_rssi = -120.5f; p.payload.ack.acked_rx_snr = -18.25f;
    size_t size = 0;
    REQUIRE(pb_get_encoded_size(&size, aethermesh_MeshPacket_fields, &p));
    std::cout << "  worst-case ACK frame: " << size << " bytes\n";
    REQUIRE(size <= meshmath::CHANNEL_ACK_FRAME_BYTES);
}

int main() {
    try {
        worstCaseAckFitsTimingBudget();
        channelCatchupReplayPassesHopStartCheck();
        worstCaseTelemetryFitsOneLoRaFrame();
        longChainAckReturnsWithinOriginatorBudget(); hopStartIsStampedByOriginatorAndKeptByRelays();
        rewoundHopStartIsDropped(); traceFormatFollowsHopStart(); fullExtendedTraceFitsOneLoRaFrame();
        duplicateDelivery(); boundedQueuesAndExpiry(); multiHopWithLostAck(); busyRadioRecovers(); alternateRouteRepair(); echoLoopDoesNotDeliver();
        shorterPathDuplicateStillAcks(); phoneRetryAdvancesFirmwareAttempts();
        radioCannotInjectLocalControls(); radioTelemetryHonorsChannelPrivacy();
        hopInflationDoesNotRedeliverOrRerelay();
        std::cout << "19 production router scenarios passed\n";
    } catch (const std::exception& error) { std::cerr << error.what() << "\n"; return 1; }
}
