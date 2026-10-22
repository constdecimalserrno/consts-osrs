/*
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's NPC Aggression Timer,
 *   NpcAggroAreaPlugin.java, into a model of its own; the timer, alert and NPC matching are left to consts-EXP)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsexp;

import java.awt.Polygon;
import java.awt.geom.Area;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

/**
 * The game's unaggressive area and its 10-minute timer, tracked the way RuneLite's NPC Aggression Timer does.
 *
 * How it works: the game remembers 2 tiles. When the player goes more than 10 steps away from both tiles, the
 * oldest one is moved to under the player and the NPC aggression timer resets. So to first figure out where the
 * 2 tiles are, we wait until the player teleports a long enough distance. At that point it's very likely that the
 * player moved out of the radius of both tiles, which resets one of them. The other should reset shortly after as
 * the player starts moving around.
 *
 * Only the client thread touches it: game ticks, game state changes and the panel's right-click reset.
 */
final class AggroArea
{
    static final int SAFE_AREA_RADIUS = 10;
    static final int UNKNOWN_AREA_RADIUS = SAFE_AREA_RADIUS * 2;
    // Farther than this in one tick is a teleport: no walk covers it.
    static final int TELEPORT_DISTANCE = SAFE_AREA_RADIUS * 4;
    static final long AGGRESSIVE_TIME_MS = TimeUnit.SECONDS.toMillis(600L);

    // Saved per RuneScape profile in consts-EXP's own config group, under keys of its own: sharing NPC Aggression
    // Timer's would let the two plugins clear each other's state at login.
    static final String CENTER1_KEY = "aggroCenter1";
    static final String CENTER2_KEY = "aggroCenter2";
    static final String LOCATION_KEY = "aggroLocation";
    static final String DURATION_KEY = "aggroDuration";

    // Index 0 is the older center, the one the game moves next.
    private final WorldPoint[] safeCenters = new WorldPoint[2];
    private WorldPoint lastPlayerLocation;
    private WorldPoint previousUnknownCenter;
    // When NPCs stop being aggressive, or 0 while there is no timer.
    private long endMillis;
    private boolean loggingIn;

    /**
     * Follows the player one game tick. Returns whether the timer restarted or was dropped, so a running alert can
     * stop and the lines can move.
     */
    boolean update(WorldPoint newLocation, long nowMillis)
    {
        boolean changed = false;

        if (lastPlayerLocation != null && safeCenters[1] == null
            && newLocation.distanceTo2D(lastPlayerLocation) > TELEPORT_DISTANCE)
        {
            safeCenters[0] = null;
            safeCenters[1] = newLocation;
            resetTimer(nowMillis);
            changed = true;

            // We don't know where the previous area was, so if the player e.g. entered a dungeon and then goes
            // back out, they may enter the previous area, which is unknown and would make the tracking inaccurate.
            previousUnknownCenter = lastPlayerLocation;
        }

        if (safeCenters[0] == null && previousUnknownCenter != null
            && previousUnknownCenter.distanceTo2D(newLocation) <= UNKNOWN_AREA_RADIUS)
        {
            // The player went back to their previous unknown area before the 2nd center point was found, which
            // means we don't know where it is again.
            changed |= safeCenters[1] != null || endMillis != 0L;
            safeCenters[1] = null;
            endMillis = 0L;
        }

        if (safeCenters[1] != null && Arrays.stream(safeCenters)
            .noneMatch(center -> center != null && center.distanceTo2D(newLocation) <= SAFE_AREA_RADIUS))
        {
            moveOlderCenter(newLocation, nowMillis);
            changed = true;
        }

        lastPlayerLocation = newLocation;
        return changed;
    }

    /** The panel's Reset Aggro Timer: a fresh center where the player stands, as if they had just left both areas. */
    void reset(WorldPoint location, long nowMillis)
    {
        if (location != null)
        {
            moveOlderCenter(location, nowMillis);
            lastPlayerLocation = location;
        }
    }

    private void moveOlderCenter(WorldPoint newCenter, long nowMillis)
    {
        safeCenters[0] = safeCenters[1];
        safeCenters[1] = newCenter;
        resetTimer(nowMillis);
        previousUnknownCenter = null;
    }

    private void resetTimer(long nowMillis)
    {
        endMillis = nowMillis + AGGRESSIVE_TIME_MS;
    }

    /** Both centers are known. Until then the lines show one area at most and the panel shows a "?". */
    boolean isCalibrated()
    {
        return safeCenters[0] != null && safeCenters[1] != null;
    }

    /** At least the newer center is known, so there is an area to draw and a timer running. */
    boolean hasArea()
    {
        return safeCenters[1] != null;
    }

    /** Older center first; either may be null. */
    WorldPoint[] getSafeCenters()
    {
        return safeCenters.clone();
    }

