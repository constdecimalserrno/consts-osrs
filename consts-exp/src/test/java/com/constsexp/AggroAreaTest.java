package com.constsexp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;

public class AggroAreaTest
{
    private static final long NOW = 1_000_000_000L;
    private static final long TEN_MINUTES = AggroArea.AGGRESSIVE_TIME_MS;

    private static final WorldPoint OLDER = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint NEWER = new WorldPoint(3215, 3200, 0);

    private final AggroArea area = new AggroArea();

    // Leaving the areas

    @Test
    public void leavingOneAreaButNotTheOtherShouldNotReset()
    {
        calibrate();

        // 5 tiles from the older center, 20 from the newer one: still inside the older area.
        assertFalse(area.update(new WorldPoint(3195, 3200, 0), NOW + 60_000L));
        // Exactly 10 tiles away (2D, the larger of dx and dy) still counts as inside.
        assertFalse(area.update(new WorldPoint(3190, 3210, 0), NOW + 61_000L));
        // And out the far side of the older area, into the newer one.
        assertFalse(area.update(new WorldPoint(3225, 3195, 0), NOW + 62_000L));

        assertArrayEquals(new WorldPoint[]{OLDER, NEWER}, area.getSafeCenters());
        assertEquals(NOW + TEN_MINUTES, area.getEndMillis());
    }

    @Test
    public void leavingBothAreasShouldResetAndMoveTheCenters()
    {
        calibrate();
        WorldPoint outsideBoth = new WorldPoint(3207, 3211, 0);

        assertTrue(area.update(outsideBoth, NOW + 60_000L));

        assertArrayEquals("the older center is dropped, the newer one ages",
            new WorldPoint[]{NEWER, outsideBoth}, area.getSafeCenters());
        assertEquals(NOW + 60_000L + TEN_MINUTES, area.getEndMillis());
        assertTrue(area.isCalibrated());
    }

    @Test
    public void timerShouldRunOutAfterTenMinutes()
    {
        calibrate();

        assertTrue(area.isAggressive(NOW + TEN_MINUTES - 1L));
        assertFalse(area.hasExpired(NOW + TEN_MINUTES - 1L));
        assertTrue(area.hasExpired(NOW + TEN_MINUTES));
        assertFalse(area.isAggressive(NOW + TEN_MINUTES));
    }

    // Calibration

    @Test
    public void walkingShouldNeverCalibrateAnUnknownArea()
    {
        WorldPoint location = new WorldPoint(3200, 3200, 0);
        for (int step = 0; step < 50; step++)
        {
            location = location.dx(2);
            assertFalse(area.update(location, NOW + step * 600L));
        }

        assertFalse(area.hasArea());
        assertEquals("no timer until the area is found", 0L, area.getEndMillis());
    }

    @Test
    public void teleportShouldLeaveItUncalibratedUntilWalkingSettlesIt()
    {
        WorldPoint before = new WorldPoint(3200, 3200, 0);
        WorldPoint arrival = new WorldPoint(3241, 3200, 0);
        area.update(before, NOW);

        assertTrue("a jump of more than 40 tiles is a teleport", area.update(arrival, NOW + 600L));

        assertTrue(area.hasArea());
        assertFalse(area.isCalibrated());
        assertArrayEquals(new WorldPoint[]{null, arrival}, area.getSafeCenters());
        assertEquals("the teleport left both areas, so the timer restarts", NOW + 600L + TEN_MINUTES, area.getEndMillis());

        assertFalse(area.update(arrival.dx(10), NOW + 1200L));
        assertFalse("still settling inside the one known area", area.isCalibrated());

        WorldPoint walkedOut = arrival.dx(11);
        assertTrue(area.update(walkedOut, NOW + 1800L));

        assertTrue(area.isCalibrated());
        assertArrayEquals(new WorldPoint[]{arrival, walkedOut}, area.getSafeCenters());
        assertEquals(NOW + 1800L + TEN_MINUTES, area.getEndMillis());
    }

    @Test
    public void jumpOfFortyTilesShouldNotCountAsATeleport()
    {
        area.update(new WorldPoint(3200, 3200, 0), NOW);

        assertFalse(area.update(new WorldPoint(3240, 3200, 0), NOW + 600L));
        assertFalse(area.hasArea());
    }

    @Test
    public void goingBackToTheUnknownAreaShouldForgetTheArea()
    {
        WorldPoint before = new WorldPoint(3200, 3200, 0);
        area.update(before, NOW);
        area.update(new WorldPoint(3300, 3300, 0), NOW + 600L);

        // Within 20 tiles of where the teleport started, the old, unknown center could be the one keeping NPCs calm.
        assertTrue(area.update(before.dx(20), NOW + 1200L));

        assertFalse(area.hasArea());
        assertEquals(0L, area.getEndMillis());
        assertFalse("nothing left to forget", area.update(before.dx(19), NOW + 1800L));
    }

