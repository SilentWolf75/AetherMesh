#ifndef AETHERMESH_WATCHDOG_H
#define AETHERMESH_WATCHDOG_H

#include <stdint.h>

/**
 * Hardware watchdog for the main loop.
 *
 * A relay left on a hill has nobody to power-cycle it. If loop() stops
 * coming round (a wedged bus, a radio that never answers, a deadlock), the
 * watchdog resets the board and it rejoins the mesh by itself.
 *
 * ESP32: the task watchdog, subscribed to the loop task.
 * nRF52: the WDT peripheral. Once started it cannot be stopped, and it keeps
 * running across a software reset, which is why feed() must start early in
 * setup(); the Adafruit bootloader feeds it during firmware updates.
 */
namespace watchdog {

constexpr uint32_t TIMEOUT_SECONDS = 60;

/** Start watching the calling task / main loop. Safe to call more than once. */
void begin();

/** Tell the watchdog the loop is alive. Call once per loop() pass. */
void feed();

/** Why the board last reset, as a short label for the boot log. */
const char* lastResetReason();

}  // namespace watchdog

#endif  // AETHERMESH_WATCHDOG_H
