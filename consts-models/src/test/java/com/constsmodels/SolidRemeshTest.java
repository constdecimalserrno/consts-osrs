package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class SolidRemeshTest
{
    private static final int RED = 0xC02020;
    private static final int BLUE = 0x2040C0;

    @Test
    public void aLoneTriangleBecomesAClosedSlabAWallThick()
    {
        Mesh triangle = CoreTest.triangle(RED);
        Mesh solid = SolidRemesh.makeSolid(triangle, 40, 0.25, 1.0, 0);
        assertClosedManifold(solid);
        assertEquals("Triangle", solid.name);

        // Straight through the triangle at three points inside it, along its normal
        double mmPerUnit = 40 / 100.0;
        double[] normal = {0, -1 / Math.sqrt(5), 2 / Math.sqrt(5)};
        for (double[] uv : new double[][]{{1 / 3.0, 1 / 3.0}, {0.5, 0.2}, {0.2, 0.5}})
        {
            double[] p = {100 * uv[0], 100 * uv[1], 50 * uv[1]};
            List<Double> hits = hits(solid, p, normal);
            assertEquals(hits.toString(), 2, hits.size());
            double thickness = (hits.get(1) - hits.get(0)) * mmPerUnit;
            assertTrue("thickness " + thickness, thickness >= 0.98 && thickness <= 1.3);
        }
    }

    @Test
    public void anOpenBoxBecomesClosedWithWallsAWallThick()
    {
        MeshBuilder b = new MeshBuilder("Open box");
        box(b, 0, 0, 0, 100, 100, 100, BLUE, BLUE, false);
        Mesh solid = SolidRemesh.makeSolid(b.build(), 30, 0.25, 1.0, 0);
        assertClosedManifold(solid);
        assertEquals(1, components(solid));

        // Across the box: in and out of one wall, the hollow, in and out of the other
        List<Double> hits = hits(solid, new double[]{-10, 50, 50}, new double[]{1, 0, 0});
        assertEquals(hits.toString(), 4, hits.size());
        double mmPerUnit = 30 / 100.0;
        for (int wall = 0; wall < 2; wall++)
        {
            double thickness = (hits.get(2 * wall + 1) - hits.get(2 * wall)) * mmPerUnit;
            assertTrue("wall " + thickness, thickness >= 0.98 && thickness <= 1.3);
        }
        assertTrue("still hollow", volume(solid) < 0.25 * 100 * 100 * 100);
    }

    @Test
    public void aCubeKeepsItsVolume()
    {
        MeshBuilder b = new MeshBuilder("Cube");
        box(b, 0, 0, 0, 100, 100, 100, BLUE, BLUE, true);
        Mesh solid = SolidRemesh.makeSolid(b.build(), 30, 0.25, 1.0, 0);
        assertClosedManifold(solid);
        double v = volume(solid);
        assertEquals(1e6, v, 1e6 * 0.01);
    }

    @Test
    public void aThinClosedPartGetsAWallThick()
    {
        // A tenth of a millimetre thick at print scale
        MeshBuilder b = new MeshBuilder("Blade");
        box(b, 0, 0, 0, 100, 40, 0.3f, BLUE, BLUE, true);
        Mesh solid = SolidRemesh.makeSolid(b.build(), 30, 0.25, 1.0, 0);
        assertClosedManifold(solid);
        List<Double> hits = hits(solid, new double[]{50, 20, -10}, new double[]{0, 0, 1});
        assertEquals(hits.toString(), 2, hits.size());
        double thickness = (hits.get(1) - hits.get(0)) * 30 / 100.0;
        assertTrue("thickness " + thickness, thickness >= 0.98 && thickness <= 1.5);
    }

    @Test
    public void overlappingPartsBecomeOneSolid()
    {
        MeshBuilder b = new MeshBuilder("Overlap");
        box(b, 0, 0, 0, 100, 100, 100, BLUE, BLUE, true);
        box(b, 50, 50, 0, 150, 150, 100, RED, RED, true);
        // And a sheet poking out of both
        int p0 = b.addVertex(-20, 75, 20);
        int p1 = b.addVertex(170, 75, 20);
        int p2 = b.addVertex(170, 75, 80);
        int p3 = b.addVertex(-20, 75, 80);
        b.addTriangle(p0, p1, p2, RED);
        b.addTriangle(p0, p2, p3, RED);
        Mesh solid = SolidRemesh.makeSolid(b.build(), 40, 0.25, 1.0, 0);
        assertClosedManifold(solid);
        assertEquals(1, components(solid));
        assertNoSelfIntersections(solid);
        double union = 2e6 - 50 * 50 * 100;
        assertTrue("volume " + volume(solid), volume(solid) > union * 0.99 && volume(solid) < union * 1.03);
    }

    @Test
    public void colorsCarryOver()
    {
        MeshBuilder b = new MeshBuilder("Two colors");
        box(b, 0, 0, 0, 100, 100, 100, BLUE, RED, true);
        Mesh input = b.build();
        Mesh solid = SolidRemesh.makeSolid(input, 30, 0.25, 1.0, 0);
        int top = 0;
        int sides = 0;
        for (int f = 0; f < solid.faceCount(); f++)
        {
            assertTrue(solid.color[f] == RED || solid.color[f] == BLUE);
            double[] n = normal(solid, f);
            double z = (solid.z[solid.a[f]] + solid.z[solid.b[f]] + solid.z[solid.c[f]]) / 3;
            if (n[2] > 0.95 && z > 99)
            {
                assertEquals(RED, solid.color[f]);
                top++;
            }
            if (Math.abs(n[0]) > 0.95 || Math.abs(n[1]) > 0.95 || n[2] < -0.95)
            {
                assertEquals(BLUE, solid.color[f]);
                sides++;
            }
        }
        assertTrue(top > 0 && sides > 0);

        // The filaments chosen for the model fit the solid
        PrintColors colors = PrintColors.of(input, 2);
        int[] filaments = colors.filamentsFor(solid);
        for (int f = 0; f < solid.faceCount(); f++)
        {
            assertEquals(solid.color[f], colors.color(filaments[f]));
        }
    }

    @Test
    public void aFlagStaysJoinedToItsPole()
    {
        MeshBuilder b = new MeshBuilder("Flag");
        box(b, 0, 0, 0, 10, 10, 100, BLUE, BLUE, true);
        int p0 = b.addVertex(10, 5, 95);
        int p1 = b.addVertex(60, 5, 95);
        int p2 = b.addVertex(60, 5, 65);
        int p3 = b.addVertex(10, 5, 65);
        b.addTriangle(p0, p1, p2, RED);
        b.addTriangle(p0, p2, p3, RED);
        Mesh solid = SolidRemesh.makeSolid(b.build(), 40, 0.25, 1.0, 0);
        assertClosedManifold(solid);
        assertEquals(1, components(solid));
        assertNoSelfIntersections(solid);
        // The flag is a wall thick right up to the pole
        List<Double> hits = hits(solid, new double[]{12, -10, 80}, new double[]{0, 1, 0});
        assertEquals(hits.toString(), 2, hits.size());
        assertTrue((hits.get(1) - hits.get(0)) * 40 / 100 >= 0.98);
    }

    @Test
    public void aRoundBottomIsCutFlatOnOnePlane()
    {
        // A ball 30 mm across, its bottom at z = 10
        Mesh ball = ball(50, 50, 60, 50, RED);
        Mesh solid = SolidRemesh.makeSolid(ball, 30, 0.25, 1.0, 0.25);
        assertClosedManifold(solid);
        assertNoSelfIntersections(solid);

        // A thin slice is gone: at least the quarter millimetre asked for, and no more than a voxel past that
        double unitsPerMm = 100 / 30.0;
        float low = solid.bounds()[2];
        assertTrue("bottom at " + low, low >= 10 + 0.25 * unitsPerMm - 0.05 && low <= 10 + 0.5 * unitsPerMm + 0.05);

        // Everything near the bottom is on it, and it's a disc facing down, a few millimetres across
        boolean[] used = new boolean[solid.vertexCount()];
        for (int f = 0; f < solid.faceCount(); f++)
        {
            used[solid.a[f]] = used[solid.b[f]] = used[solid.c[f]] = true;
        }
        for (int v = 0; v < used.length; v++)
        {
            if (used[v] && solid.z[v] < low + 0.05 * unitsPerMm)
            {
                assertEquals(low, solid.z[v], 0f);
            }
        }
        double flat = 0;
        for (int f = 0; f < solid.faceCount(); f++)
        {
            if (solid.z[solid.a[f]] == low && solid.z[solid.b[f]] == low && solid.z[solid.c[f]] == low)
            {
                assertTrue(normal(solid, f)[2] < -0.999);
                flat += PrintPrep.area(solid, f);
            }
        }
        double radius = Math.sqrt(flat / Math.PI) / unitsPerMm;
        assertTrue("flat bottom " + radius + " mm across", radius > 2 && radius < 5);
    }

    @Test
    public void aFigureWithOneFootHigherIsCutUntilItStandsOnBoth()
    {
        // A body on two feet, the right one's sole half a millimetre higher than the left's
        MeshBuilder b = new MeshBuilder("Figure");
        box(b, 0, 0, 0, 20, 30, 20, BLUE, BLUE, true);
        box(b, 40, 0, 1, 60, 30, 20, BLUE, BLUE, true);
        box(b, -5, -5, 20, 65, 35, 100, RED, RED, true);
        Mesh solid = SolidRemesh.makeSolid(b.build(), 50, 0.25, 1.0, 0.25);
        assertClosedManifold(solid);

        // Both feet lie flat on the plane the solid stands on
        float low = solid.bounds()[2];
        double left = 0;
        double right = 0;
        for (int f = 0; f < solid.faceCount(); f++)
        {
            if (solid.z[solid.a[f]] == low && solid.z[solid.b[f]] == low && solid.z[solid.c[f]] == low)
            {
                double x = (solid.x[solid.a[f]] + solid.x[solid.b[f]] + solid.x[solid.c[f]]) / 3;
                if (x < 30)
                {
                    left += PrintPrep.area(solid, f);
                }
                else
                {
                    right += PrintPrep.area(solid, f);
                }
            }
        }
        assertTrue("left " + left + ", right " + right, left > 100 && right > 50);

        // A thin slice alone leaves it on one foot
        assertTrue(low > 1);
    }

    @Test
    public void withoutAFlatBottomTheBallStaysRound()
    {
        Mesh solid = SolidRemesh.makeSolid(ball(50, 50, 60, 50, RED), 30, 0.25, 1.0, 0);
        float low = solid.bounds()[2];
        assertEquals(10, low, 0.5);
        int onBottom = 0;
        for (int v = 0; v < solid.vertexCount(); v++)
        {
            onBottom += solid.z[v] == low ? 1 : 0;
        }
        assertTrue(onBottom < 4);
    }

    @Test
    public void nothingToPrintComesOutEmpty()
    {
        assertTrue(SolidRemesh.makeSolid(new MeshBuilder("Empty").build(), 100).isEmpty());
        MeshBuilder b = new MeshBuilder("Line");
        b.addTriangle(b.addVertex(0, 0, 0), b.addVertex(10, 10, 10), b.addVertex(20, 20, 20), RED);
        assertTrue(SolidRemesh.makeSolid(b.build(), 100).isEmpty());
        assertTrue(SolidRemesh.makeSolid(CoreTest.triangle(RED), 0).isEmpty());
    }

    @Test
    public void theGridStaysWithinItsLimits()
    {
        // A whole area, 100 mm across: coarser voxels than asked for, but never more than the limits
        float[] box = {0, 0, 0, 12800, 12800, 3000};
        SolidRemesh.Plan plan = SolidRemesh.Plan.of(box, 100 / 12800.0, 0.1, 1.0);
        assertTrue(plan.nx <= SolidRemesh.MAX_AXIS && plan.ny <= SolidRemesh.MAX_AXIS && plan.nz <= SolidRemesh.MAX_AXIS);
        assertTrue((long) plan.nx * plan.ny * plan.nz <= SolidRemesh.MAX_VOXELS);
        assertTrue(plan.voxelMm > 0.1);

        // Walls are at least three voxels, and a small print keeps the voxel asked for
        plan = SolidRemesh.Plan.of(new float[]{0, 0, 0, 100, 100, 100}, 0.3, 0.25, 1.0);
        assertEquals(0.25, plan.voxelMm, 1e-9);
        assertEquals(2.0, plan.halfWall, 1e-9);
        assertEquals(1.5, SolidRemesh.Plan.of(new float[]{0, 0, 0, 100, 100, 100}, 0.3, 0.25, 0.2).halfWall, 1e-9);
        assertEquals(0.25, SolidRemesh.defaultVoxelMm(100), 1e-9);
    }

    @Test
    public void theNearestPointOnATriangle()
    {
        double[] t = {0, 0, 0, 10, 0, 0, 0, 10, 0};
        assertEquals(4, SolidRemesh.distanceSquared(t, 0, 2, 2, 2), 1e-9);
        assertEquals(2, SolidRemesh.distanceSquared(t, 0, -1, -1, 0), 1e-9);
        assertEquals(1, SolidRemesh.distanceSquared(t, 0, 5, -1, 0), 1e-9);
        assertEquals(2, SolidRemesh.distanceSquared(t, 0, 6, 6, 0), 1e-9);
        assertEquals(1, SolidRemesh.distanceSquared(t, 0, 11, 0, 0), 1e-9);
    }

    // Shared checks, for the other remesh tests too

    /** A ball of 16 rings of 32, wound outward. */
    static Mesh ball(float cx, float cy, float cz, float radius, int rgb)
    {
        MeshBuilder b = new MeshBuilder("Ball");
        int rings = 16;
        int around = 32;
        int bottom = b.addVertex(cx, cy, cz - radius);
        int[][] ring = new int[rings - 1][around];
        for (int r = 1; r < rings; r++)
        {
            double down = Math.PI * r / rings;
            for (int i = 0; i < around; i++)
            {
                double angle = 2 * Math.PI * i / around;
                ring[r - 1][i] = b.addVertex((float) (cx + radius * Math.sin(down) * Math.cos(angle)),
                    (float) (cy + radius * Math.sin(down) * Math.sin(angle)), (float) (cz - radius * Math.cos(down)));
            }
        }
        int top = b.addVertex(cx, cy, cz + radius);
        for (int i = 0; i < around; i++)
        {
            int j = (i + 1) % around;
            b.addTriangle(bottom, ring[0][j], ring[0][i], rgb);
            for (int r = 0; r + 1 < rings - 1; r++)
            {
                b.addTriangle(ring[r][i], ring[r][j], ring[r + 1][j], rgb);
                b.addTriangle(ring[r][i], ring[r + 1][j], ring[r + 1][i], rgb);
            }
            b.addTriangle(ring[rings - 2][i], ring[rings - 2][j], top, rgb);
        }
        return b.build();
    }

    /** A box of 12 triangles, wound outward; the top only when withTop. */
    static void box(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, int side, int top, boolean withTop)
    {
        int[] v = new int[8];
        for (int i = 0; i < 8; i++)
        {
            v[i] = b.addVertex((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
        }
        int[][] quads = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}};
        for (int q = 0; q < 6; q++)
        {
            if (q == 1 && !withTop)
            {
                continue;
            }
            int[] f = quads[q];
            int color = q == 1 ? top : side;
            b.addTriangle(v[f[0]], v[f[1]], v[f[2]], color);
            b.addTriangle(v[f[0]], v[f[2]], v[f[3]], color);
        }
    }

    /**
     * Every edge is shared by exactly two triangles, running opposite ways, every vertex's triangles make one fan,
     * no triangle repeats a vertex or has no area, and the whole encloses a positive volume.
     */
    static void assertClosedManifold(Mesh m)
    {
        assertFalse("empty", m.isEmpty());
        Set<Long> directed = new HashSet<>();
        Map<Integer, Map<Integer, Integer>> fans = new HashMap<>();
        for (int f = 0; f < m.faceCount(); f++)
        {
            int[] v = {m.a[f], m.b[f], m.c[f]};
            assertTrue("repeated vertex in " + f, v[0] != v[1] && v[1] != v[2] && v[0] != v[2]);
            double[] n = cross(m, f);
            assertTrue("no area: " + f, n[0] != 0 || n[1] != 0 || n[2] != 0);
            for (int k = 0; k < 3; k++)
            {
                int from = v[k];
                int to = v[(k + 1) % 3];
                assertTrue("edge used twice the same way: " + from + " " + to, directed.add(edgeKey(from, to)));
                // Around vertex from, this triangle runs from to round to the third corner
                Integer previous = fans.computeIfAbsent(from, x -> new HashMap<>()).put(to, v[(k + 2) % 3]);
                assertTrue(previous == null);
            }
        }
        for (long key : directed)
        {
            assertTrue("open edge", directed.contains(edgeKey((int) (key & 0xFFFFFFFFL), (int) (key >>> 32))));
        }
        for (Map.Entry<Integer, Map<Integer, Integer>> e : fans.entrySet())
        {
            Map<Integer, Integer> fan = e.getValue();
            int start = fan.keySet().iterator().next();
            int steps = 0;
            int at = start;
            do
            {
                at = fan.get(at);
                steps++;
            }
            while (at != start && steps <= fan.size());
            assertEquals("vertex " + e.getKey() + " is pinched", fan.size(), steps);
        }
        assertTrue("inside out", volume(m) > 0);
    }

    private static long edgeKey(int from, int to)
    {
        return (long) from << 32 | (to & 0xFFFFFFFFL);
    }

    static double volume(Mesh m)
    {
        double sum = 0;
        for (int f = 0; f < m.faceCount(); f++)
        {
            int a = m.a[f];
            int b = m.b[f];
            int c = m.c[f];
            sum += (double) m.x[a] * ((double) m.y[b] * m.z[c] - (double) m.z[b] * m.y[c])
                - (double) m.y[a] * ((double) m.x[b] * m.z[c] - (double) m.z[b] * m.x[c])
                + (double) m.z[a] * ((double) m.x[b] * m.y[c] - (double) m.y[b] * m.x[c]);
        }
        return sum / 6;
    }

    /** How many pieces the mesh is in, by shared vertices. */
    static int components(Mesh m)
    {
        int[] parent = new int[m.vertexCount()];
        for (int i = 0; i < parent.length; i++)
        {
            parent[i] = i;
        }
        for (int f = 0; f < m.faceCount(); f++)
        {
            union(parent, m.a[f], m.b[f]);
            union(parent, m.a[f], m.c[f]);
        }
        Set<Integer> roots = new HashSet<>();
        for (int f = 0; f < m.faceCount(); f++)
        {
            roots.add(find(parent, m.a[f]));
        }
        return roots.size();
    }

    private static int find(int[] parent, int i)
    {
        while (parent[i] != i)
        {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int i, int j)
    {
        parent[find(parent, i)] = find(parent, j);
    }

    /** Where the line from p along d crosses the mesh, as distances along it, nearest first. */
    static List<Double> hits(Mesh m, double[] p, double[] d)
    {
        List<Double> out = new ArrayList<>();
        for (int f = 0; f < m.faceCount(); f++)
        {
            double t = rayHit(m, f, p, d);
            if (!Double.isNaN(t))
            {
                out.add(t);
            }
        }
        out.sort(Double::compare);
        // A line through an edge or corner meets the triangles there more than once
        List<Double> distinct = new ArrayList<>();
        for (double t : out)
        {
            if (distinct.isEmpty() || t - distinct.get(distinct.size() - 1) > 1e-4)
            {
                distinct.add(t);
            }
        }
        return distinct;
    }

    /** Where the line meets triangle f (Moller and Trumbore), or NaN. */
    private static double rayHit(Mesh m, int f, double[] p, double[] d)
    {
        double[] a = corner(m, m.a[f]);
        double[] e1 = minus(corner(m, m.b[f]), a);
        double[] e2 = minus(corner(m, m.c[f]), a);
        double[] h = cross(d, e2);
        double det = dot(e1, h);
        if (Math.abs(det) < 1e-12)
        {
            return Double.NaN;
        }
        double[] s = minus(p, a);
        double u = dot(s, h) / det;
        if (u < 0 || u > 1)
        {
            return Double.NaN;
        }
        double[] q = cross(s, e1);
        double v = dot(d, q) / det;
        if (v < 0 || u + v > 1)
        {
            return Double.NaN;
        }
        return dot(e2, q) / det;
    }

    /**
     * No two triangles that share no corner cross each other: no edge of one passes through the other. Checked pair
     * by pair among triangles whose bounds meet, filed in a grid.
     */
    static void assertNoSelfIntersections(Mesh m)
    {
        float[] box = m.bounds();
        double cell = Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2])) / 64;
        Map<Long, List<Integer>> grid = new HashMap<>();
        double[][] bounds = new double[m.faceCount()][];
        for (int f = 0; f < m.faceCount(); f++)
        {
            bounds[f] = triangleBounds(m, f);
            for (long key : cells(bounds[f], box, cell))
            {
                grid.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
            }
        }
        Set<Long> tested = new HashSet<>();
        for (List<Integer> faces : grid.values())
        {
            for (int i = 0; i < faces.size(); i++)
            {
                for (int j = i + 1; j < faces.size(); j++)
                {
                    int f = Math.min(faces.get(i), faces.get(j));
                    int g = Math.max(faces.get(i), faces.get(j));
                    if (sharesCorner(m, f, g) || !overlap(bounds[f], bounds[g]) || !tested.add((long) f << 32 | g))
                    {
                        continue;
                    }
                    assertFalse("triangles " + f + " and " + g + " cross", crosses(m, f, g) || crosses(m, g, f));
                }
            }
        }
    }

    private static boolean sharesCorner(Mesh m, int f, int g)
    {
        int[] a = {m.a[f], m.b[f], m.c[f]};
        int[] b = {m.a[g], m.b[g], m.c[g]};
        for (int x : a)
        {
            for (int y : b)
            {
                if (x == y)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether an edge of triangle f passes through triangle g. */
    private static boolean crosses(Mesh m, int f, int g)
    {
        int[] v = {m.a[f], m.b[f], m.c[f]};
        for (int k = 0; k < 3; k++)
        {
            double[] p = corner(m, v[k]);
            double[] d = minus(corner(m, v[(k + 1) % 3]), p);
            double t = rayHit(m, g, p, d);
            if (t > 1e-9 && t < 1 - 1e-9)
            {
                return true;
            }
        }
        return false;
    }

    private static double[] triangleBounds(Mesh m, int f)
    {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int v : new int[]{m.a[f], m.b[f], m.c[f]})
        {
            double[] p = corner(m, v);
            for (int k = 0; k < 3; k++)
            {
                b[k] = Math.min(b[k], p[k]);
                b[k + 3] = Math.max(b[k + 3], p[k]);
            }
        }
        return b;
    }

    private static List<Long> cells(double[] b, float[] box, double cell)
    {
        List<Long> out = new ArrayList<>();
        int[] lo = new int[3];
        int[] hi = new int[3];
        for (int k = 0; k < 3; k++)
        {
            lo[k] = (int) Math.floor((b[k] - box[k]) / cell);
            hi[k] = (int) Math.floor((b[k + 3] - box[k]) / cell);
        }
        for (int x = lo[0]; x <= hi[0]; x++)
        {
            for (int y = lo[1]; y <= hi[1]; y++)
            {
                for (int z = lo[2]; z <= hi[2]; z++)
                {
                    out.add(((long) x * 4096 + y) * 4096 + z);
                }
            }
        }
        return out;
    }

    private static boolean overlap(double[] a, double[] b)
    {
        for (int k = 0; k < 3; k++)
        {
            if (a[k] > b[k + 3] || b[k] > a[k + 3])
            {
                return false;
            }
        }
        return true;
    }

    private static double[] normal(Mesh m, int f)
    {
        double[] n = cross(m, f);
        double length = Math.sqrt(dot(n, n));
        return new double[]{n[0] / length, n[1] / length, n[2] / length};
    }

    private static double[] cross(Mesh m, int f)
    {
        double[] a = corner(m, m.a[f]);
        return cross(minus(corner(m, m.b[f]), a), minus(corner(m, m.c[f]), a));
    }

    private static double[] corner(Mesh m, int v)
    {
        return new double[]{m.x[v], m.y[v], m.z[v]};
    }

    private static double[] minus(double[] a, double[] b)
    {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b)
    {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b)
    {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** A surface as a mesh, one color, for the checks above. */
    static Mesh asMesh(MarchingCubes.Surface s)
    {
        float[] x = new float[s.vertices];
        float[] y = new float[s.vertices];
        float[] z = new float[s.vertices];
        for (int i = 0; i < s.vertices; i++)
        {
            x[i] = (float) s.xyz[3 * i];
            y[i] = (float) s.xyz[3 * i + 1];
            z[i] = (float) s.xyz[3 * i + 2];
        }
        int[] a = new int[s.triangles];
        int[] b = new int[s.triangles];
        int[] c = new int[s.triangles];
        for (int t = 0; t < s.triangles; t++)
        {
            a[t] = s.corners[3 * t];
            b[t] = s.corners[3 * t + 1];
            c[t] = s.corners[3 * t + 2];
        }
        int[] color = new int[s.triangles];
        Arrays.fill(color, 0xFFFFFF);
        return new Mesh("Surface", x, y, z, a, b, c, color);
    }
}
