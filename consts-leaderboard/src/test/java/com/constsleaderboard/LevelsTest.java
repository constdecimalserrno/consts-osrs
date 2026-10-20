package com.constsleaderboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LevelsTest
{
    private static final long XP_99 = 13_034_431;

    @Test
    public void anUnrankedSkillHasNoLevel()
    {
        assertEquals(-1, Levels.level(-1));
    }

    @Test
    public void levelsFollowTheGamesTable()
    {
        assertEquals(1, Levels.level(0));
        assertEquals(1, Levels.level(82));
        assertEquals(2, Levels.level(83));
        assertEquals(98, Levels.level(XP_99 - 1));
        assertEquals(99, Levels.level(XP_99));
    }

    @Test
    public void levelsStopAt99()
    {
        assertEquals(99, Levels.level(14_391_160));
        assertEquals(99, Levels.level(200_000_000));
        assertEquals(99, Levels.level(5_000_000_000L));
    }

    @Test
    public void remainingIsTheExperienceToTheNextLevel()
    {
        assertEquals(83, Levels.remaining(0));
        assertEquals(1, Levels.remaining(82));
        assertEquals(1, Levels.remaining(XP_99 - 1));
    }

    @Test
    public void nothingRemainsAt99OrWhenUnranked()
    {
        assertEquals(0, Levels.remaining(XP_99));
        assertEquals(0, Levels.remaining(200_000_000));
        assertEquals(0, Levels.remaining(-1));
    }

    @Test
    public void progressRunsThroughTheLevelAndIsFullAt99()
    {
        assertEquals(0, Levels.progress(-1));
        assertEquals(0, Levels.progress(0));
        assertEquals(50, Levels.progress(42));
        assertEquals(98, Levels.progress(82));
        assertEquals(100, Levels.progress(XP_99));
        assertEquals(100, Levels.progress(200_000_000));
    }
}
