import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.write_firmware_manifest import artifact_record


class FirmwareManifestTest(unittest.TestCase):
    def test_record_contains_size_hash_and_versioned_name(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory, "firmware.bin")
            artifact.write_bytes(b"aethermesh")
            record = artifact_record("Test Board", artifact, "abc1234")
            self.assertEqual("Test Board - abc1234 (latest)", record["name"])
            self.assertEqual(10, record["size"])
            self.assertEqual(hashlib.sha256(b"aethermesh").hexdigest(), record["sha256"])
            self.assertEqual("usb", record["kind"])
            json.dumps(record)

    def test_ota_record_includes_board(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory, "aethermesh-heltec-v4-abc1234-ota.bin")
            artifact.write_bytes(b"ota-image")
            record = artifact_record(
                "Heltec V4",
                artifact,
                "abc1234",
                kind="ota",
                board="heltec-v4",
            )
            self.assertEqual("ota", record["kind"])
            self.assertEqual("heltec-v4", record["board"])


if __name__ == "__main__":
    unittest.main()
