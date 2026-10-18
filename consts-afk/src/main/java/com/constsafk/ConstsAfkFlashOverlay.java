package com.constsafk;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Tints the whole game screen in the colors of every alert currently flashing. */
@Singleton
class ConstsAfkFlashOverlay extends Overlay
{
    private static final int MAX_FLASH_ALPHA = 180;

    private final Client client;
    private final ConstsAfkPlugin plugin;
    private final ConstsAfkConfig config;

    @Inject
    private ConstsAfkFlashOverlay(Client client, ConstsAfkPlugin plugin, ConstsAfkConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPriority(PRIORITY_HIGHEST);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        List<Color> colors = plugin.getCurrentFlashColors();
        Canvas canvas = client.getCanvas();
        if (colors.isEmpty() || canvas == null)
        {
            return null;
        }

        Dimension size = canvas.getSize();
        int alpha = Math.max(10, Math.min(MAX_FLASH_ALPHA, config.flashOpacity()));
        int perColorAlpha = Math.max(6, alpha / colors.size());

        for (Color color : colors)
        {
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), perColorAlpha));
            graphics.fillRect(0, 0, size.width, size.height);
        }
        return null;
    }
}
