#pragma once

// Pure, dependency-free mesh math. Kept out of MeshRouter.cpp so it can be
// unit-tested on the host (see firmware/test/test_meshmath). No Arduino types.

#include <stdint.h>

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

} // namespace meshmath
