/*
 * Copyright (c) 2023, Jason O'Neill
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from Tree Despawn Timer, TreeState.java;
 *   no debug levels, the timer colors fixed at its defaults, the popular-tree rule moved in from its overlay)
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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * One tree's hidden Forestry "health", estimated a tick at a time the way Tree Despawn Timer does. Found by testing,
 * not official: it only starts going down once someone has taken a first log from the tree, then drops 1 every game
 * tick while anyone chops it and climbs back 1 every tick while nobody does. The tree falls at the next log after it
 * runs out.
 */
final class TreeTimerState
{
    // Tree Despawn Timer's default colors, by how much of the tree's time is left.
    static final Color COLOR_LOW = new Color(220, 0, 0);
    static final Color COLOR_MEDIUM = new Color(230, 160, 0);
    static final Color COLOR_HIGH = new Color(230, 230, 0);
    static final Color COLOR_FULL = new Color(0, 255, 0);

    // Forestry's hidden woodcutting boost grows with each player chopping the tree, up to 10 besides you.
    static final int POPULAR_PLAYERS = 10;
    // The Woodcutting Guild doesn't use Forestry's tree popularity.
    private static final Set<Integer> NO_POPULARITY_REGIONS = Set.of(6198, 6454);

    private final Client client;
    private final TreeTimerTree tree;
    private final WorldPoint worldPoint;
    // From the south-west tile to the middle of a tree an even number of tiles wide, in local units.
    private final Point centerOffset;
    private final List<WorldPoint> points;
    private final int maxTicks;
    private final Set<Player> playersChopping = new HashSet<>();
    // Players who were chopping it when they went out of view. They still count until seen again.
    private final Set<Player> unrenderedPlayersChopping = new HashSet<>();
    private int ticksLeft;
    private boolean haveYouChoppedLog;
    // Someone was already chopping it when we got here, so how long it has left can't be known.
    private boolean hidden;

    TreeTimerState(Client client, TreeTimerTree tree, WorldPoint worldPoint, Point centerOffset,
        List<WorldPoint> points)
    {
        this.client = client;
        this.tree = tree;
        this.worldPoint = worldPoint;
        this.centerOffset = centerOffset;
        this.points = points;
        this.maxTicks = tree.getMaxTicks();
        this.ticksLeft = maxTicks;
    }

    /** A freshly seen tree at full health, covering every tile of the object. */
    static TreeTimerState of(Client client, WorldView worldView, GameObject gameObject)
    {
        return new TreeTimerState(client, TreeTimerTree.forObject(gameObject.getId()), gameObject.getWorldLocation(),
            getCenterOffset(gameObject), getPoints(worldView, gameObject));
    }

    TreeTimerTree getTree()
    {
        return tree;
    }

    WorldPoint getWorldPoint()
    {
        return worldPoint;
    }

    Point getCenterOffset()
    {
        return centerOffset;
    }

    /** Every tile the tree stands on. A player next to any of them may be chopping it. */
    List<WorldPoint> getPoints()
    {
        return points;
    }

    // Who is chopping it

    Set<Player> getPlayersChopping()
    {
        return Collections.unmodifiableSet(playersChopping);
    }

    void addPlayerChopping(Player player)
    {
        playersChopping.add(player);
        // Seen again: no longer just remembered. Matched by name, since a player back in view is a new object.
        unrenderedPlayersChopping.removeIf(p -> Objects.equals(p.getName(), player.getName()));
    }

    void removePlayerChopping(Player player)
    {
        playersChopping.remove(player);
    }

    /** A chopper went out of view: keep counting them, since they most likely still chop. */
    void playerChoppingUnrendered(Player player)
    {
        unrenderedPlayersChopping.add(player);
        playersChopping.remove(player);
    }

    boolean hasUnrenderedPlayersChopping()
    {
        return !unrenderedPlayersChopping.isEmpty();
    }

    /** You took a log from it: chopping alone, its health now goes down. */
    void choppedLog()
    {
        haveYouChoppedLog = true;
    }

    /** Someone was chopping it before we saw it, so its time is unknown and stays hidden. */
    void hide()
    {
        hidden = true;
    }

    boolean isHidden()
    {
        return hidden;
    }