    long getEndMillis()
    {
        return endMillis;
    }

    boolean hasExpired(long nowMillis)
    {
        return endMillis != 0L && nowMillis >= endMillis;
    }

    boolean isAggressive(long nowMillis)
    {
        return endMillis != 0L && nowMillis < endMillis;
    }

    /** The union of both 21x21 areas, in world tile coordinates with each tile's south-west corner at (x, y). */
    Area generateSafeArea()
    {
        final Area area = new Area();

        for (WorldPoint wp : safeCenters)
        {
            if (wp == null)
            {
                continue;
            }

            Polygon poly = new Polygon();
            poly.addPoint(wp.getX() - SAFE_AREA_RADIUS, wp.getY() - SAFE_AREA_RADIUS);
            poly.addPoint(wp.getX() - SAFE_AREA_RADIUS, wp.getY() + SAFE_AREA_RADIUS + 1);
            poly.addPoint(wp.getX() + SAFE_AREA_RADIUS + 1, wp.getY() + SAFE_AREA_RADIUS + 1);
            poly.addPoint(wp.getX() + SAFE_AREA_RADIUS + 1, wp.getY() - SAFE_AREA_RADIUS);
            area.add(new Area(poly));
        }

        return area;
    }

    // Logging in and out

    void loggingIn()
    {
        loggingIn = true;
    }

    /**
     * Picks up what logging out saved, once per login (not after loading screens). The area is kept only if the
     * player is where they logged out.
     */
    void loggedIn(ConfigManager configManager, WorldPoint newLocation, long nowMillis)
    {
        if (!loggingIn)
        {
            return;
        }

        loggingIn = false;
        load(configManager, nowMillis);
        unsave(configManager);

        // If the player isn't at the location they logged out at, the safe unaggro area probably changed, and should
        // be disposed. Its timer goes with it: NPC Aggression Timer keeps it, but an alert counting down for an area
        // nobody knows could only be silenced by hand.
        if (newLocation == null || lastPlayerLocation == null || newLocation.distanceTo(lastPlayerLocation) != 0)
        {
            safeCenters[0] = null;
            safeCenters[1] = null;
            endMillis = 0L;
            lastPlayerLocation = newLocation;
        }
    }

    /** At the login screen: saves the area for the next login and forgets it. */
    void loggedOut(ConfigManager configManager, long nowMillis)
    {
        if (lastPlayerLocation != null)
        {
            save(configManager, nowMillis);
        }

        clear();
    }

    void clear()
    {
        Arrays.fill(safeCenters, null);
        lastPlayerLocation = null;
        previousUnknownCenter = null;
        endMillis = 0L;
    }

    private void load(ConfigManager configManager, long nowMillis)
    {
        safeCenters[0] = configManager.getRSProfileConfiguration(ConstsExpConfig.GROUP, CENTER1_KEY, WorldPoint.class);
        safeCenters[1] = configManager.getRSProfileConfiguration(ConstsExpConfig.GROUP, CENTER2_KEY, WorldPoint.class);
        lastPlayerLocation = configManager.getRSProfileConfiguration(ConstsExpConfig.GROUP, LOCATION_KEY,
            WorldPoint.class);

        // Negative when it ran out before logging out: the alert then fires right after login.
        Duration timeLeft = configManager.getRSProfileConfiguration(ConstsExpConfig.GROUP, DURATION_KEY,
            Duration.class);
        endMillis = timeLeft != null ? nowMillis + timeLeft.toMillis() : 0L;
    }

    private static void unsave(ConfigManager configManager)
    {
        configManager.unsetRSProfileConfiguration(ConstsExpConfig.GROUP, CENTER1_KEY);
        configManager.unsetRSProfileConfiguration(ConstsExpConfig.GROUP, CENTER2_KEY);
        configManager.unsetRSProfileConfiguration(ConstsExpConfig.GROUP, LOCATION_KEY);
        configManager.unsetRSProfileConfiguration(ConstsExpConfig.GROUP, DURATION_KEY);
    }

    private void save(ConfigManager configManager, long nowMillis)
    {
        if (safeCenters[0] == null || safeCenters[1] == null || lastPlayerLocation == null || endMillis == 0L)
        {
            unsave(configManager);
        }
        else
        {
            configManager.setRSProfileConfiguration(ConstsExpConfig.GROUP, CENTER1_KEY, safeCenters[0]);
            configManager.setRSProfileConfiguration(ConstsExpConfig.GROUP, CENTER2_KEY, safeCenters[1]);
            configManager.setRSProfileConfiguration(ConstsExpConfig.GROUP, LOCATION_KEY, lastPlayerLocation);
            configManager.setRSProfileConfiguration(ConstsExpConfig.GROUP, DURATION_KEY,
                Duration.ofMillis(endMillis - nowMillis));
        }
    }
}
