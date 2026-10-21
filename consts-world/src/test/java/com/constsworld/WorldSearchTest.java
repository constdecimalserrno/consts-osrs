package com.constsworld;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;

public class WorldSearchTest
{
    static World world(int id, String activity, WorldRegion region, WorldType... types)
    {
        EnumSet<WorldType> set = EnumSet.noneOf(WorldType.class);
        for (WorldType type : types)
        {
            set.add(type);
        }
        return World.builder().id(id).activity(activity).region(region).types(set).address("oldschool" + id)
            .players(800).build();
    }

    private static final World WINTERTODT = world(307, "Wintertodt", WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS);
    private static final World TOTAL_2000 = world(420, "2000 skill total", WorldRegion.UNITED_STATES_OF_AMERICA,
        WorldType.MEMBERS, WorldType.SKILL_TOTAL);
    private static final World AUSTRALIA = world(434, "-", WorldRegion.AUSTRALIA, WorldType.MEMBERS);
    private static final World FREE_GERMANY = world(335, "Trade", WorldRegion.GERMANY);

    @Test
    public void anEmptySearchMatchesEveryWorld()
    {
        assertTrue(WorldSearch.matches(WINTERTODT, ""));
        assertTrue(WorldSearch.matches(WINTERTODT, "   "));
        assertTrue(WorldSearch.matches(WINTERTODT, null));
    }

    @Test
    public void anActivityMatchesByTheStartOfItsWords()
    {
        assertTrue(WorldSearch.matches(WINTERTODT, "wintertodt"));
        assertTrue(WorldSearch.matches(WINTERTODT, "Winter"));
        assertFalse(WorldSearch.matches(WINTERTODT, "todt"));
        assertFalse(WorldSearch.matches(FREE_GERMANY, "wintertodt"));
    }

    @Test
    public void everyWordTypedHasToMatch()
    {
        assertTrue(WorldSearch.matches(TOTAL_2000, "total 2000"));
        assertTrue(WorldSearch.matches(TOTAL_2000, "skill total"));
        assertFalse(WorldSearch.matches(TOTAL_2000, "total 2200"));
    }

    @Test
    public void aWorldMatchesByItsNumber()
    {
        assertTrue(WorldSearch.matches(WINTERTODT, "307"));
        assertTrue(WorldSearch.matches(WINTERTODT, "30"));
        assertFalse(WorldSearch.matches(WINTERTODT, "308"));
    }

    @Test
    public void aWorldMatchesByItsRegion()
    {
        assertTrue(WorldSearch.matches(WINTERTODT, "uk"));
        assertTrue(WorldSearch.matches(WINTERTODT, "united kingdom"));
        assertTrue(WorldSearch.matches(FREE_GERMANY, "germany"));
        assertTrue(WorldSearch.matches(FREE_GERMANY, "de"));
        assertTrue(WorldSearch.matches(TOTAL_2000, "us"));
        assertTrue(WorldSearch.matches(TOTAL_2000, "usa"));
        assertTrue(WorldSearch.matches(AUSTRALIA, "aus"));
        assertFalse(WorldSearch.matches(AUSTRALIA, "us"));
    }

    @Test
    public void membersAndFreeWorldsMatchByEitherName()
    {
        assertTrue(WorldSearch.matches(WINTERTODT, "members"));
        assertTrue(WorldSearch.matches(WINTERTODT, "p2p"));
        assertFalse(WorldSearch.matches(WINTERTODT, "f2p"));
        assertTrue(WorldSearch.matches(FREE_GERMANY, "free"));
        assertTrue(WorldSearch.matches(FREE_GERMANY, "f2p trade"));
        assertFalse(WorldSearch.matches(FREE_GERMANY, "members"));
        assertTrue(WorldSearch.matches(WINTERTODT, "members uk"));
        assertFalse(WorldSearch.matches(FREE_GERMANY, "members uk"));
    }

    @Test
    public void aWorldWithNoActivityStillMatchesByItsNumberAndRegion()
    {
        assertTrue(WorldSearch.matches(AUSTRALIA, "434 australia"));
        assertFalse(WorldSearch.matches(AUSTRALIA, "trade"));
    }
}
