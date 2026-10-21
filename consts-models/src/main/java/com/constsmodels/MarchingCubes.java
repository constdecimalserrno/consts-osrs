package com.constsmodels;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Turns voxels into the closed surface around them, for {@link SolidRemesh}: marching cubes over each voxel's
 * occupancy, lightly smoothed so the surface isn't blocky.
 *
 * <p>Occupancy runs from 0 (out) to 255 (in), in between where the caller knows the surface passes within the voxel.
 * The smoothing is a binomial blur, 1 4 6 4 1 along each axis, so each voxel's value is a whole number up to 255
 * times 4096; the surface runs where it crosses half that plus a half, which no voxel can equal.
 *
 * <p>Every cube's piece of surface is traced from where the surface crosses its six faces, so two cubes always agree
 * on the face between them. Where a face is ambiguous (two diagonal corners in, two out) the bilinear saddle decides,
 * worked in whole numbers, so the same way from either side. So the surface is closed and every edge has exactly two
 * triangles; and since each cube's triangles stay inside the cube, it never crosses itself. Triangles wind
 * counterclockwise seen from outside. The tables are built here rather than copied from a published one.
 */
final class MarchingCubes
{
    private static final int[] WEIGHTS = {1, 4, 6, 4, 1};

    /** Full occupancy. */
    static final int FULL = 255;

    /** A voxel is in when its smoothed value is above this, half of 255 times 4096; the surface is half a step up. */
    private static final int HALF = FULL * 4096 / 2;
    private static final double LEVEL = HALF + 0.5;

    /** The nearest a vertex comes to either end of its edge, in voxels. */
    private static final double MIN_STEP = 0.01;

    // A cube's corners: corner c is at (c & 1, c >> 1 & 1, c >> 2 & 1). Its edges run from the lower corner up
    // one axis: 0 to 3 along x, 4 to 7 along y, 8 to 11 along z
    private static final int[] EDGE_FROM = {0, 2, 4, 6, 0, 1, 4, 5, 0, 1, 2, 3};
    private static final int[] EDGE_TO = {1, 3, 5, 7, 2, 3, 6, 7, 4, 5, 6, 7};

    /** Each face's corners, counterclockwise seen from outside the cube: -x, +x, -y, +y, -z, +z. */
    private static final int[][] FACES = {
        {0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6},
    };

    /** Per corner pattern: which faces are ambiguous, a bit per face. */
    private static final int[] AMBIGUOUS = new int[256];

    /**
     * Per corner pattern and which ambiguous faces join their inside corners: the surface's loops of edges, each as
     * its length followed by its edges in order.
     */
    private static final int[][] LOOPS = new int[256 * 64][];

    static
    {
        int[][] edgeOf = new int[8][8];
        for (int e = 0; e < 12; e++)
        {
            edgeOf[EDGE_FROM[e]][EDGE_TO[e]] = e;
            edgeOf[EDGE_TO[e]][EDGE_FROM[e]] = e;
        }
        for (int pattern = 0; pattern < 256; pattern++)
        {
            int ambiguous = 0;
            for (int f = 0; f < 6; f++)
            {
                int[] q = FACES[f];
                boolean b0 = in(pattern, q[0]);
                if (b0 == in(pattern, q[2]) && in(pattern, q[1]) == in(pattern, q[3]) && b0 != in(pattern, q[1]))
                {
                    ambiguous |= 1 << f;
                }
            }
            AMBIGUOUS[pattern] = ambiguous;
            for (int joined = 0; joined < 64; joined++)
            {
                if ((joined & ~ambiguous) == 0)
                {
                    LOOPS[pattern * 64 + joined] = loops(pattern, joined, edgeOf);
                }
            }
        }
    }

    private MarchingCubes()
    {
    }

    private static boolean in(int pattern, int corner)
    {
        return (pattern >> corner & 1) != 0;
    }

