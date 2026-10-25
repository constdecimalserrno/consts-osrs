package com.constsexp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The tick pendulum's model (ticket 04, the design from ticket 09's round 7). Each swing of the ball is one tick's
 * click window: clicks between one cutoff and the next are read by the server on the same tick, so the ball turns at
 * a cutoff and crosses the middle, the safest click, halfway to the next. Seen from the client, GameTick n shows
 * {@link #margin} ms into swing n: the margin is the ping, the server's time on the tick and the client ticks.
 *
 * <p>RuneLite gives a plugin no ping, so the pendulum learns the cutoff from the player's clicks: a prayer or run
 * toggle whose result shows a tick later than the bar expected (or sooner) moves the cutoff to that click. Each world
 * region starts from a guess; Your Ping overrides it. Times are in ms, from the caller's clock.
 */
final class TickPendulum
{
    enum Rhythm
    {
        SINGLE("Single click"),
        FLICK("Flick"),
        EVERY_OTHER("Every other swing");

        private final String label;

        Rhythm(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    /** A click: in the zone, elsewhere in a click swing, a flick's first click, or off the rhythm. */
    enum Verdict
    {
        PERFECT,
        GOOD,
        HALF,
        BAD
    }

    /** Swing {@code n}, {@code el} ms in. */
    static final class Swing
    {
        final int n;
        final double el;

        Swing(int n, double el)
        {
            this.n = n;
            this.el = el;
        }
    }

    static final int TICK = 600;
    static final int GUESS_PING = 60;
    // Up to 60 ms of client ticks before a click is sent, and 0.09 ms of server time a player (research A).
    static final int CLIENT_MS = 60;
    static final double SERVER_MS_PER_PLAYER = 0.09;
    // A new margin is reached at 0.3 ms per ms: the ball runs at most 30% fast or slow, and never jumps.
    private static final double RATE = 0.3;
    private static final int CATCH_UP_MS = 100;
    // The learner moves only on clicks this close to its cutoff, so a click the game delayed can't drag it far.
    private static final int LEARN_WITHIN_MS = 200;
    // Labels across a GameTick gap outside this are thrown away.
    private static final int MIN_GAP_MS = 500;
    private static final int MAX_GAP_MS = 700;
    // Two clicks this close either side of a swing's end are a flick split across it.
    private static final int SPLIT_MS = 300;
    // When something that hasn't happened happened: long enough ago that now minus it can't overflow.
    static final long NEVER = Long.MIN_VALUE / 2;

    private Rhythm rhythm = Rhythm.SINGLE;
    private int yourPing;
    private int serverMs;
    private Object region;
    private int learnedPing = GUESS_PING;
    private final Map<Object, Integer> learnedByRegion = new HashMap<>();

    private int tickN;
    private long tickAt;
    private long tickLen = TICK;
    private boolean hitsplatSeen;
    private int clickParity = 1;

    private boolean marginSet;
    private int marginFrom;
    private int marginTo;
    private long marginAt;

    private int run;
    private int done;
    private final Set<Integer> hit = new HashSet<>();
    private final Set<Integer> off = new HashSet<>();
    // A flick's first click, waiting for its second.
    private Swing open;
    private long openAt;

    // A labelled click waiting for its result: when it came, how many GameTicks since, what it saw then.
    private boolean pending;
    private long pendingOffset;
    private int pendingSeen;
    private int pendingWatched;
    private int spoiledTick = -1;

    private long upAt = NEVER;
    private long dropAt = NEVER;
    private long washAt = NEVER;
    private Verdict wash = Verdict.GOOD;
    private long pipAt = NEVER;
    private long fixAt = NEVER;

    void settings(Rhythm rhythm, int yourPing, long now)
    {
        if (rhythm != this.rhythm)
        {
            open = null;
        }
        this.rhythm = rhythm;
        this.yourPing = Math.max(0, yourPing);
        retarget(now);
    }

    /** A world with this many players, in this region: the learned ping carries within a region. */
    void world(int players, Object region, long now)
    {
        if (this.region != null)
        {
            learnedByRegion.put(this.region, learnedPing);
        }
        this.region = region;
        learnedPing = learnedByRegion.getOrDefault(region, GUESS_PING);
        serverMs = (int) Math.round(SERVER_MS_PER_PLAYER * Math.max(0, players));
        retarget(now);
    }

    /** A hitsplat on you: in Every other swing, the swing of the next GameTick takes the clicks. */
    void hitsplat()
    {
        hitsplatSeen = true;
    }

    /** GameTick, with what labelled clicks watch (prayers, run) as the server has it now. */
    void tick(long now, int watched)
    {
        tickLen = tickN == 0 ? TICK : now - tickAt;
        tickAt = now;
        tickN++;
        if (hitsplatSeen)
        {
            clickParity = tickN & 1;
            hitsplatSeen = false;
        }
        if (pending)
        {
            pendingSeen++;
            if (tickLen < MIN_GAP_MS || tickLen > MAX_GAP_MS)
            {
                pending = false;
            }
            else if (watched != pendingWatched)
            {
                pending = false;
                learn(pendingSeen == 1, now);
            }
            else if (pendingSeen >= 2)
            {
                pending = false;
            }
        }
        retarget(now);
    }

    /**
     * A click. A labelled one (a prayer or run toggle) is watched for which GameTick shows its result; {@code watched}
     * is that state now.
     */
    Verdict click(long now, boolean labelled, int watched)
    {
        if (labelled)
        {
            label(now, watched);
        }

        advance(now);
        Swing s = swing(now);
        boolean zone = inZone(s.el);
        Verdict verdict;
        if (!clickSwing(s.n))
        {
            verdict = Verdict.BAD;
            off.add(s.n);
            drop(now);
        }
        else if (rhythm != Rhythm.FLICK)
        {
            verdict = zone ? Verdict.PERFECT : Verdict.GOOD;
            hit(s.n, now);
        }
        else if (open != null && open.n == s.n)
        {
            verdict = zone && inZone(open.el) ? Verdict.PERFECT : Verdict.GOOD;
            open = null;
            hit(s.n, now);
        }
        else if (open != null && open.n == s.n - 1 && now - openAt <= SPLIT_MS)
        {
            verdict = Verdict.BAD;
            open = null;
            drop(now);
        }
        else
        {
            verdict = Verdict.HALF;
            open = s;
            openAt = now;
        }
        washAt = now;
        wash = verdict;
        if (verdict == Verdict.PERFECT)
        {
            pipAt = now;
        }
        return verdict;
    }

    private void label(long now, int watched)
    {
        long offset = now - tickAt;
        if (pending || spoiledTick == tickN)
        {
            // A second toggle in the same wait: the first's result can't be told from the second's.
            pending = false;
            spoiledTick = tickN;
            return;
        }
        if (offset > TICK || tickLen < MIN_GAP_MS || tickLen > MAX_GAP_MS)
        {
            return;
        }
        pending = true;
        pendingOffset = offset;
        pendingSeen = 0;
        pendingWatched = watched;
    }

    /** A labelled click's result showed on the first GameTick after it ({@code made}), or the second. */
    private void learn(boolean made, long now)
    {
        int cutoff = TICK - (learnedPing + serverMs + CLIENT_MS);
        if ((pendingOffset < cutoff) == made || Math.abs(pendingOffset - cutoff) > LEARN_WITHIN_MS)
        {
            return;
        }
        learnedPing = (int) Math.max(0, TICK - pendingOffset - serverMs - CLIENT_MS);
        fixAt = now;
        // That click landed on another tick than the bar showed.
        drop(now);
        retarget(now);
    }

    /** Judge each swing as it ends: a click swing needs its click by then; an off swing left alone counts. */
    void advance(long now)
    {
        int current = swing(now).n;
        if (current <= done + 1)
        {
            return;
        }
        for (int n = Math.max(done + 1, current - 2); n < current; n++)
        {
            if (!clickSwing(n))
            {
                if (!off.contains(n) && run > 0)
                {
                    hit(n, now);
                }
            }
            else if (!hit.contains(n) && run > 0)
            {
                drop(now);
                washAt = now;
                wash = Verdict.BAD;
            }
        }
        done = current - 1;
        hit.removeIf(n -> n < current - 3);
        off.removeIf(n -> n < current - 3);
    }

    private void hit(int n, long now)
    {
        if (hit.add(n))
        {
            run++;
            upAt = now;
        }
    }

    private void drop(long now)
    {
        if (run > 0)
        {
            dropAt = now;
        }
        run = 0;
    }

    Swing swing(long now)
    {
        long since = now - tickAt;
        int m = margin(now);
        double behind = Math.min(0, tickLen - TICK) * Math.min(1, Math.max(0, 1 - since / (double) CATCH_UP_MS));
        double at = Math.min(since + m + behind, TICK + m);
        int d = (int) Math.floor(at / TICK);
        return new Swing(tickN + d, at - d * (double) TICK);
    }

    static boolean toRight(int n)
    {
        return (n & 1) == 1;
    }

    boolean clickSwing(int n)
    {
        return rhythm != Rhythm.EVERY_OTHER || (n & 1) == clickParity;
    }

    /** The zone in the middle of the swing: as tight as a click can hit, wide enough for two in Flick. */
    int zoneMs()
    {
        return rhythm == Rhythm.FLICK ? 140 : 80;
    }

    boolean inZone(double el)
    {
        return Math.abs(el - TICK / 2.0) <= zoneMs() / 2.0;
    }

    int target()
    {
        return ping() + serverMs + CLIENT_MS;
    }

    int margin(long now)
    {
        long span = (long) (Math.abs(marginTo - marginFrom) / RATE);
        if (now >= marginAt + span)
        {
            return marginTo;
        }
        return now <= marginAt ? marginFrom
            : (int) Math.round(marginFrom + (marginTo - marginFrom) * (now - marginAt) / (double) span);
    }

    private void retarget(long now)
    {
        int to = target();
        if (!marginSet)
        {
            marginSet = true;
            marginFrom = marginTo = to;
            marginAt = now;
        }
        else if (to != marginTo)
        {
            marginFrom = margin(now);
            marginTo = to;
            marginAt = now;
        }
    }

    /** The ping the bar follows: yours if you set one, else the learned one. */
    int ping()
    {
        return yourPing > 0 ? yourPing : learnedPing;
    }

    boolean learned()
    {
        return yourPing <= 0;
    }

    int run()
    {
        return run;
    }

    long upAt()
    {
        return upAt;
    }

    long dropAt()
    {
        return dropAt;
    }

    long washAt()
    {
        return washAt;
    }

    Verdict wash()
    {
        return wash;
    }

    long pipAt()
    {
        return pipAt;
    }

    long fixAt()
    {
        return fixAt;
    }
}
