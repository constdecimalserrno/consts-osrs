package com.constsmodels;

import java.util.Arrays;
import java.util.BitSet;

/**
 * A box of cubic voxels, for {@link SolidRemesh}. A set of voxels is a bit set, voxel (x, y, z) at bit
 * x + nx * (y + ny * z), and grows or shrinks by true distance, so a wall grows by the same amount at any angle.
 *
 * <p>Distances are measured between voxel centers, exactly, out to {@link #MAX_REACH} voxels at a time. Squared
 * distances that far fit in a byte, so measuring takes one byte a voxel, kept from one measure to the next.
 */
final class VoxelGrid
{
    /** How far, in voxels, one measure reaches. */
    static final int MAX_REACH = 15;

    final int nx;
    final int ny;
    final int nz;

    /** Voxels in one slice of constant z: nx * ny. */
    final int slice;

    /** Voxels in all: nx * ny * nz. */
    final int size;

    /** Each voxel's squared distance to the set last measured, or {@link #cap} for that far or further. */
    private final byte[] distance;
    private int cap;

    /** Per row along x (row y + ny * z): all in the set, all at the cap, or neither, so passes can skip rows. */
    private final byte[] rows;
    private static final byte ZERO = 0;
    private static final byte FAR = 1;
    private static final byte MIXED = 2;

    VoxelGrid(int nx, int ny, int nz)
    {
        if (nx < 1 || ny < 1 || nz < 1 || (long) nx * ny * nz > Integer.MAX_VALUE / 2)
        {
            throw new IllegalArgumentException("grid of " + nx + " x " + ny + " x " + nz);
        }
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.slice = nx * ny;
        this.size = slice * nz;
        this.distance = new byte[size];
        this.rows = new byte[ny * nz];
    }

    int index(int x, int y, int z)
    {
        return x + nx * (y + ny * z);
    }

    /** The byte a voxel that measures use, free for other uses in between: the next measure overwrites it. */
    byte[] scratch()
    {
        return distance;
    }

    /** The voxels within radius of the set, the set included. */
    BitSet dilate(BitSet set, double radius)
    {
        measure(set, radius);
        return within(radius);
    }

    /** The voxels of the set further than radius from every voxel outside it (in the grid: its border isn't). */
    BitSet erode(BitSet set, double radius)
    {
        measure(complement(set), radius);
        return beyond(radius);
    }

    /** Every voxel not in the set. */
    BitSet complement(BitSet set)
    {
        BitSet out = (BitSet) set.clone();
        out.flip(0, size);
        return out;
    }

    /**
     * The walls with everything they close off: all but the voxels reached from the grid's border without passing
     * through a wall, stepping face to face.
     */
    BitSet fill(BitSet walls)
    {
        BitSet seen = (BitSet) walls.clone();
        IntStack stack = new IntStack();

        // Seeds: every open run along the border
        for (int z = 0; z < nz; z++)
        {
            for (int y = 0; y < ny; y++)
            {
                int start = index(0, y, z);
                if (z == 0 || z == nz - 1 || y == 0 || y == ny - 1)
                {
                    seedRuns(seen, start, start + nx, stack);
                }
                else
                {
                    seedRuns(seen, start, start + 1, stack);
                    seedRuns(seen, start + nx - 1, start + nx, stack);
                }
            }
        }

        // Fills a run along x at a time, then seeds the open runs beside it
        while (stack.size > 0)
        {
            int i = stack.pop();
            if (seen.get(i))
            {
                continue;
            }
            int row = i - i % nx;
            int left = Math.max(row, seen.previousSetBit(i) + 1);
            int right = seen.nextSetBit(i);
            if (right < 0 || right > row + nx)
            {
                right = row + nx;
            }
            seen.set(left, right);

            int y = (row / nx) % ny;
            int z = row / slice;
            if (y > 0)
            {
                seedRuns(seen, left - nx, right - nx, stack);
            }
            if (y < ny - 1)
            {
                seedRuns(seen, left + nx, right + nx, stack);
            }
            if (z > 0)
            {
                seedRuns(seen, left - slice, right - slice, stack);
            }
            if (z < nz - 1)
            {
                seedRuns(seen, left + slice, right + slice, stack);
            }
        }

        // What the border reached is outside; the rest is solid
        seen.andNot(walls);
        seen.flip(0, size);
        return seen;
    }

