# consts-Leaderboard

<img src="icon.png" alt="" width="48" height="72" align="right">

RuneLite plugin for Old School RuneScape ( OSRS ): any player's skills from the hiscores, in a sidebar panel. Levels stop at 99, the way the game counts them, and a 99 shows in yellow. Nothing else: no bosses, no clues, no minigames.

## What it does

- **Lookup**: right-click a player, or a name in chat, private messages, your friends or ignore list, a clan or a group ironman list, and pick **Lookup**. The sidebar opens on their skills.
- **Search**: type a name in the sidebar and press Enter.
- **Look up myself**: the button under the search bar looks up the account you're logged in to, on its own hiscores (ironman, hardcore or ultimate when it is one).
- **Skills**: every skill with its icon and level, in the skills tab's order, then the total level. A level never goes past 99, however much experience is behind it. A 99 is yellow, and so is the total level once every skill is 99. A skill that isn't ranked shows `--`.
- **Hover** a skill for its rank, its experience and the experience left to the next level (0 at 99), with a bar for how far through the level it is. Hover the total level for the overall rank and experience.

There are no settings.

## Credits

The panel and the Lookup options are RuneLite's [HiScore](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/hiscore) plugin by Adam, Psikoi and Bram91 (BSD 2-Clause), cut down to the skills: you can turn that plugin off and run this one alone. Turn it off if you run both, since both add a **Lookup** option. The ported files, `ConstsLeaderboardPlugin.java` and `ConstsLeaderboardPanel.java`, keep its license headers and license, reproduced in [NOTICE](NOTICE); the rest of consts-Leaderboard, its icons included, is [MIT](LICENSE).

## Running it

From the repo root: `./run.sh all` loads every plugin in this repo, consts-Leaderboard included. Setup, including the one-time Jagex account step, is in the [root README](../README.md#run-it).
