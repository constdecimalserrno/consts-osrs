/*
 * Copyright (c) 2023, Jason O'Neill
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from Tree Despawn Timer,
 *   TreeDespawnTimerOverlay.java; drawn only while consts-EXP's Tree Timer is on, the pie only, at its default
 *   size, no debug text, the top-level world view instead of the deprecated client calls)
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
package com.constsexp;

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
 * Draws each tree's time left on the tree as a pie that empties. It isn't an alert, so it shows whenever Tree Timer
 * (Skills, Woodcutting) and the Master Switch are on, whatever the preset.
 */
@Singleton
class ConstsExpTreeTimerOverlay extends Overlay
{
    // Tree Despawn Timer's default pie diameter.
    private static final int PIE_DIAMETER = 16;

    private final Client client;
    private final ConstsExpConfig config;
    private final TreeTimer treeTimer;

    @Inject
    ConstsExpTreeTimerOverlay(Client client, ConstsExpConfig config, TreeTimer treeTimer)
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
        if (!config.masterSwitch() || !config.showTreeTimer())
        {
            return null;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return null;
        }

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

            Color color = tree.getTimerColor();
            ProgressPieComponent pie = new ProgressPieComponent();
            pie.setPosition(point);
            pie.setBorderColor(color);
            pie.setDiameter(PIE_DIAMETER);
            pie.setFill(color);
            pie.setProgress(tree.getTimePercent());
            pie.render(graphics);
        }
        return null;
    }
}
