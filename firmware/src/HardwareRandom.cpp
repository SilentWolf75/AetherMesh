#include "HardwareRandom.h"

#include <Arduino.h>
#include <string.h>

#ifdef ESP32
#include <esp_system.h>
#else
#include <nrf_sdm.h>
#include <nrf_soc.h>
#endif

namespace hwrandom {

#ifdef ESP32

bool fill(uint8_t* out, size_t len) {
    if (out == nullptr) return false;
    // esp_fill_random draws from the hardware RNG, which is seeded by RF noise
    // while the radio (Wi-Fi or Bluetooth) is on; BLE is always on here.
    esp_fill_random(out, len);
    return true;
}

#else

namespace {

bool softdeviceEnabled() {
    uint8_t enabled = 0;
    return sd_softdevice_is_enabled(&enabled) == NRF_SUCCESS && enabled != 0;
}

void fillFromRegisters(uint8_t* out, size_t len) {
    // Bias correction on so the bytes are uniform.
    NRF_RNG->CONFIG = RNG_CONFIG_DERCEN_Msk;
    NRF_RNG->TASKS_START = 1;
    for (size_t i = 0; i < len; i++) {
        NRF_RNG->EVENTS_VALRDY = 0;
        while (NRF_RNG->EVENTS_VALRDY == 0) {
        }
        out[i] = (uint8_t)NRF_RNG->VALUE;
    }
    NRF_RNG->TASKS_STOP = 1;
}

// The SoftDevice keeps a small pool it refills in the background. Take what is
// there, wait briefly for more, and give up rather than hang if it stalls.
bool fillFromSoftdevice(uint8_t* out, size_t len) {
    size_t filled = 0;
    const uint32_t started = millis();
    while (filled < len) {
        uint8_t available = 0;
        if (sd_rand_application_bytes_available_get(&available) != NRF_SUCCESS) return false;
        if (available == 0) {
            if (millis() - started > 500) return false;
            delay(1);
            continue;
        }
        const size_t want = len - filled;
        const uint8_t take = (uint8_t)(want < available ? want : available);
        if (sd_rand_application_vector_get(out + filled, take) != NRF_SUCCESS) return false;
        filled += take;
    }
    return true;
}

}  // namespace

bool fill(uint8_t* out, size_t len) {
    if (out == nullptr) return false;
    if (len == 0) return true;
    if (softdeviceEnabled()) {
        if (!fillFromSoftdevice(out, len)) {
            memset(out, 0, len);
            return false;
        }
        return true;
    }
    fillFromRegisters(out, len);
    return true;
}

#endif

}  // namespace hwrandom
