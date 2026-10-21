package com.constsmodels;

import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ShellCapsTest
{
    private static final int GREEN = 0x2E7D32;
    private static final int BROWN = 0x5A3A1E;

    /** A dome over a ring, open underneath, as a tree's leaf clump is: rings from the rim to the top, wound outward. */
    static void dome(MeshBuilder b, float cx, float cy, float cz, float radius, float height, int rgb)
    {
        int around = 12;
        int rings = 4;
        int[][] ring = new int[rings][around];
        for (int r = 0; r < rings; r++)
        {
            double up = Math.PI / 2 * r / rings;
            for (int i = 0; i < around; i++)
            {
                double angle = 2 * Math.PI * i / around;
                ring[r][i] = b.addVertex((float) (cx + radius * Math.cos(up) * Math.cos(angle)),
                    (float) (cy + radius * Math.cos(up) * Math.sin(angle)), (float) (cz + height * Math.sin(up)));
            }
        }
        int top = b.addVertex(cx, cy, cz + height);
        for (int r = 0; r + 1 < rings; r++)
        {
            for (int i = 0; i < around; i++)
            {
                int j = (i + 1) % around;
                b.addTriangle(ring[r][i], ring[r][j], ring[r + 1][j], rgb);
                b.addTriangle(ring[r][i], ring[r + 1][j], ring[r + 1][i], rgb);
            }
        }
        for (int i = 0; i < around; i++)
        {
            b.addTriangle(ring[rings - 1][i], ring[rings - 1][(i + 1) % around], top, rgb);
        }
    }

    /** A tree: a trunk with no bottom under a leaf clump open underneath. */
    static Mesh tree()
    {
        MeshBuilder b = new MeshBuilder("Tree");
        TestMeshes.box(b, -10, -10, 0, 10, 10, 60, false, BROWN);
        dome(b, 0, 0, 50, 45, 35, GREEN);
        return b.build();
    }

    @Test
    public void anOpenClumpAndTrunkAreCappedClosed()
    {
        Mesh tree = tree();
        assertEquals(2, ShellCaps.rims(tree, PrintPrep.welded(tree)).size());
        ShellCaps.Capped capped = ShellCaps.cap(tree);
        Mesh mesh = capped.mesh;
        assertTrue(mesh.faceCount() > tree.faceCount());

        // The model's own triangles come first, as they were; every cap is in its rim's color and stands for a
        // triangle on that rim
        for (int f = 0; f < tree.faceCount(); f++)
        {
            assertEquals(f, capped.from[f]);
            assertEquals(tree.color[f], mesh.color[f]);
        }
        for (int f = tree.faceCount(); f < mesh.faceCount(); f++)
        {
            assertEquals(mesh.color[f], tree.color[capped.from[f]]);
        }

        // Welded, each part is closed and wound outward, caps and all
        Mesh welded = PrintPrep.weld(mesh);
        assertTrue(TestMeshes.closed(welded));
        assertTrue(TestMeshes.volume(welded) > 0);
        assertTrue(ShellCaps.rims(mesh, PrintPrep.welded(mesh)).isEmpty());
    }

    @Test
    public void aCappedClumpPrintsSolid()
    {
        MeshBuilder b = new MeshBuilder("Clump");
        dome(b, 0, 0, 0, 50, 40, GREEN);
        Mesh clump = b.build();

        // Up through the middle from below: open, into the shell only at the top; capped, into the solid at the bottom
        double[] from = {0, 0, -20};
        double[] up = {0, 0, 1};
        Mesh open = SolidRemesh.makeSolid(clump, 40, 0.25, 1.0, 0);
        List<Double> hits = SolidRemeshTest.hits(open, from, up);
        assertEquals(hits.toString(), 2, hits.size());
        assertTrue(hits.toString(), hits.get(0) > 50);
        Mesh solid = SolidRemesh.makeSolid(ShellCaps.cap(clump).mesh, 40, 0.25, 1.0, 0);
        SolidRemeshTest.assertClosedManifold(solid);
        hits = SolidRemeshTest.hits(solid, from, up);
        assertEquals(hits.toString(), 2, hits.size());
        assertTrue(hits.toString(), hits.get(0) < 25 && hits.get(1) > 55);
        assertTrue(SolidRemeshTest.volume(solid) > 2 * SolidRemeshTest.volume(open));
    }

    @Test
    public void aSheetBentRoundIsLeftOpen()
    {
        // Half a tube, as a cape hangs round a back: its outline bows far from any plane
        MeshBuilder b = new MeshBuilder("Cape");
        int around = 8;
        int[] low = new int[around + 1];
        int[] high = new int[around + 1];
        for (int i = 0; i <= around; i++)
        {
            double angle = Math.PI * i / around;
            low[i] = b.addVertex((float) (40 * Math.cos(angle)), (float) (40 * Math.sin(angle)), 0);
            high[i] = b.addVertex((float) (40 * Math.cos(angle)), (float) (40 * Math.sin(angle)), 100);
        }
        for (int i = 0; i < around; i++)
        {
            b.addTriangle(low[i], low[i + 1], high[i + 1], RED);
            b.addTriangle(low[i], high[i + 1], high[i], RED);
        }
        Mesh cape = b.build();
        assertEquals(1, ShellCaps.rims(cape, PrintPrep.welded(cape)).size());
        assertEquals(cape.faceCount(), ShellCaps.cap(cape).mesh.faceCount());
    }

    @Test
    public void aFlatSheetsCapLiesOnIt()
    {
        Mesh sheet = CoreTest.triangle(RED);
        Mesh capped = ShellCaps.cap(sheet).mesh;
        assertEquals(2, capped.faceCount());
        assertEquals(0, TestMeshes.volume(capped), 1e-6);
    }

    @Test
    public void aRimTooBigIsLeftOpen()
    {
        MeshBuilder b = new MeshBuilder("Ground");
        int n = ShellCaps.MAX_RIM + 1;
        int middle = b.addVertex(0, 0, 0);
        int[] rim = new int[n];
        for (int i = 0; i < n; i++)
        {
            double angle = 2 * Math.PI * i / n;
            rim[i] = b.addVertex((float) (1000 * Math.cos(angle)), (float) (1000 * Math.sin(angle)), 0);
        }
        for (int i = 0; i < n; i++)
        {
            b.addTriangle(middle, rim[i], rim[(i + 1) % n], RED);
        }
        Mesh ground = b.build();
        assertEquals(ground.faceCount(), ShellCaps.cap(ground).mesh.faceCount());
    }

    @Test
    public void earsCutAHollowedOutlineIntoTrianglesCoveringItExactly()
    {
        // An L, clockwise
        double[] x = {0, 0, 4, 4, 1, 1};
        double[] y = {0, 3, 3, 2, 2, 0};
        List<int[]> ears = ShellCaps.ears(x, y);
        assertEquals(4, ears.size());
        double area = 0;
        for (int[] ear : ears)
        {
            double twice = (x[ear[1]] - x[ear[0]]) * (y[ear[2]] - y[ear[0]])
                - (y[ear[1]] - y[ear[0]]) * (x[ear[2]] - x[ear[0]]);
            // Each wound clockwise, as the outline is
            assertTrue(twice < 0);
            area -= twice / 2;
        }
        assertEquals(3 + 3, area, 1e-9);

        // Crossing itself, it can't be
        assertNull(ShellCaps.ears(new double[]{0, 2, 0, 2}, new double[]{0, 2, 2, 0}));
    }
}
