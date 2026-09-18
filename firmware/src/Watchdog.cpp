#include "Watchdog.h"

#include <Arduino.h>

#if defined(ESP32)
#include <esp_system.h>
#include <esp_task_wdt.h>
#elif defined(NRF52_SERIES)
#include <nrf.h>
#endif

namespace watchdog {

#if defined(ESP32)

void begin() {
    // Arduino-ESP32 2.x already runs the task watchdog for the idle task;
    // calling init again reconfigures its timeout and makes it reset the chip.
    esp_task_wdt_init(TIMEOUT_SECONDS, true);
    if (esp_task_wdt_status(nullptr) != ESP_OK) {
        esp_task_wdt_add(nullptr);
    }
}

void feed() {
    esp_task_wdt_reset();
}

const char* lastResetReason() {
    switch (esp_reset_reason()) {
        case ESP_RST_POWERON: return "power on";
        case ESP_RST_EXT: return "reset pin";
        case ESP_RST_SW: return "software restart";
        case ESP_RST_PANIC: return "crash (panic)";
        case ESP_RST_INT_WDT: return "interrupt watchdog";
        case ESP_RST_TASK_WDT: return "loop watchdog";
        case ESP_RST_WDT: return "watchdog";
        case ESP_RST_DEEPSLEEP: return "wake from deep sleep";
        case ESP_RST_BROWNOUT: return "brownout (low voltage)";
        default: return "unknown";
    }
}

#elif defined(NRF52_SERIES)

void begin() {
    if (NRF_WDT->RUNSTATUS) return;  // already running (e.g. after a soft reset)
    // Keep counting while the CPU sleeps; pause while a debugger halts it.
    NRF_WDT->CONFIG = (WDT_CONFIG_HALT_Pause << WDT_CONFIG_HALT_Pos) |
                      (WDT_CONFIG_SLEEP_Run << WDT_CONFIG_SLEEP_Pos);
    NRF_WDT->CRV = TIMEOUT_SECONDS * 32768u - 1u;  // 32.768 kHz ticks
    NRF_WDT->RREN = WDT_RREN_RR0_Msk;
    NRF_WDT->TASKS_START = 1;
}

void feed() {
    if (NRF_WDT->RUNSTATUS) {
        NRF_WDT->RR[0] = WDT_RR_RR_Reload;
    }
}

const char* lastResetReason() {
    // The Adafruit core reads and clears RESETREAS before setup() runs and
    // keeps the value; the register itself reads 0 by now.
    uint32_t why = readResetReason();
    if (why & POWER_RESETREAS_DOG_Msk) return "watchdog";
    if (why & POWER_RESETREAS_LOCKUP_Msk) return "CPU lockup";
    if (why & POWER_RESETREAS_SREQ_Msk) return "software restart";
    if (why & POWER_RESETREAS_RESETPIN_Msk) return "reset pin";
    if (why & POWER_RESETREAS_OFF_Msk) return "wake from system off";
    if (why == 0) return "power on";
    return "unknown";
}

#else

void begin() {}
void feed() {}
const char* lastResetReason() { return "unknown"; }

#endif

}  // namespace watchdog
