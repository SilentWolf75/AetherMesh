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
