// Recovery from a corrupted internal filesystem on nRF52 boards.
//
// A brown-out in the middle of a flash write can leave LittleFS inconsistent.
// LittleFS then fails one of its internal assertions, which on the stock
// Adafruit core stops the CPU for good (and with the watchdog running, turns
// into a reboot loop). Instead, a failed assertion inside LittleFS marks the
// storage as damaged in a register that survives a soft reset, and restarts;
// the next boot formats the storage before anything reads it. The node comes
// back with factory settings and a new identity key, which is better than a
// board that never boots again.
#include "StorageRecovery.h"

#include <Arduino.h>

#if defined(NRF52_SERIES)
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
#include <nrf_sdm.h>
#include <nrf_soc.h>
#include <string.h>

using namespace Adafruit_LittleFS_Namespace;

namespace {
// GPREGRET values (the register survives a soft reset). The Adafruit
// bootloader reserves 0x4E, 0x57, 0x6D and 0xA8.
constexpr uint8_t FORMAT_ON_BOOT = 0xC3;     // storage damaged: format next boot
constexpr uint8_t RECENTLY_FORMATTED = 0xC4; // formatted; do not format again yet
// Running this long after a format shows the flash is healthy again.
constexpr uint32_t STABLE_AFTER_FORMAT_MS = 10UL * 60UL * 1000UL;

bool softDeviceEnabled() {
    uint8_t enabled = 0;
    sd_softdevice_is_enabled(&enabled);
    return enabled != 0;
}

void setRetained(uint8_t value) {
    // POWER is a restricted peripheral while the SoftDevice runs.
    if (softDeviceEnabled()) {
        sd_power_gpregret_clr(0, 0xFF);
        sd_power_gpregret_set(0, value);
    } else {
        NRF_POWER->GPREGRET = value;
    }
}

uint8_t readRetained() {
    if (softDeviceEnabled()) {
        uint32_t value = 0;
        sd_power_gpregret_get(0, &value);
        return (uint8_t)value;
    }
    return (uint8_t)NRF_POWER->GPREGRET;
}

bool formattedThisBoot = false;
}  // namespace

namespace storagerecovery {

bool checkAtBoot() {
    // setup() runs before the SoftDevice starts, so the register is ours.
    if (NRF_POWER->GPREGRET != FORMAT_ON_BOOT) return false;
    NRF_POWER->GPREGRET = RECENTLY_FORMATTED;
    InternalFS.begin();
    InternalFS.format();
    formattedThisBoot = true;
    return true;
}

void service(uint32_t uptimeMs) {
    static bool cleared = false;
    if (cleared || uptimeMs < STABLE_AFTER_FORMAT_MS) return;
    cleared = true;
    if (readRetained() == RECENTLY_FORMATTED) setRetained(0);
}

bool formattedOnThisBoot() {
    return formattedThisBoot;
}

}  // namespace storagerecovery

// newlib calls this for every failed assert(). Replacing it keeps a failed
// check from halting the board: report it, then restart.
extern "C" void __assert_func(const char* file, int line, const char* func, const char* expr) {
    const bool inFilesystem = file != nullptr && strstr(file, "lfs") != nullptr;
    Serial.printf("Assertion failed: %s (%s:%d %s)%s\n", expr ? expr : "?",
                  file ? file : "?", line, func ? func : "?",
                  inFilesystem ? " - storage damaged, formatting on restart" : "");
    Serial.flush();
    // Format at most once until the node has run stably afterwards: storage
    // that fails again right after a format points at the flash itself, and
    // formatting it over and over only wears it out.
    if (inFilesystem && readRetained() != RECENTLY_FORMATTED) setRetained(FORMAT_ON_BOOT);
    delay(200);
    NVIC_SystemReset();
    while (true) {
    }
}

#else

namespace storagerecovery {
bool checkAtBoot() { return false; }
bool formattedOnThisBoot() { return false; }
void service(uint32_t) {}
}  // namespace storagerecovery

#endif
