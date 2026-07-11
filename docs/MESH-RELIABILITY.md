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
- Stable route selection: refresh the current next hop, accept a better metric,
  and reject worse fresh candidates.
- Proxy route replies only from recently observed routes.
- Two-way traceroute with up to eight observed hops in each direction, per-link
  RSSI/SNR, truncation reporting, and route learning at every forwarding node.
- Configured node names in telemetry, with explicit ownership metadata so local
  aliases are not overwritten by network-advertised defaults.

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

The next protocol step should be a deterministic simulator or multi-radio test
harness that records these metrics before and after each routing change.

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
