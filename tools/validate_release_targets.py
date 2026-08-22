"""Fail when firmware targets or release versions drift across the repo."""

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


def read_version_file() -> dict[str, str]:
    values: dict[str, str] = {}
    path = ROOT / "VERSION"
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


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

    versions = read_version_file()
    if versions:
        gradle_props = (ROOT / "app" / "gradle.properties").read_text(encoding="utf-8")
        gradle_kts = (ROOT / "app" / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        version_h = (ROOT / "firmware" / "src" / "Version.h").read_text(encoding="utf-8")
        fw_match = re.search(r'#define AETHERMESH_FW_BASE "([^"]+)"', version_h)
        fw_base = fw_match.group(1) if fw_match else ""

        app_version = versions.get("app_version", "")
        app_code = versions.get("app_version_code", "")
        fw_version = versions.get("firmware_version", "")

        if app_version and f'versionName={app_version}' not in gradle_props:
            errors.append(f"gradle.properties versionName should be {app_version}")
        if app_code and f'versionCode={app_code}' not in gradle_props:
            errors.append(f"gradle.properties versionCode should be {app_code}")
        if app_version and app_version not in gradle_kts:
            errors.append(f"build.gradle.kts should default versionName to {app_version}")
        if app_code and app_code not in gradle_kts:
            errors.append(f"build.gradle.kts should default versionCode to {app_code}")
        if fw_version and fw_base != fw_version:
            errors.append(f"Version.h AETHERMESH_FW_BASE is {fw_base!r}, VERSION expects {fw_version!r}")

    if errors:
        raise SystemExit("\n".join(errors))
    print(f"Release target parity verified for {len(firmware)} boards: {', '.join(sorted(firmware))}")
    if versions:
        print(
            f"Versions OK: app {versions.get('app_version')} ({versions.get('app_version_code')}), "
            f"firmware {versions.get('firmware_version')}"
        )


if __name__ == "__main__":
    main()
