from pathlib import Path
from tempfile import TemporaryDirectory
import csv
import unittest

from tools.hardware_qualification import CHECKS, FIELDS, TARGETS, create_template, validate


class HardwareQualificationTest(unittest.TestCase):
    def test_template_contains_every_required_check(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "qualification.csv"
            create_template(path)
            report = validate(path)
            self.assertEqual(len(TARGETS) * len(CHECKS), report["required_checks"])
            self.assertEqual(len(TARGETS) * len(CHECKS), report["results"]["PENDING"])
            self.assertFalse(report["complete"])

    def test_all_pass_record_is_complete(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "qualification.csv"
            with path.open("w", newline="", encoding="utf-8") as handle:
                writer = csv.DictWriter(handle, fieldnames=FIELDS)
                writer.writeheader()
                for target in TARGETS:
                    for check in CHECKS:
                        writer.writerow({"target": target, "check": check, "result": "PASS"})
            self.assertTrue(validate(path)["complete"])

    def test_duplicate_and_missing_rows_are_rejected(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "qualification.csv"
            with path.open("w", newline="", encoding="utf-8") as handle:
                writer = csv.DictWriter(handle, fieldnames=FIELDS)
                writer.writeheader()
                writer.writerow({"target": TARGETS[0], "check": CHECKS[0], "result": "PASS"})
                writer.writerow({"target": TARGETS[0], "check": CHECKS[0], "result": "PASS"})
            report = validate(path)
            self.assertFalse(report["complete"])
            self.assertTrue(any("duplicate" in error for error in report["errors"]))


if __name__ == "__main__":
    unittest.main()
