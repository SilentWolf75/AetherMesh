#ifndef MESH_ROUTER_H
#define MESH_ROUTER_H

#include <Arduino.h>
#include "mesh.pb.h"
#include "RadioManager.h"

// Define constants
#define MAX_ROUTE_TABLE_ENTRIES 30
#define MAX_SEEN_PACKETS_CACHE 50
#define DEFAULT_HOP_LIMIT 4
#define ROUTE_TIMEOUT_MS 600000 // 10 minutes
#define MAX_PENDING_REBROADCASTS 3

struct RouteEntry {
    uint32_t targetId;
    uint32_t nextHopId;
    uint8_t metric;
    uint32_t timestamp;
    bool active;
};

struct SeenPacket {
    uint32_t senderId;
    uint32_t packetId;
    uint32_t timestamp;
};

struct PendingRebroadcast {
    aethermesh_MeshPacket packet;
    uint32_t transmitTime;
    bool active;
};

class MeshRouter {
public:
    MeshRouter(RadioManager* radioMgr);
    void init(uint32_t localId);
    void loop();
    
    // Send message interfaces
    bool sendText(uint32_t recipientId, const char* text);
    bool sendTelemetry(uint32_t recipientId, uint8_t battery, float lat, float lon);
    
    // Packet processing entrypoint (called by RadioManager receive callback)
    void processIncomingPacket(uint8_t* data, size_t len, float rssi, float snr);
    
    // Callback registers
    void onReceivedTextMessage(void (*callback)(uint32_t senderId, const char* text));
    void onReceivedTelemetry(void (*callback)(uint32_t senderId, uint8_t battery, float lat, float lon));
    void onReceivedConfig(void (*callback)(uint32_t senderId, const aethermesh_NodeConfig& config));
    
    // Routing Table Diagnostics
    uint32_t getLocalId() { return localNodeId; }
    void printRoutingTable();

    // True if this (sender, packet) pair is already in the dedup cache.
    // Lets callers (e.g. the BLE forward path) skip mesh rebroadcast duplicates.
    bool hasSeen(uint32_t senderId, uint32_t packetId) { return isDuplicatePacket(senderId, packetId); }

    // Raw packet transmit helper
    bool sendRawPacket(aethermesh_MeshPacket* packet);

private:
    RadioManager* radio;
    uint32_t localNodeId;
    uint32_t packetSequenceCounter;
    
    // Data structures
    RouteEntry routingTable[MAX_ROUTE_TABLE_ENTRIES];
    SeenPacket seenPackets[MAX_SEEN_PACKETS_CACHE];
    uint8_t seenPacketsIndex;
    PendingRebroadcast pendingRebroadcasts[MAX_PENDING_REBROADCASTS];
    
    // Telemetry/text callbacks
    void (*textCallback)(uint32_t senderId, const char* text);
    void (*telemetryCallback)(uint32_t senderId, uint8_t battery, float lat, float lon);
    void (*configCallback)(uint32_t senderId, const aethermesh_NodeConfig& config);
    
    // Private Helpers
    void addRoute(uint32_t targetId, uint32_t nextHopId, uint8_t metric);
    RouteEntry* getRoute(uint32_t targetId);
    
    bool isDuplicatePacket(uint32_t senderId, uint32_t packetId);
    void markPacketAsSeen(uint32_t senderId, uint32_t packetId);
    
    bool handleRouteRequest(uint32_t senderId, uint32_t prevHopId, const aethermesh_RouteDiscovery& rreq);
    void handleRouteReply(uint32_t senderId, uint32_t prevHopId, const aethermesh_RouteDiscovery& rrep);
    
    void sendRouteRequest(uint32_t targetId);
    void sendRouteReply(uint32_t recipientId, uint32_t targetId, uint8_t metric);
    
    // Rebroadcast Queue helpers
    void queueRebroadcast(const aethermesh_MeshPacket& packet, uint32_t transmitTime);
    void cancelRebroadcast(uint32_t senderId, uint32_t packetId);
    
    // Buffer serialization helpers
    bool serializeAndSend(aethermesh_MeshPacket* packet);
};

#endif // MESH_ROUTER_H
