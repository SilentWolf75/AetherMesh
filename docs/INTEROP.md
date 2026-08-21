# Interop (Phase I — experimental)

AetherMesh is **not Meshtastic-compatible on the air**. LoRa packet framing,
crypto, channels, and MQTT payloads differ. Do not expect Meshtastic nodes or
brokers to decode AetherMesh RF or BLE traffic.

This phase is **app-side pragmatic interop only**: settings stubs, documentation,
and light export helpers. There is no full mesh bridge.

## MQTT (outbound stub)

**Settings → Interop (experimental)** stores enable flag, broker URL, topic
prefix, and optional username. Live publish is **not wired** in 1.3.0 (no MQTT
client dependency). Prefs are kept so a later release can publish without a
settings redesign. Intended shape when outbound lands:

| Piece | Suggested value |
|-------|-----------------|
| Topic prefix | `aethermesh/{node_id_hex}/` |
| Telemetry | `…/telemetry` — JSON: `batt_v`, `uptime_s`, `role`, `sf`, `rssi`/`snr` if known |
| Position | `…/position` — JSON: `lat`, `lon`, `alt_m`, `ts_ms` |
| QoS | 0 or 1; retain optional for last position only |

### Meshtastic-compatible topic notes (future optional mapper)

Meshtastic often uses `msh/US/2/json/…` (region / channel / encoding). Mapping
AetherMesh telemetry into that tree would be a **lossy translator** on the phone
or a gateway — never on-air. Prefer a dedicated `aethermesh/…` prefix first;
add a Meshtastic-shaped mirror only if a field deployment needs an existing
dashboard.

## APRS

No APRS-IS gateway in-app. **Share APRS template** copies a one-line comment
you can paste into an existing APRS client. Callsign and passcode remain your
responsibility.

## Firmware

No firmware change for Phase I. Serial / BLE stay AetherMesh-native.
