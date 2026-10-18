# consts-AFK

RuneLite plugin for AFK play in Old School RuneScape ( OSRS ): chinning, crabbing, mining, woodcutting, fishing and salvaging. Each alert beeps, flashes the screen in its own color, and shows up as a bar in a top-right status panel. Pick a preset and only the alerts you need are on.

![The status panel on the Woodcutting preset: Idle fills and flashes yellow, Inv Full flashes magenta, then Forestry flashes lime and names the event](screenshots/status-panel.gif)

![The inventory fills while woodcutting: the screen flashes magenta and the Inv Full bar lights up](screenshots/inventory-full.jpg)

| Idle | Inventory full |
|---|---|
| ![The Idle bar lit yellow, the screen tinted yellow](screenshots/panel-idle.jpg) | ![The Inv Full bar lit magenta, the screen tinted magenta](screenshots/panel-inventory-full.jpg) |

Formerly **AFK Alerts**. Saved settings carry over: the config group is still `afkcombatalerts`.

## Presets

The **Preset** dropdown sits at the very top of the plugin config. You can also right-click the status panel and choose `Preset: …`.

| Preset | Alerts on |
|---|---|
| Custom (default) | Whatever each section's **Enable** toggle says |
| Chinning | Health, Prayer, Idle, Aggro, Logout, Boost, Random |
| Crabbing | Health, Idle, Aggro, Logout, Boost, Random |
| Mining | Idle, Logout, Random, Inventory Full |
| Woodcutting | Idle, Logout, Random, Inventory Full, Forestry |
| Fishing | Idle, Logout, Random, Inventory Full |
| Salvaging Active | Logout, Random, Inventory Full, Salvage, Cargo Hold |
| Salvaging AFK | Cargo Hold only |

Cannon isn't in any preset; turn it on under **Custom**.

Presets only choose which alerts are on. Thresholds, colors and iteration counts always come from your settings. A preset overrides the Enable toggles; switch back to **Custom** to use them again (your toggles are left untouched).

## Alerts

| Alert | Fires when | Stops when | Default color |
|---|---|---|---|
| Health | HP below threshold (`25`) | HP recovers | Green |
| Prayer | Prayer below threshold (`20`) | Prayer recovers | Blue |
| Idle | No movement, new animation, combat or XP for `6 s` | Activity resumes | Yellow |
| Aggro | 10-minute OSRS aggro timer (or custom interval) runs out | You walk `20` tiles away or right-click → Reset Aggro Timer | Red |
| Logout | No click/key for `24 min` | You click or press a key | Orange |
| Boost | Chat: `… is about to …` (potion/effect expiry) | `9` beeps or a click | Black |
| Random | A random event NPC appears (`20 s` cooldown) | `9` beeps or a click | White |
| Cannon | Chat: `Your cannon is out of ammo!` | `100` beeps or a click | Dark orange |
| Inventory Full | Chat: `Your inventory is too full to hold any more …` | `100` beeps or a click | Magenta |
| Salvage | Chat: `You salvage all you can from the shipwreck…` or `You have no more salvage to sort…` | `100` beeps or a click | Cyan |
| Cargo Hold | Chat: `…cargo hold is full` (e.g. `Your crewmate on the salvaging hook cannot salvage as the cargo hold is full.`) | `100` beeps or a click | Violet |
| Forestry | A Forestry event you picked starts nearby | `30` beeps or a click | Lime |

### Forestry events

Each event has its own checkbox in the **Forestry Alert** section, all on by default: Rising Roots, Struggling Sapling, Flowering Tree, Woodcutting Leprechaun, Beehive, Friendly Ent, Poachers, Pheasant Control and Enchantment Ritual. Untick the ones you don't care for.

An event is spotted by the NPCs or objects that mark it, the same ones RuneLite's own Woodcutting plugin uses. It alerts once per event: more entlings, a withering sapling or a loading screen don't set it off again. While it fires, the status panel names it, e.g. `Forestry Leprechaun`. Pheasant Control's Freaky Forester no longer counts as a random event.

### AFK salvaging with crew

1. Put two crewmates on salvaging hooks at a shipwreck.
2. Pick **Salvaging AFK**.
3. When the hold fills, the crew's chat message starts the Cargo Hold alert. Click anywhere to silence it, then empty the hold.

The crew message arrives as a filtered (spam) game message; consts-AFK listens on that channel.

## Training guide

