# Security

AetherMesh is an offline-first LoRa mesh for emergency and field communications. Report security issues responsibly — do not open public GitHub issues for undisclosed vulnerabilities.

## Reporting

Email the maintainer via the contact on [GitHub](https://github.com/SilentWolf75/AetherMesh) or open a **private** security advisory on the repository if you have GitHub access.

Include: affected component (app / firmware / web flasher), version or commit, reproduction steps, and impact.

## Scope

| In scope | Out of scope |
|----------|----------------|
| Mesh protocol, BLE GATT, remote config auth, replay protection | Third-party map tile servers |
| Web flasher USB/BLE write path | User-chosen weak node passwords |
| GitHub release artifact integrity (SHA-256 sums) | Physical device theft |

## Threat model (summary)

- **Mesh radio** is broadcast; assume anyone in RF range can observe traffic. Use channel PSKs and node admin passwords.
- **Remote config** (LoRa) requires admin password + authenticated envelope (protocol v2/v3). v3 derives the HMAC key with PBKDF2 **once at boot / password change** (cached); v2/plaintext are refused when `refuse_legacy` is set. See [docs/CONTROL-AUTH.md](docs/CONTROL-AUTH.md).
- **Replay / auth-fail limits:** global fail-closed counter plus per-sender LRU buckets (`sender_id` is unauthenticated).
- **Android backup** excludes Keystore-bound encrypted prefs to avoid restore crash loops. Filenames are defined in `SecurePrefsNames.kt` and must match both `backup_rules.xml` and `data_extraction_rules.xml`.
- **Package rename** (`com.example.aethermesh` → `com.silentwolf75.aethermesh`) is a separate install — see [docs/PACKAGE-MIGRATION.md](docs/PACKAGE-MIGRATION.md).
- **No MQTT / internet gateway** in the companion app — mesh stays local; phone internet is optional for firmware catalog only.

See also [docs/SECURE-RELEASES.md](docs/SECURE-RELEASES.md) for release signing and OTA trust, and [docs/CONTROL-AUTH.md](docs/CONTROL-AUTH.md) for remote-config v2→v3 deprecation.
