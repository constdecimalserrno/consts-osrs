/*
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's NPC Aggression Timer,
 *   NpcAggroAreaOverlay.java and the line building in NpcAggroAreaPlugin.java; lines kept until the area or scene
 *   moves, top-level world view instead of the deprecated client calls, no out-of-combat hiding)
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
package com.constsafk;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.geometry.Geometry;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Outlines the unaggressive area on the ground: one color while NPCs are aggressive, another once they've stopped. */
@Singleton
class ConstsAfkAggroAreaOverlay extends Overlay
{
    private static final int MAX_LOCAL_DRAW_LENGTH = 20 * Perspective.LOCAL_TILE_SIZE;

    private final Client client;
    private final ConstsAfkConfig config;
    private final ConstsAfkPlugin plugin;

    // The outline in local coordinates, rebuilt only when the centers or the loaded scene change.
    private GeneralPath lines;
    private WorldPoint[] linesCenters;
    private int linesBaseX;
    private int linesBaseY;

    @Inject
    private ConstsAfkAggroAreaOverlay(Client client, ConstsAfkConfig config, ConstsAfkPlugin plugin)
    {
        this.client = client;
        this.config = config;
        this.plugin = plugin;

        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_LOW);
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        AggroArea area = plugin.getAggroArea();
        if (!plugin.shouldShowAggroAreaLines() || !area.hasArea())
        {
            return null;
        }

        WorldView worldView = client.getTopLevelWorldView();
        Player localPlayer = client.getLocalPlayer();
        if (worldView == null || localPlayer == null)
        {
            return null;
        }

        // The lines are in the top-level scene; aboard a boat the player stands in the boat's own world view.
        WorldView playerView = localPlayer.getWorldView();
        LocalPoint playerLp = localPlayer.getLocalLocation();
        if (playerView == null || !playerView.isTopLevel() || playerLp == null)
        {
            return null;
        }

        Color outlineColor = config.unaggroAreaColor();
        if (outlineColor == null || area.isAggressive(System.currentTimeMillis()))
        {
            outlineColor = config.aggroAreaColor();
        }

        renderPath(graphics, worldView, playerLp, linesFor(worldView, area), outlineColor);
        return null;
    }

    private GeneralPath linesFor(WorldView worldView, AggroArea area)
    {
        WorldPoint[] centers = area.getSafeCenters();
        if (lines == null || !Arrays.equals(centers, linesCenters)
            || worldView.getBaseX() != linesBaseX || worldView.getBaseY() != linesBaseY)
        {
            lines = calculateLines(worldView, area);
            linesCenters = centers;
            linesBaseX = worldView.getBaseX();
            linesBaseY = worldView.getBaseY();
        }
        return lines;
    }

    /** The area's outline clipped to the loaded scene, cut into 1-tile segments and moved into local coordinates. */
    static GeneralPath calculateLines(WorldView worldView, AggroArea area)
    {
        Rectangle sceneRect = new Rectangle(
            worldView.getBaseX() + 1, worldView.getBaseY() + 1,
            Constants.SCENE_SIZE - 2, Constants.SCENE_SIZE - 2);

        GeneralPath lines = new GeneralPath(area.generateSafeArea());
        lines = Geometry.clipPath(lines, sceneRect);
        lines = Geometry.splitIntoSegments(lines, 1);
        lines = Geometry.transformPath(lines, coords -> transformWorldToLocal(worldView, coords));
        return lines;
    }

    private static void transformWorldToLocal(WorldView worldView, float[] coords)
    {
        final LocalPoint lp = LocalPoint.fromWorld(worldView, (int) coords[0], (int) coords[1]);
        coords[0] = lp.getX() - Perspective.LOCAL_TILE_SIZE / 2f;
        coords[1] = lp.getY() - Perspective.LOCAL_TILE_SIZE / 2f;
    }

    private void renderPath(Graphics2D graphics, WorldView worldView, LocalPoint playerLp, GeneralPath path, Color color)
    {
        Rectangle viewArea = new Rectangle(
            playerLp.getX() - MAX_LOCAL_DRAW_LENGTH,
            playerLp.getY() - MAX_LOCAL_DRAW_LENGTH,
            MAX_LOCAL_DRAW_LENGTH * 2,
            MAX_LOCAL_DRAW_LENGTH * 2);

        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1));

        int plane = worldView.getPlane();
        path = Geometry.clipPath(path, viewArea);
        path = Geometry.filterPath(path, (p1, p2) ->
            toCanvas(worldView, p1, plane) != null && toCanvas(worldView, p2, plane) != null);
        path = Geometry.transformPath(path, coords ->
        {
            Point point = toCanvas(worldView, coords, plane);
            coords[0] = point.getX();
            coords[1] = point.getY();
        });

        graphics.draw(path);
    }

    private Point toCanvas(WorldView worldView, float[] coords, int plane)
    {
        return Perspective.localToCanvas(client, new LocalPoint((int) coords[0], (int) coords[1], worldView), plane);
    }
}
