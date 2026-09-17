# Board definitions and release files

Edit config/boards.json to change board identities, model/file aliases, update
formats, and expected build artifacts. Run python tools/board_registry.py to
refresh the checked-in Android and browser definitions. Release validation rejects
stale generated files and mismatches with PlatformIO, CI, Pages, and the flasher.

tools/package_firmware.py uses the same definitions for both USB and OTA
manifests. It requires all source artifacts before packaging and records each
board ID, size, and SHA-256. ESP32 boards use merged USB binaries and separate OTA
binaries. Nordic boards use addressed HEX input for UF2 and DFU ZIP for Bluetooth;
the converter must preserve the HEX addresses rather than assign a common offset.

Board-specific runtime controls and peripheral wiring remain in firmware.
Hardware qualification targets also come from this registry. Adding a definition
does not certify hardware operation; follow HARDWARE-VALIDATION.md.
