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
// Soft-age before metric decay / demotion (Phase 3). Hard invalidate stays at
// ROUTE_TIMEOUT_MS; stale primaries lose preference sooner.
#define ROUTE_SOFT_AGE_MS 200000UL // ~3.3 minutes
#define MAX_PENDING_REBROADCASTS 8
#define MAX_PENDING_ACKS 4
#define MAX_PENDING_PONGS 4
#define MAX_CHANNEL_RECEIPTS 4
#define MAX_CHANNEL_HEARERS 16
#define CHANNEL_RECEIPT_TTL_MS 120000UL
#define SEEN_PACKET_TIMEOUT_MS 120000
// Hard cap on locally-originated ACK slots in the shared TX queue. MeshCore /
// Meshtastic keep control ACKs cheap and bounded; AetherMesh previously queued
// primary+recovery per hearer and starved channel/DM text in the 8-deep queue.
#define MAX_PENDING_LOCAL_ACKS 2
// Direct-range (_D) PONGs: a few CAD-skipped copies after each PING.
// startTransmit() returns before TX completes — spacing MUST include the
// expected airtime (SF11/BW125 ≈ 1.4s) or copies collide with themselves
// (radio-busy / range_pong_tx_failures storm) and never reach the pinger.
// Window must fit MAX_ATTEMPTS spaced copies at SF12.
#define PONG_RETRY_WINDOW_MS 20000
#define PONG_RESEND_INTERVAL_MS 2500
#define DIRECT_PONG_MAX_ATTEMPTS 3
#define DIRECT_PONG_RESEND_MS 400
#define DIRECT_PONG_INITIAL_DELAY_MS 350
#define ACK_MAX_RETRIES 3
// Locally-originated ACKs share the rebroadcast queue; allow longer CAD/busy
// retries at high SF than flood relays (default 5s). Single ACK attempt only
// (no recovery wave) — TTL covers slotted delay + a few CAD retries.
#define ACK_QUEUE_TTL_MS 12000UL
#define STORE_FORWARD_TTL_MS 1800000UL
#define STORE_FORWARD_RETRY_MS 60000UL
// Per-target / global discovery gaps are SF-scaled via MeshMath
// (routeDiscoveryCooldownMs / routeDiscoveryGlobalGapMs). These macros remain
// as documentation defaults matching SF≤9.
#define ROUTE_DISCOVERY_COOLDOWN_MS 8000
#define FLOOD_DEST_COOLDOWN_MS 6000
#define ROUTE_FAIL_FLOOD_MS 45000
#define MAX_ROUTE_FAILURES 8
#define PROXY_ROUTE_MAX_AGE_MS 60000
// Directed unicast stuck on CAD: abandon next hop after this many busy fails.
#define EARLY_CAD_FAIL_THRESHOLD 2
// Phase 6: small per-neighbor ACK SNR quality table (reuse route-scale budget).
#define MAX_NEIGHBOR_QUALITY 12
#define NEIGHBOR_QUALITY_TIMEOUT_MS 600000UL
#define AIRTIME_CONGESTION_WINDOW_MS 10000UL
// Hard cap so a forgotten range-test START cannot soft-stall a leave-behind node.
#define QUIET_MODE_MAX_MS (5UL * 60UL * 1000UL)

struct RouteEntry {
    uint32_t targetId;
    uint32_t nextHopId;
    uint8_t metric;
    uint32_t timestamp;
    uint32_t backupNextHopId;
    uint8_t backupMetric;
    uint32_t backupTimestamp;
    bool hasBackup;
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
    uint32_t queuedAtTime;
    uint8_t priority;
    bool active;
};

// A locally-originated want_ack packet awaiting acknowledgment.
// Retransmitted up to ACK_MAX_RETRIES times, then store-and-forward.
struct PendingAck {
    aethermesh_MeshPacket packet;
    uint32_t nextRetryTime;
    uint32_t expiresAt;
    uint32_t trackedAt;
    uint8_t retriesLeft;
    uint8_t cadFailStreak;
    bool earlyBackupDone;
    bool earlyFloodDone;
    bool stored;
    // Phase 5: one adaptive wake per store-forward wait cycle when a route
    // reappears (avoids re-arming forever on telemetry spam).
    bool storedWakeDone;
    bool active;
};

