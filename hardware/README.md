# AetherMesh hardware

This tree holds KiCad projects, mechanical notes, and AM-1 cradle work. Most day-to-day
autorouter campaign output is **local scratch** and is excluded via the repo root
`.gitignore` (see patterns under `hardware/am1/electrical/**/am1-pcb/_*`, `.history/`,
`*.pre_*`, `freerouting.jar`, etc.).

## Worth versioning (when ready)

- Schematics and symbol/footprint libraries
- `gen_am1.py` and other durable generators (not `_*.py` campaign scripts)
- Review docs and checklists
- Release Gerbers / pick-and-place for fab

## Safe to delete locally (regenerated / scratch)

- Autorouter snapshot `.kicad_pcb.pre_*`, `.bestpair`, `.smoke_swd`
- `.history/` directories
- Duplicate `freerouting.jar` copies
- Underscore-prefixed campaign scripts (`_raceA_*.py`, `_clr_*.py`, …)

Before `git add hardware/`, run `git status` and confirm you are not staging gigabytes
of scratch under `am1-pcb/`.