    /** Pushes the first voxel of every run in [from, to) not yet seen. */
    private static void seedRuns(BitSet seen, int from, int to, IntStack stack)
    {
        for (int j = seen.nextClearBit(from); j < to; )
        {
            stack.push(j);
            int end = seen.nextSetBit(j);
            if (end < 0 || end >= to)
            {
                break;
            }
            j = seen.nextClearBit(end);
        }
    }

    private static final class IntStack
    {
        private int[] items = new int[256];
        private int size;

        void push(int value)
        {
            if (size == items.length)
            {
                items = Arrays.copyOf(items, size * 2);
            }
            items[size++] = value;
        }

        int pop()
        {
            return items[--size];
        }
    }

    /**
     * Measures every voxel's distance to the nearest voxel of the set, out to reach voxels (at most
     * {@link #MAX_REACH}), for {@link #within} and {@link #beyond} to read. One pass along each axis, as distance
     * transforms do: each pass takes the nearest of the distances found so far, plus the step along its own axis.
     */
    void measure(BitSet set, double reach)
    {
        if (!(reach >= 0) || reach > MAX_REACH)
        {
            throw new IllegalArgumentException("reach " + reach);
        }
        int steps = (int) Math.floor(reach);
        cap = Math.min(255, (int) Math.floor(reach * reach) + 1);
        alongX(set, steps);
        if (steps > 0)
        {
            alongY(steps);
            alongZ(steps);
        }
    }

    /** The voxels last measured within radius (no further than the reach) of the set. */
    BitSet within(double radius)
    {
        return threshold((int) Math.floor(radius * radius), true);
    }

    /** The voxels last measured further than radius (less than the reach) from the set. */
    BitSet beyond(double radius)
    {
        return threshold((int) Math.floor(radius * radius), false);
    }

    private BitSet threshold(int limit, boolean inside)
    {
        if (limit >= cap)
        {
            throw new IllegalArgumentException("radius beyond the last measure's reach");
        }
        long[] words = new long[(size + 63) >> 6];
        for (int i = 0; i < size; i++)
        {
            if (((distance[i] & 0xFF) <= limit) == inside)
            {
                words[i >> 6] |= 1L << i;
            }
        }
        return BitSet.valueOf(words);
    }

    private void alongX(BitSet set, int steps)
    {
        byte far = (byte) cap;
        // The next voxel in the set and the next not, carried from row to row so empty and full runs scan once
        int nextIn = -1;
        int nextOut = -1;
        for (int row = 0; row < ny * nz; row++)
        {
            int start = row * nx;
            int end = start + nx;
            if (nextIn < start)
            {
                nextIn = set.nextSetBit(start);
                nextIn = nextIn < 0 ? Integer.MAX_VALUE : nextIn;
            }
            if (nextIn >= end)
            {
                Arrays.fill(distance, start, end, far);
                rows[row] = FAR;
                continue;
            }
            if (nextOut < start)
            {
                nextOut = set.nextClearBit(start);
            }
            if (nextOut >= end)
            {
                Arrays.fill(distance, start, end, (byte) 0);
                rows[row] = ZERO;
                continue;
            }
            rows[row] = MIXED;
            int next = nextIn;
            int previous = start - steps - 1;
            for (int i = start; i < end; i++)
            {
                if (i == next)
                {
                    previous = i;
                    next = set.nextSetBit(i + 1);
                    if (next < 0 || next >= end)
                    {
                        next = end + steps;
                    }
                }
                int d = Math.min(i - previous, next - i);
                distance[i] = d > steps ? far : (byte) Math.min(cap, d * d);
            }
        }
    }

