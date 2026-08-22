"""Fail if any `uses: owner/action@ref` in workflows is not a 40-char hex SHA."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
USES_RE = re.compile(r"^\s*-\s*uses:\s*([^\s#]+)", re.MULTILINE)
SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def main() -> int:
    errors: list[str] = []
    for path in sorted(WORKFLOWS.glob("*.yml")):
        text = path.read_text(encoding="utf-8")
        for match in USES_RE.finditer(text):
            ref = match.group(1).strip()
            if "@" not in ref:
                errors.append(f"{path.name}: missing @ref in {ref}")
                continue
            action, pin = ref.rsplit("@", 1)
            if action.startswith("./"):
                continue
            if not SHA_RE.match(pin):
                errors.append(
                    f"{path.name}: {ref} — pin must be exactly 40 lowercase hex "
                    f"(got {len(pin)} chars)"
                )
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"All Actions pins are 40-char SHAs in {WORKFLOWS}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
