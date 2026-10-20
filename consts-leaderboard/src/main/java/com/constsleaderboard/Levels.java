package com.constsleaderboard;

import net.runelite.api.Experience;

/**
 * Levels the way consts-Leaderboard shows them: they stop at 99. The hiscores give -1 experience for a skill that
 * isn't ranked, and such a skill has no level to show.
 */
final class Levels
{
    static final int MAX = Experience.MAX_REAL_LEVEL;

    private Levels()
    {
    }

    /** The level for this much experience, never past 99, or -1 when the skill isn't ranked. */
    static int level(long experience)
    {
        if (experience < 0)
        {
            return -1;
        }
        int xp = (int) Math.min(experience, Experience.MAX_SKILL_XP);
        return Math.min(MAX, Experience.getLevelForXp(xp));
    }

    /** Experience still needed for the next level: 0 at 99, since there is no next one. */
    static long remaining(long experience)
    {
        int level = level(experience);
        if (level < 1 || level >= MAX)
        {
            return 0;
        }
        return Experience.getXpForLevel(level + 1) - experience;
    }

    /** How far through its level this experience is, from 0 to 100. A 99 is full. */
    static int progress(long experience)
    {
        int level = level(experience);
        if (level < 1)
        {
            return 0;
        }
        if (level >= MAX)
        {
            return 100;
        }
        long from = Experience.getXpForLevel(level);
        long to = Experience.getXpForLevel(level + 1);
        return (int) ((experience - from) * 100 / (to - from));
    }
}
