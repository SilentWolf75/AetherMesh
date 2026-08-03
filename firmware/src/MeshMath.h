#pragma once

// Pure, dependency-free mesh math. Kept out of MeshRouter.cpp so it can be
// unit-tested on the host (see firmware/test/test_meshmath). No Arduino types.

#include <stdint.h>
#include <math.h>

namespace meshmath {

inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

// SNR-weighted hop cost: better links (higher SNR) cost less. SNR is clamped to
// [-20, 10] dB; cost ranges 1 (SNR>=10) to ~25 (SNR<=-20). Used as the routing
// metric so the router prefers stronger paths.
inline uint8_t hopCost(float snr) {
    float s = clampf(snr, -20.0f, 10.0f);
    return (uint8_t)(1.0f + (10.0f - s) * (24.0f / 30.0f));
}

// SNR-weighted rebroadcast backoff (ms): weaker links wait longer before
// relaying a broadcast, so the node that heard it best rebroadcasts first.
// Ranges 500ms (SNR>=10) to 2000ms (SNR<=-20).
// txdelayX100 scales the result (50=0.5x … 200=2.0x); 0 or 100 = 1.0x.
inline uint32_t rebroadcastDelayMs(float snr, uint32_t txdelayX100 = 100) {
    float s = clampf(snr, -20.0f, 10.0f);
    float base = 500.0f + (10.0f - s) * (1500.0f / 30.0f);
    uint32_t factor = txdelayX100 == 0 ? 100 : txdelayX100;
    if (factor < 50) factor = 50;
    if (factor > 200) factor = 200;
    return (uint32_t)(base * (float)factor / 100.0f);
}

// Inflate a route metric as it ages past softAgeMs so fresher / better-SNR
// candidates win before the hard route timeout. +2 cost per soft-age step.
inline uint8_t agedRouteMetric(uint8_t metric, uint32_t ageMs, uint32_t softAgeMs) {
    if (softAgeMs == 0 || ageMs <= softAgeMs) return metric;
    uint32_t steps = (ageMs - softAgeMs) / softAgeMs;
    if (steps > 16u) steps = 16u;
    uint16_t aged = (uint16_t)metric + (uint16_t)(steps * 2u);
    return aged > 255u ? 255u : (uint8_t)aged;
}

inline bool shouldReplaceRoute(uint32_t currentNextHop, uint8_t currentMetric,
                               uint32_t currentAgeMs, uint32_t candidateNextHop,
                               uint8_t candidateMetric, uint32_t routeTimeoutMs,
                               uint32_t softAgeMs = 0) {
    if (candidateNextHop == currentNextHop) return true;
    uint8_t curEffective = agedRouteMetric(currentMetric, currentAgeMs, softAgeMs);
    if (candidateMetric < curEffective) return true;
    // Nearly-equal fresh path beats a soft-stale primary (prefer fresher SNR).
    if (softAgeMs > 0 && currentAgeMs > softAgeMs &&
        candidateMetric <= curEffective + 1u) {
        return true;
    }
    return currentAgeMs > routeTimeoutMs / 2;
}

// Soft-demote primary→backup before hard invalidate when backup is fresher
// and not materially worse after aging.
inline bool shouldDemoteStalePrimary(uint32_t primaryAgeMs, uint8_t primaryMetric,
                                     uint32_t backupAgeMs, uint8_t backupMetric,
                                     uint32_t softAgeMs, bool hasBackup) {
    if (!hasBackup || softAgeMs == 0) return false;
    if (primaryAgeMs <= softAgeMs) return false;
    // Backup must be meaningfully fresher than the soft-stale primary.
    if (backupAgeMs + softAgeMs / 2u >= primaryAgeMs) return false;
    uint8_t primAged = agedRouteMetric(primaryMetric, primaryAgeMs, softAgeMs);
    uint8_t backAged = agedRouteMetric(backupMetric, backupAgeMs, softAgeMs);
    return backAged <= (uint16_t)primAged + 2u;
}

inline bool proxyRouteIsFresh(uint32_t routeAgeMs, uint32_t maxProxyAgeMs) {
    return routeAgeMs <= maxProxyAgeMs;
}

inline bool seenEntryIsFresh(uint32_t nowMs, uint32_t seenAtMs, uint32_t timeoutMs) {
    return (uint32_t)(nowMs - seenAtMs) <= timeoutMs;
}

// Mix boot entropy with the stable node id so packet sequences start across the
// full uint32 space instead of repeatedly landing in a small 1..10000 window.
inline uint32_t initialPacketSequence(uint32_t nodeId, uint32_t entropy) {
    uint32_t value = entropy ^ nodeId ^ 0x9E3779B9u;
    value ^= value >> 16;
    value *= 0x7FEB352Du;
    value ^= value >> 15;
    value *= 0x846CA68Bu;
    value ^= value >> 16;
    return value == 0 ? 1u : value;
}

// Unicast ACK retry base spacing by SF. Must clear typical reverse-ACK airtime
// at SF11/12 so a retry does not TX over the ACK; SF7–9 stay snappier.
inline uint32_t ackRetryBaseMs(uint8_t sf) {
    if (sf >= 12) return 6000;
    if (sf >= 11) return 4500;
    if (sf >= 10) return 3500;
    if (sf >= 9) return 2800;
    return 2200;
}

inline uint32_t ackRetryDelayMs(uint8_t sf, uint32_t retryCount, uint8_t routeMetric,
                                uint32_t jitterMs) {
    uint32_t multiplier = 1u << (retryCount > 2 ? 2 : retryCount);
    uint32_t routePenalty = (uint32_t)routeMetric * 100u;
    if (routePenalty > 3000u) routePenalty = 3000u;
    uint32_t j = jitterMs > 500u ? 500u : jitterMs;
    return ackRetryBaseMs(sf) * multiplier + routePenalty + j;
}

// Store-and-forward: short SF-scaled pause after a reverse path reappears so
// we do not TX into the same airtime as the observation that woke us.
inline uint32_t storeForwardWakeDelayMs(uint8_t sf, uint32_t jitterMs) {
    uint32_t base;
    if (sf >= 12) {
        base = 800;
    } else if (sf >= 11) {
        base = 500;
    } else if (sf >= 10) {
        base = 350;
    } else {
        base = 200;
    }
    uint32_t j = jitterMs > 400u ? 400u : jitterMs;
    return base + j;
}

inline bool shouldWakeStoredPending(bool isStored, bool storedWakeDone,
                                    bool hasActiveRoute) {
    return isStored && !storedWakeDone && hasActiveRoute;
}

// On DELIVERED: pull a soft-stale backup timestamp toward "half soft-age" so a
// proven topology is not demoted immediately after a successful delivery.
inline uint32_t nudgedSoftAgeTimestamp(uint32_t nowMs, uint32_t timestamp,
                                       uint32_t softAgeMs) {
    if (softAgeMs == 0) return nowMs;
    uint32_t age = (uint32_t)(nowMs - timestamp);
    if (age <= softAgeMs / 2u) return timestamp;
    return nowMs - softAgeMs / 2u;
}

// A failed send usually means another asynchronous LoRa transmission is still
// in flight. Delay the queue item instead of retrying on every main-loop pass.
inline uint32_t radioBusyRetryDelayMs(uint32_t jitterMs) {
    return 120u + (jitterMs > 180u ? 180u : jitterMs);
}

// Multi-hearer channel ACK spacing: deterministic slot from node id so a few
// hearers do not all CAD-collide in the same random window. Slot WIDTH must
// cover typical ACK airtime, but SLOT_COUNT × WIDTH must stay short — a 12×
// 1.8s grid pinned SF11 radios for ~20–45s (insurance + recovery), filled the
// 8-deep rebroadcast queue with ACKs, and dropped channel text / insurance.
// Four slots + modest widths keep collisions rare without deafening the mesh.
// MeshRouter now emits ONE ACK attempt (no recovery wave), prioritizes local
// text above ACKs, caps pending local ACKs, and treats overheard rebroadcasts
// of our own channel text as Meshtastic-style implicit HEARD.
constexpr uint32_t CHANNEL_ACK_SLOT_COUNT = 4;
// Hard caps so SF bumps cannot recreate multi-tens-of-seconds ACK holds.
constexpr uint32_t CHANNEL_ACK_MAX_DELAY_CAP_MS = 4000;
// Insurance only needs to clear the primary ACK window (no recovery wave).
constexpr uint32_t CHANNEL_INSURANCE_DELAY_CAP_MS = 5000;
// Kept for unit tests / alt-slot busy retry; channel path no longer schedules
// a second recovery ACK into the TX queue.
constexpr uint32_t CHANNEL_ACK_RECOVERY_DELAY_CAP_MS = 12000;

inline uint32_t channelAckSlotIndex(uint32_t nodeId) {
    // Murmur-inspired mix — plain (id ^ id>>8) clustered sequential ESP MAC
    // suffixes into the same few buckets.
    uint32_t mixed = nodeId;
    mixed ^= mixed >> 16;
    mixed *= 0x7FEB352Du;
    mixed ^= mixed >> 15;
    mixed *= 0x846CA68Bu;
    mixed ^= mixed >> 16;
    return mixed % CHANNEL_ACK_SLOT_COUNT;
}

// Independent mix for the collision-recovery ACK. Nodes that share a primary
// slot land elsewhere here with high probability (~11/12 for 12 slots).
inline uint32_t channelAckAltSlotIndex(uint32_t nodeId) {
    uint32_t mixed = nodeId * 0x9E3779B1u;
    mixed ^= mixed >> 16;
    mixed *= 0x85EBCA6Bu;
    mixed ^= mixed >> 13;
    return mixed % CHANNEL_ACK_SLOT_COUNT;
}

inline uint32_t channelAckSlotWidthMs(uint8_t sf) {
    // >= typical small-ACK airtime at this SF so adjacent slots do not overlap,
    // but keep the full slot grid short enough that insurance/recovery stay
    // under CHANNEL_*_DELAY_CAP_MS (see CHANNEL_ACK_SLOT_COUNT notes).
    if (sf >= 12) return 1200;
    if (sf >= 11) return 700;
    if (sf >= 10) return 500;
    if (sf >= 9) return 350;
    return 250;
}

inline uint32_t channelAckJitterCapMs(uint8_t sf) {
    if (sf >= 12) return 200;
    if (sf >= 11) return 180;
    if (sf >= 10) return 140;
    if (sf >= 9) return 120;
    return 100;
}

inline uint32_t channelAckBaseDelayMs(uint8_t sf) {
    if (sf >= 12) return 1000;
    if (sf >= 11) return 600;
    if (sf >= 10) return 350;
    if (sf >= 9) return 250;
    return 150;
}

inline uint32_t channelInsuranceJitterCapMs(uint8_t sf) {
    // Keep insurance near the old 1.8–3.2s cadence once the ACK window clears;
    // large jitter on top of SF11/12 airtime margin recreated long deaf windows.
    if (sf >= 12) return 800;
    if (sf >= 11) return 800;
    if (sf >= 10) return 600;
    return 500;
}

// Typical ACK / insurance airtime margin past the last slotted start time.
inline uint32_t channelAckAirtimeMarginMs(uint8_t sf) {
    if (sf >= 12) return 3200;
    if (sf >= 11) return 2200;
    if (sf >= 10) return 1200;
    return 700;
}

// Initial delay before transmitting a locally-originated ACK.
// delay = base(SF) + (nodeId-slot)*slotWidth + small random.
inline uint32_t channelAckDelayMs(uint8_t sf, uint32_t nodeId, uint32_t jitterMs) {
    uint32_t jitterCap = channelAckJitterCapMs(sf);
    uint32_t j = jitterMs > jitterCap ? jitterCap : jitterMs;
    return channelAckBaseDelayMs(sf) +
           channelAckSlotIndex(nodeId) * channelAckSlotWidthMs(sf) + j;
}

// Worst-case first-ACK deadline (last slot + full jitter). Used so insurance
// TX clears the entire primary hearer ACK window.
inline uint32_t channelAckMaxDelayMs(uint8_t sf) {
    uint32_t d = channelAckBaseDelayMs(sf) +
                 (CHANNEL_ACK_SLOT_COUNT - 1u) * channelAckSlotWidthMs(sf) +
                 channelAckJitterCapMs(sf);
    if (d > CHANNEL_ACK_MAX_DELAY_CAP_MS) d = CHANNEL_ACK_MAX_DELAY_CAP_MS;
    return d;
}

// Delay before the originator's single channel-text insurance TX. Must clear
// the primary hearer ACK window (max slotted delay + typical ACK airtime) so
// the originator is in RX when first-wave HEARD ACKs arrive. Recovery ACKs
// are scheduled after this window (see channelAckRecoveryDelayMs).
// jitterMs is caller entropy.
inline uint32_t channelInsuranceDelayMs(uint8_t sf, uint32_t jitterMs) {
    uint32_t jitterCap = channelInsuranceJitterCapMs(sf);
    uint32_t base = channelAckMaxDelayMs(sf) + channelAckAirtimeMarginMs(sf);
    uint32_t j = jitterMs > jitterCap ? jitterCap : jitterMs;
    uint32_t d = base + j;
    if (d > CHANNEL_INSURANCE_DELAY_CAP_MS) d = CHANNEL_INSURANCE_DELAY_CAP_MS;
    return d;
}

// Second ACK attempt after the originator insurance TX should have finished.
// Uses the alternate slot mixer so primary same-slot colliders (hidden
// terminal: CAD clear, RF collide at originator) separate on the retry.
// Unique-hearer aggregation makes a duplicate ACK harmless.
inline uint32_t channelAckRecoveryDelayMs(uint8_t sf, uint32_t nodeId,
                                          uint32_t jitterMs) {
    uint32_t afterInsurance =
        channelInsuranceDelayMs(sf, channelInsuranceJitterCapMs(sf));
    // Originator was TX during insurance — wait one more airtime before the
    // recovery wave so it is back in RX.
    uint32_t postInsuranceRx = channelAckAirtimeMarginMs(sf);
    uint32_t jitterCap = channelAckJitterCapMs(sf);
    uint32_t j = jitterMs > jitterCap ? jitterCap : jitterMs;
    uint32_t d = afterInsurance + postInsuranceRx +
                 channelAckAltSlotIndex(nodeId) * channelAckSlotWidthMs(sf) + j;
    if (d > CHANNEL_ACK_RECOVERY_DELAY_CAP_MS) d = CHANNEL_ACK_RECOVERY_DELAY_CAP_MS;
    return d;
}

// Busy/CAD deferral for queued ACKs. Shorter radioBusyRetryDelayMs spins faster
// than SF11/SF12 airtime and burns the queue TTL without clearing the channel.
inline uint32_t channelAckBusyRetryDelayMs(uint8_t sf, uint32_t jitterMs) {
    uint32_t base;
    if (sf >= 12) {
        base = 900;
    } else if (sf >= 11) {
        base = 550;
    } else if (sf >= 10) {
        base = 350;
    } else {
        base = 180;
    }
    uint32_t j = jitterMs > 400u ? 400u : jitterMs;
    return base + j;
}

inline bool deadlineBefore(uint32_t left, uint32_t right, uint32_t now) {
    return (int32_t)(left - now) < (int32_t)(right - now);
}

inline uint8_t smoothedRouteMetric(uint8_t previous, uint8_t sample) {
    return (uint8_t)(((uint16_t)previous * 3u + sample + 2u) / 4u);
}

inline bool backupRouteIsUsable(uint32_t nowMs, uint32_t seenAtMs,
                                uint32_t timeoutMs) {
    return (uint32_t)(nowMs - seenAtMs) <= timeoutMs;
}

// Directed unicast: nextHopId==0 means flood/legacy (any relay may forward).
// Otherwise only the named next hop should rebroadcast.
inline bool shouldRelayAsNextHop(uint32_t nextHopId, uint32_t localId) {
    return nextHopId == 0 || nextHopId == localId;
}

// Flood unicast only when the route table is cold or the primary path failed
// recently (backup already promoted / invalidated).
inline bool shouldFloodUnicast(bool hasActiveRoute, bool routeFailedRecently) {
    return !hasActiveRoute || routeFailedRecently;
}

// Conservative multi-hop reverse-path cost when only the last-hop SNR is known.
// Avoids treating a 3-hop observation as a 1-hop bargain.
inline uint8_t multiHopLearnedMetric(uint8_t lastHopCost) {
    uint16_t m = (uint16_t)lastHopCost * 2u;
    if (m > 255u) m = 255u;
    return (uint8_t)m;
}

// Per-destination flood pacing: suppress back-to-back discovery floods.
inline bool floodDestCooldownActive(uint32_t nowMs, uint32_t lastFloodMs,
                                    uint32_t cooldownMs) {
    if (lastFloodMs == 0) return false;
    return (uint32_t)(nowMs - lastFloodMs) < cooldownMs;
}

// RREP path splice: every hearer may install a forward route to the discovered
// target via the hop that transmitted the reply (not only the RREQ originator).
inline bool shouldInstallReplyPath(uint32_t targetId, uint32_t localId) {
    return targetId != 0 && targetId != 0xFFFFFFFFu && targetId != localId;
}

// After an ACK failure: rediscover when no healthy directed route remains
// (cold table or recent primary failure that forced flood fallback).
inline bool shouldRediscoverAfterRouteFail(bool hasUsableRoute,
                                           bool routeFailedRecently) {
    return !hasUsableRoute || routeFailedRecently;
}

// Phase 4: only emit a standalone RREQ when rediscovery is needed AND the
// outbound payload is not already a flood unicast (that flood piggybacks
// reverse-path learning at every hop).
inline bool shouldEmitRouteRequest(bool needsRediscovery,
                                   bool payloadIsFloodUnicast) {
    if (!needsRediscovery) return false;
    if (payloadIsFloodUnicast) return false;
    return true;
}

// Global RREQ pacing across destinations — prevents multi-DM failure storms.
inline bool rediscoveryGlobalPacingActive(uint32_t nowMs, uint32_t lastAnyRreqMs,
                                          uint32_t minGapMs) {
    if (lastAnyRreqMs == 0 || minGapMs == 0) return false;
    return (uint32_t)(nowMs - lastAnyRreqMs) < minGapMs;
}

inline uint32_t routeDiscoveryCooldownMs(uint8_t sf) {
    if (sf >= 12) return 16000;
    if (sf >= 11) return 12000;
    if (sf >= 10) return 10000;
    return 8000;
}

inline uint32_t routeDiscoveryGlobalGapMs(uint8_t sf) {
    if (sf >= 12) return 4000;
    if (sf >= 11) return 3000;
    if (sf >= 10) return 2000;
    return 1500;
}

// Return-path packets (ACK / config reply): flood only when no reverse route.
// Do not force flood just because a prior *forward* DM failed recently — the
// inbound packet usually just proved a usable reverse next hop.
inline bool shouldFloodReturnPath(bool hasReverseRoute) {
    return !hasReverseRoute;
}

// TX-time next_hop policy: return-path / control replies keep a reverse next
// hop even after a recent forward-path failure. Forward config requests and
// data unicasts still use flood-on-failure.
inline bool shouldPreferReturnPathNextHop(bool isAck, bool isConfigResult,
                                          bool isRouteReply,
                                          bool isTraceResponse) {
    return isAck || isConfigResult || isRouteReply || isTraceResponse;
}

// A route row is only "directed" when it names a real next hop.
inline bool hasUsableDirectedHop(uint32_t nextHopId) {
    return nextHopId != 0;
}

// Relays must replace a packet's next_hop_id with *their* next hop toward the
// destination. Leaving the originator's next hop sticky breaks multi-hop chains.
inline uint32_t restampNextHopId(uint32_t localNextHopId, bool useDirected) {
    return useDirected ? localNextHopId : 0;
}

// Phase 4: earliest backup probe must clear typical first-ACK airtime at this SF.
// At SF11/12 a Phase-3 half-ACK timer (~1.5s) would TX during the ACK window.
inline uint32_t earlyProbeMinDelayMs(uint8_t sf) {
    uint32_t floor = channelAckBaseDelayMs(sf) + channelAckAirtimeMarginMs(sf);
    if (floor < 900u) floor = 900u;
    return floor;
}

inline uint32_t earlyProbeMaxDelayMs(uint8_t sf) {
    if (sf >= 12) return 7000;
    if (sf >= 11) return 5500;
    if (sf >= 10) return 4500;
    return 4000;
}

inline uint32_t earlyFloodGapMs(uint8_t sf) {
    if (sf >= 12) return 2500;
    if (sf >= 11) return 1800;
    if (sf >= 10) return 1200;
    return 800;
}

// Clear repair-phase gates so backup probe / limited flood / stored wake do not
// overlap. Order: backup_probe → limited_flood → ack_retry/rediscover → stored.
inline bool mayEarlyBackupProbe(bool earlyBackupDone, bool earlyFloodDone,
                                bool stored) {
    return !stored && !earlyBackupDone && !earlyFloodDone;
}

inline bool mayEarlyLimitedFlood(bool earlyFloodDone, bool stored) {
    return !stored && !earlyFloodDone;
}

// Phase 3/4/5 early path repair: probe backup / limited flood before the full
// ACK retry timeout when a directed next hop looks stuck. Delays scale with SF.
inline uint32_t earlyBackupProbeDelayMs(uint8_t sf, uint8_t routeMetric,
                                        uint32_t routeAgeMs, uint32_t softAgeMs,
                                        uint32_t jitterMs) {
    uint32_t full = ackRetryDelayMs(sf, 0, routeMetric, 0);
    uint32_t half = full / 2u;
    uint32_t floor = earlyProbeMinDelayMs(sf);
    uint32_t cap = earlyProbeMaxDelayMs(sf);
    if (half < floor) half = floor;
    if (half > cap) half = cap;
    // Stale directed routes get an earlier backup probe, but never below the
    // SF airtime floor (otherwise SF12 probes collide with the first ACK).
    if (softAgeMs > 0 && routeAgeMs > softAgeMs) {
        uint32_t earlier = (half * 3u) / 4u;
        half = earlier < floor ? floor : earlier;
    }
    uint32_t j = jitterMs > 400u ? 400u : jitterMs;
    return half + j;
}

inline uint32_t earlyFloodDelayMs(uint8_t sf, uint8_t routeMetric,
                                  uint32_t routeAgeMs, uint32_t softAgeMs,
                                  uint32_t jitterMs) {
    uint32_t probe =
        earlyBackupProbeDelayMs(sf, routeMetric, routeAgeMs, softAgeMs, 0);
    uint32_t delay =
        probe + earlyFloodGapMs(sf) + (routeMetric > 20u ? 500u : 0u);
    uint32_t j = jitterMs > 400u ? 400u : jitterMs;
    return delay + j;
}

// Retarget a directed pending TX when a fresher next hop appears (do not
// suppress a genuine backup already armed as next_hop).
inline bool shouldRetargetDirectedPending(uint32_t pendingNextHop,
                                          uint32_t newNextHop,
                                          bool earlyFloodDone) {
    if (earlyFloodDone) return false;
    if (newNextHop == 0 || pendingNextHop == 0) return false;
    return pendingNextHop != newNextHop;
}

inline bool shouldEarlyBackupProbe(uint32_t waitedMs, uint32_t probeAfterMs,
                                   bool alreadyProbed, bool hasDirectedNextHop) {
    return hasDirectedNextHop && !alreadyProbed && waitedMs >= probeAfterMs;
}

inline bool shouldEarlyLimitedFlood(uint32_t waitedMs, uint32_t floodAfterMs,
                                    bool alreadyFlooded, bool backupUnavailable) {
    return backupUnavailable && !alreadyFlooded && waitedMs >= floodAfterMs;
}

// Consecutive CAD-busy failures on a directed unicast → abandon that next hop
// without waiting out the full ACK timer.
inline bool shouldAbandonDirectedOnCad(uint8_t consecutiveCadFails,
                                       uint8_t threshold) {
    return consecutiveCadFails >= threshold;
}

// ---------------------------------------------------------------------------
// Phase 6: congestion / airtime budget, ACK link quality, relay-loss polish
// ---------------------------------------------------------------------------

// Congestion score 0..3 from queue pressure + recent TX airtime fraction.
// 0=clear, 1=elevated, 2=busy, 3=congested.
inline uint8_t congestionScore(uint8_t rebroadcastDepth, uint8_t maxRebroadcast,
                               uint8_t pendingAckDepth, uint8_t maxPendingAck,
                               uint32_t recentAirtimeMs, uint32_t windowMs) {
    uint8_t q = 0;
    if (maxRebroadcast > 0) {
        if ((uint16_t)rebroadcastDepth * 2u >= maxRebroadcast) q++;
        if ((uint16_t)rebroadcastDepth * 4u >= (uint16_t)maxRebroadcast * 3u) q++;
    }
    if (maxPendingAck > 0 &&
        (uint16_t)pendingAckDepth * 2u >= maxPendingAck) {
        q++;
    }
    uint8_t a = 0;
    if (windowMs > 0) {
        if (recentAirtimeMs * 4u >= windowMs) a = 1;           // >=25%
        if (recentAirtimeMs * 2u >= windowMs) a = 2;           // >=50%
        if (recentAirtimeMs * 4u >= windowMs * 3u) a = 3;      // >=75%
    }
    uint8_t level = (uint8_t)(q + a);
    return level > 3u ? 3u : level;
}

// Early limited floods / rediscovery floods: hold off when busy+.
inline bool shouldDeferCongestedFlood(uint8_t score) {
    return score >= 2u;
}

// Adaptive STORED wake: defer when busy+ (directed first-hop stays freer).
inline bool shouldDeferCongestedStoredWake(uint8_t score) {
    return score >= 2u;
}

// Soft extra delay starts at elevated; floods/wakes get heavier backoff than
// directed first-hop retries.
inline uint32_t congestionDeferMs(uint8_t score, bool isFloodOrWake, uint8_t sf,
                                  uint32_t jitterMs) {
    if (score == 0) return 0;
    uint32_t unit;
    if (sf >= 12) {
        unit = 1200;
    } else if (sf >= 11) {
        unit = 800;
    } else if (sf >= 10) {
        unit = 500;
    } else {
        unit = 300;
    }
    uint32_t mult;
    if (isFloodOrWake) {
        mult = (uint32_t)score * 2u; // 2/4/6 × unit
    } else {
        // Directed: no extra at elevated; light at busy/congested.
        mult = score > 1u ? (uint32_t)(score - 1u) : 0u;
    }
    uint32_t j = jitterMs > 400u ? 400u : jitterMs;
    return unit * mult + j;
}

// Blend route metric with observed neighbor hop cost from ACK SNR history.
// Good neighbors (cost<=8) leave the route alone; flaky ones get a modest
// penalty so alternate next hops win without waiting for hard timeout.
inline uint8_t linkQualityAdjustedMetric(uint8_t routeMetric,
                                         uint8_t neighborHopCost) {
    if (neighborHopCost <= 8u) return routeMetric;
    uint8_t penalty = (uint8_t)((neighborHopCost - 8u) / 2u);
    uint16_t m = (uint16_t)routeMetric + penalty;
    return m > 255u ? 255u : (uint8_t)m;
}

// After backup promote / invalidate: retarget non-flooded, non-stored pending.
inline bool shouldRetargetAfterRelayLoss(bool earlyFloodDone, bool stored) {
    return !earlyFloodDone && !stored;
}

// Schedule one rediscovery only when the primary was fully dropped (no backup
// promote) and local want_ack traffic still needs a path.
inline bool shouldScheduleRediscoveryOnInvalidate(bool promotedBackup,
                                                  bool hasPendingWantAck) {
    return !promotedBackup && hasPendingWantAck;
}

// Short SF-scaled pause after relay-loss retarget so we do not TX into the
// same airtime as an in-flight failure, but still faster than a full ACK wait.
inline uint32_t relayLossRetargetDelayMs(uint8_t sf, uint32_t jitterMs) {
    uint32_t base;
    if (sf >= 12) {
        base = 350;
    } else if (sf >= 11) {
        base = 220;
    } else if (sf >= 10) {
        base = 150;
    } else {
        base = 80;
    }
    uint32_t j = jitterMs > 200u ? 200u : jitterMs;
    return base + j;
}

// Position privacy blur: snap lat/lon to the center of a grid cell sized
// 2*radiusM, so the true position stays within +/-radiusM per axis of what
// gets broadcast. Deterministic on purpose: the same true position always
// reports the same blurred position — per-packet random jitter could be
// averaged over many telemetry packets to recover the real location.
// radiusM == 0 or a (0,0) no-fix position passes through unchanged.
inline void blurPosition(float latIn, float lonIn, uint32_t radiusM,
                         float& latOut, float& lonOut) {
    if (radiusM == 0 || (latIn == 0.0f && lonIn == 0.0f)) {
        latOut = latIn;
        lonOut = lonIn;
        return;
    }
    const double M_PER_DEG_LAT = 111320.0;
    double cellLat = (2.0 * radiusM) / M_PER_DEG_LAT;
    double latSnapped = (floor((double)latIn / cellLat) + 0.5) * cellLat;

    double mPerDegLon = M_PER_DEG_LAT * cos(latSnapped * 3.14159265358979 / 180.0);
    if (mPerDegLon < 1.0) mPerDegLon = 1.0; // degenerate near the poles
    double cellLon = (2.0 * radiusM) / mPerDegLon;
    double lonSnapped = (floor((double)lonIn / cellLon) + 0.5) * cellLon;

    latOut = (float)latSnapped;
    lonOut = (float)lonSnapped;
}

} // namespace meshmath
