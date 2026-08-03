# Mesh Reliability Plan

AetherMesh should earn comparisons through measured behavior rather than a
feature checklist. The current routing work uses these targets.

## Implemented Baseline

- Full 32-bit sender identity and duplicate suppression by sender, packet, and
  retry attempt.
- SNR-weighted route cost and rebroadcast delay.
- Reliable unicast with bounded retries and explicit delivered, retrying, and
  failed states.
- Route-discovery flood cooldown to prevent retries from creating a discovery
  storm.
- Route invalidation after exhausted ACK retries.
- **Phase 1 smart routing:** unicasts carry optional `next_hop_id`. When a
  Router has an active route, it forwards directed to that next hop; other
  Routers suppress rebroadcast. Flood only when the table is cold or a path
  failed recently. Routes are reinforced from ACKs, telemetry, and traceroute;
  ACK timeout promotes backup, marks failure, and rediscovers. Clients still
  never relay.
- **Phase 2 path splice:** every hop that hears an RREP installs a forward
  route to the discovered target (not only the RREQ originator). Relays restamp
  `next_hop_id` to their own next hop toward the destination. ACK failure
  promotes backup or floods once and re-RREQs.
- **Phase 3 early repair + link decay:** directed unicasts that look stuck
  (CAD-busy streak or ~½ ACK timer with no progress) probe the backup next hop,
  then a paced limited flood, without waiting for full ACK exhaustion. Route
  metrics soft-age after ~3.3 minutes so fresher better-SNR paths win; stale
  primaries soft-demote to a fresher backup before the 10-minute hard timeout.
- **Phase 4 SF-aware repair + rediscovery care:** early backup-probe and limited
  flood delays scale with spreading factor so SF11/12 never probe during the
  first ACK airtime window. Rediscovery coalesces per target, paces globally
  across destinations, and skips a standalone RREQ when the retry is already a
  flood unicast (piggyback). ACKs and config replies prefer the reverse route
  (`next_hop` toward the originator) and flood only when that route is cold.
  Pending directed retries retarget when a better next hop appears; backup
  promotion retains the old primary as an alternate instead of discarding it.
- **Phase 5 delivery pacing + store wake:** unicast `ackRetryDelayMs` scales with
  SF (snappy SF7–9, airtime-clear SF11/12) so retries do not stomp reverse ACKs.
  STORED want_ack packets wake promptly when a reverse path / route reappears
  (telemetry, overheard ACK, RREP) instead of waiting a full store-forward
  interval — one wake per wait cycle. DELIVERED refreshes the primary route and
  soft-nudges backup age. Repair phases are gated so backup-probe, limited
  flood, rediscovery, and stored wake do not fight; channel hearer ACK and
  broadcast flood semantics stay unchanged.
- **Phase 6 congestion + link quality + relay-loss polish:** STORED wakes and
  early repair floods check pending-queue depth plus a ~10s RadioManager recent-
  airtime window; busy/congested radios defer floods/wakes (directed first-hop
  retries stay lighter) instead of stacking CAD failures. A small
  `NeighborQuality[12]` table smooths ACK RX hop-cost per neighbor and demotes
  multi-hop routes through flaky next hops inside `addRoute`. Invalidate /
  backup-promote promptly restamps pending want_ack `next_hop`, accelerates one
  retry, and schedules a single coalesced rediscovery when the path is fully
  dropped — without discovery storms.
- **Phase 7 harden + observability:** TX-time return-path restamp covers ACK,
  config *result*, RREP, and traceroute response (not forward config requests);
  directed routing requires a non-zero next hop; BLE `MeshDiagnostics` + Serial
  expose `directed_relays` / `suppress_relays` / `flood_unicasts` / `rreq_sent` /
  `early_repairs` for field checks.
- **No in-packet path array:** Phase 2 table splice plus restamp already installs
  the forward path at every RREP hearer and keeps multi-hop DM directed. An
  on-wire hop list would add airtime and proto/app surface without fixing a
  remaining correctness gap — revisit only if field traces show relays still
  lack next-hop state after discovery.
- Stable route selection: refresh the current next hop, accept a better metric,
  smooth route metrics, retain a fresh alternate next hop, and promote that
  alternate immediately when the preferred route fails or expires.
- Proxy route replies only from recently observed routes.
- Two-way traceroute with up to eight observed hops in each direction, per-link
  RSSI/SNR, truncation reporting, and route learning at every forwarding node.
- Configured node names in telemetry, with explicit ownership metadata so local
  aliases are not overwritten by network-advertised defaults.
- Expiring duplicate history so a sender can safely reuse a packet ID after the
  replay window without allowing immediate duplicates through.
- Exponential, route-aware ACK retry delays and airtime-aware channel backoff.
- A bounded rebroadcast queue that preserves the packets nearest their send
  deadlines and highest control/data priority when the mesh is congested.
- Process-wide Android packet IDs seeded across the full positive 31-bit range.
- Thirty-minute bounded store-and-forward after fast ACK retries are exhausted,
  with explicit `STORED` and `EXPIRED` delivery states.
- Firmware diagnostics for traffic, retries, ACKs, duplicates, CAD contention,
  queue drops, route changes, queue depth, airtime, protocol version, and
  smart-routing counters (directed/suppress/flood/RREQ/early repair), retained
  and exportable by the Android app (BLE snapshot; not LoRa).
- Versioned V2 remote configuration authenticated with HMAC-SHA256 and a
  persisted session/counter replay window, while legacy nodes retain the old
  password path during migration.
