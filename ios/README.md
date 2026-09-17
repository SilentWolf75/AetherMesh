# AetherMesh for iOS

Early iOS companion app. It talks to the same AetherMesh nodes and uses the same
wire format and encryption as the Android app.

| Path | What it is | Verified |
|------|------------|----------|
| `AetherMeshKit/` | Swift package: protobuf types, chat and control crypto, packet policies, and `MeshSession` (login, chat, nodes, delivery status, traceroute) | `swift test` on Linux and in CI, against the firmware/Android test vectors |
| `AetherMesh/` | SwiftUI app: CoreBluetooth transport, Keychain storage, Connect/Chat/Nodes screens | Not compiled locally (needs Xcode). CI builds it for the simulator on macOS |
| `project.yml` | XcodeGen spec that produces `AetherMesh.xcodeproj` | |

## What works in this first version

- Scan for nodes, connect, and log in with the node password (optionally saved in the Keychain).
- Channel chat, encrypted with the channel key when one is set. It never falls back to plaintext if encryption fails.
- Heard-node list with battery, SNR and firmware version.
- Traceroute, including extended-range (compact) traces.
- Delivery states for sent messages.

Not yet: direct-message UI, message history across app restarts, node settings,
remote config, firmware updates, map, notifications. The session already
supports DMs (`sendText(_:to:)`); only the screen is missing.

Legacy AES-ECB chat messages are intentionally not decrypted: ECB has no
integrity check, so forged messages would be indistinguishable. AES-GCM
formats (v1 and v2) are supported.

## Build on a Mac

1. Install Xcode 16 or newer and [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`.
2. `cd ios && xcodegen generate && open AetherMesh.xcodeproj`
3. In Signing & Capabilities, choose your team. A free Apple ID can install on your own iPhone for 7 days; TestFlight or the App Store need a paid Apple Developer account.
4. Run on a real iPhone. The simulator has no Bluetooth.

## Test the core on any machine

```bash
cd ios/AetherMeshKit && swift test
```

Works on macOS or Linux with Swift 6.x.

## Regenerate protobuf types

After changing `proto/mesh.proto`, regenerate firmware and Android as usual, then:

```bash
python proto/generate_swift_proto.py --protoc PATH/TO/protoc --plugin-cmd PATH/TO/protoc-gen-swift
```

Build `protoc-gen-swift` from the swift-protobuf version in `AetherMeshKit/Package.resolved`
(`swift build -c release --product protoc-gen-swift`).
