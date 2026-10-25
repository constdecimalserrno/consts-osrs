/*
 * Copyright 2020 Christopher Bitler
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: only the guard check, isGuardLured and isInPisc from
 *   StealingArtefactsPlugin.java and its Constants.java, worked out once a tick instead of while drawing)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.constsexp;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

/**
 * Stealing Artefacts' two guards in Port Piscarilius, from pajlads' Stealing Artefacts: each is lured while it stands
 * on its post facing its lured way, the east one south and the south-east one west.
 */
final class GuardLures
{
    private static final int GUARD_ID = 6980;
    private static final WorldPoint EAST_POST = new WorldPoint(1777, 3746, 0);
    private static final WorldPoint SOUTH_EAST_POST = new WorldPoint(1780, 3731, 0);
    // Orientations: 0 faces south, 512 west.
    private static final int EAST_LURED = 0;
    private static final int SOUTH_EAST_LURED = 512;

    private GuardLures()
    {
    }

    /**
     * Null when {@code player} is outside Port Piscarilius; else whether the east guard and
     * the south-east guard are lured, in that order. A guard off its post isn't.
     */
    static boolean[] check(WorldPoint player, Iterable<? extends NPC> npcs)
    {
        if (player.getX() < 1739 || player.getX() > 1860 || player.getY() < 3675 || player.getY() > 3803)
        {
            return null;
        }
        boolean[] lured = new boolean[2];
        for (NPC npc : npcs)
        {
            if (npc.getId() != GUARD_ID)
            {
                continue;
            }
            WorldPoint at = npc.getWorldLocation();
            int orientation = npc.getCurrentOrientation();
            lured[0] |= EAST_POST.equals(at) && orientation == EAST_LURED;
            lured[1] |= SOUTH_EAST_POST.equals(at) && orientation == SOUTH_EAST_LURED;
        }
        return lured;
    }
}
