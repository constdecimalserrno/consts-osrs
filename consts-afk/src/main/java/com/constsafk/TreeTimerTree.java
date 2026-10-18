/*
 * Copyright (c) 2023, Jason O'Neill
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from Tree Despawn Timer, TreeConfig.java;
 *   the object id is checked before the region)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsafk;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.ObjectID;

/**
 * The trees Forestry keeps standing for a set time, each with how long that is and the objects that are it. The
 * times are the OSRS Wiki's, as Tree Despawn Timer has them.
 */
enum TreeTimerTree
{
    // Seconds from: https://oldschool.runescape.wiki/w/Woodcutting#Mechanics
    OAK(27, ObjectID.POH_SMALL_TREE3_5, ObjectID.OAKTREE, ObjectID.AVIUM_OAK_1),
    WILLOW(30, ObjectID.WILLOWTREE, ObjectID.WILLOW_TREE2, ObjectID.WILLOW_TREE3, ObjectID.WILLOW_TREE4),
    TEAK(30, ObjectID.TEAKTREE, ObjectID.PRIF_TEAKTREE, ObjectID.TEAKTREE_UPDATE),
    MAPLE(60, ObjectID.MAPLETREE, ObjectID.PRIF_MAPLETREE, ObjectID.MAPLETREE_UPDATE),
    HOLLOW(36, ObjectID.HOLLOW_TREE, ObjectID.HOLLOW_TREE_BIG),
    MAHOGANY(60, ObjectID.MAHOGANYTREE, ObjectID.PRIF_MAHOGANYTREE, ObjectID.MAHOGANYTREE_UPDATE),
    ARCTIC_PINE(60 + 24, ObjectID.ARCTIC_PINE),
    // DEADMAN_YEWTREE_INSURANCESTALL is the yew in Lumbridge Graveyard.
    YEW(60 + 54, ObjectID.YEWTREE, ObjectID.DEADMAN_YEWTREE, ObjectID.DEADMAN_YEWTREE_INSURANCESTALL,
        ObjectID.PRIF_YEWTREE, ObjectID.YEWTREE_UPDATE, ObjectID.TREE_YEW_DEFAULT01),
    CAMPHOR(60 + 54, ObjectID.CAMPHOR_TREE),
    MAGIC(60 * 3 + 54, ObjectID.MAGICTREE, ObjectID.DEADMAN_MAGICTREE),
    IRONWOOD(60 * 3 + 54, ObjectID.IRONWOOD_TREE),
    REDWOOD(60 * 4 + 24, ObjectID.REDWOODTREE_L, ObjectID.REDWOODTREE_R),
    ROSEWOOD(60 * 4 + 24, ObjectID.ROSEWOOD_TREE);

    // Miscellania and Etceteria: the kingdom's trees there never fall and give no logs, so a timer would only mislead.
    private static final Set<Integer> BLOCKED_REGIONS = Set.of(10044, 10300);

    private static final Map<Integer, TreeTimerTree> BY_OBJECT_ID = new HashMap<>();

    static
    {
        for (TreeTimerTree tree : values())
        {
            for (int id : tree.objectIds)
            {
                BY_OBJECT_ID.put(id, tree);
            }
        }
    }

    private final int maxTicks;
    private final int[] objectIds;

    TreeTimerTree(int maxSeconds, int... objectIds)
    {
        this.maxTicks = (int) Math.round(maxSeconds / 0.6d);
        this.objectIds = objectIds;
    }

    /** How many game ticks the tree can be chopped from full before it falls. */
    int getMaxTicks()
    {
        return maxTicks;
    }

    /** The tree an object is, or null. */
    static TreeTimerTree forObject(int objectId)
    {
        return BY_OBJECT_ID.get(objectId);
    }

    /** Whether the object is a tree that gets a timer: one of ours, and not in a region that doesn't use Forestry's. */
    static boolean isTree(GameObject gameObject)
    {
        // The id first: most objects aren't trees, and it saves working out a region for each.
        return BY_OBJECT_ID.containsKey(gameObject.getId())
            && !BLOCKED_REGIONS.contains(gameObject.getWorldLocation().getRegionID());
    }
}
