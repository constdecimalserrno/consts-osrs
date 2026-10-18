package com.constsdestination;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
class ConstsDestinationOverlay extends Overlay
{
    private final Client client;
    private final ConstsDestinationPlugin plugin;
    private final ConstsDestinationConfig config;

    @Inject
    private ConstsDestinationOverlay(Client client, ConstsDestinationPlugin plugin, ConstsDestinationConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Clicked tile (orange, persists until next click)
        renderClickedTile(graphics);

        // Hover tile (blue, follows cursor)
        if (!isHoveringInteractable())
        {
            renderHoverTile(graphics);
        }

        return null;
    }

    private void renderClickedTile(Graphics2D graphics)
    {
        WorldPoint point = plugin.getClickedTile();
        if (point == null)
        {
            return;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(client, point);
        if (localPoint == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
        if (poly == null)
        {
            return;
        }

        renderTilePoly(graphics, poly, config.clickedColor());
    }

    private void renderHoverTile(Graphics2D graphics)
    {
        Tile tile = client.getSelectedSceneTile();
        if (tile == null)
        {
            return;
        }

        LocalPoint localPoint = tile.getLocalLocation();
        if (localPoint == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
        if (poly == null)
        {
            return;
        }

        renderTilePoly(graphics, poly, config.highlightColor());
    }

    private void renderTilePoly(Graphics2D graphics, Polygon poly, Color baseColor)
    {
        int fillOpacity = config.fillOpacity();

        // Border
        Stroke originalStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(config.borderWidth()));
        graphics.setColor(baseColor);
        graphics.draw(poly);
        graphics.setStroke(originalStroke);

        // Fill
        if (fillOpacity > 0)
        {
            graphics.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), fillOpacity));
            graphics.fill(poly);
        }
    }

    private boolean isHoveringInteractable()
    {
        MenuEntry[] entries = client.getMenuEntries();
        if (entries == null || entries.length == 0)
        {
            return false;
        }

        MenuAction action = entries[entries.length - 1].getType();
        switch (action)
        {
            case NPC_FIRST_OPTION:
            case NPC_SECOND_OPTION:
            case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION:
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
                return true;
            default:
                return false;
        }
    }
}
