"""Fail when firmware targets drift between PlatformIO, CI, Pages, and flasher UI."""

from __future__ import annotations

import configparser
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
UI_TARGETS = {
    "heltec_v4": "heltec-v4",
    "heltec_v3": "heltec-v3",
    "rak4631": "rak4631",
    "rak3401_1w": "rak3401-1w",
    "rak19026": "rak19026",
    "lilygo_t_echo": "lilygo-t-echo",
    "lilygo_t_deck": "lilygo-t-deck",
    "elecrow_crowpanel_35": "elecrow-crowpanel-35",
}


def main() -> None:
    config = configparser.ConfigParser()
    config.read(ROOT / "firmware" / "platformio.ini", encoding="utf-8")
    firmware = {section.removeprefix("env:") for section in config.sections() if section.startswith("env:")}
    firmware.discard("native")

    ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
    matrix_match = re.search(r"env:\s*\[([^]]+)]", ci)
    if not matrix_match:
        raise SystemExit("Could not find firmware matrix in ci.yml")
    ci_targets = {item.strip() for item in matrix_match.group(1).split(",")}

    pages = (ROOT / ".github" / "workflows" / "pages.yml").read_text(encoding="utf-8")
    pages_targets = set(re.findall(r"pio run -e ([a-zA-Z0-9_]+)", pages))
    flasher = (ROOT / "web-flasher" / "index.html").read_text(encoding="utf-8")
    ui_targets = set(re.findall(r'<option value="([^"]+)"', flasher))

    errors = []
    if firmware != set(UI_TARGETS):
        errors.append(f"Update UI_TARGETS mapping: platformio={sorted(firmware)} mapping={sorted(UI_TARGETS)}")
    if ci_targets != firmware:
        errors.append(f"CI target drift: missing={sorted(firmware - ci_targets)} extra={sorted(ci_targets - firmware)}")
    if pages_targets != firmware:
        errors.append(f"Pages target drift: missing={sorted(firmware - pages_targets)} extra={sorted(pages_targets - firmware)}")
    expected_ui = {UI_TARGETS[target] for target in firmware}
    if not expected_ui.issubset(ui_targets):
        errors.append(f"Flasher target drift: missing={sorted(expected_ui - ui_targets)}")
    for target in firmware:
        if target not in pages:
            errors.append(f"Pages manifest does not mention {target}")

    if errors:
        raise SystemExit("\n".join(errors))
    print(f"Release target parity verified for {len(firmware)} boards: {', '.join(sorted(firmware))}")


if __name__ == "__main__":
    main()
