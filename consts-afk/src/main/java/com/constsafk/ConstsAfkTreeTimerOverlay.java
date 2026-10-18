/*
 * Copyright (c) 2023, Jason O'Neill
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from Tree Despawn Timer,
 *   TreeDespawnTimerOverlay.java; drawn only while consts-AFK's Tree Timer is on, sizes fixed at its defaults,
 *   no debug text, the top-level world view instead of the deprecated client calls)
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
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;

/**
 * Draws each tree's time left on the tree: a pie, a tick count or minutes and seconds, bigger on a popular tree. It
 * isn't an alert, so it shows whenever Woodcutting: Tree Timer is on, whatever the preset.
 */
@Singleton
class ConstsAfkTreeTimerOverlay extends Overlay
{
    // Tree Despawn Timer's default sizes: the pie's diameter and the text's font size, normal and popular.
    static final int SIZE_NORMAL = 16;
    static final int SIZE_POPULAR = 25;

    private final Client client;
    private final ConstsAfkConfig config;
    private final TreeTimer treeTimer;

    @Inject
    ConstsAfkTreeTimerOverlay(Client client, ConstsAfkConfig config, TreeTimer treeTimer)
    {
        this.client = client;
        this.config = config;
        this.treeTimer = treeTimer;

        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showTreeTimer())
        {
            return null;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return null;
        }

        TreeTimerDisplay display = config.treeTimerDisplay();
        int subTickMillis = treeTimer.getSubTickMillis(System.currentTimeMillis());
        for (TreeTimerState tree : treeTimer.getTrees())
        {
            if (!tree.shouldShowTimer())
            {
                continue;
            }
            // Null for a tree on another plane than ours.
            LocalPoint lp = LocalPoint.fromWorld(worldView, tree.getWorldPoint());
            if (lp == null)
            {
                continue;
            }
            LocalPoint centeredPoint = new LocalPoint(lp.getX() + tree.getCenterOffset().getX(),
                lp.getY() + tree.getCenterOffset().getY(), worldView);
            Point point = Perspective.localToCanvas(client, centeredPoint, worldView.getPlane());
            if (point == null)
            {
                continue;
            }

            boolean popular = config.treeTimerPopularIndicator() && tree.isPopular();
            Color color = tree.getTimerColor();
            String text = timerText(display, tree, subTickMillis);
            if (text == null)
            {
                renderPie(graphics, point, tree, color, popular);
            }
            else
            {
                TreeTimerText textComponent = new TreeTimerText(text, new java.awt.Point(point.getX(), point.getY()));
                textComponent.setEmphasize(popular);
                textComponent.setColor(color);
                textComponent.render(graphics);
            }
        }
        return null;
    }

    /** What the timer reads: nothing for the pie, else the ticks left, or minutes and seconds left. */
    static String timerText(TreeTimerDisplay display, TreeTimerState tree, int subTickMillis)
    {
        if (display == TreeTimerDisplay.TICKS)
        {
            return String.valueOf(tree.getTimeTicks());
        }
        if (display == TreeTimerDisplay.SECONDS)
        {
            int seconds = tree.getTimeSeconds(subTickMillis);
            return String.format("%d:%02d", seconds / 60, seconds % 60);
        }
        // The pie, also for a setting saved under a name that no longer exists.
        return null;
    }

    private static void renderPie(Graphics2D graphics, Point point, TreeTimerState tree, Color color, boolean popular)
    {
        ProgressPieComponent pie = new ProgressPieComponent();
        pie.setPosition(point);
        pie.setBorderColor(color);
        pie.setDiameter(SIZE_NORMAL);
        if (popular)
        {
            pie.setBorder(Color.BLACK, 2);
            pie.setDiameter(SIZE_POPULAR);
        }
        pie.setFill(color);
        pie.setProgress(tree.getTimePercent());
        pie.render(graphics);
    }
}
