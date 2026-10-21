package com.constsworld;

import static com.constsworld.WorldSearchTest.world;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;

public class ConstsWorldPanelTest
{
    private static final List<World> WORLDS = List.of(
        world(330, "Tempoross", WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS),
        world(302, "Trade", WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS),
        world(416, "-", WorldRegion.UNITED_STATES_OF_AMERICA, WorldType.MEMBERS),
        world(307, "Wintertodt", WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS),
        world(308, "-", WorldRegion.UNITED_KINGDOM));

    private static final Map<Integer, Integer> PLAYERS = Map.of(330, 1200, 302, 1900, 416, 300, 307, 300, 308, -1);

    private static List<Integer> sorted(ConstsWorldPanel.WorldOrder order, boolean ascending)
    {
        List<World> worlds = new ArrayList<>(WORLDS);
        worlds.sort(ConstsWorldPanel.comparator(order, ascending, w -> PLAYERS.get(w.getId())));
        return worlds.stream().map(World::getId).collect(Collectors.toList());
    }

    @Test
    public void theListSortsByWorldNumberBothWays()
    {
        assertEquals(List.of(302, 307, 308, 330, 416), sorted(ConstsWorldPanel.WorldOrder.WORLD, true));
        assertEquals(List.of(416, 330, 308, 307, 302), sorted(ConstsWorldPanel.WorldOrder.WORLD, false));
    }

    @Test
    public void theListSortsByPlayersWithTiesByWorldNumber()
    {
        assertEquals(List.of(308, 307, 416, 330, 302), sorted(ConstsWorldPanel.WorldOrder.PLAYERS, true));
        assertEquals(List.of(302, 330, 307, 416, 308), sorted(ConstsWorldPanel.WorldOrder.PLAYERS, false));
    }

    @Test
    public void worldsWithNoActivityStayAtTheBottomWhicheverWayTheActivitiesSort()
    {
        assertEquals(List.of(330, 302, 307, 308, 416), sorted(ConstsWorldPanel.WorldOrder.ACTIVITY, true));
        assertEquals(List.of(307, 302, 330, 308, 416), sorted(ConstsWorldPanel.WorldOrder.ACTIVITY, false));
    }

    @Test
    public void totalLevelWorldsSortByTheirNumbersNotTheirText()
    {
        List<String> activities = new ArrayList<>(List.of("2350 skill total", "Blast Furnace", "500 skill total",
            "1250 skill total", "750 skill total", "blast furnace 2", "Barbarian Assault"));
        activities.sort(ConstsWorldPanel::compareActivities);
        assertEquals(List.of("500 skill total", "750 skill total", "1250 skill total", "2350 skill total",
            "Barbarian Assault", "Blast Furnace", "blast furnace 2"), activities);
    }

    @Test
    public void eachTabCountsTheWorldsItShows()
    {
        assertEquals("Hot list (0)", ConstsWorldPanel.tabTitle("Hot list", 0));
        assertEquals("All worlds (15)", ConstsWorldPanel.tabTitle("All worlds", 15));
    }

    @Test
    public void anEmptyHotListSaysWhereToAddWorlds()
    {
        assertEquals("Your hot list is empty. Star worlds in All worlds to add them here, then click one here to hop.",
            ConstsWorldPanel.emptyMessage(ConstsWorldPanel.Tab.HOT_LIST, true, false));
        assertEquals("Nothing in your hot list matches. Try All worlds.",
            ConstsWorldPanel.emptyMessage(ConstsWorldPanel.Tab.HOT_LIST, true, true));
    }

    @Test
    public void allWorldsSaysWhenItsLoadingAndWhatToSearchFor()
    {
        assertEquals("Loading worlds...", ConstsWorldPanel.emptyMessage(ConstsWorldPanel.Tab.ALL_WORLDS, false, false));
        assertEquals("No world matches. Try an activity like Tempoross, a world like 330 or a region like UK.",
            ConstsWorldPanel.emptyMessage(ConstsWorldPanel.Tab.ALL_WORLDS, true, true));
    }

    @Test
    public void theStarSaysWhatAClickDoes()
    {
        assertEquals("Add world 330 to the hot list", WorldRow.hotAction(330, false));
        assertEquals("Remove world 330 from the hot list", WorldRow.hotAction(330, true));
    }
}
