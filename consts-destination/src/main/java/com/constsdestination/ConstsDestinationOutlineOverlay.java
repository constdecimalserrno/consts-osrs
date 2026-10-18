/*
 * Copyright (c) 2021, Adam <Adam@sigterm.info>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's Interact Highlight
 *   plugin into consts-Destination; the tracking moved out of the plugin class, the outline colors follow
 *   consts-Destination's settings)
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
package com.constsdestination;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.ColorUtil;

/**
 * Outlines what you hover in the hover color and what you clicked in the clicked color (the attack color when it's a
 * fight), flashing white for a moment on the click - Interact Highlight's outlines, in consts-Destination's colors.
 */
class ConstsDestinationOutlineOverlay extends Overlay
{
    private static final Color INTERACT_CLICK_COLOR = new Color(0x90ffffff);

    private final Client client;
    private final InteractTracker tracker;
    private final ConstsDestinationConfig config;
    private final ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    private ConstsDestinationOutlineOverlay(Client client, InteractTracker tracker, ConstsDestinationConfig config,
        ModelOutlineRenderer modelOutlineRenderer)
    {
        this.client = client;
        this.tracker = tracker;
        this.config = config;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (config.outlineOnHover())
        {
            renderMouseover();
        }
        if (config.outlineOnInteract())
        {
            renderTarget();
        }
        return null;
    }

    private void renderMouseover()
    {
        MenuEntry entry = tracker.hoveredEntry();
        if (entry == null)
        {
            return;
        }

        MenuAction menuAction = entry.getType();
        InteractTracker.Kind kind = InteractTracker.kindOf(menuAction);
        if (kind == null || !ConstsDestinationPlugin.outlines(config, kind))
        {
            return;
        }

        boolean onInteract = config.outlineOnInteract();
        Color hover = config.highlightColor();
        switch (kind)
        {
            case OBJECT:
            {
                TileObject tileObject = tracker.findTileObject(entry.getWorldViewId(), entry.getParam0(), entry.getParam1(),
                    entry.getIdentifier());
                if (tileObject != null && (tileObject != tracker.getInteractedObject() || !onInteract))
                {
                    modelOutlineRenderer.drawOutline(tileObject, config.outlineWidth(), hover, config.outlineFeather());
                }
                break;
            }
            case ITEM:
            {
                ItemLayer layer = tracker.findItemLayer(entry.getWorldViewId(), entry.getParam0(), entry.getParam1());
                TileItem item = InteractTracker.findItem(layer, entry.getIdentifier());
                if (item != null && (item != tracker.getInteractedItem() || !onInteract))
                {
                    modelOutlineRenderer.drawOutline(layer, item, config.outlineWidth(), hover, config.outlineFeather());
                }
                break;
            }
            case NPC:
            {
                NPC npc = entry.getNpc();
                if (npc != null && (npc != tracker.getInteractedActor() || !onInteract))
                {
                    Color color = tracker.isAttack(menuAction) ? config.attackColor() : hover;
                    modelOutlineRenderer.drawOutline(npc, config.outlineWidth(), color, config.outlineFeather());
                }
                break;
            }
            case PLAYER:
            {
                Player player = entry.getPlayer();
                if (player != null && (player != tracker.getInteractedActor() || !onInteract))
                {
                    modelOutlineRenderer.drawOutline(player, config.outlineWidth(), hover, config.outlineFeather());
                }
                break;
            }
        }
    }

    private void renderTarget()
    {
        long sinceClick = client.getGameCycle() - tracker.getGameCycle();
        Color hover = config.highlightColor();

        TileObject interactedObject = tracker.getInteractedObject();
        TileItem item = tracker.getInteractedItem();
        if (item != null && interactedObject instanceof ItemLayer && config.outlineGroundItems())
        {
            modelOutlineRenderer.drawOutline((ItemLayer) interactedObject, item, config.outlineWidth(),
                clickColor(hover, config.clickedColor(), sinceClick), config.outlineFeather());
        }
        else if (interactedObject != null && item == null && config.outlineObjects())
        {
            modelOutlineRenderer.drawOutline(interactedObject, config.outlineWidth(),
                clickColor(hover, config.clickedColor(), sinceClick), config.outlineFeather());
        }

        Actor target = tracker.getInteractedActor();
        if (target instanceof NPC && config.outlineNpcs())
        {
            Color start = tracker.isAttacked() ? config.attackColor() : hover;
            Color end = tracker.isAttacked() ? config.attackColor() : config.clickedColor();
            modelOutlineRenderer.drawOutline(target, config.outlineWidth(), clickColor(start, end, sinceClick),
                config.outlineFeather());
        }
        else if (target instanceof Player && config.outlinePlayers())
        {
            modelOutlineRenderer.drawOutline(target, config.outlineWidth(),
                clickColor(hover, config.clickedColor(), sinceClick), config.outlineFeather());
        }
    }

    // The click: from the hover color to a white flash and on to the target's color, over ten game cycles.
    private static Color clickColor(Color start, Color end, long time)
    {
        if (time < 5)
        {
            return ColorUtil.colorLerp(start, INTERACT_CLICK_COLOR, time / 5f);
        }
        else if (time < 10)
        {
            return ColorUtil.colorLerp(INTERACT_CLICK_COLOR, end, (time - 5) / 5f);
        }
        return end;
    }
}
