#pragma once

// On-device region / GPS actions. Header-only so host tests can cover the
// same cycle the Heltec button, T-Deck keys, T-Echo clicks, and CrowPanel
// taps use. No Arduino types.

#include <stdint.h>

namespace ondevice {

enum : uint32_t {
    RegionUs915 = 0,
    RegionEu868 = 1,
    GpsOn = 0,
    GpsOff = 1,
    GpsDuty = 2,
};

enum Page : uint8_t {
    PageHome = 0,
    PageGps = 1,
    PageMessages = 2,
    PageNodes = 3,
    PageSystem = 4,
};

enum Action : uint8_t {
    ActNone = 0,
    ActToggleRegion,
    ActConfirmRegion,
    ActCycleGpsMode,
    ActCycleDuty,
};

inline uint32_t nextRegion(uint32_t region) {
    return region == RegionUs915 ? RegionEu868 : RegionUs915;
}

inline const char* regionLabel(uint32_t region) {
    return region == RegionEu868 ? "EU868" : "US915";
}

// Same order as the T-Echo side-button 3-click: ON -> DUTY -> OFF -> ON.
inline uint32_t nextGpsMode(uint32_t mode) {
    if (mode == GpsOn) return GpsDuty;
    if (mode == GpsDuty) return GpsOff;
    return GpsOn;
}

inline const char* gpsModeLabel(uint32_t mode) {
    if (mode == GpsDuty) return "DUTY";
    if (mode == GpsOff) return "OFF";
    return "ON";
}

// Matches the Android PositionSettings wake-interval chips.
inline uint32_t nextGpsDutyIntervalSecs(uint32_t secs) {
    const uint32_t steps[4] = {300, 900, 1800, 3600};
    for (uint32_t i = 0; i < 4; i++) {
        if (secs < steps[i]) return steps[i];
    }
    return steps[0];
}

inline const char* gpsDutyMinutesLabel(uint32_t secs) {
    if (secs <= 300) return "5m";
    if (secs <= 900) return "15m";
    if (secs <= 1800) return "30m";
    return "60m";
}

// Unconfigured boards: short press toggles US/EU, long press saves.
// Configured: GPS page cycles GPS mode; SYSTEM cycles duty (or GPS if not duty).
inline Action shortPressAction(bool regionConfigured) {
    return regionConfigured ? ActNone : ActToggleRegion;
}

inline Action longPressAction(bool regionConfigured, uint8_t page, uint32_t gpsMode) {
    if (!regionConfigured) return ActConfirmRegion;
    if (page == PageGps) return ActCycleGpsMode;
    if (page == PageSystem) {
        return gpsMode == GpsDuty ? ActCycleDuty : ActCycleGpsMode;
    }
    return ActNone;
}

}  // namespace ondevice
