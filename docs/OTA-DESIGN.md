# OTA firmware updates — design & plan

**Status: design only. Not implemented.** OTA flashing carries bricking risk,
and the RAK lives outside on solar (physical retrieval = expensive). This is
deliberately scoped as its own hardware-in-the-loop session rather than shipped
as unverified code. This doc is the plan for that session.

## Why not "just add it"

A subtle bug in a firmware-transfer path — a dropped chunk, an off-by-one in the
partition write, a bad CRC check — can leave a node unbootable. On a
USB-accessible desk node that's a 20-second reflash; on the solar RAK it means
climbing to retrieve it. So OTA must be built with a guaranteed recovery path
and tested Heltec-first.

## Platform reality (they differ — no single implementation)

### Heltec V4 (ESP32-S3)
- No standard BLE OTA in Arduino-ESP32. Two real options:
  - **A. WiFi web updater** (recommended first): the ESP32 joins WiFi, runs a
    tiny HTTP server with an upload form backed by `Update.h`. Robust,
    well-documented, browser-based. Recovery: USB. Cost: needs WiFi credentials
    plumbed into config, and the node briefly leaves mesh duty to update.
  - **B. Custom BLE OTA**: a dedicated BLE characteristic receives firmware in
    ~500-byte chunks with flow control (notify-ack per chunk), written to the
    OTA partition via `Update.write()`, finalized with `Update.end()` +
    `esp_ota_set_boot_partition`. Self-contained (works through the app, no
    WiFi) but entirely custom on both sides and slower (BLE throughput).

### RAK4631 / RAK3401 (nRF52840)
- Uses the Adafruit nRF52 bootloader, which supports **DFU over BLE** — but via
  the Adafruit/Nordic DFU protocol, not something we invent. The app would embed
  a DFU client (init packet, object model, CRC per object). This is a different,
  larger protocol than the ESP32 path. **Do the RAK last, and only after the
  ESP32 path is proven** — it's the node we least want to brick.

## Recommended path

1. **Version awareness first (DONE):** git-stamped firmware versions + the
   mixed-firmware banner already tell you what's where. That's the prerequisite
   and it's shipped.
2. **Heltec WiFi web updater (option A):** lowest risk, standard `Update.h`,
   USB recovery. Add a "Enable WiFi update (5 min)" action that joins a
   configured SSID, prints the node's IP to serial/OLED, and serves the upload
   page. Auto-disable after a timeout so it never stays on WiFi.
3. **Heltec BLE OTA (option B)** only if untethered updates are needed.
4. **RAK BLE DFU** last, via a proper Nordic DFU library.

## Safety requirements (non-negotiable for the implementation session)

- Test on a **USB-connected Heltec on the desk** first, cable in hand.
- Verify the uploaded image's size and (ideally) a CRC/hash before
  `Update.end()`; abort cleanly on mismatch (Update.h does most of this).
- Never auto-reboot into a new image without a confirmed successful write.
- Keep the factory/OTA partition scheme intact (the default ESP32 OTA layout
  already provides a fallback partition).
- **Do not enable OTA on the RAK until the ESP32 path has flashed and booted
  successfully at least 3 times.**

## Rough effort
- ESP32 WiFi updater: ~half a day incl. testing.
- ESP32 BLE OTA: ~1–2 days (chunk protocol + app progress UI + testing).
- RAK BLE DFU: ~1–2 days (DFU client integration + testing).
