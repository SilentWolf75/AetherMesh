# Leave-behind field notes (Phase F)

Companion to `MESH-RELIABILITY.md` App UX 1.2.5.

## Phone (1.2.5)

- Telemetry samples already land in `telemetry_history`; Nodes / Details show
  voltage trend (↑/↓/→) and days-since-heard for quiet bases.
- `last_position_at` tracks the last valid GPS/fixed fix. Telemetry without a
  fix no longer wipes stored coordinates.
- GPS duty label comes from cached `node_settings_<id>` prefs (local or remote
  config), not from the air — mesh telemetry does not carry `gps_mode`.
- **Low-V safe** badge when voltage is below 3.50 V and not charging (same enter
  threshold as firmware). No new proto field.

## Firmware

- Enter LV safe below **3.50 V** (not charging); exit at **3.65 V** or when
  charging. Cap TX at **14 dBm**, stretch telemetry (≥10 min) and GPS duty
  (≥30 min), power off always-on GPS until recovery.
- ESP32 persists `lv_safe` in NVS lightly; RAK re-evaluates from voltage.
- Serial: `DEPLOY_LB` still applies Router + GPS periodic 15m + power-save.

## Phase G next

Hardware cradle CAD/PCB under `hardware/am1/` is audit-only — do not fab.
Use the Phase G checklist in `MESH-RELIABILITY.md`; software can proceed to
Phase H while electrical is redrawn.
