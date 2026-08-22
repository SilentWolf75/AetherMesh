# Changelog

Release history for AetherMesh. Detailed phase notes live in [docs/RELEASE-NOTES-1.3.md](docs/RELEASE-NOTES-1.3.md).

## [1.3.5] — unreleased

### App
- Release builds enable R8 minify; ProGuard keep rules exercised in CI.
- Remote config auth protocol v3 (AMCFG3 + PBKDF2-derived HMAC key).
- `security-crypto` 1.0.0 stable; encrypted prefs excluded from backup/transfer (see `SecurePrefsNames`).
- **Package migration:** export/import JSON + banner when legacy `com.example.aethermesh` is installed ([docs/PACKAGE-MIGRATION.md](docs/PACKAGE-MIGRATION.md)).
- Version strings centralized in [VERSION](VERSION).

### Firmware 1.3.2
- Remote config auth v3 (PBKDF2) with v2 backward compatibility; **cached** PBKDF2 key (derive on boot/password change only).
- `refuse_legacy` setting to reject protocol &lt; 3 (documented deprecation window).
- Replay slots 8→16; fail-closed auth rate limit (global + per-sender LRU); reduced NVS write churn.
- See [docs/CONTROL-AUTH.md](docs/CONTROL-AUTH.md).

### Tooling / CI
- Pinned GitHub Actions; Dependabot for Actions; SECURITY.md + CONTRIBUTING.md.
- DB migration chain static checks; release target + version parity validation.
- Python lint (ruff) and Android lint in CI.

## [1.3.4] — 2026-08-22

- Package rename to `com.silentwolf75.aethermesh` (Play-ready identity).
- LICENSE + NOTICE; hardware scratch gitignore; backup rules for encrypted prefs.

## [1.3.0] — 2026-08

- Phases I–L: field reliability toolkit, leave-behind ops polish, release readiness.
- See [docs/RELEASE-NOTES-1.3.md](docs/RELEASE-NOTES-1.3.md).

## Earlier

- 1.2.x: smart routing, channel ACKs, OTA trust channels, UI polish rounds.
- See git history and [docs/FIXES-2026-07-03.md](docs/FIXES-2026-07-03.md).
