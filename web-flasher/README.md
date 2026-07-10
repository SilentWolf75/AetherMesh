# AetherMesh Web Flasher

A static page for first-time setup and recovery flashing from the browser.
ESP32-S3 targets use the Web Serial API with
[esptool-js](https://github.com/espressif/esptool-js). nRF52 targets use UF2
drag-and-drop firmware downloads. Day-to-day updates go over Bluetooth from the
Android app once a node already has OTA-capable firmware.

## How it's served

`pages.yml` builds Heltec V4, Heltec V3, RAK4631, RAK3401 1W, LILYGO T-Echo,
LILYGO T-Deck, Elecrow CrowPanel 3.5, and the Android APK. It merges complete ESP32-S3 USB images,
converts nRF52 builds to UF2 where needed, drops everything into `firmware/` with a
`manifest.json`, and deploys this directory to GitHub Pages. The firmware
binaries are built fresh each deploy and are not committed.

## One-time repo setup

GitHub -> **Settings -> Pages -> Build and deployment -> Source: GitHub Actions**.
The workflow runs on pushes that touch `web-flasher/`, `firmware/`, `proto/`,
`app/`, or the Pages workflow.

## Browser support

Desktop **Chrome / Edge / Opera** only. Web Serial is not available in
Firefox/Safari, and mobile browser support is limited.

## Local testing

Serve the folder over http. Web Serial needs a secure context, and `localhost`
counts:

```bash
cd web-flasher
python -m http.server 8000
```

Then open `http://localhost:8000`.

## Supported targets

- Heltec V4 / ESP32-S3
- Heltec V3 / ESP32-S3
- LILYGO T-Deck / ESP32-S3
- Elecrow CrowPanel 3.5 / ESP32-S3
- RAK4631 / nRF52
- RAK3401 1W / nRF52
- LILYGO T-Echo / nRF52
