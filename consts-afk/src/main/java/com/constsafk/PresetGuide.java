package com.constsafk;

import java.util.EnumMap;
import java.util.Map;

/**
 * A short training guide per preset: where it shines, what its alerts are telling you, and what to tune. Shown in
 * the sidebar guide; the README's Training guide says the same.
 */
final class PresetGuide
{
    private static final Map<Preset, PresetGuide> GUIDES = new EnumMap<>(Preset.class);

    static
    {
        put(Preset.CUSTOM,
            "Anything the presets don't cover, including a cannon.",
            "Each alert's own Enable toggle decides. Cannon is only here.",
            "Switching to a preset never touches your Custom toggles, so you can always come back.");
        put(Preset.CHINNING,
            "Maniacal monkeys in Kruk's Dungeon, after Monkey Madness II. Grey, red or black chinchompas by Ranged level.",
            "Aggro says the monkeys have stopped being aggressive after ten minutes: run out of the area and back. "
                + "Health and Prayer catch you before you run low. Boost says your Ranging potion is wearing off. "
                + "Idle means you are out of chins or out of targets.",
            "Set Health and Prayer thresholds to where you would drink, and turn on Emit Immediately for both so a "
                + "stray click can't silence them.");
        put(Preset.CRABBING,
            "Sand crabs on Crabclaw Isle or the Hosidius coast, ammonite crabs on Fossil Island, rock crabs near "
                + "Rellekka. Stand where two or three crabs can reach you.",
            "Aggro is the one that matters: after ten minutes the crabs stop attacking. Walk 20 tiles away and back "
                + "and the timer resets itself. Health tells you to eat; Idle means everything around you is asleep "
                + "or dead.",
            "At low levels raise the Health threshold to about half your Hitpoints. No Prayer here, so a low Prayer "
                + "level never beeps.");
        put(Preset.MINING,
            "Rocks that last: amethyst in the Mining Guild (92 Mining), or pay-dirt in the Motherlode Mine (30 Mining).",
            "Idle fires when your rock or vein runs out. Inv Full says it is time to bank or fill the hopper. Random "
                + "and Logout keep you from getting dragged off or logged out.",
            "If the pauses between ores trip Idle, raise its time a little.");
        put(Preset.WOODCUTTING,
            "Chop where other people chop: Forestry events turn up more often with company, and pay out "
                + "anima-infused bark for Forestry rewards. Long-lived trees like teaks, mahogany or redwoods keep "
                + "you going.",
            "Forestry flashes lime and names the event, so you can tell a Leprechaun from an Ent at a glance. Idle "
                + "fires when your tree falls; Inv Full when it is time to bank.",
            "Untick the Forestry events you would rather chop through. One alert per event, however many entlings "
                + "show up.");
        put(Preset.FISHING,
            "Spots you can sit at: barbarian fishing at Otto's Grotto, or any spot where you drop fish as you go.",
            "Idle fires when the spot moves, and when your inventory fills, since you stop fishing either way. "
                + "Random and Logout as always.",
            "Nothing to tune for most spots.");
        put(Preset.SALVAGING_ACTIVE,
            "Working a salvaging hook at a shipwreck yourself.",
            "Salvage fires when the wreck is picked clean or you have nothing left to sort. Inv Full when your "
                + "inventory fills, Cargo when the hold is full.",
            "Salvage and Cargo beep 100 times by default; lower their iterations if you are always at the keyboard.");
        put(Preset.SALVAGING_AFK,
            "Two crewmates on salvaging hooks at a shipwreck, and you somewhere else.",
            "Only Cargo: when the hold fills, the crew says so in chat and the screen goes violet. Click to silence "
                + "it, then empty the hold.",
            "Nothing. It is one alert on purpose.");
    }

    private final String where;
    private final String alerts;
    private final String tune;

    private PresetGuide(String where, String alerts, String tune)
    {
        this.where = where;
        this.alerts = alerts;
        this.tune = tune;
    }

    private static void put(Preset preset, String where, String alerts, String tune)
    {
        GUIDES.put(preset, new PresetGuide(where, alerts, tune));
    }

    static PresetGuide of(Preset preset)
    {
        return GUIDES.get(preset != null ? preset : Preset.CUSTOM);
    }

    String getWhere()
    {
        return where;
    }

    String getAlerts()
    {
        return alerts;
    }

    String getTune()
    {
        return tune;
    }
}
