package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class MarchingCubesTest
{
    // As in MarchingCubes: corner c at (c & 1, c >> 1 & 1, c >> 2 & 1), edges along x, then y, then z
    private static final int[] EDGE_FROM = {0, 2, 4, 6, 0, 1, 4, 5, 0, 1, 2, 3};
    private static final int[] EDGE_TO = {1, 3, 5, 7, 2, 3, 6, 7, 4, 5, 6, 7};

    @Test
    public void everyCrossedEdgeIsInExactlyOneLoop()
    {
        for (int pattern = 0; pattern < 256; pattern++)
        {
            for (int joined = 0; joined < 64; joined++)
            {
                int[] loops = MarchingCubes.loopsFor(pattern, joined);
                int[] uses = new int[12];
                for (int at = 0; at < loops.length; )
                {
                    int count = loops[at++];
                    assertTrue(count >= 3);
                    for (int k = 0; k < count; k++)
                    {
                        uses[loops[at++]]++;
                    }
                }
                for (int e = 0; e < 12; e++)
                {
                    boolean crossed = (pattern >> EDGE_FROM[e] & 1) != (pattern >> EDGE_TO[e] & 1);
                    assertEquals("pattern " + pattern + " edge " + e, crossed ? 1 : 0, uses[e]);
                }
            }
        }
    }

    @Test
    public void randomVoxelsGiveClosedSurfacesThatNeverCrossThemselves()
    {
        Random random = new Random(11);
        for (int round = 0; round < 10; round++)
        {
            int n = 16;
            VoxelGrid grid = new VoxelGrid(n, n, n);
            int[][] values = new int[n][grid.slice];
            // Every other round all in or out, the rest partly in
            boolean whole = round % 2 == 0;
            double density = (whole ? 0.45 : 0.7) + 0.03 * round;
            // Clear of the border by three, so the smoothing stays inside the grid
            for (int z = 3; z < n - 3; z++)
            {
                for (int y = 3; y < n - 3; y++)
                {
                    for (int x = 3; x < n - 3; x++)
                    {
                        values[z][x + n * y] = random.nextDouble() < density ? (whole ? MarchingCubes.FULL : 96 + random.nextInt(160)) : 0;
                    }
                }
            }
            MarchingCubes.Surface surface = MarchingCubes.extract(grid, (z, out) -> System.arraycopy(values[z], 0, out, 0, out.length),
                Integer.MAX_VALUE);
            Mesh mesh = SolidRemeshTest.asMesh(surface);
            SolidRemeshTest.assertClosedManifold(mesh);
            SolidRemeshTest.assertNoSelfIntersections(mesh);
        }
    }

    @Test
    public void aBallComesOutRound()
    {
        // Smoothing pulls a curved surface in by about a voxel over its radius: a tenth here
        MarchingCubes.Surface ball = ball(10.3);
        Mesh mesh = SolidRemeshTest.asMesh(ball);
        SolidRemeshTest.assertClosedManifold(mesh);
        double r = 10.3 - 1 / 10.3;
        assertEquals(4 * Math.PI / 3 * r * r * r, SolidRemeshTest.volume(mesh), 4 * Math.PI / 3 * r * r * r * 0.01);
        for (int v = 0; v < ball.vertices; v++)
        {
            double at = Math.sqrt(Math.pow(ball.xyz[3 * v] - 16, 2) + Math.pow(ball.xyz[3 * v + 1] - 16, 2) + Math.pow(ball.xyz[3 * v + 2] - 16, 2));
            assertEquals(r, at, 0.1);
        }
    }

    @Test
    public void tooManyTrianglesGivesUp()
    {
        VoxelGrid grid = new VoxelGrid(32, 32, 32);
        MarchingCubes.Surface ball = MarchingCubes.extract(grid, ballOccupancy(10.3), 100);
        assertEquals(null, ball);
    }

    /** A ball of radius r voxels around (16, 16, 16), its occupancy ramped across the surface. */
    static MarchingCubes.Surface ball(double r)
    {
        return MarchingCubes.extract(new VoxelGrid(32, 32, 32), ballOccupancy(r), Integer.MAX_VALUE);
    }

    private static MarchingCubes.Occupancy ballOccupancy(double r)
    {
        return (z, out) ->
        {
            for (int y = 0; y < 32; y++)
            {
                for (int x = 0; x < 32; x++)
                {
                    double d = Math.sqrt((x - 16) * (x - 16) + (y - 16) * (y - 16) + (z - 16) * (z - 16));
                    out[x + 32 * y] = (int) Math.round(Math.max(0, Math.min(1, 0.5 + r - d)) * MarchingCubes.FULL);
                }
            }
        };
    }
}
