"""Mirror each firmware channel's published build onto the Pages site.

Browsers cannot download GitHub Release assets from another origin: the
download URLs redirect to a storage host that sends no CORS headers, so the web
flasher can list releases but never read their files. The Pages deploy
therefore copies the newest published build of each channel next to the
flasher, where it is same-origin:

  web-flasher/firmware/release/   newest non-pre-release that ships manifest.json
  web-flasher/firmware/beta/      newest pre-release that ships manifest.json

Each directory gets that release's own manifest.json (whose SHA-256 values the
flasher verifies every download against), its binaries, and a release.json
naming the tag. A channel with nothing publishable is left empty, and the
flasher reports it as empty rather than falling back to the other channel.

Downloads go through the gh CLI, so this needs GH_TOKEN in CI.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

CHANNELS = {"release": False, "beta": True}  # channel -> wants pre-release


def pick(releases: list[dict], want_prerelease: bool) -> dict | None:
    """Newest published release for one channel, or None.

    A draft is never published, and a release without manifest.json cannot be
    verified, so neither is offered. Order is the API's (newest first).
    """
    for release in releases:
        if release.get("draft"):
            continue
        if bool(release.get("prerelease")) != want_prerelease:
            continue
        names = {asset.get("name") for asset in release.get("assets", [])}
        if "manifest.json" in names:
            return release
    return None


def _gh_json(args: list[str]) -> list[dict]:
    out = subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def mirror(repo: str, output: Path, run=subprocess.run) -> dict[str, str | None]:
    releases = _gh_json(["api", f"repos/{repo}/releases?per_page=50"])
    mirrored: dict[str, str | None] = {}
    for channel, want_prerelease in CHANNELS.items():
        target = output / channel
        target.mkdir(parents=True, exist_ok=True)
        chosen = pick(releases, want_prerelease)
        if chosen is None:
            mirrored[channel] = None
            continue
        tag = chosen["tag_name"]
        run(["gh", "release", "download", tag, "--repo", repo, "--dir", str(target), "--clobber"],
            check=True)
        (target / "release.json").write_text(
            json.dumps({"tag": tag, "prerelease": want_prerelease}) + "\n", encoding="utf-8")
        mirrored[channel] = tag
    return mirrored


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, help="owner/name")
    parser.add_argument("--output", type=Path, default=Path("web-flasher/firmware"))
    args = parser.parse_args()
    for channel, tag in mirror(args.repo, args.output).items():
        print(f"{channel}: {tag or 'nothing published'}")


if __name__ == "__main__":
    main()
