# AetherMesh

A LoRa-based mesh networking system with an Android companion app. Nodes form a
peer-to-peer radio mesh over long-range LoRa, while an Android phone connects to a
nearby node over Bluetooth LE to send/receive messages and view telemetry on a map.

```
   Phone  ◀──── BLE ────▶  Node  ◀──── LoRa ────▶  Node  ◀──── LoRa ────▶  Node
 (Android app)          (firmware)              (firmware)              (firmware)
```

## Features

- Peer-to-peer LoRa mesh (broadcast + unicast with route discovery, 4-hop default)
- Text messaging with channels
- GPS telemetry (location, battery, board model) shown on an offline map
- BLE bridge between phone and node, with auto-reconnect
- Node configuration over BLE (LoRa spreading factor, bandwidth, TX power, region)
- Multi-hardware: Heltec LoRa V4, RAK WisBlock RAK4631, RAK3401 (1W)

## Repository layout

| Path | What it is |
|------|------------|
| `proto/` | Protocol Buffer definitions (`mesh.proto`) — the wire format shared by firmware and app |
| `firmware/` | PlatformIO C++ firmware for the mesh nodes (ESP32 / nRF52) |
| `app/` | Android companion app (Kotlin + Jetpack Compose) |

### Firmware (`firmware/src/`)

| File | Role |
|------|------|
| `main.cpp` | Entry point: node ID, settings (NVS), GPS, broadcast loop, OLED (Heltec) |
| `RadioManager.*` | LoRa abstraction over RadioLib / SX1262 |
| `MeshRouter.*` | Mesh routing: routing table, dedup cache, route discovery (RREQ/RREP) |
| `BLEManager.*` | BLE peripheral exposing the AetherMesh GATT service |
| `mesh.pb.{c,h}` | nanopb-generated protobuf code (see "Regenerating proto" below) |

### Android app (`app/app/src/main/java/com/example/aethermesh/`)

| File | Role |
|------|------|
| `ble/BleConnectionManager.kt` | BLE central: scan, connect, auto-reconnect, packet TX/RX |
| `ble/AetherMeshService.kt` | Foreground service keeping the BLE link alive |
| `data/AetherMeshRepository.kt` | Data layer: parses packets, exposes StateFlows |
| `data/DatabaseHelper.kt` | SQLite schema for nodes and messages |
| `ui/main/MainScreen.kt` | Compose UI: Chats, Nodes, Map, Settings, Connection tabs |
| `ui/main/MainScreenViewModel.kt` | ViewModel bridging repository ↔ UI |

## BLE GATT contract

| Item | UUID |
|------|------|
| Service | `a75e0001-8b01-4475-bf7d-9477b83e7953` |
| TX (phone → node) | `a75e0002-8b01-4475-bf7d-9477b83e7953` |
| RX (node → phone, notify) | `a75e0003-8b01-4475-bf7d-9477b83e7953` |

Nodes advertise as `AetherMesh-XXXX`, where `XXXX` is the lower 16 bits of the node ID in hex.
Packets on both BLE and LoRa are protobuf-encoded `MeshPacket`s.

## Building

### Firmware

Requires [PlatformIO](https://platformio.org/).

```bash
cd firmware
pio run -e heltec_v4            # build (default board)
pio run -e heltec_v4 -t upload  # flash
pio device monitor              # serial monitor @ 115200
```

Other environments: `rak4631`, `rak3401_1w`.

### Android app

Requires Android Studio (or the Gradle wrapper) and a device on Android 7.0+ (min SDK 24).

```bash
cd app
./gradlew :app:assembleDebug    # build APK
./gradlew :app:installDebug     # install on connected device
```

Create `app/local.properties` with your SDK path (`sdk.dir=...`) — this file is git-ignored.

## Regenerating proto

The wire format lives in `proto/mesh.proto`.

- **Android** regenerates Kotlin classes automatically via the protobuf Gradle plugin on each build.
- **Firmware** does **not** auto-generate. After editing `mesh.proto`, regenerate the nanopb C files:

  ```bash
  cd proto
  python generate_proto.py        # writes firmware/src/mesh.pb.{c,h}
  ```

  Because of this manual step, `firmware/src/mesh.pb.{c,h}` are committed to the repo.

## Hardware

| Board | Notes |
|-------|-------|
| Heltec WiFi LoRa 32 V4 | Default target; has OLED display support |
| RAK WisBlock RAK4631 | nRF52840 + SX1262 |
| RAK3401 (1W) | High-power variant (TX power up to ~30 dBm) |
