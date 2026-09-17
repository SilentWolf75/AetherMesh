#ifndef GPS_STATUS_H
#define GPS_STATUS_H

// Classifies the onboard GNSS for telemetry (Telemetry.gps_state etc.). Pure so
// it is host-tested (test/test_gpsstatus); main.cpp gathers the inputs from
// TinyGPS++ and the GPS power state.
//
// Why: TinyGPS++ keeps location.isValid() true forever after the first fix, and
// telemetry falls back to a phone-shared position, so coordinates alone cannot
// say whether a node's GPS works. The app showed "LOCKED" for a node whose
// module had never seen a satellite.

#include <stdint.h>

namespace gpsstatus {

// Values match aethermesh_Telemetry_GpsState / _PositionSource.
enum State : uint32_t {
    StateUnknown = 0,
    StateAbsent = 1,
    StateOff = 2,
    StateSleeping = 3,
    StateSearching = 4,
    StateFix = 5,
};

enum Source : uint32_t {
    SourceNone = 0,
    SourceGps = 1,
    SourcePhone = 2,
    SourceFixed = 3,
};

// A fix older than this is history, not a live lock (modules report ~1 Hz).
constexpr uint32_t LIVE_FIX_MAX_AGE_MS = 10000;

struct Inputs {
    bool moduleDetected;      // hasOnboardGps
    uint32_t gpsMode;         // 0 = always on, 1 = off, 2 = duty-cycle
    bool powered;             // module currently powered and being read
    bool locationValid;       // TinyGPS++ location.isValid() (sticky after first fix)
    uint32_t locationAgeMs;
    uint32_t satellitesUsed;
    uint32_t satellitesInView;
    bool hdopValid;
    double hdop;
    bool fixedPosition;
    bool phonePositionFresh;
};

inline State state(const Inputs& in) {
    if (!in.moduleDetected) return StateAbsent;
    if (in.gpsMode == 1) return StateOff;
    if (!in.powered) return in.gpsMode == 2 ? StateSleeping : StateOff;
    if (in.locationValid && in.locationAgeMs <= LIVE_FIX_MAX_AGE_MS) return StateFix;
    return StateSearching;
}

// Mirrors the telemetry position priority in main.cpp: fixed, GPS, phone.
inline Source source(const Inputs& in) {
    if (in.fixedPosition) return SourceFixed;
    if (in.locationValid) return SourceGps;
    if (in.phonePositionFresh) return SourcePhone;
    return SourceNone;
}

inline uint32_t hdopX10(const Inputs& in) {
    if (!in.hdopValid || in.hdop <= 0.0) return 0;
    const double scaled = in.hdop * 10.0 + 0.5;
    return scaled > 9999.0 ? 9999u : (uint32_t)scaled;
}

inline uint32_t fixAgeSecs(const Inputs& in) {
    return in.locationValid ? in.locationAgeMs / 1000u : 0u;
}

/**
 * Satellites in view from per-constellation GSV counts ($GPGSV, $GLGSV, ...).
 * A constellation whose last GSV is older than maxAgeMs no longer counts.
 */
inline uint32_t satellitesInView(const uint32_t* counts, const uint32_t* agesMs, int n, uint32_t maxAgeMs) {
    uint32_t total = 0;
    for (int i = 0; i < n; i++) {
        if (agesMs[i] <= maxAgeMs) total += counts[i];
    }
    return total;
}

} // namespace gpsstatus

#endif
