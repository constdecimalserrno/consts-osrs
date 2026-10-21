package com.constsdestination.tilepacks.ui.overlay;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.constsdestination.ConstsDestinationConfig;
import com.constsdestination.tilepacks.PointManager;
import com.google.common.collect.ArrayListMultimap;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;

public class TilePackOverlaysTest
{
    private PointManager points;
    private ConstsDestinationConfig config;
    private Graphics2D graphics;
    private GroundMarkerOverlay world;
    private GroundMarkerMinimapOverlay minimap;

    @Before
    public void setUp()
    {
        points = mock(PointManager.class);
        when(points.getPoints()).thenReturn(ArrayListMultimap.create());
        config = mock(ConstsDestinationConfig.class);
        when(config.tilePacksDrawOnMinimap()).thenReturn(true);
        graphics = mock(Graphics2D.class);
        world = new GroundMarkerOverlay(points, mock(Client.class), config);
        minimap = new GroundMarkerMinimapOverlay(points, mock(Client.class), config);
    }

    @Test
    public void theMasterSwitchOffHidesPackTilesInTheWorldAndOnTheMinimap()
    {
        when(config.masterSwitch()).thenReturn(false);

        world.render(graphics);
        minimap.render(graphics);

        verifyNoInteractions(points, graphics);
    }

    @Test
    public void withTheMasterSwitchOnBothDrawThePacksTiles()
    {
        when(config.masterSwitch()).thenReturn(true);

        world.render(graphics);
        minimap.render(graphics);

        verify(points, times(2)).getPoints();
    }

    @Test
    public void theMinimapFollowsItsOwnSetting()
    {
        when(config.masterSwitch()).thenReturn(true);
        when(config.tilePacksDrawOnMinimap()).thenReturn(false);

        minimap.render(graphics);

        verifyNoInteractions(points, graphics);
    }
}
