# AetherMesh hardware

This tree holds KiCad projects, mechanical notes, and AM-1 cradle work.

**Default policy:** everything under `hardware/` except this `README.md` is
**gitignored**. That keeps `git ls-files -o --exclude-standard hardware/` at **0**
and prevents a mistaken `git add -A` from committing multi‑GB autorouter scratch.

## Force-add when ready to version

```bash
git add -f hardware/am1/path/to/schematic.kicad_sch
```

Worth versioning (when curated):

- Schematics and symbol/footprint libraries
- Durable generators (e.g. `gen_am1.py`)
- Review docs and checklists
- Release Gerbers / pick-and-place for fab

## Safe to delete locally

- Autorouter snapshots (`.pre*`, `.smoke*`, `.bestpair`, `.GOOD_*`)
- `.history/` directories
- `freerouting*.jar`
- Underscore-prefixed campaign scripts
