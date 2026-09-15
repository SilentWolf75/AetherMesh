#include "MeshRouter.h"
#include "pb_encode.h"
#include "pb_decode.h"
#include <vector>
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
int main() {
    try {
        duplicateDelivery(); boundedQueuesAndExpiry(); multiHopWithLostAck(); busyRadioRecovers(); alternateRouteRepair();
        std::cout << "5 production router scenarios passed\n";
    } catch (const std::exception& error) { std::cerr << error.what() << "\n"; return 1; }
}
