"""Mirror each firmware channel's published builds onto the Pages site.

Browsers cannot download GitHub Release assets from another origin: the
download URLs redirect to a storage host that sends no CORS headers, so the web
flasher can list releases but never read their files. The Pages deploy
therefore copies published builds next to the flasher, where they are
same-origin:

  web-flasher/firmware/<channel>/index.json        newest first: tag, name, date, notes
  web-flasher/firmware/<channel>/<tag>/manifest.json
  web-flasher/firmware/<channel>/<tag>/<files the manifest lists>

The newest few builds of each channel are kept, not just one, so a beta that
turns out badly can be rolled back from the flasher. Only the files a
release's USB manifest lists are copied: over-the-air images are fetched by the
app straight from the release and would only double the site's size.

Every build's own manifest carries the SHA-256 the flasher verifies each
download against. A channel with nothing publishable gets no index, and the
flasher reports it as empty rather than falling back to the other channel.

Downloads go through the gh CLI, so this needs GH_TOKEN in CI.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

CHANNELS = {"release": False, "beta": True}  # channel -> wants pre-release
KEEP_PER_CHANNEL = 3
NOTES_LIMIT = 6000


def pick_many(releases: list[dict], want_prerelease: bool, limit: int = KEEP_PER_CHANNEL) -> list[dict]:
    """Newest publishable releases for one channel, newest first.

    A draft is never published, and a release without manifest.json cannot be
    verified, so neither is offered. Order is the API's (newest first).
    """
    chosen = []
    for release in releases:
        if len(chosen) >= limit:
            break
        if release.get("draft"):
            continue
        if bool(release.get("prerelease")) != want_prerelease:
            continue
        names = {asset.get("name") for asset in release.get("assets", [])}
        if "manifest.json" in names:
            chosen.append(release)
    return chosen


def pick(releases: list[dict], want_prerelease: bool) -> dict | None:
    """Newest publishable release for one channel, or None."""
    found = pick_many(releases, want_prerelease, limit=1)
    return found[0] if found else None


def index_entry(release: dict) -> dict:
    notes = (release.get("body") or "").strip()
    if len(notes) > NOTES_LIMIT:
        notes = notes[:NOTES_LIMIT].rstrip() + "\n\n(Truncated. The full notes are on the release page.)"
    return {
        "tag": release["tag_name"],
        "name": release.get("name") or release["tag_name"],
        "published": release.get("published_at") or "",
        "notes": notes,
    }


def _gh_json(args: list[str]) -> list[dict]:
    out = subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def _download(repo: str, tag: str, patterns: list[str], target: Path, run) -> None:
    command = ["gh", "release", "download", tag, "--repo", repo, "--dir", str(target), "--clobber"]
    for pattern in patterns:
        command += ["--pattern", pattern]
    run(command, check=True)


def mirror(repo: str, output: Path, run=subprocess.run) -> dict[str, list[str]]:
    releases = _gh_json(["api", f"repos/{repo}/releases?per_page=50"])
    mirrored: dict[str, list[str]] = {}
    for channel, want_prerelease in CHANNELS.items():
        channel_dir = output / channel
        channel_dir.mkdir(parents=True, exist_ok=True)
        entries = []
        for release in pick_many(releases, want_prerelease):
            tag = release["tag_name"]
            target = channel_dir / tag
            target.mkdir(parents=True, exist_ok=True)
            _download(repo, tag, ["manifest.json"], target, run)
            listed = json.loads((target / "manifest.json").read_text(encoding="utf-8"))
            files = sorted({item["file"] for item in listed if item.get("file")})
            if files:
                _download(repo, tag, files, target, run)
            entries.append(index_entry(release))
        mirrored[channel] = [entry["tag"] for entry in entries]
        if entries:
            (channel_dir / "index.json").write_text(json.dumps(entries, indent=2) + "\n", encoding="utf-8")
    return mirrored


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, help="owner/name")
    parser.add_argument("--output", type=Path, default=Path("web-flasher/firmware"))
    args = parser.parse_args()
    for channel, tags in mirror(args.repo, args.output).items():
        print(f"{channel}: {', '.join(tags) if tags else 'nothing published'}")


if __name__ == "__main__":
    main()
