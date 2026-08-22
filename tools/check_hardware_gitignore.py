"""Fail unless hardware/ has zero untracked files after .gitignore."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    result = subprocess.run(
        ["git", "ls-files", "-o", "--exclude-standard", "hardware/"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    lines = [line for line in result.stdout.splitlines() if line.strip()]
    print(len(lines))
    if lines:
        sample = "\n".join(lines[:20])
        print(
            f"FAIL: {len(lines)} untracked file(s) under hardware/ "
            f"(show up to 20):\n{sample}",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
