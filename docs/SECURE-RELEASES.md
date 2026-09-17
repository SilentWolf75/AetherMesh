# Secure Releases

AetherMesh publishes SHA-256 checksums and GitHub build-provenance attestations
for the Android APK and every firmware artifact. ESP32 BLE OTA additionally
streams and verifies a SHA-256 digest before the new image is finalized.

## Android Signing

The Pages workflow builds a release-signed APK when these repository secrets
are configured:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Create and retain the production key offline. Never commit the keystore or its
passwords. If the secrets are absent, the workflow publishes a debug-signed APK
and writes `ANDROID_BUILD_TYPE.txt` beside it so the release type is explicit.

## Published Evidence

- `SHA256SUMS.txt` detects damaged or substituted downloads.
- `firmware/manifest.json` gives the browser the expected size and digest.
- GitHub artifact attestations bind published files to the repository workflow
  and commit that produced them.
- A complete hardware qualification CSV binds field results to the same commit.

Before announcing a field-stable release, verify the attestation with GitHub's
CLI and require the hardware qualification validator to report `complete: true`.

```bash
gh attestation verify aethermesh-app.apk --repo SilentWolf75/AetherMesh
python tools/hardware_qualification.py release-hardware.csv
```

Maintainer signing keys are intentionally not present in this repository.

## OTA trust (Phase H — pragmatic)

The companion app (1.2.6+) hardens wireless updates without requiring image
signing keys in-tree:

1. **Path separation** — Heltec/ESP32 BLE OTA accepts `.bin` only; RAK Nordic
   DFU accepts `.zip` only. USB `-usb.bin` / `.uf2` packages are refused for BLE.
2. **Board match** — asset / filename board ids (`heltec-v4`, `rak4631`, …) must
   match the connected node’s telemetry model when inferable.
3. **Integrity** — GitHub Pages `ota-manifest.json` downloads verify size +
   SHA-256. GitHub Release assets verify size (API has no digest field).
4. **Channels** — **Stable** prefers non-prerelease GitHub Releases assets
   tagged/named for PlatformIO envs (`heltec_v4`, `rak4631`, …). **Latest** uses
   the Pages catalog. Publish field-stable firmware as a GitHub Release with
   board-named OTA assets so Stable has something to pick.

### Follow-up (not in Phase H)

- Detached signature or signed manifest over OTA images (Ed25519 / cosign).
- Firmware-side reject of images that fail signature / board-id header checks.
- Attestation verify from the phone (optional; Pages already attests in CI).

## Release Channels

| Channel | Workflow | Gate |
| --- | --- | --- |
| Stable | `stable-release.yml` | Hardware qualification evidence for the exact commit, plus CI |
| Beta | `beta-release.yml` | CI only — that is what makes it a beta |
| Latest | `pages.yml` | CI on every push to `main` |

Both release workflows attach `manifest.json` (USB images) and
`ota-manifest.json` (over-the-air images) alongside the binaries. Those manifests
carry the SHA-256 of every artifact, and both the browser flasher and the Android
app refuse to install anything that does not match. A release published without
its manifest is invisible to both clients by design, rather than being installed
unverified.

Beta tags must contain `-beta.`; the workflow refuses anything else, so an
unqualified build cannot be published under a name people read as qualified.
