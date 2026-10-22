# consts-EXP backlog

Last updated: 2026-10-18

Planned work, not history. Git log has the history.

## Next up

- [ ] Verify the Forestry alert in game, event by event.
  - Each of the nine should alert once, name itself on the panel, and not re-fire while it runs.
  - Pheasant Control should not also set off the Random alert.
- [ ] Fishing: find the game message for a full inventory while fishing and add it to the Inventory Full triggers
  if it isn't `Your inventory is too full to hold any more …`. Until then the Idle alert catches it.
- [ ] Verify the Cargo Hold alert in game.
  - Confirm the crew message still reads `…cargo hold is full`.
  - If it repeats while you unload the hold, add a re-trigger cooldown (like Random Event's).
- [ ] Cannon: stop the burst when the cannon is reloaded, so Emit Immediately doesn't beep the full 100 times.
- [ ] Optional cargo hold fill readout (e.g. `Cargo 87/120`) from the `SAILING_BOAT_*_CARGOHOLD` item containers, if the client receives them without the hold open.

## Ideas

- [ ] Per-alert sounds (one Alert Sound covers every alert today).
- [ ] Alert cooldown guardrails: max repeats per window, minimum silence after the first beep.
- [ ] Overlay layout options: compact single-line mode, larger mode.
- [ ] Idle detection toggles: pick which signals count as activity (XP, movement, animation, interacting).
- [ ] Per-alert flash patterns and colorblind-friendly color presets.
- [ ] User-defined presets (save the current toggles under a name).

## Engineering

- [ ] Auto-detect JDKs in `run.sh` instead of hardcoded Homebrew/Temurin paths.
- [ ] More tests: flash priority with several alerts at once, status panel rendering.
- [ ] Optional debug logging for why each alert is on or off.

## Release

- [x] Plugin Hub prep: `runelite-plugin.properties` with `build=standard`, `icon.png`, a GIF and screenshots, and
  `scripts/publish-hub-branch.sh`. Compiles under the Hub's standard build files.
- [ ] Clean-up pass, then play-test in a client built from consts-osrs.
- [ ] Plugin Hub submission: cut the hub branch, open the PR to `runelite/plugin-hub`. QA on Windows/macOS/Linux.