    @Test
    public void resetShouldStartAFreshCenterWhereThePlayerStands()
    {
        WorldPoint here = new WorldPoint(3200, 3200, 0);

        area.reset(here, NOW);

        assertArrayEquals(new WorldPoint[]{null, here}, area.getSafeCenters());
        assertEquals(NOW + TEN_MINUTES, area.getEndMillis());

        WorldPoint later = new WorldPoint(3203, 3203, 0);
        area.reset(later, NOW + 60_000L);

        assertArrayEquals("the older center goes, as when leaving both areas",
            new WorldPoint[]{here, later}, area.getSafeCenters());
        assertEquals(NOW + 60_000L + TEN_MINUTES, area.getEndMillis());
    }

    // Logging out and in

    @Test
    public void savedAreaShouldRoundTripThroughTheConfigManager()
    {
        Map<String, Object> saved = new HashMap<>();
        ConfigManager configManager = profileConfig(saved);
        calibrate();

        area.loggedOut(configManager, NOW + 60_000L);

        assertEquals(OLDER, saved.get(AggroArea.CENTER1_KEY));
        assertEquals(NEWER, saved.get(AggroArea.CENTER2_KEY));
        assertEquals(NEWER, saved.get(AggroArea.LOCATION_KEY));
        assertEquals(Duration.ofMinutes(9L), saved.get(AggroArea.DURATION_KEY));
        assertFalse("logged out, nothing is tracked", area.hasArea());

        // Five minutes on the login screen don't count: the game's timer only runs while you're logged in.
        AggroArea restored = new AggroArea();
        restored.loggingIn();
        restored.loggedIn(configManager, NEWER, NOW + 360_000L);

        assertArrayEquals(new WorldPoint[]{OLDER, NEWER}, restored.getSafeCenters());
        assertEquals(NOW + 360_000L + Duration.ofMinutes(9L).toMillis(), restored.getEndMillis());
        assertTrue("read once, then cleared", saved.isEmpty());
    }

    @Test
    public void loggingInSomewhereElseShouldDropTheSavedArea()
    {
        Map<String, Object> saved = new HashMap<>();
        ConfigManager configManager = profileConfig(saved);
        calibrate();
        area.loggedOut(configManager, NOW);

        area.loggingIn();
        area.loggedIn(configManager, NEWER.dx(1), NOW);

        assertFalse(area.hasArea());
        assertEquals(0L, area.getEndMillis());
        assertTrue(saved.isEmpty());
    }

    @Test
    public void loadingScreenShouldNotReloadTheSavedArea()
    {
        Map<String, Object> saved = new HashMap<>();
        ConfigManager configManager = profileConfig(saved);
        calibrate();

        // LOGGED_IN after a loading screen, with no LOGGING_IN before it.
        area.loggedIn(configManager, new WorldPoint(3000, 3000, 0), NOW);

        assertArrayEquals(new WorldPoint[]{OLDER, NEWER}, area.getSafeCenters());
    }

    @Test
    public void halfKnownAreaShouldNotBeSaved()
    {
        Map<String, Object> saved = new HashMap<>();
        saved.put(AggroArea.CENTER1_KEY, OLDER);
        ConfigManager configManager = profileConfig(saved);
        area.reset(NEWER, NOW);

        area.loggedOut(configManager, NOW);

        assertTrue("stale keys are cleared too", saved.isEmpty());
    }

    // The outline

    @Test
    public void outlineOfOverlappingAreasShouldBeOneShape()
    {
        area.reset(new WorldPoint(3200, 3200, 0), NOW);
        area.reset(new WorldPoint(3205, 3205, 0), NOW);

        Area outline = area.generateSafeArea();

        assertTrue(outline.isSingular());
        List<Set<Point>> shapes = corners(outline);
        assertEquals(1, shapes.size());
        // Two 21x21 squares, the second 5 tiles up and right: a stepped octagon from (3190, 3190) to (3216, 3216).
        assertEquals(points(3190, 3190, 3211, 3190, 3211, 3195, 3216, 3195, 3216, 3216, 3195, 3216, 3195, 3211,
            3190, 3211), shapes.get(0));
        assertEquals(new Rectangle2D.Double(3190, 3190, 26, 26), outline.getBounds2D());
    }

