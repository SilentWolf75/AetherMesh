"""Write a deterministic firmware manifest with SHA-256 integrity metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def artifact_record(label: str, path: Path, commit_hash: str) -> dict:
    if not path.is_file():
        raise FileNotFoundError(path)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return {
        "name": f"{label} - {commit_hash} (latest)",
        "file": path.name,
        "size": path.stat().st_size,
        "sha256": digest,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--hash", required=True)
    parser.add_argument("--artifact", action="append", nargs=2, metavar=("LABEL", "FILE"), required=True)
    args = parser.parse_args()
    records = [artifact_record(label, Path(filename), args.hash) for label, filename in args.artifact]
    args.output.write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