    /**
     * The loops for one corner pattern. On each face, seen from outside the cube and going counterclockwise, the
     * surface's segment runs from an edge that steps into the set to the next edge that steps out of it, so the
     * outside is on its left; an ambiguous face whose inside corners join pairs each step in with the step out
     * before it instead. Each crossed edge starts one segment and ends another, so the segments close into loops,
     * counterclockwise seen from outside the set.
     */
    private static int[] loops(int pattern, int joined, int[][] edgeOf)
    {
        int[] next = new int[12];
        Arrays.fill(next, -1);
        for (int f = 0; f < 6; f++)
        {
            int[] q = FACES[f];
            int step = (joined >> f & 1) == 0 ? 1 : 3;
            for (int k = 0; k < 4; k++)
            {
                if (in(pattern, q[k]) || !in(pattern, q[(k + 1) & 3]))
                {
                    continue;
                }
                int j = (k + step) & 3;
                while (in(pattern, q[j]) == in(pattern, q[(j + 1) & 3]))
                {
                    j = (j + step) & 3;
                }
                next[edgeOf[q[k]][q[(k + 1) & 3]]] = edgeOf[q[j]][q[(j + 1) & 3]];
            }
        }
        int[] out = new int[24];
        int length = 0;
        boolean[] used = new boolean[12];
        for (int e = 0; e < 12; e++)
        {
            if (next[e] < 0 || used[e])
            {
                continue;
            }
            int lengthAt = length++;
            int count = 0;
            for (int at = e; !used[at]; at = next[at])
            {
                used[at] = true;
                out[length++] = at;
                count++;
            }
            out[lengthAt] = count;
        }
        return Arrays.copyOf(out, length);
    }

    /** The loops one cube's surface makes, for tests: as {@link #LOOPS} holds them. */
    static int[] loopsFor(int pattern, int joined)
    {
        return LOOPS[pattern * 64 + (joined & AMBIGUOUS[pattern])];
    }

    /** Each voxel's occupancy, a slice at a time. */
    interface Occupancy
    {
        /** Fills out with slice z's occupancy, voxel (x, y) at x + nx * y: 0 out, {@link #FULL} in. */
        void slice(int z, int[] out);
    }

    /** The surface around a set of voxels, each all in or all out. */
    static Surface extract(VoxelGrid grid, BitSet set)
    {
        return extract(grid, (z, out) ->
        {
            Arrays.fill(out, 0);
            int base = z * grid.slice;
            for (int i = set.nextSetBit(base); i >= 0 && i < base + grid.slice; i = set.nextSetBit(i + 1))
            {
                out[i - base] = FULL;
            }
        }, Integer.MAX_VALUE);
    }

    /**
     * The surface where occupancy crosses half: vertices in voxel coordinates, voxel (x, y, z)'s center at (x, y, z).
     * Closed as long as nothing within three voxels of the grid's border is occupied, as the smoothing reaches two.
     * Null as soon as it has more than maxTriangles.
     */
    static Surface extract(VoxelGrid grid, Occupancy occupancy, int maxTriangles)
    {
        int nx = grid.nx;
        int ny = grid.ny;
        int nz = grid.nz;
        int slice = grid.slice;
        Surface out = new Surface();

        // The blur across x and y of the five slices around the one being smoothed along z
        int[][] flat = new int[5][slice];
        int[] raw = new int[slice];
        int[] spread = new int[slice];
        boolean[] flatAny = new boolean[5];
        for (int z = -2; z < 2; z++)
        {
            flatAny[Math.floorMod(z, 5)] = smoothSlice(grid, occupancy, z, flat[Math.floorMod(z, 5)], raw, spread);
        }

        int[] lower = new int[slice];
        int[] upper = new int[slice];
        boolean lowerAny = false;

        // Each layer's vertices on its x and y edges, below and above, and on the z edges between: -1 for none yet
        int[][] xEdges = {new int[slice], new int[slice]};
        int[][] yEdges = {new int[slice], new int[slice]};
        int[] zEdges = new int[slice];
        Arrays.fill(xEdges[1], -1);
        Arrays.fill(yEdges[1], -1);

        int[] value = new int[8];
        int[] loopVertices = new int[12];
        for (int z = 0; z < nz; z++)
        {
            flatAny[Math.floorMod(z + 2, 5)] = smoothSlice(grid, occupancy, z + 2, flat[Math.floorMod(z + 2, 5)], raw, spread);
            boolean upperAny = false;
            Arrays.fill(upper, 0);
            for (int d = -2; d <= 2; d++)
            {
                int slot = Math.floorMod(z + d, 5);
                if (!flatAny[slot])
                {
                    continue;
                }
                upperAny = true;
                int w = WEIGHTS[d + 2];
                int[] source = flat[slot];
                for (int i = 0; i < slice; i++)
                {
                    upper[i] += w * source[i];
                }
            }

            int[] swap = xEdges[0];
            xEdges[0] = xEdges[1];
            xEdges[1] = swap;
            swap = yEdges[0];
            yEdges[0] = yEdges[1];
            yEdges[1] = swap;
            Arrays.fill(xEdges[1], -1);
            Arrays.fill(yEdges[1], -1);
            Arrays.fill(zEdges, -1);

            if (z > 0 && (lowerAny || upperAny))
            {
                int z0 = z - 1;
                for (int y = 0; y < ny - 1; y++)
                {
                    for (int x = 0; x < nx - 1; x++)
                    {
                        int i = x + nx * y;
                        int pattern = (lower[i] > HALF ? 1 : 0)
                            | (lower[i + 1] > HALF ? 2 : 0)
                            | (lower[i + nx] > HALF ? 4 : 0)
                            | (lower[i + nx + 1] > HALF ? 8 : 0)
                            | (upper[i] > HALF ? 16 : 0)
                            | (upper[i + 1] > HALF ? 32 : 0)
                            | (upper[i + nx] > HALF ? 64 : 0)
                            | (upper[i + nx + 1] > HALF ? 128 : 0);
                        if (pattern == 0 || pattern == 255)
                        {
                            continue;
                        }
                        value[0] = lower[i];
                        value[1] = lower[i + 1];
                        value[2] = lower[i + nx];
                        value[3] = lower[i + nx + 1];
                        value[4] = upper[i];
                        value[5] = upper[i + 1];
                        value[6] = upper[i + nx];
                        value[7] = upper[i + nx + 1];

                        int joined = 0;
                        int ambiguous = AMBIGUOUS[pattern];
                        for (int f = 0; ambiguous != 0 && f < 6; f++)
                        {
                            if ((ambiguous >> f & 1) != 0 && saddleIsIn(value, FACES[f]))
                            {
                                joined |= 1 << f;
                            }
                        }

                        int[] loops = LOOPS[pattern * 64 + joined];
                        for (int at = 0; at < loops.length; )
                        {
                            int count = loops[at++];
                            for (int k = 0; k < count; k++)
                            {
                                int e = loops[at++];
                                loopVertices[k] = vertexOn(out, e, x, y, z0, nx, value, xEdges, yEdges, zEdges);
                            }
                            out.addLoop(loopVertices, count);
                        }
                    }
                }
            }

            if (out.triangles > maxTriangles)
            {
                return null;
            }
            swap = lower;
            lower = upper;
            upper = swap;
            lowerAny = upperAny;
        }
        return out;
    }

