# Flashing AetherMesh Firmware

The GitHub Pages workflow builds the current web flasher bundle for Heltec V4,
Heltec V3, RAK4631, RAK3401 1W, LILYGO T-Echo, LILYGO T-Deck, and the Android APK.

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

### App OTA

Use the board-specific OTA `.bin`. The local staging helper currently creates
the Heltec V4 OTA `.bin`; other ESP32-S3 boards can be built with PlatformIO and
packaged the same way.

1. Copy the `.bin` file to the phone.
2. In the app, connect to the node.
3. Open Settings -> Firmware Update.
4. Choose the `.bin` file and start the BLE OTA update.

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

Flash the merged image at offset `0x0`:

```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\python.exe" `
  "$env:USERPROFILE\.platformio\packages\tool-esptoolpy\esptool.py" `
  --chip esp32s3 --port COM8 --before no_reset --after hard_reset `
  write_flash -z 0x0 ota-images\aethermesh-heltec-v4-<hash>-usb.bin
```

Adjust the COM port and filename for the board. If auto-reset is unreliable,
put the ESP32-S3 board into download mode before flashing.

## RAK4631 / RAK3401 1W

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

## Web Flasher

[https://silentwolf75.github.io/AetherMesh/](https://silentwolf75.github.io/AetherMesh/)

The browser flasher is for first-time setup and recovery. It currently presents:

- Heltec V4 / ESP32-S3
- Heltec V3 / ESP32-S3
- LILYGO T-Deck / ESP32-S3
- RAK4631 / nRF52
- RAK3401 1W / nRF52
- LILYGO T-Echo / nRF52

Desktop Chrome, Edge, or Opera is required for Web Serial flashing. For nRF52
boards, the page provides UF2 downloads for drag-and-drop bootloader flashing.

## Rules of Thumb

- Do not flash firmware for one board family onto another board family.
- Keep one known-good USB recovery path before testing OTA or DFU changes.
- Firmware version is printed on boot, in serial logs, and in the app's node
  details when telemetry is received.
