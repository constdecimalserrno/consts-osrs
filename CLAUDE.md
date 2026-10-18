# consts-osrs

RuneLite plugins for Old School RuneScape, published under the pseudonym `constdecimalserrno`. Today there is one: consts-AFK, in `consts-afk/`.

## Rules

- **Presentation follows every big change.** A change a player would notice (an alert, a preset, the settings layout, the status panel, a new feature) isn't done until, in the same pass:
  1. `consts-afk/README.md` says so: presets table, alerts table, Settings, Training guide. Update the root README too if its one-line summary changed.
  2. The README's GIF and screenshots still match what the plugin shows. When the status panel changes, change the landing page hero first, then re-render the GIF from it.
  3. The landing page, https://constdecimalserrno.dev/consts-afk/, says the same: `src/pages/consts-afk.astro` in the blog repo, its link card (`node scripts/shoot-card.mjs consts-afk`) and the portfolio entry. Then it's deployed with the blog's own `npm run publish`.
- **The pseudonym only.** No real name, home-directory path or OS username in any file, commit, issue or PR. Grep for home-directory paths before every push. Commits use the repo-local identity `constdecimalserrno <279822888+constdecimalserrno@users.noreply.github.com>`.
- **Don't build where RuneLite runs.** `./run.sh` launches RuneLite from this folder's build output, so a build here can pull classes out from under a running client. Build and test in a copy or a git worktree instead.

## Build and release

- Test: `./gradlew test`. The launcher needs JDK 17+ for Gradle and JDK 11 for the client (see the root README).
- Release: bump `version` in `build.gradle`, build `consts-afk/build/libs/consts-afk-<version>.jar`, then `gh release create v<version>` with the jar attached.
- Plugin Hub: `scripts/publish-hub-branch.sh consts-afk` cuts the `plugin-hub/consts-afk` branch and prints the manifest lines. The Hub's standard build compiles only `consts-afk/src/main` against RuneLite, so use nothing outside the RuneLite client and API: no third-party libraries, no core plugin classes.
