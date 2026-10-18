# consts-Destination

<img src="icon.png" alt="" width="48" height="72" align="right">

RuneLite plugin for Old School RuneScape ( OSRS ): it highlights the tile under your cursor, and when you click to walk somewhere, that tile stays lit in a second color until your next click. You always know where you're about to go and where you're headed.

## What it does

- **Hover**: the tile under your cursor is outlined in blue, and the outline follows the mouse. It stays out of the way while you hover an NPC or an object.
- **Clicked**: click a tile to walk there and it turns orange, and stays orange while you walk.
- **Cleared**: any other click (an NPC, an object, an item, the interface) clears the orange tile, and so does logging out.

## Settings

| Setting | What it does | Default |
|---|---|---|
| Highlight Color | Color for the tile under your cursor | Blue `#3EAAFF` |
| Border Width | Outline thickness in pixels | `2` |
| Fill Opacity | How solid the fill is: 0 is outline only, 255 is solid | `30` |
| Clicked Color | Color for the tile you clicked to walk to | Orange `#FFA500` |

## Running it

From the repo root: `./run.sh all` loads every plugin in this repo, consts-Destination included. Setup, including the one-time Jagex account step, is in the [root README](../README.md#run-it).
