package com.constsexp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.constsexp.TickPendulum.Rhythm;
import com.constsexp.TickPendulum.Verdict;
import org.junit.Before;
import org.junit.Test;

public class TickPendulumTest
{
    private static final long T0 = 1_000_000L;
    // Nothing a label watches has changed, or it has.
    private static final int SAME = 0;
    private static final int CHANGED = 1;

    private TickPendulum pendulum;

    @Before
    public void setUp()
    {
        pendulum = new TickPendulum();
        // No players: no server time, so the margin is the 60 ms guess plus 60 ms of client ticks and the cutoff 480.
        pendulum.world(0, "US", T0);
        pendulum.tick(T0, SAME);
    }

    @Test
    public void eachSwingIsOneClickWindowAheadOfTheGameTickByTheMargin()
    {
        pendulum = new TickPendulum();
        pendulum.world(800, "US", T0);
        pendulum.tick(T0, SAME);
        assertEquals("60 ping + 72 server + 60 client", 192, pendulum.margin(T0));

        // GameTick 1 shows 192 ms into swing 1, which turns 408 ms later at the cutoff.
        assertSwing(1, 192, T0);
        assertSwing(1, 300, T0 + 108);
        assertSwing(2, 0, T0 + 408);
        assertTrue("odd swings go left to right", TickPendulum.toRight(1));
        assertFalse(TickPendulum.toRight(2));
    }

    @Test
    public void onALateTickTheBallHoldsWhereTheTickWillShow()
    {
        assertSwing(2, 120, T0 + 600);
        assertSwing(2, 120, T0 + 900);

        pendulum.tick(T0 + 900, SAME);
        assertSwing(2, 120, T0 + 900);
        assertSwing(2, 220, T0 + 1000);
    }

    @Test
    public void onAnEarlyTickTheBallCatchesUpOver100Ms()
    {
        pendulum.tick(T0 + 500, SAME);

        assertSwing(2, 20, T0 + 500);
        assertSwing(2, 120, T0 + 550);
        assertSwing(2, 220, T0 + 600);
    }

    @Test
    public void aClickInTheMiddleZoneIsPerfectAndElsewhereInTheSwingCounts()
    {
        // Swing 1's middle is 180 ms after GameTick 1; the zone is 80 ms, 40 either side.
        assertEquals(80, pendulum.zoneMs());
        assertEquals(Verdict.PERFECT, pendulum.click(T0 + 220, false, SAME));
        assertEquals(1, pendulum.run());

        pendulum.tick(T0 + 600, SAME);
        assertEquals(Verdict.GOOD, pendulum.click(T0 + 600 + 230, false, SAME));
        assertEquals(2, pendulum.run());
    }

    @Test
    public void aSwingWithoutItsClickDropsTheCountAsItEnds()
    {
        pendulum.click(T0 + 180, false, SAME);
        pendulum.tick(T0 + 600, SAME);
        pendulum.click(T0 + 780, false, SAME);
        assertEquals(2, pendulum.run());

        pendulum.tick(T0 + 1200, SAME);
        pendulum.advance(T0 + 1200 + 479);
        assertEquals("swing 3 is still open", 2, pendulum.run());
        pendulum.advance(T0 + 1200 + 481);
        assertEquals(0, pendulum.run());
        assertEquals(T0 + 1681, pendulum.dropAt());
    }

    @Test
    public void swingsLeftAloneWithNoCountDontFlashRed()
    {
        pendulum.tick(T0 + 600, SAME);
        pendulum.advance(T0 + 1100);

        assertEquals(0, pendulum.run());
        assertEquals(TickPendulum.NEVER, pendulum.washAt());
    }

    @Test
    public void aFlickTakesTwoClicksInOneSwing()
    {
        pendulum.settings(Rhythm.FLICK, 0, T0);
        assertEquals(140, pendulum.zoneMs());

        assertEquals(Verdict.HALF, pendulum.click(T0 + 140, false, SAME));
        assertEquals(0, pendulum.run());
        assertEquals(Verdict.PERFECT, pendulum.click(T0 + 230, false, SAME));
        assertEquals(1, pendulum.run());
    }

    @Test
    public void aFlickSplitAcrossTheEndIsRed()
    {
        pendulum.settings(Rhythm.FLICK, 0, T0);
        pendulum.click(T0 + 180, false, SAME);
        pendulum.click(T0 + 220, false, SAME);
        assertEquals(1, pendulum.run());

        pendulum.tick(T0 + 600, SAME);
        // Swing 2 starts 480 ms after GameTick 1: one click either side of it.
        pendulum.click(T0 + 1060, false, SAME);
        assertEquals(Verdict.BAD, pendulum.click(T0 + 1100, false, SAME));
        assertEquals(0, pendulum.run());
    }

