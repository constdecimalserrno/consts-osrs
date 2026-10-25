package com.constsexp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * XP per hour for each skill trained since the login: the XP after its first drop over the time since that drop, so
 * steady drops give their true rate and a single drop no spike. A reset sets a skill back to zero, with its clock
 * stopped until its next drop. Times are in ms, from the caller's clock. Drops come on the client thread and the side
 * menu reads on Swing's, so every method locks.
 */
final class XpRates
{
    // They come with combat XP, so they show only while no other skill has gained any.
    private static final Set<Skill> SIDE_SKILLS = EnumSet.of(Skill.HITPOINTS, Skill.SLAYER);

    private static final class Rate
    {
        long gained;
        long firstXp;
        // When its first drop came since the login or its reset; -1 for none yet.
        long firstAt = -1;
    }

    // By first drop, oldest first.
    private final Map<Skill, Rate> rates = new LinkedHashMap<>();
    private Skill last;
    private Skill lastSide;

    synchronized void gain(Skill skill, int xp, long now)
    {
        Rate rate = rates.computeIfAbsent(skill, s -> new Rate());
        if (rate.firstAt < 0)
        {
            rate.firstAt = now;
            rate.firstXp = xp;
        }
        rate.gained += xp;
        if (SIDE_SKILLS.contains(skill))
        {
            lastSide = skill;
        }
        else
        {
            last = skill;
        }
    }

    synchronized void reset(Skill skill)
    {
        rates.computeIfPresent(skill, (s, rate) -> new Rate());
    }

    synchronized void resetAll()
    {
        rates.replaceAll((s, rate) -> new Rate());
    }

    /** Forgets every skill, as at the login screen. */
    synchronized void clear()
    {
        rates.clear();
        last = null;
        lastSide = null;
    }

    /** The skill you're training: the last to gain XP, Hitpoints and Slayer only if no other has. Null for none. */
    synchronized Skill shown()
    {
        return last != null ? last : lastSide;
    }

    /** Every skill trained since the login, newest first. */
    synchronized List<Skill> skills()
    {
        List<Skill> skills = new ArrayList<>(rates.keySet());
        Collections.reverse(skills);
        return skills;
    }

    synchronized long gained(Skill skill)
    {
        Rate rate = rates.get(skill);
        return rate != null ? rate.gained : 0;
    }

    synchronized long elapsed(Skill skill, long now)
    {
        Rate rate = rates.get(skill);
        return rate != null && rate.firstAt >= 0 ? Math.max(0, now - rate.firstAt) : 0;
    }

    synchronized long perHour(Skill skill, long now)
    {
        long elapsed = elapsed(skill, now);
        Rate rate = rates.get(skill);
        return elapsed > 0 ? (rate.gained - rate.firstXp) * 3_600_000L / elapsed : 0;
    }

    /** A rate's time: 32:10, or 1:02:03 past an hour. */
    static String formatTime(long millis)
    {
        long s = Math.max(0, millis / 1_000);
        return s < 3_600 ? String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
            : String.format(Locale.ROOT, "%d:%02d:%02d", s / 3_600, s / 60 % 60, s % 60);
    }

    /** XP short enough for a 129 px row: 850, 24.3k, 312k, 1.21M. */
    static String format(long xp)
    {
        if (xp < 1_000)
        {
            return String.valueOf(xp);
        }
        if (xp < 99_950)
        {
            return String.format(Locale.ROOT, "%.1fk", xp / 1_000.0);
        }
        if (xp < 999_500)
        {
            return Math.round(xp / 1_000.0) + "k";
        }
        return String.format(Locale.ROOT, "%.2fM", xp / 1_000_000.0);
    }
}
