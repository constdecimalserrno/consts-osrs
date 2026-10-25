package com.constsmodels;

/**
 * One animation of a captured actor, as its frames: where every corner of the {@link Mesh} is at each one. A frame
 * moves corners only; its triangles and colors are the mesh's. Frame f's corner v is at
 * ({@code x[f][v]}, {@code y[f][v]}, {@code z[f][v]}), in the mesh's axes and units.
 *
 * <p>Timing is the game's: frame f lasts {@code ticks[f]} client ticks of 20 ms, so it starts at the sum of the earlier
 * frames' ticks. Played on a loop, the frame after the last is {@code loopFrom}. A clip never changes once built.
 */
final class Clip
{
    /** How long a client tick is, in milliseconds. */
    static final int TICK_MS = 20;

    /** What it is: Idle, Turn left, Turn right, Walk, Walk back, Sidestep left, Sidestep right or Run. */
    final String label;

    /** The game's id for the animation. */
    final int animation;

    /** Each frame's length in client ticks, at least 1. */
    final int[] ticks;

    /** The frame a loop restarts at. */
    final int loopFrom;

    final float[][] x;
    final float[][] y;
    final float[][] z;

    Clip(String label, int animation, int[] ticks, int loopFrom, float[][] x, float[][] y, float[][] z)
    {
        if (ticks.length == 0 || x.length != ticks.length || y.length != ticks.length || z.length != ticks.length)
        {
            throw new IllegalArgumentException("a clip needs a position for every corner at each of its frames");
        }
        if (loopFrom < 0 || loopFrom >= ticks.length)
        {
            throw new IllegalArgumentException("loop from frame " + loopFrom + " of " + ticks.length);
        }
        for (int f = 0; f < ticks.length; f++)
        {
            if (ticks[f] < 1 || x[f].length != x[0].length || y[f].length != x[0].length || z[f].length != x[0].length)
            {
                throw new IllegalArgumentException("frame " + f + " is out of step with the rest");
            }
        }
        this.label = label;
        this.animation = animation;
        this.ticks = ticks;
        this.loopFrom = loopFrom;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    int frameCount()
    {
        return ticks.length;
    }

    int vertexCount()
    {
        return x[0].length;
    }
}
