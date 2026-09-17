# Hardware Release Validation

Run this checklist on every supported board before marking a firmware version as
field-stable. Record the board, commit hash, radio region, antenna, battery, and
test result in the release notes.

## Common Checks

- Cold boot shows the correct logo, board name, firmware hash, and usable UI.
- USB flashing and recovery mode install the artifact published for that board.
- BLE reconnects after app restart, node restart, sleep, and a failed password.
- A direct message reaches `DELIVERED`; ACK loss produces bounded retries and a
  final `FAILED` state instead of remaining pending.
- Broadcast, direct message, range test, and traceroute work at one, three, and
  five controlled hops without duplicate application delivery.
- Removing the preferred relay causes discovery and successful alternate-route
  recovery; restoring it does not create route flapping.
- GPS lock/no-lock, privacy precision, telemetry interval, naming, battery,
  charging, sleep/wake, and power-save state survive reboot.
- A 12-hour soak has no receive stalls, runaway retransmissions, heap decline,
  display corruption, or unexpected reset.

## Board-Specific Controls

| Target | Additional checks |
|---|---|
| Heltec V4 | OLED pages, user button, GPS connector, charger indication |
| Heltec V3 | OLED pages, user button, charger indication |
| RAK4631 | Nordic UF2/DFU, WisBlock GPS, IO2 sensor power |
| RAK3401 1W | UF2/DFU, high-power TX limits, thermal and current draw |
| RAK19026 | OLED, power management, UF2/DFU, attached WisBlock modules |
| LILYGO T-Echo | E-paper refresh, frontlight timeout, touch/side buttons, BME280 |
| SenseCAP T1000-E | UF2 DFU (T1000-E drive), LR1110 TX/RX, AG3335 GPS, button/LED/buzzer, battery |
| LILYGO T-Deck | Keyboard map, trackball/buttons, sleep/wake, all five color pages |
| CrowPanel 3.5 | Rotation, color order, touch wake, swipe navigation, all five pages |

## Benchmark Record

Before and after routing changes, run `python tools/mesh_simulator.py` and attach
its JSON output. Field runs should export the range-test CSV and note topology,
distance, speed, antenna height, and weather so results remain comparable.

## Qualification Record

Generate the release checklist, fill it while testing each physical board, then
validate it. A release is not hardware-qualified until every required row is
`PASS`; compilation alone does not satisfy this gate.

A blank checklist is in [docs/hardware-qualification-template.csv](hardware-qualification-template.csv)
(72 rows: 9 boards × 8 checks). Copy it, fill `PASS` rows with the **full 40-char
commit SHA** you actually flashed, then validate:

```bash
python tools/hardware_qualification.py docs/hardware-qualification-template.csv --create-template
cp docs/hardware-qualification-template.csv release-hardware.csv
python tools/hardware_qualification.py release-hardware.csv
```

Field-stable GitHub releases are drafted only via `.github/workflows/stable-release.yml`
once every required row is `PASS` for that commit.

## Privacy and Nordic update qualification

For T-Echo and T1000-E, verify both USB UF2 recovery and Android Nordic DFU ZIP
updates on physical boards. Check BLE reconnect after each update, GPS acquisition,
battery reporting, and sleep/wake.

For location privacy, disable position in one channel, confirm the radio's report,
then power-cycle the radio without the phone connected. A second radio must see no
coordinates. Repeat with the largest configured blur radius across channels.
Interrupt BLE before confirmation and verify the app never claims confirmation;
reconnect and verify the persisted setting is reported. Repeat after changing boards.

Automated retry tests simulate missing reports, failed persistence reports, and
reboots; they do not establish physical flash retention or over-the-air behavior.
