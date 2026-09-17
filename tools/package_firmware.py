"""Package every supported board using the shared release definitions."""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

if __package__:
    from .board_registry import BOARDS
    from .write_firmware_manifest import artifact_record
else:
    from board_registry import BOARDS
    from write_firmware_manifest import artifact_record

def package(build_root, output, commit, version, converter, boot_app0, boards=BOARDS, run=subprocess.run):
    # Validate all inputs before producing any release artifacts.
    for board in boards:
        folder = build_root / board["env"]
        sources = [board["usbSource"], board["otaSource"]]
        if board["updateFormat"] == "esp-bin":
            sources += ["bootloader.bin", "partitions.bin"]
            if not boot_app0.is_file():
                raise FileNotFoundError(boot_app0)
        for name in sources:
            if not (folder / name).is_file():
                raise FileNotFoundError(folder / name)
    output.mkdir(parents=True, exist_ok=True)
    usb_records, ota_records = [], []
    for board in boards:
        folder = build_root / board["env"]
        prefix = f'aethermesh-{board["stem"]}-{commit}'
        nordic = board["updateFormat"] == "nordic-dfu"
        usb = output / (prefix + (".uf2" if nordic else "-usb.bin"))
        ota = output / (prefix + (".zip" if nordic else "-ota.bin"))
        if nordic:
            # HEX carries the correct link address, including each SoftDevice layout.
            command = [sys.executable, str(converter), str(folder / board["usbSource"]),
                       "-c", "-f", "0xADA52840", "-o", str(usb)]
        else:
            command = [sys.executable, "-m", "esptool", "--chip", "esp32s3", "merge_bin",
                       "-o", str(usb), "0x0", str(folder / "bootloader.bin"),
                       "0x8000", str(folder / "partitions.bin"), "0xe000", str(boot_app0),
                       "0x10000", str(folder / board["usbSource"])]
        run(command, check=True)
        shutil.copyfile(folder / board["otaSource"], ota)
        for records, path, kind in [(usb_records, usb, "usb"), (ota_records, ota, "ota")]:
            records.append(artifact_record(board["label"], path, commit, kind=kind,
                                           board=board["id"], version=version))
    for name, records in [("manifest.json", usb_records), ("ota-manifest.json", ota_records)]:
        (output / name).write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--build-root", type=Path, default=Path("firmware/.pio/build"))
    parser.add_argument("--output", type=Path, default=Path("web-flasher/firmware"))
    parser.add_argument("--hash", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--converter", type=Path, required=True)
    parser.add_argument("--boot-app0", type=Path, required=True)
    args = parser.parse_args()
    package(args.build_root, args.output, args.hash, args.version, args.converter, args.boot_app0)
