#ifndef AETHERMESH_STORAGE_RECOVERY_H
#define AETHERMESH_STORAGE_RECOVERY_H

#include <stdint.h>

/**
 * Recovery from a corrupted internal filesystem (nRF52 only; a no-op on
 * ESP32, whose NVS repairs itself). See StorageRecovery.cpp.
 */
namespace storagerecovery {

/**
 * Call first thing in setup(), before anything opens a file. Formats the
 * storage if the previous run found it damaged. Returns true if it did.
 */
bool checkAtBoot();

/** True if this boot started by formatting damaged storage. */
bool formattedOnThisBoot();

/** Call from loop(); after a while running, allows a future format again. */
void service(uint32_t uptimeMs);

}  // namespace storagerecovery

#endif  // AETHERMESH_STORAGE_RECOVERY_H