// Locally-originated channel/broadcast text awaiting optional hearer ACKs.
// Unlike PendingAck, this does not retransmit on timeout — it only aggregates
// unique ACK senders and reports HEARD counts to the companion app.
struct ChannelReceiptTrack {
    uint32_t packetId;
    uint32_t hearers[MAX_CHANNEL_HEARERS];
    uint8_t heardCount;
    uint32_t expiresAt;
    bool active;
};

struct PendingPongReply {
    uint32_t recipientId;
    char content[32];
    char pingId[11];
    uint8_t hopLimit;
    uint32_t sendAtMs;
    uint32_t firstQueuedMs;
    uint32_t sendCount;
    bool directOnly;
    bool active;
};

struct RouteDiscoveryState {
    uint32_t targetId;
    uint32_t lastRequestMs;
};

// Recent primary-route failures: allow flood fallback and rediscovery.
struct RouteFailure {
    uint32_t targetId;
    uint32_t failedAtMs;
    bool active;
};

struct FloodDestState {
    uint32_t targetId;
    uint32_t lastFloodMs;
};

// Phase 6: smoothed ACK hop-cost per immediate neighbor (next hop).
struct NeighborQuality {
    uint32_t neighborId;
    uint8_t hopCost;
    uint8_t samples;
    uint32_t updatedAt;
    bool active;
};

class MeshRouter {
public:
    MeshRouter(RadioManager* radioMgr);
    void init(uint32_t localId);
    void loop();

    // 0 = Client (no LoRa relay — MeshCore-style companion)
    // 1 = Router (relay + BLE/UI)
    // 2 = Low-Power Repeater (relay, BLE off)
    void setNodeRole(uint32_t role);
    uint32_t getNodeRole() const { return nodeRole; }
    bool canRelay() const { return nodeRole != 0; }

    // Default hop_limit for locally originated packets (1–8).
    void setDefaultHopLimit(uint8_t hops);
    uint8_t getDefaultHopLimit() const { return defaultHopLimit; }

    // Rebroadcast pace multiplier ×100 (50–200). Affects flood/relay stagger.
    void setRebroadcastTxdelayX100(uint32_t x100);
    uint32_t getRebroadcastTxdelayX100() const { return rebroadcastTxdelayX100; }
    
    // Send message interfaces
    bool sendText(uint32_t recipientId, const char* text);
    // Unicast text without want_ack / retransmit tracking (used for range-test PONG replies).
    bool sendTextNoAck(uint32_t recipientId, const char* text, bool urgent = false, uint8_t hopLimit = DEFAULT_HOP_LIMIT);
    // lat/lon must already be privacy-blurred by the caller when positionPrecision > 0
    bool sendTelemetry(uint32_t recipientId, uint8_t battery, float lat, float lon, const char* nodeName, bool charging = false, float voltage = 0.0f, uint32_t positionPrecision = 0, uint32_t loraSf = 0, uint32_t region = 0);
    
    // Packet processing entrypoint (called by RadioManager receive callback)
    void processIncomingPacket(uint8_t* data, size_t len, float rssi, float snr);
    
    // Callback registers
    void onReceivedTextMessage(void (*callback)(uint32_t senderId, const char* text));
    void onReceivedTelemetry(void (*callback)(uint32_t senderId, uint8_t battery, float lat, float lon));
    void onReceivedConfig(void (*callback)(const aethermesh_MeshPacket& packet));
    void onDeliveryStatus(void (*callback)(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr, uint32_t heardCount, uint32_t fromNodeId));
    
    // Routing Table Diagnostics
    uint32_t getLocalId() { return localNodeId; }
    void printRoutingTable();
    void getDiagnostics(aethermesh_MeshDiagnostics& diagnostics) const;

    // Quiet mesh during phone-driven direct range tests: pause store-forward
    // retries and expose session counters via MeshDiagnostics.
    // Auto-clears after QUIET_MODE_MAX_MS; callers should also clear on BLE disconnect.
    void setQuietMode(bool enabled);
    bool isQuietMode() const { return quietMode; }
    void resetRangeTestCounters();

