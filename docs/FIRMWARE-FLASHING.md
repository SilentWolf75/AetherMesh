# Flashing AetherMesh firmware

Build + stage all artifacts (into `ota-images\`, named by git hash):

```powershell
powershell -File tools\stage-firmware.ps1
```

## Heltec V4

### OTA (normal path — no cables)
File: `aethermesh-heltec-<hash>-ota.bin`
1. Copy the file to the phone.
2. App → Settings → Firmware Update → Choose firmware .bin → Update via BLE OTA.
3. Node verifies the image (MD5), reboots into it. A failed transfer leaves the
   running firmware untouched. Takes ~1 min (fast profile) or ~3 min against
   older firmware (auto-negotiated).

### USB (bootstrap / recovery)
File: `aethermesh-heltec-<hash>-usb.bin` (complete image: bootloader +
partition table + otadata + app).
1. Enter download mode: **hold PRG**, tap **RST**, release PRG (screen stays
   blank). Auto-reset flashing is unreliable on these boards — always do this.
2. Flash at offset 0x0:
```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\python.exe" `
  "$env:USERPROFILE\.platformio\packages\tool-esptoolpy\esptool.py" `
  --chip esp32s3 --port COM8 --before no_reset --after hard_reset `
  write_flash -z 0x0 ota-images\aethermesh-heltec-<hash>-usb.bin
```
(Adjust the COM port; `pio device list` shows it — Heltec is VID 303A.)

## RAK4631

One file serves both paths: `aethermesh-rak4631-<hash>.zip` (Nordic DFU package).

### OTA (normal path)
1. Copy the .zip to the phone.
2. Connect the app to the RAK → Settings → Firmware Update → Choose firmware
   .zip → Update. The node reboots into its DFU bootloader and Android's DFU
   service streams the package to it (watch the % on the Firmware Update
   screen). If the transfer never starts, the bootloader falls back to the
   current firmware.

### USB (bootstrap / recovery)
```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\pio.exe" run -e rak4631 -t upload --upload-port COM11
```
or flash a staged zip directly with adafruit-nrfutil:
```powershell
adafruit-nrfutil dfu serial --package ota-images\aethermesh-rak4631-<hash>.zip -p COM11 -b 115200
```
(RAK is VID 239A. If the port won't respond, double-tap RST to force the
bootloader.)

## Rules of thumb
- The updater app stays compatible with old firmware (transfer parameters are
  negotiated), but a node must already run an OTA-capable build (>= item 45)
  to receive OTA at all — older nodes need one USB flash first.
- Never OTA a Heltec image to a RAK or vice versa.
- Firmware version is printed on the boot splash (Heltec), in the serial boot
  banner, in every 30 s "Radio health" serial line, and on each node card in
  the app.
