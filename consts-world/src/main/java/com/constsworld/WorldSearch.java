package com.constsworld;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;

/**
 * The search box. A world matches when every word typed starts one of the world's own words: its number, its
 * activity, its region (the name and country codes, and "uk") and "members" and "p2p" or "free" and "f2p". So
 * "winter" finds Wintertodt, "total 2000" the 2000 total level worlds and "members uk" the UK members worlds. Case
 * doesn't matter, and an empty search matches every world.
 */
final class WorldSearch
{
    private WorldSearch()
    {
    }

    static boolean matches(World world, String query)
    {
        List<String> typed = words(query);
        if (typed.isEmpty())
        {
            return true;
        }

        List<String> own = words(world);
        for (String word : typed)
        {
            if (own.stream().noneMatch(w -> w.startsWith(word)))
            {
                return false;
            }
        }
        return true;
    }

    static List<String> words(World world)
    {
        StringBuilder text = new StringBuilder();
        text.append(world.getId());
        if (world.getActivity() != null)
        {
            text.append(' ').append(world.getActivity());
        }

        boolean members = world.getTypes() != null && world.getTypes().contains(WorldType.MEMBERS);
        text.append(members ? " members p2p" : " free f2p");

        WorldRegion region = world.getRegion();
        if (region != null)
        {
            text.append(' ').append(region.name())
                .append(' ').append(region.getAlpha2())
                .append(' ').append(region.getAlpha3());
            if (region == WorldRegion.UNITED_KINGDOM)
            {
                text.append(" uk");
            }
        }
        return words(text.toString());
    }

    private static List<String> words(String text)
    {
        if (text == null)
        {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .filter(w -> !w.isEmpty())
            .collect(Collectors.toList());
    }
}
