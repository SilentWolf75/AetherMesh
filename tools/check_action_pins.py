"""Fail when a workflow pins a GitHub Action to a SHA that does not exist.

A 40-hex pin can still be fabricated: `actions/checkout@11bd71901bbe5b1635a8...`
was the right length, shared a real prefix, and broke every job. Length checks
cannot catch that — only resolving the SHA against the API can.

Run: python tools/check_action_pins.py
Set GITHUB_TOKEN to avoid unauthenticated rate limits (CI passes it in).
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
PIN_RE = re.compile(
    r"uses:\s*([A-Za-z0-9._-]+/[A-Za-z0-9._-]+)(?:/[A-Za-z0-9._-]+)?@([0-9a-f]{40})\b"
)
TAG_RE = re.compile(
    r"uses:\s*([A-Za-z0-9._-]+/[A-Za-z0-9._-]+)(?:/[A-Za-z0-9._-]+)?@(v[0-9][^\s#]*)"
)


def collect(pattern: re.Pattern[str]) -> set[tuple[str, str]]:
    found: set[tuple[str, str]] = set()
    for path in sorted(WORKFLOWS.glob("*.yml")):
        for repo, ref in pattern.findall(path.read_text(encoding="utf-8")):
            found.add((repo, ref))
    return found


def sha_exists(repo: str, sha: str) -> bool:
    request = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/commits/{sha}",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "aethermesh-pin-check"},
    )
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response).get("sha", "").startswith(sha)
    except urllib.error.HTTPError as exc:
        if exc.code in (404, 422):
            return False
        raise


def main() -> int:
    pins = collect(PIN_RE)
    if not pins:
        print("No SHA-pinned actions found; nothing to verify.")
        return 0

    errors: list[str] = []
    for repo, sha in sorted(pins):
        if sha_exists(repo, sha):
            print(f"OK   {repo}@{sha}")
        else:
            errors.append(f"BAD  {repo}@{sha} does not resolve on GitHub")

    for repo, tag in sorted(collect(TAG_RE)):
        errors.append(f"BAD  {repo}@{tag} is a mutable tag; pin it to a 40-hex commit SHA")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"All {len(pins)} action pins resolve.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