    @Test
    public void outlineOfSeparateAreasShouldBeTwoSquares()
    {
        area.reset(new WorldPoint(3200, 3200, 0), NOW);
        area.reset(new WorldPoint(3230, 3200, 0), NOW);

        Area outline = area.generateSafeArea();

        assertFalse(outline.isSingular());
        Set<Set<Point>> shapes = new HashSet<>(corners(outline));
        Set<Set<Point>> expected = new HashSet<>(Arrays.asList(
            points(3190, 3190, 3211, 3190, 3211, 3211, 3190, 3211),
            points(3220, 3190, 3241, 3190, 3241, 3211, 3220, 3211)));
        assertEquals(expected, shapes);
    }

    @Test
    public void outlineOfOneKnownCenterShouldBeOneSquare()
    {
        area.reset(new WorldPoint(3200, 3200, 0), NOW);

        assertEquals(Arrays.asList(points(3190, 3190, 3211, 3190, 3211, 3211, 3190, 3211)),
            corners(area.generateSafeArea()));
    }

    @Test
    public void sceneLinesShouldBeOneTileSegmentsAroundTheAreaInLocalCoordinates()
    {
        WorldView worldView = mock(WorldView.class);
        when(worldView.getBaseX()).thenReturn(3150);
        when(worldView.getBaseY()).thenReturn(3150);
        when(worldView.getSizeX()).thenReturn(104);
        when(worldView.getSizeY()).thenReturn(104);
        area.reset(new WorldPoint(3200, 3200, 0), NOW);

        GeneralPath lines = ConstsExpAggroAreaOverlay.calculateLines(worldView, area);

        // The square's 21-tile sides are cut into 84 segments. Each corner sits on a tile's south-west edge:
        // scene tile 40 is local 40 * 128.
        int segments = 0;
        float[] coords = new float[6];
        for (PathIterator it = lines.getPathIterator(null); !it.isDone(); it.next())
        {
            int type = it.currentSegment(coords);
            if (type == PathIterator.SEG_LINETO)
            {
                segments++;
            }
            assertEquals(0f, coords[0] % 128f, 0f);
            assertEquals(0f, coords[1] % 128f, 0f);
            assertTrue(coords[0] >= 40 * 128 && coords[0] <= 61 * 128);
            assertTrue(coords[1] >= 40 * 128 && coords[1] <= 61 * 128);
        }
        assertEquals(84, segments);
    }

    private void calibrate()
    {
        area.reset(OLDER, NOW - 60_000L);
        area.reset(NEWER, NOW);
        assertTrue(area.isCalibrated());
    }

    /** A ConfigManager whose RuneScape profile keys live in the given map. */
    private static ConfigManager profileConfig(Map<String, Object> saved)
    {
        ConfigManager configManager = mock(ConfigManager.class);
        doAnswer(invocation ->
        {
            saved.put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(configManager).setRSProfileConfiguration(eq(ConstsExpConfig.GROUP), anyString(), any());
        doAnswer(invocation ->
        {
            saved.remove(invocation.<String>getArgument(1));
            return null;
        }).when(configManager).unsetRSProfileConfiguration(eq(ConstsExpConfig.GROUP), anyString());
        when(configManager.<Object>getRSProfileConfiguration(eq(ConstsExpConfig.GROUP), anyString(), any(Type.class)))
            .thenAnswer(invocation -> saved.get(invocation.<String>getArgument(1)));
        return configManager;
    }

    /** Each closed shape in the outline, as its set of corner points. */
    private static List<Set<Point>> corners(Area outline)
    {
        List<Set<Point>> shapes = new ArrayList<>();
        Set<Point> shape = null;
        double[] coords = new double[6];
        for (PathIterator it = outline.getPathIterator(null); !it.isDone(); it.next())
        {
            int type = it.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO)
            {
                shape = new HashSet<>();
                shapes.add(shape);
            }
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO)
            {
                shape.add(new Point((int) coords[0], (int) coords[1]));
            }
        }
        return shapes;
    }

    private static Set<Point> points(int... xy)
    {
        Set<Point> points = new HashSet<>();
        for (int i = 0; i < xy.length; i += 2)
        {
            points.add(new Point(xy[i], xy[i + 1]));
        }
        return points;
    }

    private static final class Point
    {
        private final int x;
        private final int y;

        private Point(int x, int y)
        {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other)
        {
            return other instanceof Point && ((Point) other).x == x && ((Point) other).y == y;
        }

        @Override
        public int hashCode()
        {
            return 31 * x + y;
        }

        @Override
        public String toString()
        {
            return "(" + x + ", " + y + ")";
        }
    }
}
