package com.constsexp;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.junit.Test;

/** The icons keep the sizes the Plugin Hub (48×72) and RuneLite's sidebar (16×16) draw them at. */
public class IconTest
{
    @Test
    public void theHubIconIs48By72AndTheSidebarIcon16By16() throws Exception
    {
        BufferedImage hub = ImageIO.read(Paths.get("icon.png").toFile());
        assertEquals(48, hub.getWidth());
        assertEquals(72, hub.getHeight());
        BufferedImage panel = ImageIO.read(ConstsExpPlugin.class.getResource("panel_icon.png"));
        assertEquals(16, panel.getWidth());
        assertEquals(16, panel.getHeight());
    }
}
