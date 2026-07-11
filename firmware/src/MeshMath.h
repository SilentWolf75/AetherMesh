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
inline uint32_t rebroadcastDelayMs(float snr) {
    float s = clampf(snr, -20.0f, 10.0f);
    return (uint32_t)(500.0f + (10.0f - s) * (1500.0f / 30.0f));
}

inline bool shouldReplaceRoute(uint32_t currentNextHop, uint8_t currentMetric,
                               uint32_t currentAgeMs, uint32_t candidateNextHop,
                               uint8_t candidateMetric, uint32_t routeTimeoutMs) {
    return candidateNextHop == currentNextHop ||
           candidateMetric < currentMetric ||
           currentAgeMs > routeTimeoutMs / 2;
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

inline uint32_t ackRetryDelayMs(uint32_t retryCount, uint8_t routeMetric,
                                uint32_t jitterMs) {
    uint32_t multiplier = 1u << (retryCount > 2 ? 2 : retryCount);
    uint32_t routePenalty = (uint32_t)routeMetric * 100u;
    if (routePenalty > 3000u) routePenalty = 3000u;
    return 3000u * multiplier + routePenalty + jitterMs;
}

inline bool deadlineBefore(uint32_t left, uint32_t right, uint32_t now) {
    return (int32_t)(left - now) < (int32_t)(right - now);
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
