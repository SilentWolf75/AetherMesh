# Flashing AetherMesh Firmware

The GitHub Pages workflow builds the current web flasher bundle for Heltec V4,
Heltec V3, RAK4631, RAK3401 1W, RAK19026, LILYGO T-Echo, SenseCAP T1000-E,
LILYGO T-Deck, Elecrow CrowPanel 3.5, and the Android APK.

The local staging helper is older and currently stages the Heltec V4 app/USB
images plus the RAK4631 DFU zip into `ota-images\`, named by the current git
hash:

```powershell
powershell -File tools\stage-firmware.ps1
```

## ESP32-S3 Boards

Current ESP32-S3 firmware targets:

- `heltec_v4`
- `heltec_v3`
- `lilygo_t_deck`
- `elecrow_crowpanel_35`

### App OTA

Use the board-specific OTA `.bin`. The local staging helper currently creates
the Heltec V4 OTA `.bin`; other ESP32-S3 boards can be built with PlatformIO and
packaged the same way.

1. Copy the `.bin` file to the phone.
2. In the app, connect to the node.
3. Open Settings -> Firmware Update.
4. Choose the `.bin` file and start the BLE OTA update.

Or download from GitHub inside the app:

1. Connect and authenticate the node.
2. Open **Settings → Firmware Update**.
3. Choose **Stable** (GitHub Releases) or **Latest** (Pages `ota-manifest.json`).
4. Tap **Check GitHub…**, then **Download**. The app picks the asset for the
   connected board (`heltec_v4` / `rak4631` env tags in Release names, or
   manifest `board` ids), verifies size (+ SHA-256 on Pages), then flash with
   **Update via BLE OTA**.
5. Wrong family (Heltec `.bin` vs RAK `.zip`) or wrong board id is refused with
   an on-screen error. After a failed mid-transfer, follow the in-app recovery
   note or use the USB web flasher.

The Pages deploy publishes both the USB/UF2 web-flasher `manifest.json` and the
app-facing `ota-manifest.json` (ESP32 OTA bins + RAK DFU zips). Do not flash a
`-usb.bin` over BLE OTA. For field-stable builds, attach the same `-ota.bin` /
`.zip` files to a GitHub Release so the Stable channel can find them.

The node writes to the inactive OTA partition, verifies the image, and reboots
only after a successful transfer. A failed transfer leaves the current firmware
in place.

The first OTA-capable build still has to be installed over USB. A node that does
not already contain the OTA receiver cannot receive an OTA update.

### USB Bootstrap / Recovery

Use the board-specific merged USB image. The GitHub Pages workflow creates
merged USB images for the current ESP32-S3 web flasher targets. The local helper
currently creates the Heltec V4 merged image.

Example filenames:

- `aethermesh-heltec-v4-<hash>-usb.bin`
- `aethermesh-heltec-v3-<hash>-usb.bin`
- `aethermesh-t-deck-<hash>-usb.bin`
- `aethermesh-crowpanel-35-<hash>-usb.bin`

Flash the merged image at offset `0x0`:

```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\python.exe" `
  "$env:USERPROFILE\.platformio\packages\tool-esptoolpy\esptool.py" `
  --chip esp32s3 --port COM8 --before no_reset --after hard_reset `
  write_flash -z 0x0 ota-images\aethermesh-heltec-v4-<hash>-usb.bin