    /**
     * Whether a face's bilinear saddle is in the set, so its two inside corners join across it. Worked in whole
     * numbers from the corner values, so the cubes on either side of the face always agree.
     */
    private static boolean saddleIsIn(int[] value, int[] face)
    {
        // Twice each value less the level: odd, so never zero
        long s0 = 2L * value[face[0]] - (2 * HALF + 1);
        long s1 = 2L * value[face[1]] - (2 * HALF + 1);
        long s2 = 2L * value[face[2]] - (2 * HALF + 1);
        long s3 = 2L * value[face[3]] - (2 * HALF + 1);
        long num = s0 * s2 - s1 * s3;
        long den = s0 + s2 - s1 - s3;
        return num != 0 && (num > 0) == (den > 0);
    }

    /** The vertex where the surface crosses edge e of the cube at (x, y, z), shared with the cubes around it. */
    private static int vertexOn(Surface out, int e, int x, int y, int z, int nx, int[] value, int[][] xEdges,
        int[][] yEdges, int[] zEdges)
    {
        int from = EDGE_FROM[e];
        int dx = from & 1;
        int dy = from >> 1 & 1;
        int dz = from >> 2 & 1;
        int at = x + dx + nx * (y + dy);
        int[] slot;
        if (e < 4)
        {
            slot = xEdges[dz];
        }
        else if (e < 8)
        {
            slot = yEdges[dz];
        }
        else
        {
            slot = zEdges;
        }
        if (slot[at] >= 0)
        {
            return slot[at];
        }
        // Never right at a corner, so the vertices around a corner stay apart
        double a = value[from];
        double t = Math.max(MIN_STEP, Math.min(1 - MIN_STEP, (LEVEL - a) / (value[EDGE_TO[e]] - a)));
        double vx = x + dx + (e < 4 ? t : 0);
        double vy = y + dy + (e >= 4 && e < 8 ? t : 0);
        double vz = z + dz + (e >= 8 ? t : 0);
        slot[at] = out.addVertex(vx, vy, vz);
        return slot[at];
    }

