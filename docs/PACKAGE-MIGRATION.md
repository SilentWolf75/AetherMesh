# Package identity migration (`com.example.aethermesh` → `com.silentwolf75.aethermesh`)

Android treats `applicationId` as the permanent install identity. Renaming the package
does **not** upgrade an existing install — it installs a **second app** with an empty
database and a fresh Keystore. Message history, node directory, channel PSKs, and
paired-node passwords do not carry over automatically.

## Deliberate choice (field units)

Before distributing builds under `com.silentwolf75.aethermesh` to phones that already
run `com.example.aethermesh`:

| Option | When to use |
|--------|-------------|
| **Accept reset** | Test phones, empty history OK, or you will re-pair nodes manually |
| **Export → import** | Any phone with message history or stored node passwords you need to keep |

There is no silent in-place upgrade across package IDs.

## Export → import procedure

Do this **on the old app before uninstalling it** (while Keystore can still decrypt secrets).

1. Open **Settings → Developer → Data & Logs Management**
2. Tap **Export app data (package migration)** — saves `aethermesh_migration_*.json`
3. Copy the file somewhere safe (USB, cloud, email to yourself). **It contains passwords and chat keys in plaintext.**
4. Install **AetherMesh** (`com.silentwolf75.aethermesh`) — sideload or Play when published
5. Open **Settings → Developer → Import app data (package migration)** and pick the JSON file
6. Uninstall the old `com.example.aethermesh` app when satisfied

The new app shows a one-time banner if the legacy package is still installed.

## What is included in the migration file

- Chat messages (SQLite)
- Node directory
- Channel definitions
- Chat encryption keys (DB + secure prefs)
- Node admin passwords and ECDH material (secure prefs)
- App preferences (`aethermesh_prefs`, non-secret keys)
- Per-node settings files (`node_settings_*`)

## What is **not** included

- BLE bonding / GATT pairing (reconnect and re-enter node BLE password if needed)
- Keystore master key (by design — secrets are exported explicitly in step 2)
- Android Auto Backup of encrypted prefs (excluded — see below)

## Backup rules and encrypted prefs

`EncryptedSharedPreferences.create("aethermesh_secure_prefs", …)` stores ciphertext that
**cannot** be restored without the device Keystore key. With `allowBackup="true"`, both
`backup_rules.xml` and `data_extraction_rules.xml` must exclude that exact filename
(no `.xml` suffix) in **cloud backup and device transfer** sections. The name is defined
once in `SecurePrefsNames.kt` and validated by `BackupRulesTest`.

## Play / sideload note

Google Play rejects `com.example.*`. The rename is required for store listing; plan the
migration window while the install base is still small.
