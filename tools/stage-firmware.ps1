# Builds both firmware targets and stages versioned OTA + USB artifacts into
# ota-images\. Run from anywhere:  powershell -File tools\stage-firmware.ps1
#
# Produces (hash = current git short hash):
#   aethermesh-heltec-<hash>-ota.bin   app image  -> app Settings > Firmware Update
#   aethermesh-heltec-<hash>-usb.bin   FULL image -> esptool write_flash at 0x0
#   aethermesh-rak4631-<hash>.zip      DFU package -> app OTA *and* USB
#                                      (adafruit-nrfutil dfu serial)
# See docs/FIRMWARE-FLASHING.md for the exact flash commands.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$fw = Join-Path $root "firmware"
$out = Join-Path $root "ota-images"
$pio = "$env:USERPROFILE\.platformio\penv\Scripts\pio.exe"
$py = "$env:USERPROFILE\.platformio\penv\Scripts\python.exe"
$esptool = "$env:USERPROFILE\.platformio\packages\tool-esptoolpy\esptool.py"
$bootApp0 = "$env:USERPROFILE\.platformio\packages\framework-arduinoespressif32\tools\partitions\boot_app0.bin"

Push-Location $fw
try {
    & $pio run -e heltec_v4 -e rak4631
    if ($LASTEXITCODE -ne 0) { throw "firmware build failed" }

    $hash = (git -C $root rev-parse --short=7 HEAD).Trim()
    New-Item -ItemType Directory -Force $out | Out-Null

    # Heltec: OTA app image (the app flashes it into the inactive OTA slot)
    Copy-Item ".pio\build\heltec_v4\firmware.bin" (Join-Path $out "aethermesh-heltec-$hash-ota.bin") -Force

    # Heltec: complete USB image (bootloader + partition table + otadata + app)
    # merged into one file flashable at offset 0x0
    & $py $esptool --chip esp32s3 merge_bin `
        -o (Join-Path $out "aethermesh-heltec-$hash-usb.bin") `
        0x0 ".pio\build\heltec_v4\bootloader.bin" `
        0x8000 ".pio\build\heltec_v4\partitions.bin" `
        0xe000 $bootApp0 `
        0x10000 ".pio\build\heltec_v4\firmware.bin"
    if ($LASTEXITCODE -ne 0) { throw "esptool merge_bin failed" }

    # RAK: one DFU package serves both OTA (app streams it to the bootloader)
    # and USB (adafruit-nrfutil dfu serial)
    Copy-Item ".pio\build\rak4631\firmware.zip" (Join-Path $out "aethermesh-rak4631-$hash.zip") -Force

    Write-Host ""
    Write-Host "Staged firmware $hash :"
    Get-ChildItem $out | Where-Object Name -match $hash | ForEach-Object {
        Write-Host ("  {0}  ({1:N0} bytes)" -f $_.Name, $_.Length)
    }
} finally {
    Pop-Location
}
