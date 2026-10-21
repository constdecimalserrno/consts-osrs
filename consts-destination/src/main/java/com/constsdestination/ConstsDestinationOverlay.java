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
    private final InteractTracker tracker;

    @Inject
    private ConstsDestinationOverlay(Client client, ConstsDestinationPlugin plugin, ConstsDestinationConfig config,
        InteractTracker tracker)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.masterSwitch())
        {
            return null;
        }

        // Clicked tile (orange, persists until next click)
        if (config.showClickedTile())
        {
            renderClickedTile(graphics);
        }

        // Hover tile (blue, follows cursor), unless what's under the cursor is outlined instead
        if (config.showHoverTile() && !isHoveringOutlinedEntity())
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

    private boolean isHoveringOutlinedEntity()
    {
        MenuEntry entry = tracker.hoveredEntry();
        InteractTracker.Kind kind = entry != null ? InteractTracker.kindOf(entry.getType()) : null;
        return kind != null && config.outlineOnHover() && ConstsDestinationPlugin.outlines(config, kind);
    }
}
