#ifndef MESH_TABLES_H
#define MESH_TABLES_H

// Routing bookkeeping split out of MeshRouter so it can be exercised on the
// native test environment. MeshRouter.cpp cannot be compiled off-device (it
// pulls in Arduino and the radio driver), which left the dedup ring and the
// route promotion/eviction rules untested even though the decisions they call
// into -- meshmath:: -- are fully covered.
//
// Everything here is pure: the clock is passed in as `now` rather than read
// from millis(), so tests can drive rollover and expiry directly.

#include <stdint.h>

#include "MeshMath.h"

namespace meshtables {

struct SeenPacket {
    uint32_t senderId;
    uint32_t packetId;
    uint32_t retryCount;
    uint32_t hopLimit;
    uint32_t timestamp;
};

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

/**
 * Fixed-size dedup ring. Entries are matched on (senderId, packetId) and only
 * count as seen while still inside the TTL; a re-observation refreshes the
 * existing slot instead of consuming a new one, so a chatty sender cannot
 * evict the rest of the table.
 */
template <int CAPACITY>
class SeenCache {
public:
    void reset() {
        for (int i = 0; i < CAPACITY; i++) {
            entries_[i].senderId = 0;
            entries_[i].packetId = 0;
            entries_[i].retryCount = 0;
            entries_[i].hopLimit = 0;
            entries_[i].timestamp = 0;
        }
        index_ = 0;
    }

    bool hasSeen(uint32_t now, uint32_t senderId, uint32_t packetId, uint32_t ttlMs) const {
        return find(now, senderId, packetId, ttlMs) != nullptr;
    }

    /**
     * True when this packet was already seen at an equal or higher retry count.
     * A higher retry_count is a genuine retransmit attempt and must be allowed
     * through so relays can forward the newer attempt.
     */
    bool isDuplicate(uint32_t now, uint32_t senderId, uint32_t packetId,
                     uint32_t retryCount, uint32_t ttlMs) const {
        const SeenPacket* entry = find(now, senderId, packetId, ttlMs);
        if (entry == nullptr) return false;
        return retryCount <= entry->retryCount;
    }

    /**
     * True when this observation rewound hop_limit on the same attempt.
     * Used to drop flood-loop / mutated-TTL copies without re-relaying.
     */
    bool isHopInflation(uint32_t now, uint32_t senderId, uint32_t packetId,
                        uint32_t hopLimit, uint32_t retryCount, uint32_t ttlMs) const {
        const SeenPacket* entry = find(now, senderId, packetId, ttlMs);
        if (entry == nullptr) return false;
        return meshmath::isHopLimitInflation(entry->hopLimit, hopLimit,
                                             entry->retryCount, retryCount);
    }

    void markSeen(uint32_t now, uint32_t senderId, uint32_t packetId,
                  uint32_t retryCount, uint32_t hopLimit = 0) {
        // Match without a freshness check: a stale slot for this same packet is
        // reused rather than leaving a duplicate row behind.
        for (int i = 0; i < CAPACITY; i++) {
            if (entries_[i].senderId == senderId && entries_[i].packetId == packetId) {
                entries_[i].retryCount = retryCount;
                entries_[i].hopLimit = hopLimit;
                entries_[i].timestamp = now;
                return;
            }
        }
        entries_[index_].senderId = senderId;
        entries_[index_].packetId = packetId;
        entries_[index_].retryCount = retryCount;
        entries_[index_].hopLimit = hopLimit;
        entries_[index_].timestamp = now;
        index_ = (uint8_t)((index_ + 1) % CAPACITY);
    }

    /**
     * On a same-attempt duplicate, remember the highest remaining hop_limit
     * (shortest path observed) without treating it as a new delivery.
     */
    void noteBestHop(uint32_t now, uint32_t senderId, uint32_t packetId,
                     uint32_t retryCount, uint32_t hopLimit, uint32_t ttlMs) {
        for (int i = 0; i < CAPACITY; i++) {
            if (entries_[i].senderId != senderId || entries_[i].packetId != packetId) continue;
            if (!meshmath::seenEntryIsFresh(now, entries_[i].timestamp, ttlMs)) continue;
            if (retryCount == entries_[i].retryCount && hopLimit > entries_[i].hopLimit) {
                entries_[i].hopLimit = hopLimit;
            }
            entries_[i].timestamp = now;
            return;
        }
    }

    /** Test/diag helper: current stored hop_limit, or 0 if absent. */
    uint32_t storedHopLimit(uint32_t now, uint32_t senderId, uint32_t packetId,
                            uint32_t ttlMs) const {
        const SeenPacket* entry = find(now, senderId, packetId, ttlMs);
        return entry ? entry->hopLimit : 0;
    }

    static int capacity() { return CAPACITY; }

private:
    const SeenPacket* find(uint32_t now, uint32_t senderId, uint32_t packetId,
                           uint32_t ttlMs) const {
        for (int i = 0; i < CAPACITY; i++) {
            if (entries_[i].senderId == senderId && entries_[i].packetId == packetId &&
                meshmath::seenEntryIsFresh(now, entries_[i].timestamp, ttlMs)) {
                return &entries_[i];
            }
        }
        return nullptr;
    }

