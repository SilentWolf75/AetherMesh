"""Create and validate AetherMesh hardware qualification records."""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter
from datetime import datetime
from pathlib import Path

try:
    from .board_registry import BOARDS
except ImportError:
    from board_registry import BOARDS
TARGETS = tuple(board["env"] for board in BOARDS)
CHECKS = (
    "boot_ui", "flash_recovery", "ble_reconnect", "delivery",
    "multihop", "route_failover", "settings_persistence", "soak_12h",
)
FIELDS = ("target", "check", "result", "commit", "region", "antenna", "tester", "timestamp_utc", "notes")


def create_template(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        for target in TARGETS:
            for check in CHECKS:
                writer.writerow({"target": target, "check": check, "result": "PENDING"})


def validate(path: Path, expected_commit: str | None = None) -> dict:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        missing_columns = set(FIELDS) - set(reader.fieldnames or ())
        if missing_columns:
            raise ValueError(f"Missing columns: {', '.join(sorted(missing_columns))}")
        rows = list(reader)

    seen: dict[tuple[str, str], str] = {}
    errors: list[str] = []
    for line, row in enumerate(rows, 2):
        target, check = row["target"].strip(), row["check"].strip()
        result = row["result"].strip().upper()
        if target not in TARGETS:
            errors.append(f"line {line}: unknown target {target!r}")
            continue
        if check not in CHECKS:
            errors.append(f"line {line}: unknown check {check!r}")
            continue
        key = (target, check)
        if key in seen:
            errors.append(f"line {line}: duplicate {target}/{check}")
            continue
        if result not in {"PASS", "FAIL", "BLOCKED", "PENDING"}:
            errors.append(f"line {line}: invalid result {result!r}")
        if result == "PASS":
            commit = row["commit"].strip()
            if not re.fullmatch(r"[0-9a-f]{40}", commit):
                errors.append(f"line {line}: PASS requires a full lowercase commit SHA")
            if expected_commit is not None and commit != expected_commit:
                errors.append(f"line {line}: tested commit does not match release commit")
            for field in ("region", "antenna", "tester", "timestamp_utc"):
                if not row[field].strip():
                    errors.append(f"line {line}: PASS requires {field}")
            try:
                timestamp = datetime.fromisoformat(row["timestamp_utc"].strip().replace("Z", "+00:00"))
                if timestamp.utcoffset() is None or timestamp.utcoffset().total_seconds() != 0:
                    errors.append(f"line {line}: timestamp_utc must have a UTC offset")
            except ValueError:
                errors.append(f"line {line}: invalid timestamp_utc")
        seen[key] = result

    expected = {(target, check) for target in TARGETS for check in CHECKS}
    for target, check in sorted(expected - set(seen)):
        errors.append(f"missing {target}/{check}")
    commits = {row["commit"].strip() for row in rows if row["result"].strip().upper() == "PASS"}
    if len(commits) > 1:
        errors.append("All passing checks must refer to the same commit")
    counts = Counter(seen.values())
    complete = not errors and len(seen) == len(expected) and counts["PASS"] == len(expected)
    return {
        "complete": complete,
        "targets": len(TARGETS),
        "required_checks": len(expected),
        "results": {name: counts[name] for name in ("PASS", "FAIL", "BLOCKED", "PENDING")},
        "errors": errors,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("record", type=Path)
    parser.add_argument("--create-template", action="store_true")
    parser.add_argument("--allow-incomplete", action="store_true")
    parser.add_argument("--commit", help="Require every passing check to match this full commit SHA")
    args = parser.parse_args()
    if args.create_template:
        create_template(args.record)
        print(f"Created {args.record} with {len(TARGETS) * len(CHECKS)} required checks")
        return
    report = validate(args.record, args.commit)
    print(json.dumps(report, indent=2))
    if not report["complete"] and not args.allow_incomplete:
        raise SystemExit("Hardware qualification is incomplete")


if __name__ == "__main__":
    main()
