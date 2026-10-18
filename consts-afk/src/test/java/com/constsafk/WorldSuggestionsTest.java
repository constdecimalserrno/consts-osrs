package com.constsafk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;

public class WorldSuggestionsTest
{
    private static final List<World> WORLDS = Arrays.asList(
        world(301, "-", 900, WorldType.MEMBERS),
        world(302, "-", 120, WorldType.MEMBERS),
        world(303, "-", 40, WorldType.MEMBERS),
        world(304, "-", 10, WorldType.MEMBERS, WorldType.PVP),
        world(305, "2000 skill total", 5, WorldType.MEMBERS, WorldType.SKILL_TOTAL),
        world(306, "-", 1),
        world(307, "-", 15, WorldType.MEMBERS, WorldType.HIGH_RISK),
        world(444, "Forestry", 300, WorldType.MEMBERS),
        world(445, "Forestry", 1100, WorldType.MEMBERS),
        world(446, "Forestry", 700, WorldType.MEMBERS),
        world(447, "Forestry", 50, WorldType.MEMBERS),
        world(520, "Salvaging", 800, WorldType.MEMBERS),
        world(521, "Salvaging", 200, WorldType.MEMBERS),
        world(530, "Blast Furnace", 3, WorldType.MEMBERS),
        world(540, "-", -1, WorldType.MEMBERS));

    @Test
    public void woodcuttingShouldSuggestTheBusiestForestryWorlds()
    {
        assertEquals(Arrays.asList(445, 446, 444), ids(Preset.WOODCUTTING));
    }

    @Test
    public void salvagingShouldSuggestSalvagingWorldsQuietestFirst()
    {
        assertEquals(Arrays.asList(521, 520), ids(Preset.SALVAGING_ACTIVE));
        assertEquals(Arrays.asList(521, 520), ids(Preset.SALVAGING_AFK));
    }

    @Test
    public void presetsWithoutAThemeShouldSuggestTheQuietestRegularMembersWorlds()
    {
        // Not the PvP, high-risk, skill-total, free, themed or offline worlds, however quiet.
        for (Preset preset : EnumSet.of(Preset.CHINNING, Preset.CRABBING, Preset.MINING, Preset.FISHING))
        {
            assertEquals(preset.toString(), Arrays.asList(303, 302, 301), ids(preset));
        }
    }

    @Test
    public void customOrNoWorldListShouldSuggestNothing()
    {
        assertTrue(WorldSuggestions.suggest(Preset.CUSTOM, WORLDS).isEmpty());
        assertTrue(WorldSuggestions.suggest(Preset.WOODCUTTING, null).isEmpty());
    }

    private static List<Integer> ids(Preset preset)
    {
        return WorldSuggestions.suggest(preset, WORLDS).stream().map(World::getId).collect(Collectors.toList());
    }

    private static World world(int id, String activity, int players, WorldType... types)
    {
        EnumSet<WorldType> set = EnumSet.noneOf(WorldType.class);
        set.addAll(Arrays.asList(types));
        return World.builder().id(id).activity(activity).players(players).types(set).build();
    }
}