    // Health

    /**
     * Which way the health moves this tick. Chopping it alone, nothing happens until your first log; anyone else
     * chopping is taken to have had theirs.
     */
    int getTickDelta()
    {
        if (!playersChopping.isEmpty() || hasUnrenderedPlayersChopping())
        {
            if (playersChopping.size() >= 2 || !playersChopping.contains(client.getLocalPlayer()) || haveYouChoppedLog
                || hasUnrenderedPlayersChopping())
            {
                return -1;
            }
        }
        else if (ticksLeft < maxTicks)
        {
            return 1;
        }
        else if (haveYouChoppedLog && ticksLeft == maxTicks)
        {
            // Back to full: the next time you chop it alone, it waits for a first log again.
            haveYouChoppedLog = false;
        }
        return 0;
    }

    void tick()
    {
        ticksLeft += getTickDelta();
    }

    /** Shown while someone chops it or it has lost health, unless it was already being chopped when we got here. */
    boolean shouldShowTimer()
    {
        if (hidden)
        {
            return false;
        }
        if (!playersChopping.isEmpty())
        {
            return true;
        }
        return ticksLeft < maxTicks;
    }

    float getTimePercent()
    {
        return Math.max(ticksLeft / (float) maxTicks, 0);
    }

    int getTimeTicks()
    {
        return Math.max(ticksLeft, 0);
    }

    /** Seconds left, moved on by the time since the last game tick so the count runs smoothly between ticks. */
    int getTimeSeconds(int subTickMillis)
    {
        int secondsLeft = (int) Math.floor((ticksLeft * Constants.GAME_TICK_LENGTH + subTickMillis * getTickDelta())
            / 1000f);
        return Math.max(secondsLeft, 0);
    }

    Color getTimerColor()
    {
        double percent = getTimePercent() * 100;
        if (percent < 15)
        {
            return COLOR_LOW;
        }
        if (percent < 40)
        {
            return COLOR_MEDIUM;
        }
        if (percent < 80)
        {
            return COLOR_HIGH;
        }
        return COLOR_FULL;
    }

    /**
     * Ten or more players chop it besides you, the most the boost counts. The Forestry infobox on mobile shows the
     * bonus doesn't include yourself.
     */
    boolean isPopular()
    {
        int minPlayers = playersChopping.contains(client.getLocalPlayer()) ? POPULAR_PLAYERS + 1 : POPULAR_PLAYERS;
        return playersChopping.size() >= minPlayers && canShowPopularIndicator();
    }

    private boolean canShowPopularIndicator()
    {
        return !NO_POPULARITY_REGIONS.contains(worldPoint.getRegionID());
    }

    // Where it stands

    private static List<WorldPoint> getPoints(WorldView worldView, GameObject gameObject)
    {
        WorldPoint minPoint = toWorldPoint(worldView, gameObject, gameObject.getSceneMinLocation());
        WorldPoint maxPoint = toWorldPoint(worldView, gameObject, gameObject.getSceneMaxLocation());

        if (minPoint.equals(maxPoint))
        {
            return Collections.singletonList(minPoint);
        }

        final int plane = minPoint.getPlane();
        final List<WorldPoint> list = new ArrayList<>();
        for (int x = minPoint.getX(); x <= maxPoint.getX(); x++)
        {
            for (int y = minPoint.getY(); y <= maxPoint.getY(); y++)
            {
                list.add(new WorldPoint(x, y, plane));
            }
        }
        return list;
    }

    private static Point getCenterOffset(GameObject gameObject)
    {
        int x = 0;
        int y = 0;
        if (gameObject.sizeX() % 2 == 0)
        {
            x = (gameObject.sizeX() - 1) * Perspective.LOCAL_HALF_TILE_SIZE;
        }
        if (gameObject.sizeY() % 2 == 0)
        {
            y = (gameObject.sizeY() - 1) * Perspective.LOCAL_HALF_TILE_SIZE;
        }
        return new Point(x, y);
    }

    private static WorldPoint toWorldPoint(WorldView worldView, GameObject gameObject, Point scenePoint)
    {
        return WorldPoint.fromScene(worldView, scenePoint.getX(), scenePoint.getY(), gameObject.getPlane());
    }
}
