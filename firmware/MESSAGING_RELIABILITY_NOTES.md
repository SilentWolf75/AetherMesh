# Messaging reliability — MeshCore / Meshtastic lessons (2026-08)

Field symptom: channel/DM text not getting out or received despite ACK-slot and ASAP-queue fixes. Root cause was **control-plane ACK volume starving application TX** on a shared 8-deep queue, plus half-duplex deafness during insurance/recovery waves.

## What MeshCore / Meshtastic do differently

| Area | MeshCore | Meshtastic | AetherMesh (before) |
|------|----------|------------|---------------------|
| Group/channel | Flood only; no per-hearer ACK | Flood + **implicit ACK** (overhear rebroadcast) | `want_ack` → every hearer queues ACK(s) |
| ACK attempts | Bounded; separate ACK dedup table | Single ACK path; cancel rebroadcast on dupe | Primary **+ recovery** ACK per channel hearer |
| TX priority | Direct=0 (highest); floods score-delayed | Priority queue; text HIGH, ACK higher but not doubled | Shared queue; recovery ACKs competed with text |
| Phone inject | Companion does not relay | FromRadio queue + priority | BLE → `sendRawPacket`; busy drop fixed earlier |

Full MeshCore path-cache / Meshtastic NextHop redesign is a larger project. This interim restores **delivery first** while keeping the AetherMesh proto and channel HEARD UX.

## Fixes shipped

1. **One ACK attempt** — channel hearers no longer schedule a recovery ACK wave.
2. **Cap pending local ACKs** (`MAX_PENDING_LOCAL_ACKS = 2`) so hearer storms cannot fill the TX queue.
3. **Implicit HEARD** — overhearing a rebroadcast of our own channel text counts the relay as heard and cancels pending insurance (Meshtastic-style).
4. **Shorter insurance cap** (5s) — only need to clear the primary ACK window.
5. Keep channel `want_ack` optional in the app (Settings → Channel hearer receipts, default **off**). When on, Client-role nodes can still report HEARD; DMs unchanged (retransmit + DELIVERED). When off, channel sends use `wantAck=false` and UI SENT means on-air (flood-style).

## App notes (1.2.3)

- Pref key: `channel_hearer_receipts` in `aethermesh_prefs` (default false).
- Composer is never blocked on pending HEARD.
- HEARD copy is a bonus receipt, not a delivery mailbox.
- Status labels: QUEUED = queued for mesh; channel SENT = on air; HEARD = bonus.
- Incoming rows dedupe on `(sender_id, packet_id)` so channel catch-up replays
  do not create duplicate chat bubbles.

## Phase D — Channel store-and-forward

Routers/Repeaters retain up to 8 recent channel text packets (30 min TTL). After
a neighbor is offline ≥2 min and a route/telemetry sighting returns, paced
unicast catch-up replays the backlog (`want_ack=false`, P6 congestion defer).
Serial hints: `Channel store: kept…`, `Channel catch-up armed…`,
`Channel catch-up TX…`. Clients never store/relay.

## Files

- `firmware/src/MeshRouter.cpp` / `.h`
- `firmware/src/MeshMath.h`
- `firmware/test/test_meshmath/test_meshmath.cpp`
- `app/.../ChatScreen.kt`, `DatabaseHelper.kt`, `AetherMeshRepository.kt`
- `docs/MESH-RELIABILITY.md`

## Flash

```text
pio run -e heltec_v4 -t upload --upload-port COMx
pio run -e rak4631 -t upload --upload-port COMy
```

## Verify

1. Serial: after a channel send, look for `Queued ACK` (not `recovery`), and optionally `Implicit HEARD … via relay`.
2. Two+ nodes on same channel: message appears on receivers quickly; originator phone leaves “waiting…” when a hearer ACKs or a relay rebroadcasts.
3. Rapid phone sends while another node is ACKing: local text should still TX (`Radio busy on phone text … queued ASAP` / ACK eviction logs OK).
4. DM still gets `DELIVERED` via unicast ACK + retries.

## Push?

Prefer field-verify on flashed boards before commit/push. If delivery is solid, commit these router changes.