The same guide is in the game: the **consts-AFK guide** tab in RuneLite's sidebar shows the entry for the preset that's on, and its dropdown switches presets. Turn the tab off with **Show Sidebar Guide** under General.

### Chinning

- **Where:** Maniacal monkeys in Kruk's Dungeon, after Monkey Madness II. Grey, red or black chinchompas by Ranged level.
- **What the alerts mean:** Aggro says the monkeys have stopped being aggressive after ten minutes: run out of the area and back. Health and Prayer catch you before you run low. Boost says your Ranging potion is wearing off. Idle means you are out of chins or out of targets.
- **Tune:** Set Health and Prayer thresholds to where you would drink, and turn on Emit Immediately for both so a stray click can't silence them.

### Crabbing

- **Where:** Sand crabs on Crabclaw Isle or the Hosidius coast, ammonite crabs on Fossil Island, rock crabs near Rellekka. Stand where two or three crabs can reach you.
- **What the alerts mean:** Aggro is the one that matters: after ten minutes the crabs stop attacking. Walk 20 tiles away and back and the timer resets itself. Health tells you to eat; Idle means everything around you is asleep or dead.
- **Tune:** At low levels raise the Health threshold to about half your Hitpoints. No Prayer here, so a low Prayer level never beeps.

### Mining

- **Where:** Rocks that last: amethyst in the Mining Guild (92 Mining), or pay-dirt in the Motherlode Mine (30 Mining).
- **What the alerts mean:** Idle fires when your rock or vein runs out. Inv Full says it is time to bank or fill the hopper. Random and Logout keep you from getting dragged off or logged out.
- **Tune:** If the pauses between ores trip Idle, raise its time a little.

### Woodcutting

- **Where:** Chop where other people chop: Forestry events turn up more often with company, and pay out anima-infused bark for Forestry rewards. Long-lived trees like teaks, mahogany or redwoods keep you going.
- **What the alerts mean:** Forestry flashes lime and names the event, so you can tell a Leprechaun from an Ent at a glance. Idle fires when your tree falls; Inv Full when it is time to bank.
- **Tune:** Untick the Forestry events you would rather chop through. One alert per event, however many entlings show up.

### Fishing

- **Where:** Spots you can sit at: barbarian fishing at Otto's Grotto, or any spot where you drop fish as you go.
- **What the alerts mean:** Idle fires when the spot moves, and when your inventory fills, since you stop fishing either way. Random and Logout as always.
- **Tune:** Nothing to tune for most spots.

### Salvaging Active

- **Where:** Working a salvaging hook at a shipwreck yourself.
- **What the alerts mean:** Salvage fires when the wreck is picked clean or you have nothing left to sort. Inv Full when your inventory fills, Cargo when the hold is full.
- **Tune:** Salvage and Cargo beep 100 times by default; lower their iterations if you are always at the keyboard.

### Salvaging AFK

- **Where:** Two crewmates on salvaging hooks at a shipwreck, and you somewhere else.
- **What the alerts mean:** Only Cargo: when the hold fills, the crew says so in chat and the screen goes violet. Click to silence it, then empty the hold.
- **Tune:** Nothing. It is one alert on purpose.

### Custom

- **Where:** Anything the presets don't cover, including a cannon.
- **What the alerts mean:** Each alert's own Enable toggle decides. Cannon is only here.
- **Tune:** Switching to a preset never touches your Custom toggles, so you can always come back.

## Shared behavior

- **Interaction Pause** (`3 s`): every click or key press silences alerts briefly and acknowledges any one-off (chat or NPC) alert.
- **Emit Immediately** (Health, Prayer, Boost, Cannon): keep beeping through the pause. For Boost and Cannon, clicks also no longer cancel the alert.
- **Startup Delay** (`8 s`): nothing fires right after the plugin starts.
- **Loading screens** (teleports, region changes) don't reset timers or running alerts. Logging out does.
- **Sound**: all alerts use the system beep. `Repeat Interval` (`1.2 s`) sets the gap between beeps.
- **Sidebar guide**: a consts-AFK tab with a preset switcher and the Training guide above. **Show Sidebar Guide** (General) hides it.
- **Status panel**: shows the active preset, one bar per enabled alert (hover for details) and the pause countdown. `Show Animation ID` (General) adds your current animation id for tuning the idle alert.

## Running it

From the repo root: `./run.sh all`. Setup, including the one-time Jagex account step, is in the [root README](../README.md#run-it).
