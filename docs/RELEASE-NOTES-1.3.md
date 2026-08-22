# AetherMesh 1.3.0 — Phases A–L

App **1.3.0** / `versionCode` **9**. Flash matching firmware when you install
this APK; do not mix older BLE protocol builds with new diagnostics UI.

## Already shipped (A–H)

| Phase | Summary |
|-------|---------|
| **A** | Channel hearer receipts optional (default off) |
| **B** | Deploy presets: Leave-behind / Handheld / Repeater |
| **C** | Mesh Health diagnostics (BLE counters, export CSV) |
| **D** | Channel store-and-forward catch-up on routers |
| **E** | Unread / mute / search / export (1.2.4) |
| **F** | LV-safe + leave-behind field UI (1.2.5) |
| **G** | Cradle checklist docs only (`hardware/am1/` audit) |
| **H** | OTA trust: Stable Releases / Latest Pages (1.2.6) |

## New in 1.3.0 (I–L)

### I — Interop (withdrawn in 1.3.2)
- 1.3.0 shipped Settings stubs only (MQTT prefs / APRS template; **no** MQTT client).
- **1.3.2** removes the Interop Settings category and dead prefs for offline-first
  emergency use (no internet messaging bridges in the companion app).

### J — Field reliability toolkit
- Mesh Health → **mesh self-test** (5 channel pings → HEARD / RX Δ score, EN/ES dialog)
- One-tap **Share airtime/queue** plain-text snapshot (+ CSV export retained)
- Spanish strings for new UI

### K — Leave-behind ops polish
- Deploy profile **confirmation summary** before Apply (Leave-behind / Handheld / Repeater)
- Catch-up / queue hint from diagnostics + queued DM count
- Nodes sort **By stale** (oldest heard first; days-since label on rows)

### L — Release readiness
- Version **1.3.0** (9) in Developer → About / BuildConfig
- This release note

## Install when ready

1. Build / flash firmware for your boards (`heltec_v4`, `rak4631`, …)
2. Install the 1.3.0 APK
3. Push / tag the release when field-validated
