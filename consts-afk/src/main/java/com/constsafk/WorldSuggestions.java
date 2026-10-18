package com.constsafk;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * Worlds worth hopping to for a preset, from RuneLite's live world list: the worlds the game themes for the activity
 * where it has them (Forestry, Salvaging), otherwise the quietest regular members worlds, where contested crabs,
 * monkeys and rocks are easiest to get.
 */
final class WorldSuggestions
{
    static final int LIMIT = 3;

    // Worlds nobody should be sent to for AFK training.
    private static final Set<WorldType> UNSUITABLE = EnumSet.of(
        WorldType.PVP, WorldType.BOUNTY, WorldType.PVP_ARENA, WorldType.SKILL_TOTAL, WorldType.QUEST_SPEEDRUNNING,
        WorldType.HIGH_RISK, WorldType.LAST_MAN_STANDING, WorldType.BETA_WORLD, WorldType.LEGACY_ONLY,
        WorldType.EOC_ONLY, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT, WorldType.FRESH_START_WORLD,
        WorldType.DEADMAN, WorldType.SEASONAL);

    private WorldSuggestions()
    {
    }

    /** The activity the game's world list themes worlds for, or null where a quiet regular world is best. */
    static String activityFor(Preset preset)
    {
        switch (preset)
        {
            case WOODCUTTING:
                return "Forestry";
            case SALVAGING_ACTIVE:
            case SALVAGING_AFK:
                return "Salvaging";
            default:
                return null;
        }
    }

    /** What the suggestions are, in a few words, for the sidebar. */
    static String describe(Preset preset)
    {
        if (preset == Preset.CUSTOM)
        {
            return "Pick a preset to get world suggestions.";
        }
        if (preset == Preset.WOODCUTTING)
        {
            return "Forestry worlds, busiest first: events come more often with company.";
        }
        String activity = activityFor(preset);
        return activity != null
            ? activity + " worlds, quietest first."
            : "The quietest regular members worlds.";
    }

    static List<World> suggest(Preset preset, List<World> worlds)
    {
        if (preset == null || preset == Preset.CUSTOM || worlds == null)
        {
            return Collections.emptyList();
        }

        String activity = activityFor(preset);
        Comparator<World> quietest = Comparator.comparingInt(World::getPlayers);
        Comparator<World> order = preset == Preset.WOODCUTTING ? quietest.reversed() : quietest;
        return worlds.stream()
            .filter(WorldSuggestions::isMembersTrainingWorld)
            .filter(w -> activity != null ? activity.equalsIgnoreCase(trimmed(w.getActivity())) : isRegular(w))
            .sorted(order)
            .limit(LIMIT)
            .collect(Collectors.toList());
    }

    private static boolean isMembersTrainingWorld(World world)
    {
        EnumSet<WorldType> types = world.getTypes();
        return types != null && types.contains(WorldType.MEMBERS) && Collections.disjoint(types, UNSUITABLE)
            && world.getPlayers() >= 0;
    }

    // Untitled worlds read "-" in the world list.
    private static boolean isRegular(World world)
    {
        String activity = trimmed(world.getActivity());
        return activity.isEmpty() || "-".equals(activity);
    }

    private static String trimmed(String activity)
    {
        return activity == null ? "" : activity.trim();
    }
}
