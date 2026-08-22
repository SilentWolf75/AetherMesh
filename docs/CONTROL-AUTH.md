# Remote control authentication (firmware)

## Capability advertisement

Firmware **1.3.2+** stamps `AETHERMESH_PROTOCOL_VERSION` (**3**) on outbound packets and
diagnostics. The Android app chooses signing from the peer's advertised version:

| Peer `protocol_version` | App sends |
|-------------------------|-----------|
| ≥ 3 | v3 (AMCFG3 + PBKDF2) |
| 2 | v2 (AMCFG2 + raw password) — **required during rollout** before every node is flashed |
| &lt; 2 | plaintext `config_password` |

Flashing the app alone without this gate would send v3 tags that ≤1.3.1 firmware
verifies as v2 → permanent "Auth failed" on rename / remote config.

## Node setting: refuse legacy

NVS / InternalFS key **`refuse_legacy`** (bool, default **false**).

When **true**:

- `PacketAuth` rejects `protocol_version < 3`
- Firmware also rejects the plaintext (&lt; v2) path

When enabling **`refuse_legacy`**, refresh peer telemetry first — the app caches
`protocol_version` per node. A stale `2` becomes a rejected packet once the flag is on.

ESP32: `preferences` key `refuse_legacy`.  
nRF (RAK / T-Echo): `/refuse_legacy.bin` (one byte, non-zero = true).

## Cross-language golden vectors

Pinned tags for password `admin-key` and the published Relay fixture:

| Protocol | Tag (16 bytes hex) |
|----------|--------------------|
| v2 | `165a8fa5f809a08d3063ea46c78c64e4` |
| v3 | `0cd1d291935a725a4ea210f3ac3dddbf` |

Asserted in `ControlAuthTest.kt`, `tools/test_control_auth_vectors.py`, and
`firmware/test/test_packetauth` (which links **shipped** `PacketAuth.cpp`:
`buildConfigCanonical` + `setControlPassword`/`verifyConfig`). Native builds inject
OpenSSL only as the HMAC primitive (`AETHERMESH_NATIVE_CRYPTO`); the PBKDF2
iteration/XOR structure and canonical layout are the firmware code.

## PBKDF2 cost

**Firmware:** derivation runs **once** in `packetauth::setControlPassword()` at boot and
whenever the admin password is saved — **not** per packet. Each v3 verify is a single
HMAC against the cached key.

**App:** `ControlKeyDerivation` memoizes the last derived key so `ControlAuth.sign`
(v3) does not re-run 120k iterations on every remote rename/config. The cache is
keyed by a domain-separated SHA-256 digest, not the password, so no admin password
is retained in the heap. `saveNodePassword` and `clearSavedPassword` both call
`ControlKeyDerivation.clearCache()`, so a password change invalidates it.

Caching only helps calls 2..n. The first derivation after a miss still costs roughly
60-150 ms, so call `ControlAuth.sign` off the main thread when wiring new UI paths.

## Rate limiting

Failed remote-config auths:

- **Global** window: 24 failures / 60 s → reject all senders (fail closed)
- **Per-sender** LRU buckets (8): 8 failures / 60 s; buckets always record (evict oldest), never silently drop

`sender_id` is unauthenticated; do not rely on per-sender limits alone.