    private void alongY(int steps)
    {
        int[] source = new int[slice];
        byte[] before = new byte[ny];
        int[] row = new int[nx];
        for (int z = 0; z < nz; z++)
        {
            int rowBase = z * ny;
            System.arraycopy(rows, rowBase, before, 0, ny);
            boolean copied = false;
            for (int y = 0; y < ny; y++)
            {
                if (before[y] == ZERO || allFar(before, y - steps, y + steps))
                {
                    continue;
                }
                int base = z * slice;
                if (!copied)
                {
                    for (int i = 0; i < slice; i++)
                    {
                        source[i] = distance[base + i] & 0xFF;
                    }
                    copied = true;
                }
                int at = y * nx;
                System.arraycopy(source, at, row, 0, nx);
                for (int d = 1; d <= steps && d * d < cap; d++)
                {
                    if (y >= d && before[y - d] != FAR)
                    {
                        lower(row, source, at - d * nx, d * d);
                    }
                    if (y + d < ny && before[y + d] != FAR)
                    {
                        lower(row, source, at + d * nx, d * d);
                    }
                }
                store(row, base + at);
                rows[rowBase + y] = MIXED;
            }
        }
    }

    private void alongZ(int steps)
    {
        // Each slice and its rows as they were before this pass, for the slices after it
        byte[][] before = new byte[steps + 1][slice];
        byte[][] beforeRows = new byte[steps + 1][ny];
        byte[] column = new byte[2 * steps + 1];
        int[] row = new int[nx];
        for (int z = 0; z < nz; z++)
        {
            int base = z * slice;
            int slot = z % (steps + 1);
            System.arraycopy(distance, base, before[slot], 0, slice);
            System.arraycopy(rows, z * ny, beforeRows[slot], 0, ny);
            for (int y = 0; y < ny; y++)
            {
                for (int d = -steps; d <= steps; d++)
                {
                    int k = z + d;
                    column[d + steps] = k < 0 || k >= nz ? FAR : d < 0 ? beforeRows[k % (steps + 1)][y] : rows[k * ny + y];
                }
                if (column[steps] == ZERO || allFar(column, 0, 2 * steps))
                {
                    continue;
                }
                int at = base + y * nx;
                for (int x = 0; x < nx; x++)
                {
                    row[x] = distance[at + x] & 0xFF;
                }
                for (int d = 1; d <= steps && d * d < cap; d++)
                {
                    if (column[steps - d] != FAR)
                    {
                        lower(row, before[(z - d) % (steps + 1)], y * nx, d * d);
                    }
                    if (column[steps + d] != FAR)
                    {
                        lower(row, distance, at + d * slice, d * d);
                    }
                }
                store(row, at);
                rows[z * ny + y] = MIXED;
            }
        }
    }

    /** Whether every row from first to last that lies in the grid is all at the cap. */
    private static boolean allFar(byte[] states, int first, int last)
    {
        for (int k = Math.max(0, first); k <= Math.min(states.length - 1, last); k++)
        {
            if (states[k] != FAR)
            {
                return false;
            }
        }
        return true;
    }

    private void store(int[] row, int at)
    {
        for (int x = 0; x < nx; x++)
        {
            distance[at + x] = (byte) Math.min(row[x], cap);
        }
    }

    private static void lower(int[] row, int[] source, int from, int step)
    {
        for (int x = 0; x < row.length; x++)
        {
            int v = source[from + x] + step;
            if (v < row[x])
            {
                row[x] = v;
            }
        }
    }

    private static void lower(int[] row, byte[] source, int from, int step)
    {
        for (int x = 0; x < row.length; x++)
        {
            int v = (source[from + x] & 0xFF) + step;
            if (v < row[x])
            {
                row[x] = v;
            }
        }
    }
}
