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
            json.dumps(record)


if __name__ == "__main__":
    unittest.main()