    @Test
    public void everyOtherSwingTakesItsClickInTheSwingYourHitsplatShowsIn()
    {
        pendulum.settings(Rhythm.EVERY_OTHER, 0, T0);
        pendulum.hitsplat();
        pendulum.tick(T0 + 600, SAME);
        assertTrue(pendulum.clickSwing(2));
        assertFalse(pendulum.clickSwing(3));

        assertEquals(Verdict.GOOD, pendulum.click(T0 + 600 + 100, false, SAME));
        pendulum.tick(T0 + 1200, SAME);
        // Swing 3 left alone keeps the rhythm and counts.
        pendulum.advance(T0 + 1200 + 481);
        assertEquals(2, pendulum.run());

        pendulum.tick(T0 + 1800, SAME);
        pendulum.click(T0 + 1800 + 180, false, SAME);
        pendulum.tick(T0 + 2400, SAME);
        assertEquals("a click on the off swing", Verdict.BAD, pendulum.click(T0 + 2400 + 180, false, SAME));
        assertEquals(0, pendulum.run());
    }

    @Test
    public void aLabelledClickThatMissedTheCutoffItExpectedMovesTheCutoffToIt()
    {
        assertEquals(60, pendulum.ping());
        assertTrue(pendulum.learned());
        pendulum.click(T0 + 400, true, SAME);
        pendulum.tick(T0 + 600, SAME);
        pendulum.click(T0 + 700, false, SAME);
        assertEquals(2, pendulum.run());
        pendulum.tick(T0 + 1200, CHANGED);

        // It showed a tick late, so the cutoff is at most 400 ms: 600 - 400 - 60 client = 140 ms of ping.
        assertEquals(140, pendulum.ping());
        assertEquals(T0 + 1200, pendulum.fixAt());
        assertEquals("that click landed on another tick", 0, pendulum.run());
    }

    @Test
    public void aLabelledClickThatMadeACutoffItDidntExpectMovesItLater()
    {
        pendulum.click(T0 + 520, true, SAME);
        pendulum.tick(T0 + 600, CHANGED);

        assertEquals(20, pendulum.ping());
    }

    @Test
    public void labelsThatAgreeOrComeFarFromTheCutoffChangeNothing()
    {
        pendulum.click(T0 + 400, true, SAME);
        pendulum.tick(T0 + 600, CHANGED);
        assertEquals(60, pendulum.ping());

        pendulum.click(T0 + 600 + 200, true, CHANGED);
        pendulum.tick(T0 + 1200, CHANGED);
        pendulum.tick(T0 + 1800, SAME);
        assertEquals("280 ms from the cutoff", 60, pendulum.ping());
        assertEquals(TickPendulum.NEVER, pendulum.fixAt());
    }

    @Test
    public void twoLabelledClicksInOneTickSpoilTheLabel()
    {
        pendulum.click(T0 + 300, true, SAME);
        pendulum.click(T0 + 400, true, SAME);
        pendulum.tick(T0 + 600, SAME);
        pendulum.tick(T0 + 1200, CHANGED);

        assertEquals(60, pendulum.ping());
    }

    @Test
    public void clicksInALateTickHoldOrAcrossAnOddGapAreNotLabelled()
    {
        pendulum.click(T0 + 650, true, SAME);
        pendulum.tick(T0 + 800, SAME);
        pendulum.tick(T0 + 1400, CHANGED);
        assertEquals("in the hold", 60, pendulum.ping());

        pendulum.click(T0 + 1400 + 400, true, SAME);
        pendulum.tick(T0 + 2000, SAME);
        pendulum.tick(T0 + 2750, CHANGED);
        assertEquals("a 750 ms gap", 60, pendulum.ping());
    }

    @Test
    public void theLearnedPingIsKeptPerRegion()
    {
        pendulum.click(T0 + 400, true, SAME);
        pendulum.tick(T0 + 600, SAME);
        pendulum.tick(T0 + 1200, CHANGED);
        assertEquals(140, pendulum.ping());

        pendulum.world(1000, "UK", T0 + 1300);
        assertEquals(60, pendulum.ping());
        pendulum.world(2000, "US", T0 + 1400);
        assertEquals(140, pendulum.ping());
        assertEquals("140 ping + 180 server + 60 client", 380, pendulum.target());
    }

    @Test
    public void yourPingOverridesTheLearnerWhichKeepsLearning()
    {
        pendulum.settings(Rhythm.SINGLE, 100, T0);
        assertEquals(100, pendulum.ping());
        assertFalse(pendulum.learned());

        pendulum.click(T0 + 400, true, SAME);
        pendulum.tick(T0 + 600, SAME);
        pendulum.tick(T0 + 1200, CHANGED);
        assertEquals(100, pendulum.ping());

        pendulum.settings(Rhythm.SINGLE, 0, T0 + 1200);
        assertEquals(140, pendulum.ping());
    }

    @Test
    public void aNewMarginIsReachedAt03MsPerMsSoTheBallNeverJumps()
    {
        pendulum.settings(Rhythm.SINGLE, 120, T0);

        assertEquals(120, pendulum.margin(T0));
        assertEquals(150, pendulum.margin(T0 + 100));
        assertEquals(180, pendulum.margin(T0 + 200));
        assertEquals(180, pendulum.margin(T0 + 1000));
    }

    private void assertSwing(int n, int elapsedMs, long now)
    {
        TickPendulum.Swing swing = pendulum.swing(now);
        assertEquals("swing", n, swing.n);
        assertEquals("ms into it", elapsedMs, swing.el, 0.5);
    }
}
