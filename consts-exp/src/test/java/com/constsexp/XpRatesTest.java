package com.constsexp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;

public class XpRatesTest
{
    private static final long T0 = 1_000_000L;
    private static final long MINUTE = 60_000L;

    private final XpRates rates = new XpRates();

    @Test
    public void nothingTrainedShowsNothing()
    {
        assertNull(rates.shown());
        assertEquals(List.of(), rates.skills());
        assertEquals(0, rates.perHour(Skill.MINING, T0));
    }

    @Test
    public void theRateIsTheXpAfterTheFirstDropOverTheTimeSinceIt()
    {
        rates.gain(Skill.MINING, 100, T0);
        assertEquals("one drop: no time to rate it over yet", 0, rates.perHour(Skill.MINING, T0));
        assertEquals(100, rates.gained(Skill.MINING));

        // 100 XP a minute, steady.
        rates.gain(Skill.MINING, 100, T0 + MINUTE);
        rates.gain(Skill.MINING, 100, T0 + 2 * MINUTE);
        assertEquals(6_000, rates.perHour(Skill.MINING, T0 + 2 * MINUTE));
        assertEquals(300, rates.gained(Skill.MINING));
        assertEquals(2 * MINUTE, rates.elapsed(Skill.MINING, T0 + 2 * MINUTE));

        // A break: the rate falls while the clock runs.
        assertEquals(3_000, rates.perHour(Skill.MINING, T0 + 4 * MINUTE));
    }

    @Test
    public void aResetTakesTheSkillOffTheListUntilItsNextDrop()
    {
        rates.gain(Skill.MINING, 100, T0);
        rates.gain(Skill.MINING, 100, T0 + MINUTE);
        rates.gain(Skill.FISHING, 50, T0 + MINUTE);

        rates.reset(Skill.MINING);
        assertEquals(0, rates.gained(Skill.MINING));
        assertEquals(0, rates.perHour(Skill.MINING, T0 + 2 * MINUTE));
        assertEquals(0, rates.elapsed(Skill.MINING, T0 + 2 * MINUTE));
        assertEquals("the skill leaves the list", List.of(Skill.FISHING), rates.skills());
        assertEquals("other skills are left alone", 50, rates.gained(Skill.FISHING));

        rates.gain(Skill.MINING, 100, T0 + 10 * MINUTE);
        rates.gain(Skill.MINING, 100, T0 + 11 * MINUTE);
        assertEquals("back on top, from zero", List.of(Skill.MINING, Skill.FISHING), rates.skills());
        assertEquals(6_000, rates.perHour(Skill.MINING, T0 + 11 * MINUTE));
    }

    @Test
    public void resetAllEmptiesTheList()
    {
        rates.gain(Skill.MINING, 100, T0);
        rates.gain(Skill.FISHING, 50, T0);

        rates.clear();
        assertEquals(List.of(), rates.skills());
        assertEquals(0, rates.gained(Skill.MINING));
        assertNull("the status panel's row hides", rates.shown());
    }

    @Test
    public void theShownSkillFallsBackToTheNewestLeftWhenItLeaves()
    {
        rates.gain(Skill.ATTACK, 40, T0);
        rates.gain(Skill.HITPOINTS, 13, T0);
        rates.gain(Skill.FISHING, 50, T0 + MINUTE);
        rates.gain(Skill.MINING, 100, T0 + 2 * MINUTE);

        rates.reset(Skill.MINING);
        assertEquals(Skill.FISHING, rates.shown());
        rates.reset(Skill.FISHING);
        assertEquals(Skill.ATTACK, rates.shown());
        rates.reset(Skill.ATTACK);
        assertEquals("hitpoints once nothing else is left", Skill.HITPOINTS, rates.shown());
        rates.reset(Skill.HITPOINTS);
        assertNull(rates.shown());
    }

    @Test
    public void theShownSkillFollowsTheDropsOrderNotTheClock()
    {
        // Two skills in the same ms (longrange), then the clock steps back.
        rates.gain(Skill.RANGED, 40, T0);
        rates.gain(Skill.DEFENCE, 40, T0);
        assertEquals(Skill.DEFENCE, rates.shown());
        rates.gain(Skill.RANGED, 40, T0 - MINUTE);
        assertEquals(Skill.RANGED, rates.shown());
    }

    @Test
    public void aSkillWithNoDropForTheIdleTimeLeavesTheList()
    {
        long hour = 60 * MINUTE;
        rates.gain(Skill.MINING, 100, T0);
        rates.gain(Skill.FISHING, 50, T0);
        rates.gain(Skill.FISHING, 50, T0 + 30 * MINUTE);

        rates.dropIdle(hour, T0 + hour - 1);
        assertEquals("not an hour yet", List.of(Skill.FISHING, Skill.MINING), rates.skills());

        rates.dropIdle(hour, T0 + hour);
        assertEquals("an hour since mining's last drop; fishing's was 30 minutes ago", List.of(Skill.FISHING),
            rates.skills());
        assertEquals(Skill.FISHING, rates.shown());

        rates.dropIdle(0, T0 + 10 * hour);
        assertEquals("0 drops nothing", List.of(Skill.FISHING), rates.skills());
    }

    @Test
    public void clearForgetsEverySkill()
    {
        rates.gain(Skill.MINING, 100, T0);

        rates.clear();
        assertNull(rates.shown());
        assertEquals(List.of(), rates.skills());
    }

    @Test
    public void theShownSkillIsTheLastToGainXpButHitpointsAndSlayerOnlyWhenNothingElseHas()
    {
        rates.gain(Skill.HITPOINTS, 13, T0);
        assertEquals("only hitpoints so far", Skill.HITPOINTS, rates.shown());

        rates.gain(Skill.ATTACK, 40, T0);
        rates.gain(Skill.HITPOINTS, 13, T0 + 600);
        rates.gain(Skill.SLAYER, 30, T0 + 1200);
        assertEquals("combat XP comes with hitpoints and slayer", Skill.ATTACK, rates.shown());

        rates.gain(Skill.MINING, 5, T0 + 1800);
        assertEquals(Skill.MINING, rates.shown());
        assertEquals("newest first, by first drop", List.of(Skill.MINING, Skill.SLAYER, Skill.ATTACK, Skill.HITPOINTS),
            rates.skills());
    }

    @Test
    public void ratesReadShort()
    {
        assertEquals("850", XpRates.format(850));
        assertEquals("24.3k", XpRates.format(24_340));
        assertEquals("100k", XpRates.format(99_990));
        assertEquals("312k", XpRates.format(312_400));
        assertEquals("1.21M", XpRates.format(1_214_000));
        assertEquals("32:10", XpRates.formatTime(1_930_000));
        assertEquals("1:02:03", XpRates.formatTime(3_723_000));
    }
}
