package com.constsworld;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The worlds in the hot list, in the order they were added, and the text RuneLite's config keeps them as: "330,302".
 * It starts empty.
 */
final class HotList
{
    private final Set<Integer> worlds = new LinkedHashSet<>();

    /** Reads the hot list back from config, skipping anything that isn't a world number. */
    static HotList parse(String saved)
    {
        HotList list = new HotList();
        if (saved == null)
        {
            return list;
        }

        for (String part : saved.split(","))
        {
            try
            {
                int world = Integer.parseInt(part.trim());
                if (world > 0)
                {
                    list.add(world);
                }
            }
            catch (NumberFormatException e)
            {
                // Not a world number; leave it out
            }
        }
        return list;
    }

    String format()
    {
        return worlds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    boolean contains(int world)
    {
        return worlds.contains(world);
    }

    /** Adds a world at the end; false when it's already in the list. */
    boolean add(int world)
    {
        return worlds.add(world);
    }

    /** False when the world wasn't in the list. */
    boolean remove(int world)
    {
        return worlds.remove(world);
    }

    boolean isEmpty()
    {
        return worlds.isEmpty();
    }

    List<Integer> worlds()
    {
        return List.copyOf(worlds);
    }
}
