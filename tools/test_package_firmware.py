import json
import tempfile
import unittest
from pathlib import Path
from tools.board_registry import BOARDS, generate
from tools.package_firmware import package

class PackagingTest(unittest.TestCase):
    def test_generated_definitions_current(self):
        generate(check=True)

    def test_all_boards_have_usb_and_ota_with_integrity(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            build, output = root / "build", root / "out"
            boot = root / "boot_app0.bin"
            boot.write_bytes(b"boot")
            for board in BOARDS:
                folder = build / board["env"]
                folder.mkdir(parents=True)
                for source in {board["usbSource"], board["otaSource"], "bootloader.bin", "partitions.bin"}:
                    (folder / source).write_bytes(b"firmware")
            commands = []
            def run(command, check):
                self.assertTrue(check)
                commands.append(command)
                Path(command[command.index("-o") + 1]).write_bytes(b"converted")
            package(build, output, "abcdef0", "1.3.0", root / "uf2conv.py", boot, run=run)
            usb = json.loads((output / "manifest.json").read_text())
            ota = json.loads((output / "ota-manifest.json").read_text())
            self.assertEqual({b["id"] for b in BOARDS}, {a["board"] for a in usb})
            self.assertEqual({b["id"] for b in BOARDS}, {a["board"] for a in ota})
            for board, command, artifact in zip(BOARDS, commands, ota):
                self.assertEqual(64, len(artifact["sha256"]))
                self.assertGreater(artifact["size"], 0)
                if board["updateFormat"] == "nordic-dfu":
                    self.assertTrue(command[2].endswith("firmware.hex"))
                    self.assertNotIn("-b", command)
                    self.assertTrue(artifact["file"].endswith(".zip"))

    def test_missing_dfu_fails_before_packaging(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            board = next(b for b in BOARDS if b["id"] == "seeed-t1000-e")
            folder = root / board["env"]
            folder.mkdir()
            (folder / "firmware.hex").write_text(":00000001FF")
            with self.assertRaises(FileNotFoundError):
                package(root, root / "out", "abcdef0", "1", root / "converter", root / "boot", boards=[board])
            self.assertFalse((root / "out").exists())
