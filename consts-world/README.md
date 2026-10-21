# consts-World

<img src="icon.png" alt="" width="48" height="72" align="right">

RuneLite plugin for Old School RuneScape ( OSRS ): a world switcher in the sidebar with your hot list first. Search every world by its activity, sort what you find, star the worlds you hop between, then hop to one with a click.

![The consts-World panel: a hot list of five skilling worlds (Blast Furnace, Wintertodt, Tempoross, Forestry, Guardians of the Rift) with the current one in green, then a search for "blast furnace" that matches 15 worlds, sorted by players](screenshots/panel.png)

## What it does

- **Hot list**: always first in the panel, whatever you search or sort. It starts empty. Click a world in it to hop there; on the login screen, the click picks that world instead. Worlds stay in the order you added them, and the list is saved with your RuneLite settings.
- **Search**: type in the box to narrow the list of every world. Each word you type has to start one of a world's words: its activity (`wintertodt`, `blast`, `tempoross`), its number (`330`), its region (`uk`, `germany`, `us`, `aus`) or `members`, `p2p`, `free` or `f2p`. So `skill total 2000` finds the 2000 total level worlds, and `members uk` the UK members worlds. The line under the box counts the matches.
- **Sort**: click World, # (players) or Activity to sort the list, and again to flip it. Worlds without an activity stay at the bottom, and total level worlds sort by their numbers: 500 before 1250. Right-click a heading to refresh the worlds.
- **Stars**: the star at the end of a row adds that world to the hot list, or removes it once it's filled. A row's right-click menu does the same.
- **Hop**: double-click a world in the list to hop there, as in RuneLite's World Hopper. The world you're on is green.

There are no settings.

## Credits

The panel and the hopping are RuneLite's [World Hopper](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/worldhopper) plugin by Adam, Lotto, gregg1494 and Psikoi (BSD 2-Clause), cut down to its panel with a hot list in place of its favourites: no quick-hop keys, pings, filters or Hop-to option. You can run both. The ported files, `ConstsWorldPlugin.java`, `ConstsWorldPanel.java`, `WorldRow.java` and `WorldTableHeader.java`, and the flags and arrow they use keep that license, reproduced in [NOTICE](NOTICE); the rest of consts-World, its star and globe icons included, is [MIT](LICENSE).

## Running it

From the repo root: `./run.sh all` loads every plugin in this repo, consts-World included. Setup, including the one-time Jagex account step, is in the [root README](../README.md#run-it).
