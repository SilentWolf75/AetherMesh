#ifndef TRACE_HOPS_H
#define TRACE_HOPS_H

// Compact traceroute path encoding for extended-range traces (hop_start > 8).
// The repeated node/RSSI/SNR fields cost ~9 bytes per hop and cannot carry 16
// hops each way inside one LoRa frame; this layout is a fixed 5 bytes per hop:
//   node id  uint32 little-endian
//   SNR      int8, quarter dB
// Per-hop RSSI is left out: a 6-byte layout made a full 16+16 hop response
// 280 bytes, past the SX1262's 255-byte frame. SNR drives the route metric.
// Pure and host-tested (test/test_tracehops); the Android app decodes the same
// layout.

#include <math.h>
#include <stddef.h>
#include <stdint.h>

namespace tracehops {

constexpr size_t HOP_BYTES = 5;
constexpr size_t MAX_HOPS = 16;
constexpr size_t MAX_BYTES = HOP_BYTES * MAX_HOPS;

inline int8_t clampInt8(float value) {
    const float rounded = roundf(value);
    if (rounded < -128.0f) return -128;
    if (rounded > 127.0f) return 127;
    return (int8_t)rounded;
}

inline size_t hopCount(size_t byteLength) {
    return byteLength / HOP_BYTES;
}

inline uint32_t nodeAt(const uint8_t* bytes, size_t index) {
    const uint8_t* hop = bytes + index * HOP_BYTES;
    return (uint32_t)hop[0] | ((uint32_t)hop[1] << 8) | ((uint32_t)hop[2] << 16) |
           ((uint32_t)hop[3] << 24);
}

inline int8_t snrQuarterDbAt(const uint8_t* bytes, size_t index) {
    return (int8_t)bytes[index * HOP_BYTES + 4];
}

/**
 * Append one hop. Returns false (and leaves the path untouched) when the path
 * is already at capacity. Callers mark the direction truncated in that case.
 */
template <typename Size>
inline bool append(uint8_t* bytes, Size& length, size_t capacity,
                   uint32_t nodeId, float snr) {
    if (capacity > MAX_BYTES) capacity = MAX_BYTES;
    if ((size_t)length + HOP_BYTES > capacity) return false;
    uint8_t* hop = bytes + length;
    hop[0] = (uint8_t)(nodeId & 0xFF);
    hop[1] = (uint8_t)((nodeId >> 8) & 0xFF);
    hop[2] = (uint8_t)((nodeId >> 16) & 0xFF);
    hop[3] = (uint8_t)((nodeId >> 24) & 0xFF);
    hop[4] = (uint8_t)clampInt8(snr * 4.0f);
    length = (Size)(length + HOP_BYTES);
    return true;
}

} // namespace tracehops

#endif