    /**
     * Blurs slice z's occupancy across x and y into out; returns whether any of it is above zero. Slices outside the
     * grid are empty.
     */
    private static boolean smoothSlice(VoxelGrid grid, Occupancy occupancy, int z, int[] out, int[] raw, int[] spread)
    {
        Arrays.fill(out, 0);
        if (z < 0 || z >= grid.nz)
        {
            return false;
        }
        int nx = grid.nx;
        int ny = grid.ny;
        occupancy.slice(z, raw);
        int minRow = ny;
        int maxRow = -1;
        for (int y = 0; y < ny; y++)
        {
            int at = y * nx;
            boolean any = false;
            for (int x = 0; x < nx && !any; x++)
            {
                any = raw[at + x] != 0;
            }
            if (!any)
            {
                Arrays.fill(spread, at, at + nx, 0);
                continue;
            }
            for (int x = 0; x < nx; x++)
            {
                int sum = 0;
                for (int d = Math.max(-2, -x); d <= Math.min(2, nx - 1 - x); d++)
                {
                    sum += WEIGHTS[d + 2] * raw[at + x + d];
                }
                spread[at + x] = sum;
            }
            minRow = Math.min(minRow, y);
            maxRow = y;
        }
        if (maxRow < 0)
        {
            return false;
        }
        for (int y = Math.max(0, minRow - 2); y <= Math.min(ny - 1, maxRow + 2); y++)
        {
            int at = y * nx;
            for (int d = -2; d <= 2; d++)
            {
                int yy = y + d;
                if (yy < minRow || yy > maxRow)
                {
                    continue;
                }
                int w = WEIGHTS[d + 2];
                int from = yy * nx;
                for (int x = 0; x < nx; x++)
                {
                    out[at + x] += w * spread[from + x];
                }
            }
        }
        return true;
    }

    /** Triangles over shared vertices, as marching cubes and the decimation pass them along. */
    static final class Surface
    {
        /** Each vertex's x, y, z, three to a vertex. */
        double[] xyz = new double[3 * 1024];
        int vertices;

        /** Each triangle's three vertex indices, counterclockwise seen from outside. */
        int[] corners = new int[3 * 1024];
        int triangles;

        int addVertex(double x, double y, double z)
        {
            if (3 * vertices + 3 > xyz.length)
            {
                xyz = Arrays.copyOf(xyz, xyz.length * 2);
            }
            xyz[3 * vertices] = x;
            xyz[3 * vertices + 1] = y;
            xyz[3 * vertices + 2] = z;
            return vertices++;
        }

        void addTriangle(int a, int b, int c)
        {
            if (3 * triangles + 3 > corners.length)
            {
                corners = Arrays.copyOf(corners, corners.length * 2);
            }
            corners[3 * triangles] = a;
            corners[3 * triangles + 1] = b;
            corners[3 * triangles + 2] = c;
            triangles++;
        }

        /**
         * Adds a loop of vertices as triangles, keeping its winding: up to six as a fan from the corner that gives
         * the best-shaped triangles, anything longer as a fan around a new vertex at its middle.
         */
        void addLoop(int[] loop, int count)
        {
            if (count <= 6)
            {
                int apex = 0;
                double bestShape = -1;
                for (int from = 0; from < count; from++)
                {
                    double shape = Double.MAX_VALUE;
                    for (int k = 1; k + 1 < count; k++)
                    {
                        shape = Math.min(shape, shape(loop[from], loop[(from + k) % count], loop[(from + k + 1) % count]));
                    }
                    if (shape > bestShape)
                    {
                        bestShape = shape;
                        apex = from;
                    }
                }
                for (int k = 1; k + 1 < count; k++)
                {
                    addTriangle(loop[apex], loop[(apex + k) % count], loop[(apex + k + 1) % count]);
                }
            }
            else
            {
                double cx = 0;
                double cy = 0;
                double cz = 0;
                for (int k = 0; k < count; k++)
                {
                    cx += xyz[3 * loop[k]];
                    cy += xyz[3 * loop[k] + 1];
                    cz += xyz[3 * loop[k] + 2];
                }
                int middle = addVertex(cx / count, cy / count, cz / count);
                for (int k = 0; k < count; k++)
                {
                    addTriangle(middle, loop[k], loop[(k + 1) % count]);
                }
            }
        }

        /** A triangle's shape: twice its area over its edges' squared lengths, largest when equilateral. */
        private double shape(int a, int b, int c)
        {
            double ux = xyz[3 * b] - xyz[3 * a];
            double uy = xyz[3 * b + 1] - xyz[3 * a + 1];
            double uz = xyz[3 * b + 2] - xyz[3 * a + 2];
            double vx = xyz[3 * c] - xyz[3 * a];
            double vy = xyz[3 * c + 1] - xyz[3 * a + 1];
            double vz = xyz[3 * c + 2] - xyz[3 * a + 2];
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double wx = vx - ux;
            double wy = vy - uy;
            double wz = vz - uz;
            double sum = ux * ux + uy * uy + uz * uz + vx * vx + vy * vy + vz * vz + wx * wx + wy * wy + wz * wz;
            return sum > 0 ? Math.sqrt(nx * nx + ny * ny + nz * nz) / sum : 0;
        }
    }
}
