# Interop — withdrawn from the companion app (1.3.2)

AetherMesh is an **offline-first** emergency mesh: phone ↔ node is BLE, node ↔ node is LoRa. The companion app does **not** expose MQTT, APRS-IS, or other messaging bridges that would steer users toward putting the mesh on the internet.

## What was removed

In **1.3.0–1.3.1**, Settings briefly showed an experimental **Interop** category with:

- MQTT broker / topic **prefs stubs** (never wired to an MQTT client)
- APRS comment **template share** (no APRS-IS gateway)
- Links to this doc

**1.3.2** removes that Settings entry, the stubs, and the dead prefs. No MQTT client was ever shipped.

## Product stance

- Keep local BLE ↔ LoRa mesh.
- Do **not** add outbound messaging bridges (MQTT, Meshtastic broker mirrors, APRS-IS) in the companion app unless product explicitly revisits offline-first policy.
- Optional, **user-initiated** firmware catalog checks (GitHub Releases / Pages) remain under Settings → Firmware Update — that is phone OS internet for OTA packages, not mesh gatewaying.

## Historical note (not product UI)

AetherMesh is still **not Meshtastic-compatible on the air**. LoRa framing, crypto, and channels differ.
