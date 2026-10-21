# consts-Destination

<img src="icon.png" alt="" width="48" height="72" align="right">

RuneLite plugin for Old School RuneScape ( OSRS ): where you're about to act, and where you acted. Anything under your cursor that you can do something with lights up in blue - the tile, or the NPC, player, object or ground item on it. Click, and what you clicked turns orange and stays that way until you're done: the tile you're walking to, or what you're interacting with.

And where to stand: [tile packs](#tile-packs), marked tiles for bosses, raids, minigames, clues and sailing, each turned on with a click. They're Tile Packs by TrevorMDev, folded in.

## What it does

- **Hover**: the tile under your cursor is outlined blue and follows the mouse. Over an NPC, a player, an object or a ground item, that thing is outlined blue instead - red if your click would attack it.
- **Clicked**: click a tile to walk there and it turns orange while you walk. Click an NPC, a player, an object or an item and its outline flashes white, then stays orange - red in a fight - while you're interacting with it.
- **Cleared**: walking somewhere else, using an item, getting there, or logging out clears it.
- **Tile packs**: the tiles of every pack you've turned on, in the world and, if you like, on the minimap.
- **Clicks**: turn **Ground Item Pickup** off and a left-click goes past ground items to the tree, NPC or tile behind them, which helps in Forestry: taking one needs a right-click, and it isn't outlined. **Minigame Focus** does the same for loot and campfires only. Items you can't trade, the ones minigames have you pick up (eggs, soul fragments, bruma roots) and clue scrolls, stay one click.

## Tile packs

The sidebar panel's **Tile packs** tab, beside Settings, is a list of packs of marked tiles for content all over the game, from Abyssal Sire to Zulrah, with raids, minigames, clues and sailing between. Turn a pack on and its tiles show in the world, colored and labelled as the pack has them. They're kept apart from RuneLite's Ground Markers, so they don't touch your own markers.

Each pack is a row with its controls:

| Control | What it does |
|---|---|
| Green plus / red minus | Turns the pack on or off |
| YouTube, Discord or arrows icon | Opens where the tiles came from, most often a video that shows them |
| Copy | Copies the pack's tiles to the clipboard, in Ground Markers' format |
| Eye | Hides the pack from the list without deleting it, or shows it again |
| Pencil (your own packs) | Changes the pack's name or tiles |
| Cross (your own packs) | Deletes the pack, for good |

The search box finds packs by part of their name. The two eyes under it show the packs you haven't hidden and the ones you have: white is on, grey is off.

### Adding your own pack

Scroll to the bottom of the list. Give the pack a name, paste its tiles and press **Add Pack**: it's added, and on. The tiles are a Ground Markers export: mark the tiles with RuneLite's Ground Markers, right-click the world map orb beside the minimap and choose **Export ground markers**, which copies the markers around you. [runelite.net/tile](https://runelite.net/tile/) checks or edits them. Got tiles others could use? The Discord button under the form is Tile Packs' Discord, where packs are shared and added to the list.

### Coming from Tile Packs

The first time consts-Destination starts with tile packs (1.3.0), it brings over what you had in Tile Packs: the packs you had on, your own packs, the packs you hid, the list's filters and Tile Packs' settings. Tile Packs' saved data is only read, never changed, so Tile Packs keeps working if you keep it. Once your packs are here you can turn the Tile Packs plugin off, or the same tiles are drawn twice. It happens once: later changes in Tile Packs don't follow.

## Settings

The tile in RuneLite's sidebar opens a panel with two tabs, **Settings** and **Tile packs**, on Settings. The Settings tab is the same as the plugin's page in the Configuration panel, so it's one click away while you play. Right-click a setting's name to reset it.

The switch beside the panel's title is the **Master Switch** (first in the plugin config too). Off hides every tile and outline, the tile packs' too, and leaves left-clicks as the game has them, without turning the plugin off, so the sidebar button stays.

| Section | Setting | What it does | Default |
|---|---|---|---|
| Clicks | Ground Item Pickup | Left-click takes ground items. Off: a right-click takes them, and a left-click goes to what's behind them | On |
| Clicks | Minigame Focus | Loot and campfires need a right-click; items you can't trade stay one click | Off |
| Colors | Hover Color | The tile under your cursor, and anything you hover | Blue `#3EAAFF` |
| Colors | Clicked Color | The tile you're walking to, and what you're interacting with | Orange `#FFA500` |
| Colors | Attack Color | An NPC you're about to attack, or attacking | Red `#FF4632` |
| Tiles | Hover Tile / Clicked Tile | Turn either tile off | On |
| Tiles | Border Width | Tile outline thickness in pixels | `2` |
| Tiles | Fill Opacity | Tile fill: 0 is outline only, 255 is solid | `30` |
| Outlines | On Hover / On Interact | Turn either kind of outline off | On |
| Outlines | NPCs / Players / Objects / Ground Items | Which things get outlined | All on |
| Outlines | Width / Feather | Outline thickness, and how much of it fades out (0 to 4) | `4` / `4` |
| Tile packs | Show Labels | The labels a pack gives its tiles | On |
| Tile packs | Override Color Active / Override Color | Every pack tile in one color | Off / Yellow |
| Tile packs | Draw Tiles on Minimap | Pack tiles on the minimap too | Off |
| Tile packs | Border Width | Pack tile outline thickness in pixels | `2` |
| Tile packs | Fill Opacity | The tiles' dark fill: 0 is none, 255 is solid | `50` |

## Credits

The outlines are RuneLite's [Interact Highlight](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/interacthighlight) by Adam (BSD 2-Clause, Copyright (c) 2021, Adam), folded into consts-Destination: you can turn that plugin off and run this one alone. The ported files, `InteractTracker.java` and `ConstsDestinationOutlineOverlay.java`, keep its license header and license, reproduced in [NOTICE](NOTICE). The sidebar panel is RuneLite's [Configuration panel](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/config) by Adam (BSD 2-Clause), cut down to consts-Destination's own settings: `ConfigSidePanel.java` keeps its license header and license, in the same NOTICE.

The tile packs are [Tile Packs](https://github.com/TrevorMDev/tile-packs) by TrevorMDev (BSD 2-Clause, Copyright (c) 2022, TrevorMDev), folded into consts-Destination at its Plugin Hub version (8091ee8): once your packs are here you can turn that plugin off and run this one alone. The ported files, in `tilepacks/`, keep their license headers, Tile Packs' and those of the RuneLite authors whose code came into Tile Packs from RuneLite, mostly its Ground Markers plugin (TheLonelyDev, Adam, Jordan Atwood, Benjamin), and the licenses are reproduced in [NOTICE](NOTICE). The built-in packs were gathered by TrevorMDev with packs and fixes from David Lord, Matthew Johnson, Blackberry0Pie, Vic Segers and Tile Packs' community; each pack links to where its tiles came from, most often the video that shows them. The icons are Tile Packs': made by Pixel perfect, Freepik, Utari Nuraeni, Andrean Prabowo and uicon, from [flaticon.com](https://www.flaticon.com/).

The rest of consts-Destination is [MIT](LICENSE).

## Running it

From the repo root: `./run.sh all` loads every plugin in this repo, consts-Destination included. Setup, including the one-time Jagex account step, is in the [root README](../README.md#run-it).