    // True if this (sender, packet) pair is already in the dedup cache.
    // Lets callers (e.g. the BLE forward path) skip mesh rebroadcast duplicates.
    bool hasSeen(uint32_t senderId, uint32_t packetId) { return hasSeenPacketId(senderId, packetId); }

    // Raw packet transmit helper. urgent=true skips CAD (range-test PING/PONG).
    bool sendRawPacket(aethermesh_MeshPacket* packet, bool urgent = false);

private:
    RadioManager* radio;
    uint32_t localNodeId;
    uint32_t packetSequenceCounter;
    uint64_t sessionId;
    uint32_t nodeRole;
    uint8_t defaultHopLimit;
    uint32_t rebroadcastTxdelayX100;
    
    // Data structures
    RouteEntry routingTable[MAX_ROUTE_TABLE_ENTRIES];
    SeenPacket seenPackets[MAX_SEEN_PACKETS_CACHE];
    uint8_t seenPacketsIndex;
    PendingRebroadcast pendingRebroadcasts[MAX_PENDING_REBROADCASTS];
    PendingAck pendingAcks[MAX_PENDING_ACKS];
    ChannelReceiptTrack channelReceipts[MAX_CHANNEL_RECEIPTS];
    PendingPongReply pendingPongs[MAX_PENDING_PONGS];
    RouteDiscoveryState routeDiscoveries[6];
    RouteFailure routeFailures[MAX_ROUTE_FAILURES];
    FloodDestState floodDests[6];
    NeighborQuality neighborQuality[MAX_NEIGHBOR_QUALITY];
    uint32_t lastAnyDiscoveryMs; // Phase 4: global RREQ pacing across targets
    uint32_t relayedPackets;
    uint32_t retryPackets;
    uint32_t ackedPackets;
    uint32_t ackTimeouts;
    uint32_t duplicatePackets;
    uint32_t queueDrops;
    uint32_t routeChanges;
    // Smart-routing counters (BLE MeshDiagnostics + Serial; never LoRa).
    uint32_t directedRelays;
    uint32_t suppressRelays;
    uint32_t floodUnicasts;
    uint32_t rreqSent;
    uint32_t earlyRepairs;
    uint32_t rangePingsRx;
    uint32_t rangePongsQueued;
    uint32_t rangePongsSent;
    uint32_t rangePongTxFailures;
    bool quietMode;
    uint32_t quietModeStartedAt;
    
    // Telemetry/text callbacks
    void (*textCallback)(uint32_t senderId, const char* text);
    void (*telemetryCallback)(uint32_t senderId, uint8_t battery, float lat, float lon);
    void (*configCallback)(const aethermesh_MeshPacket& packet);
    void (*deliveryStatusCallback)(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr, uint32_t heardCount, uint32_t fromNodeId);
    
    // Private Helpers
    void addRoute(uint32_t targetId, uint32_t nextHopId, uint8_t metric);
    RouteEntry* getRoute(uint32_t targetId);
    void invalidateRoute(uint32_t targetId);
    void markRouteFailed(uint32_t targetId);
    bool isRouteFailedRecently(uint32_t targetId) const;
    // True when nodeId is a recently heard 1-hop neighbor (nextHop == self).
    bool isLiveNeighbor(uint32_t nodeId) const;
    void learnReverseRoute(uint32_t originId, uint32_t viaHopId, uint8_t lastHopCost);
    void applyDirectedNextHop(aethermesh_MeshPacket* packet, bool preferReturnPath = false);
    bool noteFloodDest(uint32_t targetId);
    // Phase 3: demote soft-stale primary to backup; early backup/flood repair.
    void maybeSoftDemoteRoute(RouteEntry* route);
    bool tryEarlyPathRepair(PendingAck& pending, uint32_t now);
    // Phase 4: retarget pending directed unicasts when a better next hop lands.
    void refreshPendingDirectedNextHop(uint32_t targetId, uint32_t newNextHop);
    // Phase 5: accelerate STORED want_ack retries when a route reappears.
    void wakeStoredPendingForTarget(uint32_t targetId);
    // Phase 5: refresh primary + soft-nudge backup on DELIVERED.
    void reinforceRouteOnDelivery(uint32_t destId, float ackSnr);
    // Phase 6: congestion score from queue depth + recent airtime.
    uint8_t currentCongestionScore() const;
    uint8_t countActiveRebroadcasts() const;
    uint8_t countActivePendingAcks() const;
    // Phase 6: per-neighbor ACK SNR → route metric bias.
    void noteNeighborAckQuality(uint32_t neighborId, float ackSnr);
    uint8_t neighborLinkCost(uint32_t neighborId) const;
    uint8_t metricWithNeighborQuality(uint32_t nextHopId, uint8_t metric) const;
    // Phase 6: after invalidate/promote — retarget pending + one rediscovery.
    void retargetPendingAfterRelayLoss(uint32_t targetId, bool promotedBackup,
                                       uint32_t newNextHop);
    
