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
 * XP per hour for each skill you're training: the XP after its first drop over the time since that drop, so steady
 * drops give their true rate and a single drop no spike. A skill joins at its first drop and leaves at a reset, at the
 * login screen, or once it's gone idle (see dropIdle). Times are in ms, from the caller's clock. Drops come on the
 * client thread and the side menu reads on Swing's, so every method locks.
 */
final class XpRates
{
    // They come with combat XP, so they show only while no other skill has gained any.
    private static final Set<Skill> SIDE_SKILLS = EnumSet.of(Skill.HITPOINTS, Skill.SLAYER);

    private static final class Rate
    {
        long gained;
        long firstXp;
        long firstAt;
        long lastAt;
        // Which drop of all was its last, so the shown skill follows the drops' order, not the clock.
        long lastDrop;
    }

    // By first drop, oldest first.
    private final Map<Skill, Rate> rates = new LinkedHashMap<>();
    private long drops;

    synchronized void gain(Skill skill, int xp, long now)
    {
        Rate rate = rates.computeIfAbsent(skill, s ->
        {
            Rate first = new Rate();
            first.firstAt = now;
            first.firstXp = xp;
            return first;
        });
        rate.gained += xp;
        rate.lastAt = now;
        rate.lastDrop = ++drops;
    }

    /** Takes the skill off the list; its next drop brings it back, from zero. */
    synchronized void reset(Skill skill)
    {
        rates.remove(skill);
    }

    /** Forgets every skill: Reset all, and the login screen. */
    synchronized void clear()
    {
        rates.clear();
    }

    /** Takes off every skill whose last drop is idleMs or more ago; 0 or less keeps them all. */
    synchronized void dropIdle(long idleMs, long now)
    {
        if (idleMs > 0)
        {
            rates.values().removeIf(rate -> now - rate.lastAt >= idleMs);
        }
    }

    /** The skill you're training: the last left to gain XP, Hitpoints and Slayer only if no other is left. */
    synchronized Skill shown()
    {
        Skill shown = null;
        // Starts as a side skill before any drop, so the first skill always takes it.
        boolean shownSide = true;
        long shownDrop = 0;
        for (Map.Entry<Skill, Rate> entry : rates.entrySet())
        {
            boolean side = SIDE_SKILLS.contains(entry.getKey());
            long drop = entry.getValue().lastDrop;
            if ((shownSide && !side) || (side == shownSide && drop > shownDrop))
            {
                shown = entry.getKey();
                shownSide = side;
                shownDrop = drop;
            }
        }
        return shown;
    }

    /** Every skill on the list, newest first (by first drop). */
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
        return rate != null ? Math.max(0, now - rate.firstAt) : 0;
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
