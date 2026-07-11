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
#define MAX_PENDING_REBROADCASTS 6
#define MAX_PENDING_ACKS 4
#define MAX_PENDING_PONGS 4
#define SEEN_PACKET_TIMEOUT_MS 120000
// Field data: PONGs travel the strong link direction and always delivered on
// the FIRST attempt (~30 successes, zero via retry), while retries of already
// delivered pongs collided with the pinger's next ping. Keep exactly one
// insurance retry, timed to land between 5s-interval pings.
#define PONG_RETRY_WINDOW_MS 4000
#define PONG_RESEND_INTERVAL_MS 2500
#define ACK_MAX_RETRIES 3
#define ROUTE_DISCOVERY_COOLDOWN_MS 5000
#define PROXY_ROUTE_MAX_AGE_MS 60000

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
    uint32_t retryCount;
    uint32_t timestamp;
};

struct PendingRebroadcast {
    aethermesh_MeshPacket packet;
    uint32_t transmitTime;
    bool active;
};

// A locally-originated want_ack packet awaiting acknowledgment.
// Retransmitted up to ACK_MAX_RETRIES times, then dropped.
struct PendingAck {
    aethermesh_MeshPacket packet;
    uint32_t nextRetryTime;
    uint8_t retriesLeft;
    bool active;
};

struct PendingPongReply {
    uint32_t recipientId;
    char content[32];
    uint8_t hopLimit;
    uint32_t sendAtMs;
    uint32_t firstQueuedMs;
    uint32_t sendCount;
    bool active;
};

struct RouteDiscoveryState {
    uint32_t targetId;
    uint32_t lastRequestMs;
};

class MeshRouter {
public:
    MeshRouter(RadioManager* radioMgr);
    void init(uint32_t localId);
    void loop();
    
    // Send message interfaces
    bool sendText(uint32_t recipientId, const char* text);
    // Unicast text without want_ack / retransmit tracking (used for range-test PONG replies).
    bool sendTextNoAck(uint32_t recipientId, const char* text, bool urgent = false, uint8_t hopLimit = DEFAULT_HOP_LIMIT);
    // lat/lon must already be privacy-blurred by the caller when positionPrecision > 0
    bool sendTelemetry(uint32_t recipientId, uint8_t battery, float lat, float lon, const char* nodeName, bool charging = false, float voltage = 0.0f, uint32_t positionPrecision = 0);
    
    // Packet processing entrypoint (called by RadioManager receive callback)
    void processIncomingPacket(uint8_t* data, size_t len, float rssi, float snr);
    
    // Callback registers
    void onReceivedTextMessage(void (*callback)(uint32_t senderId, const char* text));
    void onReceivedTelemetry(void (*callback)(uint32_t senderId, uint8_t battery, float lat, float lon));
    void onReceivedConfig(void (*callback)(uint32_t senderId, const aethermesh_NodeConfig& config));
    void onDeliveryStatus(void (*callback)(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr));
    
    // Routing Table Diagnostics
    uint32_t getLocalId() { return localNodeId; }
    void printRoutingTable();

    // True if this (sender, packet) pair is already in the dedup cache.
    // Lets callers (e.g. the BLE forward path) skip mesh rebroadcast duplicates.
    bool hasSeen(uint32_t senderId, uint32_t packetId) { return hasSeenPacketId(senderId, packetId); }

    // Raw packet transmit helper. urgent=true skips CAD (range-test PING/PONG).
    bool sendRawPacket(aethermesh_MeshPacket* packet, bool urgent = false);

private:
    RadioManager* radio;
    uint32_t localNodeId;
    uint32_t packetSequenceCounter;
    
    // Data structures
    RouteEntry routingTable[MAX_ROUTE_TABLE_ENTRIES];
    SeenPacket seenPackets[MAX_SEEN_PACKETS_CACHE];
    uint8_t seenPacketsIndex;
    PendingRebroadcast pendingRebroadcasts[MAX_PENDING_REBROADCASTS];
    PendingAck pendingAcks[MAX_PENDING_ACKS];
    PendingPongReply pendingPongs[MAX_PENDING_PONGS];
    RouteDiscoveryState routeDiscoveries[6];
    
    // Telemetry/text callbacks
    void (*textCallback)(uint32_t senderId, const char* text);
    void (*telemetryCallback)(uint32_t senderId, uint8_t battery, float lat, float lon);
    void (*configCallback)(uint32_t senderId, const aethermesh_NodeConfig& config);
    void (*deliveryStatusCallback)(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr);
    
    // Private Helpers
    void addRoute(uint32_t targetId, uint32_t nextHopId, uint8_t metric);
    RouteEntry* getRoute(uint32_t targetId);
    void invalidateRoute(uint32_t targetId);
    
    bool hasSeenPacketId(uint32_t senderId, uint32_t packetId);
    bool isDuplicatePacket(uint32_t senderId, uint32_t packetId, uint32_t retryCount);
    void markPacketAsSeen(uint32_t senderId, uint32_t packetId, uint32_t retryCount);
    
    bool handleRouteRequest(uint32_t senderId, uint32_t prevHopId, const aethermesh_RouteDiscovery& rreq);
    void handleRouteReply(uint32_t senderId, uint32_t prevHopId, const aethermesh_RouteDiscovery& rrep);
    
    void sendRouteRequest(uint32_t targetId);
    void sendRouteReply(uint32_t recipientId, uint32_t targetId, uint8_t metric);

    void appendTraceHop(aethermesh_TraceRoute& trace, bool returning, float rssi, float snr);
    uint8_t traceMetric(const aethermesh_TraceRoute& trace, bool returning) const;
    void sendTraceResponse(const aethermesh_TraceRoute& request);
    
    // Rebroadcast Queue helpers
    void queueRebroadcast(const aethermesh_MeshPacket& packet, uint32_t transmitTime);
    void cancelRebroadcast(uint32_t senderId, uint32_t packetId, uint32_t retryCount = UINT32_MAX);

    // ACK/retransmit helpers
    void sendAck(uint32_t recipientId, uint32_t ackedPacketId, float rssi, float snr);
    void trackForAck(const aethermesh_MeshPacket& packet);
    void clearPendingAck(uint32_t ackedPacketId, float ackRssi = 0.0f, float ackSnr = 0.0f);
    void emitDeliveryStatus(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi = 0.0f, float ackSnr = 0.0f);

    void maybeQueuePongForPingText(const aethermesh_MeshPacket& packet, float rssi, float snr);
    void queuePongReply(uint32_t recipientId, const char* pingId, float rssi, float snr, bool directOnly);
    void drainPendingPongReplies();
    
    // Buffer serialization helpers
    bool serializeAndSend(aethermesh_MeshPacket* packet, bool urgent = false);
};

#endif // MESH_ROUTER_H
