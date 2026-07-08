#pragma once

// AetherMesh firmware version. The base version is manual; the git short hash is
// injected at build time by version_stamp.py (see platformio.ini extra_scripts),
// so every node reports exactly which build it runs. A trailing '*' means the
// build had uncommitted firmware changes.
//
// Reported in Telemetry.firmware_version — keep the composed string within its
// max_size (20 incl. null); see proto/mesh.options.

#define AETHERMESH_FW_BASE "1.2.0"

#ifndef FW_GIT_HASH
#define FW_GIT_HASH "local"
#endif

#define AETHERMESH_FW_VERSION AETHERMESH_FW_BASE "-" FW_GIT_HASH
// (OTA fast-profile confirmation build marker.)
// (RAK DFU round-trip test marker.)