    SeenPacket entries_[CAPACITY];
    uint8_t index_;
};

/** What a route observation did to an existing row. */
enum RouteUpdate {
    ROUTE_NO_CHANGE = 0,
    ROUTE_REFRESHED_PRIMARY,  // same next hop; metric smoothed, timestamp bumped
    ROUTE_PROMOTED,           // new next hop installed; previous primary demoted to backup
    ROUTE_BACKUP_INSTALLED    // kept the primary, recorded a better/fresher alternate
};

/**
 * Fold one observation of `targetId via nextHopId at metric` into an existing
 * route row. Pure bookkeeping -- logging, pending-packet retargeting and
 * counters stay with the caller, keyed off the returned outcome.
 *
 * ROUTE_PROMOTED always implies the next hop changed: an identical next hop is
 * handled by the ROUTE_REFRESHED_PRIMARY path before replacement is considered.
 */
inline RouteUpdate applyRouteObservation(RouteEntry& entry, uint32_t nextHopId, uint8_t metric,
                                         uint32_t now, uint32_t routeTimeoutMs,
                                         uint32_t softAgeMs) {
    if (entry.nextHopId == nextHopId) {
        entry.metric = meshmath::smoothedRouteMetric(entry.metric, metric);
        entry.timestamp = now;
        return ROUTE_REFRESHED_PRIMARY;
    }

    // Hearing the known backup again keeps it fresh whatever happens below.
    if (entry.hasBackup && entry.backupNextHopId == nextHopId) {
        entry.backupMetric = meshmath::smoothedRouteMetric(entry.backupMetric, metric);
        entry.backupTimestamp = now;
    }

    if (meshmath::shouldReplaceRoute(entry.nextHopId, entry.metric, now - entry.timestamp,
                                     nextHopId, metric, routeTimeoutMs, softAgeMs)) {
        entry.backupNextHopId = entry.nextHopId;
        entry.backupMetric = entry.metric;
        entry.backupTimestamp = entry.timestamp;
        entry.hasBackup = true;
        entry.nextHopId = nextHopId;
        entry.metric = metric;
        entry.timestamp = now;
        return ROUTE_PROMOTED;
    }

    if (!entry.hasBackup ||
        !meshmath::backupRouteIsUsable(now, entry.backupTimestamp, routeTimeoutMs) ||
        metric + 2u < meshmath::agedRouteMetric(entry.backupMetric,
                                                now - entry.backupTimestamp, softAgeMs)) {
        entry.backupNextHopId = nextHopId;
        entry.backupMetric = metric;
        entry.backupTimestamp = now;
        entry.hasBackup = true;
        return ROUTE_BACKUP_INSTALLED;
    }

    return ROUTE_NO_CHANGE;
}

/**
 * Index of the least recently refreshed row, chosen by elapsed age rather than
 * by raw timestamp order. Comparing millis() values directly picks the wrong
 * victim once the 49.7-day counter wraps -- a freshly stamped post-rollover row
 * has a small value and looks like the oldest entry in the table. Nodes left in
 * the field long enough to wrap would start evicting their newest routes.
 */
inline int oldestRouteIndex(const RouteEntry* entries, int count, uint32_t now) {
    if (count <= 0) return 0;
    int oldest = 0;
    uint32_t oldestAge = (uint32_t)(now - entries[0].timestamp);
    for (int i = 1; i < count; i++) {
        const uint32_t age = (uint32_t)(now - entries[i].timestamp);
        if (age > oldestAge) {
            oldestAge = age;
            oldest = i;
        }
    }
    return oldest;
}

/**
 * Reply hop budget learned per sender from its most recent packet. Replies
 * built later (queued pongs, config results verified after the control key is
 * ready) no longer have the request in hand, so the budget is kept by node id.
 * Unknown senders fall back to meshmath::replyHopLimit's legacy rule.
 */
class ReplyHopTable {
public:
    static constexpr int CAPACITY = 32;

    ReplyHopTable() { clear(); }

    void clear() {
        for (int i = 0; i < CAPACITY; i++) entries_[i] = Entry{0, 0, 0};
    }

    void observe(uint32_t now, uint32_t nodeId, uint8_t replyHopLimit) {
        if (nodeId == 0 || replyHopLimit == 0) return;
        int slot = -1;
        for (int i = 0; i < CAPACITY; i++) {
            if (entries_[i].nodeId == nodeId) {
                slot = i;
                break;
            }
            if (slot < 0 && entries_[i].nodeId == 0) slot = i;
        }
        if (slot < 0) {
            // Evict the least recently observed sender (rollover-safe ages).
            uint32_t oldestAge = 0;
            slot = 0;
            for (int i = 0; i < CAPACITY; i++) {
                const uint32_t age = (uint32_t)(now - entries_[i].timestamp);
                if (age >= oldestAge) {
                    oldestAge = age;
                    slot = i;
                }
            }
        }
        entries_[slot] = Entry{nodeId, replyHopLimit, now};
    }

    /** Learned reply hop limit for nodeId, or 0 when never heard. */
    uint8_t lookup(uint32_t nodeId) const {
        if (nodeId == 0) return 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (entries_[i].nodeId == nodeId) return entries_[i].replyHopLimit;
        }
        return 0;
    }

private:
    struct Entry {
        uint32_t nodeId;
        uint8_t replyHopLimit;
        uint32_t timestamp;
    };
    Entry entries_[CAPACITY];
};

} // namespace meshtables

#endif
