"""Write a deterministic firmware manifest with SHA-256 integrity metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def artifact_record(
    label: str,
    path: Path,
    commit_hash: str,
    *,
    kind: str = "usb",
    board: str = "",
) -> dict:
    if not path.is_file():
        raise FileNotFoundError(path)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    record = {
        "name": f"{label} - {commit_hash} (latest)",
        "file": path.name,
        "size": path.stat().st_size,
        "sha256": digest,
        "kind": kind,
    }
    if board:
        record["board"] = board
    return record


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--hash", required=True)
    parser.add_argument(
        "--kind",
        default="usb",
        help="Default artifact kind for this manifest (usb, ota, uf2).",
    )
    parser.add_argument(
        "--artifact",
        action="append",
        nargs="+",
        metavar="FIELD",
        required=True,
        help="LABEL FILE [BOARD] — optional BOARD id for OTA matching (e.g. heltec-v4).",
    )
    args = parser.parse_args()
    records = []
    for fields in args.artifact:
        if len(fields) < 2:
            raise SystemExit(f"--artifact needs LABEL FILE, got: {fields}")
        label, filename = fields[0], fields[1]
        board = fields[2] if len(fields) > 2 else ""
        records.append(
            artifact_record(
                label,
                Path(filename),
                args.hash,
                kind=args.kind,
                board=board,
            )
        )
    args.output.write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
