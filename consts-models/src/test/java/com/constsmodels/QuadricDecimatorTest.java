package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.Random;
import org.junit.Test;

public class QuadricDecimatorTest
{
    @Test
    public void aBallLosesMostOfItsTrianglesButNotItsShape()
    {
        MarchingCubes.Surface ball = MarchingCubesTest.ball(10.3);
        int before = ball.triangles;
        MarchingCubes.Surface fewer = QuadricDecimator.decimate(ball, 0.5);
        assertTrue(fewer.triangles + " of " + before, fewer.triangles * 3 < before);
        Mesh mesh = SolidRemeshTest.asMesh(fewer);
        SolidRemeshTest.assertClosedManifold(mesh);
        SolidRemeshTest.assertNoSelfIntersections(mesh);
        for (int v = 0; v < fewer.vertices; v++)
        {
            double r = Math.sqrt(Math.pow(fewer.xyz[3 * v] - 16, 2) + Math.pow(fewer.xyz[3 * v + 1] - 16, 2) + Math.pow(fewer.xyz[3 * v + 2] - 16, 2));
            assertEquals(10.3, r, 0.6);
        }
    }

    @Test
    public void randomShapesStayClosed()
    {
        Random random = new Random(5);
        for (int round = 0; round < 12; round++)
        {
            int n = 20;
            VoxelGrid grid = new VoxelGrid(n, n, n);
            BitSet set = new BitSet();
            for (int z = 3; z < n - 3; z++)
            {
                for (int y = 3; y < n - 3; y++)
                {
                    for (int x = 3; x < n - 3; x++)
                    {
                        if (random.nextDouble() < 0.35 + 0.02 * round)
                        {
                            set.set(grid.index(x, y, z));
                        }
                    }
                }
            }
            MarchingCubes.Surface surface = MarchingCubes.extract(grid, set);
            MarchingCubes.Surface fewer = QuadricDecimator.decimate(surface, round % 2 == 0 ? 0.3 : 0.5);
            Mesh mesh = SolidRemeshTest.asMesh(fewer);
            SolidRemeshTest.assertClosedManifold(mesh);
            SolidRemeshTest.assertNoSelfIntersections(mesh);
        }
    }
}
