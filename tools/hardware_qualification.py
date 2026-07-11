"""Create and validate AetherMesh hardware qualification records."""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from pathlib import Path

TARGETS = (
    "heltec_v4", "heltec_v3", "rak4631", "rak3401_1w", "rak19026",
    "lilygo_t_echo", "lilygo_t_deck", "elecrow_crowpanel_35",
)
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


def validate(path: Path) -> dict:
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
        seen[key] = result

    expected = {(target, check) for target in TARGETS for check in CHECKS}
    for target, check in sorted(expected - set(seen)):
        errors.append(f"missing {target}/{check}")
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
    args = parser.parse_args()
    if args.create_template:
        create_template(args.record)
        print(f"Created {args.record} with {len(TARGETS) * len(CHECKS)} required checks")
        return
    report = validate(args.record)
    print(json.dumps(report, indent=2))
    if not report["complete"] and not args.allow_incomplete:
        raise SystemExit("Hardware qualification is incomplete")


if __name__ == "__main__":
    main()