- ESP32 OTA images streamed with SHA-256 verification before finalization.

## What Traceroute Means

The forward and return routes are recorded separately because LoRa paths can be
asymmetric. A hop's signal values describe the packet as received by that hop.
The app never estimates missing nodes. If a path exceeds the bounded packet
format, it reports that the path was truncated.

Traceroute is also an active route probe. Each forwarding node learns the
reverse path from the trace it receives, which makes the diagnostic useful to
the mesh rather than only visual.

## Map Integrity

- A known node is not assumed to be a direct neighbor.
- Direct link lines require a one-hop observation.
- A completed trace can draw the exact observed path when every hop has a
  position.
- Range-test tracks are grouped by target and sorted by timestamp, so separate
  tests are never joined by a false line.
- A position is valid when it is in range and is not the no-fix sentinel
  `(0,0)`; equator and prime-meridian positions remain valid.

## Benchmark Gates

Future routing changes should be tested against repeatable scenarios:

1. Direct delivery success and latency at several SNR bands.
2. Three- and five-hop delivery success with one moving endpoint.
3. Recovery time after the preferred relay disappears.
4. Total transmissions per delivered message under congestion.
5. Duplicate application deliveries during retry and ACK loss.
6. Route-discovery packets emitted per destination per minute.
7. Traceroute path agreement with controlled physical topologies.
8. Name and map consistency across disconnect, app restart, and node reboot.

The deterministic simulator in `tools/mesh_simulator.py` covers direct,
five-hop, lossy, and relay-failure topologies. Run it before and after routing
changes and retain the JSON report with the test record:

```bash
python tools/mesh_simulator.py
python -m unittest tools.test_mesh_simulator
```

The simulator is a regression gate, not a substitute for radios. Release
candidates still require the controlled multi-radio and field checks in
`docs/HARDWARE-VALIDATION.md`.

The hardware qualification CSV generated by `tools/hardware_qualification.py`
is the release gate for those physical checks. It must not be marked complete
from build results alone.

## Reference Designs

- Meshtastic records separate forward and return hop/SNR arrays and uses trace
  results to update next-hop knowledge:
  <https://github.com/meshtastic/firmware/blob/develop/src/modules/TraceRouteModule.cpp>
- MeshCore supports flood discovery, compact direct paths, and a trace payload
  that collects SNR along a selected path:
  <https://github.com/meshcore-dev/MeshCore/blob/main/docs/packet_format.md>

AetherMesh keeps full 32-bit IDs in trace results and records both RSSI and SNR.
Those choices cost more airtime than compact hashes, so the protocol bounds each
direction at eight hops and reports truncation.

## Routing Roadmap

| Phase | Status | Scope |
|------|--------|--------|
| 1 | Done | Directed `next_hop_id`, stronger learning, flood-on-failure, Client no-relay |
| 2 | Done | RREP path splice at every hop, next_hop restamp, path repair re-RREQ |
| 3 | Done | Backup flood timers / early backup probe; link-quality soft-age + demote; skip in-packet path array |
| 4 | Done | SF-aware probe/flood delays; coalesced/piggybacked rediscovery; directed ACK/config return path; pending retarget + backup retain |
| 5 | Done | SF-scaled ACK retry spacing; adaptive store-forward wake; DELIVERED route freshness + soft-age nudge; repair-phase guardrails |
| 6 | Done | Congestion budget for STORED wake / repair floods; ACK-SNR neighbor quality → route metric; relay-loss retarget + one rediscovery |
| 7 | Done (this work) | Harden P1–P6 (return-path restamp, directed hop gate); BLE/Serial smart-routing counters; field verify checklist |
| 8+ | Later | Field gates only (multi-hop move / relay loss / congestion under load); revisit path array only if splice gaps appear |

Channel/broadcast semantics stay flood + hearer ACK (unchanged).

**Field readiness:** Phases 1–7 cover the highest remaining software delivery
leverage in the routing stack. Next value is measured multi-radio / field
validation (`docs/HARDWARE-VALIDATION.md`), not another routing phase, unless
traces show a concrete splice or congestion miss.

## Verify Smart Routing on Serial

Flash Router/Repeater nodes (not Client-only companions) and open a 115200
serial monitor. Every ~10s the node prints the routing table plus a one-line
summary:

`Smart routing: routes=N directed=… suppress=… flood=… rreq=… early=… changes=…`

Checklist after a multi-hop DM (A → relay → B):

1. **Cold start:** first DM may show `flood` and/or `rreq` rising; table gains
   a `NextHop` toward B after ACK/RREP.
2. **Warm path:** second DM should show `directed` increasing on the relay;
   originator serial shows `via 0x…` next hop, not flood.
3. **Suppress:** a third Router that is *not* the next hop should log
   `Suppress relay …` and bump `suppress` (not `directed`).
4. **Client no-relay:** a Client-role node must not increment `directed` /
   `flood` for other nodes' unicasts (it may still ACK and learn routes).
5. **Relay loss:** power off the preferred next hop; expect `early` and/or
   `rreq`, then `flood`, then a new `NextHop` after rediscovery — delivery
   should recover without leaving the phone stuck on retrying forever.
6. **Phone mirror:** Settings → mesh diagnostics shows the same counters over
   BLE (`Directed` / `Suppress` / `Flood` / `RREQ` / `Early`) plus
   `Routes` / `Changes`. Export CSV if you need a before/after record.

Do not treat rising `flood` alone as failure during first contact or right
after a relay disappears — that is the designed fallback. Failure is
sustained flood+RREQ with no route table growth and no DELIVERED.
