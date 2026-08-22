# Contributing to AetherMesh

Thank you for helping improve offline mesh tooling. This project targets field reliability over feature count.

## Before you start

1. Read [LICENSE](LICENSE) and [NOTICE](NOTICE) for third-party attribution.
2. Check open issues and [docs/RELEASE-NOTES-1.3.md](docs/RELEASE-NOTES-1.3.md) for recent changes.
3. Keep changes focused — small PRs review faster than kitchen-sink diffs.

## Development setup

| Component | Command |
|-----------|---------|
| Firmware (Heltec V4) | `cd firmware && pio run -e heltec_v4` |
| Firmware unit tests | `cd firmware && pio test -e native` (needs host g++ and `libssl-dev` / OpenSSL) |
| Android app | `cd app && ./gradlew :app:assembleDebug` |
| Android unit tests | `cd app && ./gradlew :app:testDebugUnitTest` |
| Release target parity | `python tools/validate_release_targets.py` |
| Web flasher syntax | `node tools/check_web_flasher.js` |

## Pull requests

- Target branch: `main`
- CI must pass (firmware matrix, native tests, Android build + unit tests, simulator, flasher check).
- Bump [VERSION](VERSION) when changing app or firmware release numbers.
- Do **not** commit `hardware/` autorouter scratch — see [hardware/README.md](hardware/README.md).
- Do **not** commit secrets (`.env`, keystores).

## Code style

- **Kotlin:** match existing Compose/Material patterns; prefer clear operator-facing strings (EN; ES where the screen already bilingual).
- **Firmware:** minimize `#ifdef` sprawl in new code; add native tests for pure logic when possible.
- **Commits:** one logical change per commit; complete sentences in the subject line.

## Security

See [SECURITY.md](SECURITY.md) for vulnerability reporting.

## Package identity change

Renaming `applicationId` installs a **second app** — not an upgrade. See [docs/PACKAGE-MIGRATION.md](docs/PACKAGE-MIGRATION.md) before field rollout.
