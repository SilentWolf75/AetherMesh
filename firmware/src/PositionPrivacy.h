#pragma once
#include <stdint.h>
#include "MeshMath.h"

namespace positionprivacy {
constexpr uint32_t Disabled = 0x80000000u;
constexpr uint32_t MaxRadiusM = 100000u;
inline uint32_t record(bool disabled, uint32_t radius) {
    return (disabled ? Disabled : 0u) | (radius > MaxRadiusM ? MaxRadiusM : radius);
}
inline uint32_t validateRecord(uint32_t value) {
    return (value & ~Disabled) > MaxRadiusM ? Disabled : value;
}
/** Phone→node GPS inject is suppressed while channel privacy disables sharing. */
inline bool allowInheritedFix(uint32_t policy) {
    return (validateRecord(policy) & Disabled) == 0;
}
inline void apply(uint32_t policy, uint32_t nodeRadius, float lat, float lon,
                  float& outLat, float& outLon, uint32_t& outRadius) {
    policy = validateRecord(policy);
    outRadius = meshmath::effectiveBlurRadiusM(nodeRadius, policy & ~Disabled);
    if (policy & Disabled) {
        outLat = outLon = 0;
        outRadius = 0;
    } else {
        meshmath::blurPosition(lat, lon, outRadius, outLat, outLon);
    }
}
}
