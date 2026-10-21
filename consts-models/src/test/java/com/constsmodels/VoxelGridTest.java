package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.Random;
import org.junit.Test;

public class VoxelGridTest
{
    @Test
    public void oneVoxelGrowsIntoABall()
    {
        VoxelGrid grid = new VoxelGrid(9, 9, 9);
        BitSet one = new BitSet();
        one.set(grid.index(4, 4, 4));
        // The voxels within 2: the center, 6 at 1, 12 at root 2, 8 at root 3 and 6 at 2
        assertEquals(33, grid.dilate(one, 2).cardinality());
        assertEquals(7, grid.dilate(one, 1.2).cardinality());
    }

    @Test
    public void aBlockShrinksByWholeVoxels()
    {
        VoxelGrid grid = new VoxelGrid(13, 13, 13);
        BitSet block = box(grid, 2, 10);
        BitSet shrunk = grid.erode(block, 2);
        assertEquals(box(grid, 4, 8), shrunk);
    }

    @Test
    public void distancesMatchMeasuringEveryPair()
    {
        Random random = new Random(7);
        for (int round = 0; round < 6; round++)
        {
            VoxelGrid grid = new VoxelGrid(11 + round, 9, 13 - round);
            BitSet set = new BitSet();
            double density = round % 2 == 0 ? 0.02 : 0.6;
            for (int i = 0; i < grid.size; i++)
            {
                if (random.nextDouble() < density)
                {
                    set.set(i);
                }
            }
            double reach = 1.5 + round;
            grid.measure(set, reach);
            for (double radius : new double[]{1, 1.5, reach})
            {
                BitSet near = grid.within(radius);
                for (int z = 0; z < grid.nz; z++)
                {
                    for (int y = 0; y < grid.ny; y++)
                    {
                        for (int x = 0; x < grid.nx; x++)
                        {
                            boolean expected = nearest(grid, set, x, y, z) <= radius * radius;
                            assertEquals("at " + x + " " + y + " " + z, expected, near.get(grid.index(x, y, z)));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void fillingClosesHollowsButNotOpenings()
    {
        VoxelGrid grid = new VoxelGrid(12, 12, 12);
        BitSet walls = box(grid, 2, 9);
        walls.andNot(box(grid, 3, 8));
        BitSet filled = grid.fill(walls);
        assertEquals(box(grid, 2, 9), filled);

        // A hole in one wall lets the outside in
        walls.clear(grid.index(5, 5, 2));
        BitSet open = grid.fill(walls);
        assertEquals(walls, open);
        assertFalse(open.get(grid.index(5, 5, 5)));

        // Nothing holds nothing in
        assertTrue(grid.fill(new BitSet()).isEmpty());
    }

    private static BitSet box(VoxelGrid grid, int from, int to)
    {
        BitSet out = new BitSet();
        for (int z = from; z <= to; z++)
        {
            for (int y = from; y <= to; y++)
            {
                for (int x = from; x <= to; x++)
                {
                    out.set(grid.index(x, y, z));
                }
            }
        }
        return out;
    }

    private static int nearest(VoxelGrid grid, BitSet set, int x, int y, int z)
    {
        int best = Integer.MAX_VALUE;
        for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1))
        {
            int dx = i % grid.nx - x;
            int dy = i / grid.nx % grid.ny - y;
            int dz = i / grid.slice - z;
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }
}
