#pragma once

#include <stdint.h>
#include <string.h>

#include "IdentityPolicy.h"

// Which public key belongs to which node, remembered across announcements.
// This is the "first use" half of trust on first use: once a node id has a key
// here, a different key for that id is a question for the user, not an update.
//
// Entries are never evicted by age — a key that ages out would silently become
// "first use" again, which is the exact hole TOFU exists to close. When the
// table is full the least recently *seen* entry gives way, and that eviction is
// reported so the caller can say so rather than quietly forgetting someone.
namespace identity {

struct Peer {
    uint32_t nodeId;
    uint8_t xPublic[PUBLIC_KEY_BYTES];
    uint8_t edPublic[PUBLIC_KEY_BYTES];
    uint32_t epoch;
    uint32_t lastSeenMs;
    bool unresolvedChange;  // a conflicting key arrived and the user has not ruled
};

class PeerTable {
public:
    static constexpr int CAPACITY = 24;

    void clear() {
        count_ = 0;
        evictions_ = 0;
        memset(peers_, 0, sizeof(peers_));
    }

    int size() const { return count_; }
    uint32_t evictions() const { return evictions_; }

    const Peer* lookup(uint32_t nodeId) const {
        for (int i = 0; i < count_; i++) {
            if (peers_[i].nodeId == nodeId) return &peers_[i];
        }
        return nullptr;
    }

    // Applies an announcement that has already had its signature checked.
    // Returns what the caller should tell the user about; the table only stores
    // what identity::shouldStore allows.
    Trust observe(uint32_t nowMs, uint32_t nodeId, const uint8_t* xPublic,
                  const uint8_t* edPublic, uint32_t epoch, bool signatureOk) {
        if (nodeId == 0) return Trust::Invalid;
        Peer* existing = mutableLookup(nodeId);
        const Trust decision =
            classify(signatureOk, existing != nullptr,
                     existing != nullptr ? existing->edPublic : nullptr,
                     existing != nullptr ? existing->epoch : 0, xPublic, edPublic, epoch);

        if (decision == Trust::Invalid) return decision;

        if (existing != nullptr) {
            existing->lastSeenMs = nowMs;
            if (decision == Trust::Changed) {
                // Keep the key we already trust; remember that someone disputes it.
                existing->unresolvedChange = true;
                return decision;
            }
            if (shouldStore(decision)) {
                memcpy(existing->xPublic, xPublic, PUBLIC_KEY_BYTES);
                memcpy(existing->edPublic, edPublic, PUBLIC_KEY_BYTES);
                existing->epoch = epoch;
                existing->unresolvedChange = (decision == Trust::Rotated);
            }
            return decision;
        }

        if (!shouldStore(decision)) return decision;
        Peer* slot = allocate(nowMs);
        slot->nodeId = nodeId;
        memcpy(slot->xPublic, xPublic, PUBLIC_KEY_BYTES);
        memcpy(slot->edPublic, edPublic, PUBLIC_KEY_BYTES);
        slot->epoch = epoch;
        slot->lastSeenMs = nowMs;
        slot->unresolvedChange = false;
        return decision;
    }

    // The user (or the node owner at the console) accepting a disputed key.
    bool acceptChange(uint32_t nowMs, uint32_t nodeId, const uint8_t* xPublic,
                      const uint8_t* edPublic, uint32_t epoch) {
        Peer* existing = mutableLookup(nodeId);
        if (existing == nullptr) return false;
        if (!keyIsUsable(xPublic, PUBLIC_KEY_BYTES) || !keyIsUsable(edPublic, PUBLIC_KEY_BYTES)) {
            return false;
        }
        memcpy(existing->xPublic, xPublic, PUBLIC_KEY_BYTES);
        memcpy(existing->edPublic, edPublic, PUBLIC_KEY_BYTES);
        existing->epoch = epoch;
        existing->lastSeenMs = nowMs;
        existing->unresolvedChange = false;
        return true;
    }

    // A peer is only sealed to while its key is undisputed.
    bool mayEncrypt(uint32_t nodeId) const {
        const Peer* peer = lookup(nodeId);
        return peer != nullptr && !peer->unresolvedChange &&
               keyIsUsable(peer->xPublic, PUBLIC_KEY_BYTES);
    }

    const Peer* at(int index) const {
        if (index < 0 || index >= count_) return nullptr;
        return &peers_[index];
    }

private:
    Peer* mutableLookup(uint32_t nodeId) {
        for (int i = 0; i < count_; i++) {
            if (peers_[i].nodeId == nodeId) return &peers_[i];
        }
        return nullptr;
    }

    Peer* allocate(uint32_t nowMs) {
        if (count_ < CAPACITY) return &peers_[count_++];
        int oldest = 0;
        for (int i = 1; i < CAPACITY; i++) {
            if ((uint32_t)(nowMs - peers_[i].lastSeenMs) >
                (uint32_t)(nowMs - peers_[oldest].lastSeenMs)) {
                oldest = i;
            }
        }
        evictions_++;
        return &peers_[oldest];
    }

    Peer peers_[CAPACITY] = {};
    int count_ = 0;
    uint32_t evictions_ = 0;
};

}  // namespace identity