    bool hasSeenPacketId(uint32_t senderId, uint32_t packetId);
    bool isDuplicatePacket(uint32_t senderId, uint32_t packetId, uint32_t retryCount);
    void markPacketAsSeen(uint32_t senderId, uint32_t packetId, uint32_t retryCount);
    
    bool handleRouteRequest(uint32_t senderId, uint32_t prevHopId, const aethermesh_RouteDiscovery& rreq);
    void handleRouteReply(uint32_t senderId, uint32_t prevHopId, const aethermesh_RouteDiscovery& rrep);
    // Install forward route to RREP target via the transmitting hop (path splice).
    void installReplyPath(uint32_t targetId, uint32_t viaHopId, uint8_t metric);
    
    bool sendRouteRequest(uint32_t targetId);
    void sendRouteReply(uint32_t recipientId, uint32_t targetId, uint8_t metric);

    void appendTraceHop(aethermesh_TraceRoute& trace, bool returning, float rssi, float snr);
    uint8_t traceMetric(const aethermesh_TraceRoute& trace, bool returning) const;
    void sendTraceResponse(const aethermesh_TraceRoute& request);
    
    // Rebroadcast Queue helpers
    void queueRebroadcast(const aethermesh_MeshPacket& packet, uint32_t transmitTime);
    uint8_t packetPriority(const aethermesh_MeshPacket& packet) const;
    bool isLocalOriginatedText(const aethermesh_MeshPacket& packet) const;
    // Push pending ACK deadlines so freshly queued local text can claim the radio.
    void deferQueuedAcks(uint32_t deferMs);
    // When immediate LoRa TX fails (busy/CAD), still guarantee local text gets a slot.
    void ensureLocalTextQueued(const aethermesh_MeshPacket& packet, uint32_t transmitTime);
    void cancelRebroadcast(uint32_t senderId, uint32_t packetId, uint32_t retryCount = UINT32_MAX);

    // ACK/retransmit helpers
    // Channel/broadcast path never schedules a recovery ACK (one attempt).
    void sendAck(uint32_t recipientId, uint32_t ackedPacketId, float rssi, float snr);
    void trackForAck(const aethermesh_MeshPacket& packet);
    void trackChannelReceipt(uint32_t packetId);
    void noteChannelHearing(uint32_t ackedPacketId, uint32_t fromNodeId, float ackRssi = 0.0f, float ackSnr = 0.0f);
    // Drop farthest local ACK so a new one can claim a slot (bounded storm).
    bool evictFarthestLocalAck();
    uint8_t countActiveLocalAcks() const;
    void clearPendingAck(uint32_t ackedPacketId, float ackRssi = 0.0f, float ackSnr = 0.0f);
    void emitDeliveryStatus(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi = 0.0f, float ackSnr = 0.0f, uint32_t heardCount = 0, uint32_t fromNodeId = 0);

    void maybeQueuePongForPingText(const aethermesh_MeshPacket& packet, float rssi, float snr);
    void queuePongReply(uint32_t recipientId, const char* pingId, float rssi, float snr, bool directOnly);
    void drainPendingPongReplies();

    // True when a unicast with no route table entry should still be flooded
    // by relay roles (DM / config discovery — MeshCore flood-then-direct).
    bool shouldFloodUnknownUnicast(const aethermesh_MeshPacket& packet) const;
    
    // Buffer serialization helpers
    bool serializeAndSend(aethermesh_MeshPacket* packet, bool urgent = false);
};

#endif // MESH_ROUTER_H
