# consts-osrs

RuneLite plugins for Old School RuneScape, published under the pseudonym `constdecimalserrno`: consts-AFK in `consts-afk/`, consts-Destination in `consts-destination/`, consts-Leaderboard in `consts-leaderboard/`, consts-Models in `consts-models/` and consts-World in `consts-world/`.

## Rules

- **Presentation follows every big change.** A change a player would notice (an alert, a preset, the settings layout, the status panel, a new feature) isn't done until, in the same pass:
  1. The plugin's README says so (for consts-AFK: presets table, alerts table, Settings, Training guide). Update the root README too if a plugin's one-line summary changed.
  2. The README's GIF and screenshots still match what the plugin shows. When the status panel changes, change the landing page hero first, then re-render the GIF from it.
  3. The landing page, https://constdecimalserrno.dev/consts-osrs/, says the same: `src/pages/consts-osrs.astro` in the blog repo (one carousel slide and one section per plugin), its link card (`node scripts/shoot-card.mjs consts-osrs`) and the portfolio entry. Then it's deployed with the blog's own `npm run publish`.
- **The pseudonym only.** No real name, home-directory path or OS username in any file, commit, issue or PR. Grep for home-directory paths before every push. Commits use the repo-local identity `constdecimalserrno <279822888+constdecimalserrno@users.noreply.github.com>`.
- **Quote a plugin's name only after its label.** Where a title says what the thing is, the name goes in quotes after "OSRS plugin": release titles (`OSRS plugin "consts-AFK" v1.13`), and on the blog the landing page title and the portfolio heading (`OSRS plugins “consts-osrs”`, curly quotes there). Everywhere else the name stands bare: body copy, headings, chapter tabs, READMEs, the repo description, release notes.
- **Don't build where RuneLite runs.** `./run.sh` launches RuneLite from this folder's build output, so a build here can pull classes out from under a running client. Build and test in a copy or a git worktree instead.

## Build and release

- Test: `./gradlew test`. The launcher needs JDK 17+ for Gradle and JDK 11 for the client (see the root README).
- **Each plugin has its own version number**, in its own `build.gradle`, released on its own tag. Bump and release only the plugins that changed; there is no version for the repo as a whole. The landing page shows each plugin's version, so a release updates it there too.
- Release: bump `version` in the plugin's own `build.gradle`, build `<plugin>/build/libs/<plugin>-<version>.jar`, then `gh release create <plugin>-v<version>` with the jar attached, titled `OSRS plugin "<Name>" v<major>.<minor>`. consts-AFK's releases before 1.8.0 are tagged plain `v<version>`.
- License: MIT. Every plugin folder keeps its own `LICENSE` (the Hub refuses a build without one at the root of what it builds). Code ported from elsewhere keeps its license, credited in the plugin's `NOTICE` and README.
- Shared code is copied, not shared: the Hub builds each plugin folder alone. `ConfigSidePanel.java` (the sidebar settings panel) is the same in every plugin that has one, package line aside; change it in `consts-afk` and copy it, and `ConfigSidePanelCopyTest` fails until the copies match.
- Plugin Hub: `scripts/publish-hub-branch.sh <plugin>` cuts the `plugin-hub/<plugin>` branch and prints the manifest lines; each plugin is its own Hub entry. The Hub's standard build compiles only `<plugin>/src/main` against RuneLite, so use nothing outside the RuneLite client and API: no third-party libraries, no core plugin classes.

## Ticket runbook

Every ticket runs the `/workbench:ticket-runbook` skill, with these settings:

- **Service:** the plugins as const plays them: the play kit (`osrs-ship` publishes `build/play-kit`, and the MacBook pulls it at its next launch), each plugin's GitHub release, and the landing page on the blog.
- **Deploy window:** any time, with const's yes. A ship reaches the MacBook at its next launch, never mid-session.
- **Freeze:** none. Never build in the main checkout while RuneLite runs from it (`./run.sh`).
- **Shared code that needs extra care:** `ConfigSidePanel.java` (one copy per plugin, held equal by `ConfigSidePanelCopyTest`); the root `build.gradle`, `settings.gradle`, `PluginLauncher.java` and `scripts/` (every plugin and the play kit).
- **Outages confirmed as expected, and who confirmed them:** none yet.

## Living doc

Our living doc: https://claude.ai/artifact/H5jWP4bN3v66WAV5Vwuxbx. Its ticket files live in `.scratch/consts-exp/` and render to the local mirror `.scratch/consts-exp/living-doc.html`. Both stay in the main checkout, out of git (`.git/info/exclude`, since this repo is public), current after every change.

After every ticket change, and for every new ticket (pasted, or from the page's form), use the `/workbench:living-doc` skill. It syncs the local mirror and the artifact, and respins the artifact when this link is unreachable after a Claude seat switch.