```

Adjust the COM port and filename for the board. If auto-reset is unreliable,
put the ESP32-S3 board into download mode before flashing.

## RAK4631 / RAK3401 1W / RAK19026

RAK boards use the Nordic/Adafruit DFU bootloader path.

### App DFU

Use the staged `.zip` package:

```text
aethermesh-rak4631-<hash>.zip
```

1. Copy the `.zip` file to the phone.
2. Connect the app to the RAK node.
3. Open Settings -> Firmware Update.
4. Choose the `.zip` package and start the update.

The node reboots into its DFU bootloader and the Android DFU service streams the
package. If the transfer never starts, the bootloader falls back to the current
firmware.

### USB Bootstrap / Recovery

```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\pio.exe" run -e rak4631 -t upload --upload-port COM11
```

Or flash a staged zip directly with `adafruit-nrfutil`:

```powershell
adafruit-nrfutil dfu serial --package ota-images\aethermesh-rak4631-<hash>.zip -p COM11 -b 115200
```

RAK boards usually appear as VID `239A`. If the port will not respond,
double-tap reset to force bootloader mode.

## LILYGO T-Echo

The T-Echo firmware target is:

```text
lilygo_t_echo
```

The web flasher provides a UF2 build for the T-Echo. Use the board bootloader's
mounted USB drive and drag the UF2 file onto it.

## SenseCAP Card Tracker T1000-E

Credit-card tracker (nRF52840 + Semtech LR1110 + Mediatek AG3335). PlatformIO
env:

```text
seeed_t1000_e
```

Seeed's docs (Meshtastic-oriented, same hardware) are useful for flash recovery:

- [Get started / flash](https://wiki.seeedstudio.com/sensecap_t1000_e/)
- [Tracker introduction / pins](https://wiki.seeedstudio.com/t1000_e_intro/)
- [Open-source LoRaWAN examples](https://wiki.seeedstudio.com/open_source_lorawan/)
  (SES/Arduino LoRaWAN — **not** AetherMesh; same DFU/bootloader)

AetherMesh stays on our mesh stack (not LoRaWAN / SES examples). Flash only
`seeed_t1000_e` builds onto the **Meshtastic-capable** T1000-E SKU — Seeed
sells a separate LoRaWAN SKU; do not cross-flash those factory images. Wrong
nRF52 UF2 images can brick the unit. Do **not** use generic NRF-OTA tools from
their FAQ.

Hardware notes from Seeed (pins already in `variants/Seeed_T1000-E`):

- LED on P0.24, PWM buzzer on P0.25
- Press once to power on (rising tone; LED ~1s)
- Charge with a normal USB charger — not a fast-charge brick

### USB / UF2

1. Connect the magnetic charging cable to the PC.
2. Enter DFU: hold the button, then quickly seat / double-tap the cable until
   the green LED stays solid and a mass-storage drive named **T1000-E** mounts
   (serial may show as `T1000-E xxx`).
3. Drag `aethermesh-t1000-e-<hash>.uf2` onto that drive (app base `0x27000`,
   SoftDevice S140 7.3.0). Wait for the drive to disappear / reboot.
4. Or: `pio run -e seeed_t1000_e -t upload` (adafruit-nrfutil / 1200 bps touch).

Hard reset if the unit is wedged: unplug, hold button, plug in, hold ~3s,
release. Bootloader recovery uses Seeed's
`t1000_e_bootloader-…_s140_7.3.0.zip` via adafruit-nrfutil serial DFU — do not
substitute another nRF52 board UF2.

### App DFU

Use the Nordic DFU zip from Pages / a local build:

```text
aethermesh-t1000-e-<hash>.zip
```

Same phone path as RAK: Settings → Firmware Update → pick the zip.

## Web Flasher

[https://silentwolf75.github.io/AetherMesh/](https://silentwolf75.github.io/AetherMesh/)

The browser flasher is for first-time setup and recovery. It currently presents:

- Heltec V4 / ESP32-S3
- Heltec V3 / ESP32-S3
- LILYGO T-Deck / ESP32-S3
- Elecrow CrowPanel 3.5 / ESP32-S3
- RAK4631 / nRF52
- RAK3401 1W / nRF52
- RAK19026 / nRF52
- LILYGO T-Echo / nRF52
- SenseCAP T1000-E / nRF52 + LR1110

Desktop Chrome, Edge, or Opera is required for Web Serial flashing. For nRF52
boards, the page provides UF2 downloads for drag-and-drop bootloader flashing.

## Rules of Thumb

- Do not flash firmware for one board family onto another board family.
- Keep one known-good USB recovery path before testing OTA or DFU changes.
- Firmware version is printed on boot, in serial logs, and in the app's node
  details when telemetry is received.
