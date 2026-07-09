# AetherMesh Web Flasher

A static page that flashes a **Heltec V4** node over USB from the browser using
the Web Serial API + [esptool-js](https://github.com/espressif/esptool-js) — no
install needed. For the first-time USB bootstrap and recovery; day-to-day
updates go over Bluetooth from the app.

## How it's served
`pages.yml` builds the Heltec firmware, merges the complete USB image
(bootloader + partitions + otadata + app) with `esptool merge_bin`, drops it
into `firmware/` with a `manifest.json`, and deploys this directory to GitHub
Pages. The firmware binaries are built fresh each deploy and are **not**
committed (see `firmware/.gitignore`).

## One-time repo setup
GitHub → **Settings → Pages → Build and deployment → Source: GitHub Actions**.
The workflow runs on pushes that touch `web-flasher/`, `firmware/`, or `proto/`.

## Browser support
Desktop **Chrome / Edge / Opera** only (Web Serial isn't in Firefox/Safari and
mobile support is thin).

## Local testing
Serve the folder over http (Web Serial needs a secure context; `localhost`
counts) and provide a `-usb.bin` via the file picker:
```
cd web-flasher && python -m http.server 8000
# open http://localhost:8000
```

## Roadmap
- RAK4631 (nRF52): add a `.uf2` build output + drag-drop flow, or in-browser
  serial DFU.
- Pull versioned builds from GitHub Releases instead of only "latest".
