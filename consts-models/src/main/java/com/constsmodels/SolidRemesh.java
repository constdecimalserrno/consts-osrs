package com.constsmodels;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Makes a model one closed solid that prints, the cleanup a game model otherwise gets by hand in Blender. Game
 * models are surfaces, not solids: capes, flags and leaves have no thickness, shells are open at the neck and
 * sleeves, parts overlap and edges are shared by any number of faces. What comes out has every edge shared by
 * exactly two triangles, wound outward, and every part at least a wall thick.
 *
 * <p>It works on voxels at print scale, the way Blender's voxel remesh does:
 * <ol>
 * <li>Every voxel within half a wall of a triangle is marked: a shell a wall thick around every surface.</li>
 * <li>The shell is grown over small gaps, and everything it then closes off from the outside is filled in. That
 *     fills closed parts and the parts that overlap them.</li>
 * <li>The result shrinks back by as much as it grew, so a closed part keeps its own size and shape.</li>
 * <li>What's left too thin to hold a ball a wall across (a cape, a flag, a sword's blade) takes the shell from
 *     step 1 instead, so it ends up a wall thick, and the notches where it meets a thicker part are filled.</li>
 * <li>Marching cubes over the voxels, lightly smoothed, give the surface. Where the surface is the model's own, or
 *     a thin part's shell, the exact distance to the model places it between voxels, so flat faces come out flat
 *     rather than in steps. Quadric decimation then thins out the triangles, moving the surface no more than half a
 *     voxel.</li>
 * <li>Asked for a flat bottom, the lowest slice of the solid is cut off first, and what's left at the cut lies flat on
 *     one plane: where the print meets the plate, so it stands without rocking and needs nothing holding up its feet.
 *     The slice is thin, a voxel or so, unless the solid would then stand on too little to hold it up, as a figure
 *     with one foot a little higher than the other does: then it's as thick as it takes for the middle of the solid
 *     to be over what it stands on, up to {@link #MAX_FLAT_SHARE} of the print.</li>
 * <li>Each triangle takes the color of the model's triangle nearest to it.</li>
 * </ol>
 *
 * <p>Voxels are {@link #defaultVoxelMm} by default, never more than {@link #MAX_AXIS} along any side nor
 * {@link #MAX_VOXELS} in all, and marching cubes may give decimation no more than {@link #MAX_EXTRACTED} triangles; a
 * coarser voxel is used when one would be more. So however big the model, the voxels take about a byte and a half each
 * and decimation about 110 MB at most. Walls are at least three voxels. A player at 100 mm takes about two seconds,
 * so it runs off the client thread.
 */
final class SolidRemesh
{
    /** The thinnest wall, in millimetres at print scale, by default. */
    static final double MIN_WALL_MM = 1.0;

    /**
     * How much is cut off the bottom for a flat one by default, in millimetres: at least this and a voxel, and up to a
     * voxel more, as the bottom falls between voxels.
     */
    static final double FLAT_MM = 0.25;

    /** The most cut off the bottom so the print stands, as a share of the print's longest side. */
    static final double MAX_FLAT_SHARE = 0.02;

    /** Gaps up to the wall plus this, in millimetres at print scale, are sealed. */
    static final double SEAL_MM = 1.0;

    /** The finest voxel used, in millimetres. */
    static final double MIN_VOXEL_MM = 0.1;

    /** The most voxels along any side, and in all. */
    static final int MAX_AXIS = 512;
    static final int MAX_VOXELS = 1 << 25;

    /** Voxels along the longest side by default, when walls allow. */
    private static final int DEFAULT_VOXELS = 400;

    /** How far decimation may move the surface, in voxels. */
    private static final double MAX_ERROR = 0.5;

    /**
     * The most triangles marching cubes may give decimation, which takes about 110 bytes a triangle; past this the
     * voxels get coarser.
     */
    private static final int MAX_EXTRACTED = 1_000_000;

    /** A triangle with less than half this area, in square voxels, has none to print. */
    private static final double MIN_TWICE_AREA = 1e-9;

    /** Half a wall is never less than this, in voxels, so the thinnest wall survives the smoothing. */
    private static final double MIN_HALF_WALL = 1.5;

    /**
     * How near the flat bottom's plane a vertex must be, in voxels, to be put on it: a thin part's bottom, rounded by
     * the smoothing, comes this close.
     */
    private static final double ONTO_FLOOR = 0.25;

    private SolidRemesh()
    {
    }

    /** The mesh as one closed solid at the default voxel size and wall, with a flat bottom. */
    static Mesh makeSolid(Mesh mesh, double sizeMm)
    {
        return makeSolid(mesh, sizeMm, PrintProgress.NONE);
    }

    /** The same, telling progress how far it's got. */
    static Mesh makeSolid(Mesh mesh, double sizeMm, PrintProgress progress)
    {
        return makeSolid(mesh, sizeMm, defaultVoxelMm(sizeMm), MIN_WALL_MM, FLAT_MM, progress);
    }

    /** The voxel size used by default for a print sizeMm long: fine enough for a wall three voxels thick. */
    static double defaultVoxelMm(double sizeMm)
    {
        return Math.max(MIN_VOXEL_MM, Math.min(sizeMm / DEFAULT_VOXELS, MIN_WALL_MM / 3));
    }

    /**
     * The mesh as one closed solid, in the same units and axes. Its triangles already share their corners, some a
     * small fraction of a voxel apart, so it wants scaling and placing on the plate but no welding. Parts of the
     * model that float free of the rest, further apart than the seal, stay separate pieces.
     *
     * @param sizeMm the longest side of the print, in millimetres, which sets the scale the rest are at
     * @param voxelMm the voxel size, in millimetres: the detail kept
     * @param minWallMm the thinnest a part may be, in millimetres
     * @param flatMm how much to cut off the bottom at least to make it flat, in millimetres, never less than a voxel;
     *     0 for none. More goes, up to {@link #MAX_FLAT_SHARE} of sizeMm, if the solid needs it to stand
     */
    static Mesh makeSolid(Mesh mesh, double sizeMm, double voxelMm, double minWallMm, double flatMm)
    {
        return makeSolid(mesh, sizeMm, voxelMm, minWallMm, flatMm, PrintProgress.NONE);
    }

    /** The same, telling progress how far it's got. */
    static Mesh makeSolid(Mesh mesh, double sizeMm, double voxelMm, double minWallMm, double flatMm,
        PrintProgress progress)
    {
        float[] box = mesh.bounds();
        double longest = Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2]));
        if (mesh.isEmpty() || !(longest > 0) || !(sizeMm > 0))
        {
            return empty(mesh.name);
        }
        double wallMm = minWallMm >= 0 ? minWallMm : MIN_WALL_MM;
        Plan plan = Plan.of(box, sizeMm / longest, voxelMm > 0 ? voxelMm : defaultVoxelMm(sizeMm), wallMm);
        while (true)
        {
            double[] triangles = inVoxels(mesh, plan);
            if (!anyArea(triangles))
            {
                return empty(mesh.name);
            }
            Nearest nearest = new Nearest(triangles, plan);
            MarchingCubes.Surface surface = voxelSurface(triangles, plan, nearest, flatMm,
                Math.max(flatMm, MAX_FLAT_SHARE * sizeMm), progress.step(0, 0.75));
            if (surface == null)
            {
                // Too much surface to thin out in bounded memory: coarser voxels, a bit over half the triangles
                plan = Plan.of(box, sizeMm / longest, plan.voxelMm * 1.3, wallMm);
                continue;
            }
            if (flatMm > 0 && surface.vertices > 0)
            {
                // Step 6: the bottom on one plane, before decimation so it keeps it, and again after
                double floor = lowest(surface);
                ontoFloor(surface, floor);
                surface = QuadricDecimator.decimate(surface, MAX_ERROR);
                ontoFloor(surface, floor);
            }
            else
            {
                surface = QuadricDecimator.decimate(surface, MAX_ERROR);
            }
            progress.done(0.95);
            Mesh solid = toMesh(mesh.name, plan, surface, nearest, mesh.color);
            progress.done(1);
            return solid;
        }
    }

    /**
     * Steps 1 to 5 up to decimation: the surface of the solid, in voxel coordinates, with its bottom cut off if a flat
     * one is asked for (from flatMm to deepestMm thick), or null as soon as it has more than {@link #MAX_EXTRACTED}
     * triangles. The voxels are let go when it returns.
     */
    private static MarchingCubes.Surface voxelSurface(double[] triangles, Plan plan, Nearest nearest, double flatMm,
        double deepestMm, PrintProgress progress)
    {
        VoxelGrid grid = new VoxelGrid(plan.nx, plan.ny, plan.nz);

        // Step 1
        BitSet shell = new BitSet(grid.size);
        BitSet surface = new BitSet(grid.size);
        rasterize(triangles, grid, plan.shell, (index, face, d2, x, y, z) ->
        {
            shell.set(index);
            if (d2 <= 0.25)
            {
                surface.set(index);
            }
            // Any point's nearest triangle passes within this of its voxel's center
            if (d2 <= 0.76)
            {
                nearest.add(face, x, y, z);
            }
        });
        nearest.index();
        progress.done(0.25);

        // Steps 2 to 4
        Solid solid = Solid.of(grid, plan, shell, surface);
        progress.done(0.6);

        // Step 5: the distance to the model from every voxel near it, to place the surface between voxels
        byte[] distance = grid.scratch();
        Arrays.fill(distance, (byte) Solid.FAR);
        double reach = plan.shell + 0.5;
        double step = reach / (Solid.FAR - 1);
        rasterize(triangles, grid, reach, (index, face, d2, x, y, z) ->
        {
            int q = (int) Math.round(Math.sqrt(d2) / step);
            if (q < (distance[index] & 0xFF))
            {
                distance[index] = (byte) q;
            }
        });
        progress.done(0.7);
        MarchingCubes.Occupancy occupancy = solid.occupancy(grid, distance, step, plan.shell);
        if (flatMm > 0 && !solid.all.isEmpty())
        {
            // Step 6: nothing below the cut, so the surface crosses it half a voxel down, level wherever the solid
            // goes on above it. The lowest slice of the solid holds the bottom, give or take half a voxel
            int lowest = solid.all.nextSetBit(0) / grid.slice;
            int cut = standingCut(grid, solid.all,
                lowest + 1 + Math.max(1, (int) Math.round(flatMm / plan.voxelMm)),
                lowest + 1 + Math.max(1, (int) Math.round(deepestMm / plan.voxelMm)));
            MarchingCubes.Occupancy whole = occupancy;
            occupancy = (z, out) ->
            {
                if (z < cut)
                {
                    Arrays.fill(out, 0);
                }
                else
                {
                    whole.slice(z, out);
                }
            };
        }
        MarchingCubes.Surface extracted = MarchingCubes.extract(grid, occupancy, MAX_EXTRACTED);
        progress.done(1);
        return extracted;
    }

    /**
     * The slice to cut the solid's bottom off at, from first to last: the first where what lies flat on the plate
     * stands under the middle of the solid above it, so it won't tip over, or first if none does. What lies flat at a
     * cut is where the slice below it is solid five voxels across each way, as far as the smoothing reaches: the cut
     * goes through it, so it comes out level.
     */
    private static int standingCut(VoxelGrid grid, BitSet solid, int first, int last)
    {
        last = Math.min(last, grid.nz - 1);
        if (first >= last)
        {
            return first;
        }
        // The middle of the solid above the thinnest cut, seen from above
        double sumX = 0;
        double sumY = 0;
        long count = 0;
        for (int i = solid.nextSetBit(first * grid.slice); i >= 0; i = solid.nextSetBit(i + 1))
        {
            sumX += i % grid.nx;
            sumY += i / grid.nx % grid.ny;
            count++;
        }
        if (count == 0)
        {
            return first;
        }
        double middleX = sumX / count;
        double middleY = sumY / count;

        int[] xs = new int[2 * grid.ny];
        int[] ys = new int[2 * grid.ny];
        int[] across = new int[grid.slice];
        int[] down = new int[grid.slice];
        for (int cut = first; cut <= last; cut++)
        {
            // How many voxels in a row are solid up to each, along x and then, of those with five, along y
            int base = (cut - 1) * grid.slice;
            for (int y = 0; y < grid.ny; y++)
            {
                int run = 0;
                for (int x = 0; x < grid.nx; x++)
                {
                    run = solid.get(base + y * grid.nx + x) ? run + 1 : 0;
                    across[y * grid.nx + x] = run;
                }
            }
            for (int x = 0; x < grid.nx; x++)
            {
                int run = 0;
                for (int y = 0; y < grid.ny; y++)
                {
                    // Solid from x - 2 to x + 2
                    boolean wide = x + 2 < grid.nx && across[y * grid.nx + x + 2] >= 5;
                    run = wide ? run + 1 : 0;
                    down[y * grid.nx + x] = run;
                }
            }

            // The ends of each row of what lies flat are enough for the hull around it
            int n = 0;
            for (int y = 2; y + 2 < grid.ny; y++)
            {
                int left = -1;
                int right = -1;
                for (int x = 0; x < grid.nx; x++)
                {
                    if (down[(y + 2) * grid.nx + x] >= 5)
                    {
                        left = left < 0 ? x : left;
                        right = x;
                    }
                }
                if (left >= 0)
                {
                    xs[n] = left;
                    ys[n++] = y;
                    xs[n] = right;
                    ys[n++] = y;
                }
            }
            if (inHull(xs, ys, n, middleX, middleY))
            {
                return cut;
            }
        }
        return first;
    }

    /** Whether the point is inside the convex hull of the first n points (Andrew's monotone chain). */
    private static boolean inHull(int[] xs, int[] ys, int n, double px, double py)
    {
        if (n < 3)
        {
            return false;
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++)
        {
            order[i] = i;
        }
        Arrays.sort(order, (p, q) -> xs[p] != xs[q] ? Integer.compare(xs[p], xs[q]) : Integer.compare(ys[p], ys[q]));
        // The lower hull left to right, then the upper right to left; the last point is the first again
        int[] hull = new int[2 * n];
        int k = 0;
        for (int j = 0; j < n; j++)
        {
            int i = order[j];
            while (k >= 2 && turn(xs, ys, hull[k - 2], hull[k - 1], i) <= 0)
            {
                k--;
            }
            hull[k++] = i;
        }
        int lower = k + 1;
        for (int j = n - 2; j >= 0; j--)
        {
            int i = order[j];
            while (k >= lower && turn(xs, ys, hull[k - 2], hull[k - 1], i) <= 0)
            {
                k--;
            }
            hull[k++] = i;
        }
        k--;
        if (k < 3)
        {
            return false;
        }
        // Counterclockwise: the point is inside if it's left of, or on, every edge
        for (int j = 0; j < k; j++)
        {
            int a = hull[j];
            int b = hull[(j + 1) % k];
            if ((xs[b] - xs[a]) * (py - ys[a]) - (ys[b] - ys[a]) * (px - xs[a]) < 0)
            {
                return false;
            }
        }
        return true;
    }

    /** Twice the signed area of the triangle a, b, c: positive when it turns left. */
    private static long turn(int[] xs, int[] ys, int a, int b, int c)
    {
        return (long) (xs[b] - xs[a]) * (ys[c] - ys[a]) - (long) (ys[b] - ys[a]) * (xs[c] - xs[a]);
    }

    /** The lowest a surface goes. */
    private static double lowest(MarchingCubes.Surface surface)
    {
        double low = Double.MAX_VALUE;
        for (int i = 0; i < surface.vertices; i++)
        {
            low = Math.min(low, surface.xyz[3 * i + 2]);
        }
        return low;
    }

    /** Puts every vertex below the floor, or less than {@link #ONTO_FLOOR} above it, onto it. */
    private static void ontoFloor(MarchingCubes.Surface surface, double floor)
    {
        for (int i = 0; i < surface.vertices; i++)
        {
            if (surface.xyz[3 * i + 2] < floor + ONTO_FLOOR)
            {
                surface.xyz[3 * i + 2] = floor;
            }
        }
    }

    private static Mesh empty(String name)
    {
        return new Mesh(name, new float[0], new float[0], new float[0], new int[0], new int[0], new int[0], new int[0]);
    }

    /** The mesh's triangles in voxel coordinates, nine numbers to a triangle. */
    private static double[] inVoxels(Mesh mesh, Plan plan)
    {
        double[] out = new double[9 * mesh.faceCount()];
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            int[] corners = {mesh.a[f], mesh.b[f], mesh.c[f]};
            for (int k = 0; k < 3; k++)
            {
                out[9 * f + 3 * k] = (mesh.x[corners[k]] - plan.ox) / plan.unit;
                out[9 * f + 3 * k + 1] = (mesh.y[corners[k]] - plan.oy) / plan.unit;
                out[9 * f + 3 * k + 2] = (mesh.z[corners[k]] - plan.oz) / plan.unit;
            }
        }
        return out;
    }

    /** Whether any triangle has area, so there is something to print. */
    private static boolean anyArea(double[] triangles)
    {
        double[] n = new double[3];
        for (int at = 0; at < triangles.length; at += 9)
        {
            if (normal(triangles, at, n) >= MIN_TWICE_AREA)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts the normal of the triangle of nine coordinates at triangles[at] into n, as long as twice its area, and
     * returns that length.
     */
    private static double normal(double[] triangles, int at, double[] n)
    {
        double ux = triangles[at + 3] - triangles[at];
        double uy = triangles[at + 4] - triangles[at + 1];
        double uz = triangles[at + 5] - triangles[at + 2];
        double vx = triangles[at + 6] - triangles[at];
        double vy = triangles[at + 7] - triangles[at + 1];
        double vz = triangles[at + 8] - triangles[at + 2];
        n[0] = uy * vz - uz * vy;
        n[1] = uz * vx - ux * vz;
        n[2] = ux * vy - uy * vx;
        return Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
    }

    /** What {@link #rasterize} finds: a voxel within reach of a triangle, and its squared distance. */
    private interface Visitor
    {
        void visit(int index, int face, double distanceSquared, int x, int y, int z);
    }

    /** Visits every voxel whose center is within reach of a triangle with any area, once per such triangle. */
    private static void rasterize(double[] triangles, VoxelGrid grid, double reach, Visitor visitor)
    {
        int[] dims = {grid.nx, grid.ny, grid.nz};
        int[] strides = {1, grid.nx, grid.slice};
        double reach2 = reach * reach;
        int[] cell = new int[3];
        double[] n = new double[3];
        double[] edge = new double[9];
        for (int f = 0; f < triangles.length / 9; f++)
        {
            int at = 9 * f;
            double length = normal(triangles, at, n);
            if (length < MIN_TWICE_AREA)
            {
                // No area, so nothing to print
                continue;
            }
            n[0] /= length;
            n[1] /= length;
            n[2] /= length;

            // Columns along the axis the triangle faces most; each column crosses it once
            double ax = Math.abs(n[0]);
            double ay = Math.abs(n[1]);
            double az = Math.abs(n[2]);
            int w = ax >= ay && ax >= az ? 0 : ay >= az ? 1 : 2;
            int u = (w + 1) % 3;
            int v = (w + 2) % 3;
            double[] pu = {triangles[at + u], triangles[at + 3 + u], triangles[at + 6 + u]};
            double[] pv = {triangles[at + v], triangles[at + 3 + v], triangles[at + 6 + v]};
            int uMin = Math.max(0, (int) Math.ceil(Math.min(pu[0], Math.min(pu[1], pu[2])) - reach));
            int uMax = Math.min(dims[u] - 1, (int) Math.floor(Math.max(pu[0], Math.max(pu[1], pu[2])) + reach));
            int vMin = Math.max(0, (int) Math.ceil(Math.min(pv[0], Math.min(pv[1], pv[2])) - reach));
            int vMax = Math.min(dims[v] - 1, (int) Math.floor(Math.max(pv[0], Math.max(pv[1], pv[2])) + reach));

            // The triangle's edges seen down the columns, as lines with unit normals pointing in
            double turn = (pu[1] - pu[0]) * (pv[2] - pv[0]) - (pv[1] - pv[0]) * (pu[2] - pu[0]) > 0 ? 1 : -1;
            for (int k = 0; k < 3; k++)
            {
                int next = (k + 1) % 3;
                double eu = pu[next] - pu[k];
                double ev = pv[next] - pv[k];
                double edgeLength = Math.sqrt(eu * eu + ev * ev);
                edge[3 * k] = -ev * turn / edgeLength;
                edge[3 * k + 1] = eu * turn / edgeLength;
                edge[3 * k + 2] = -(edge[3 * k] * pu[k] + edge[3 * k + 1] * pv[k]);
            }

            double span = reach / Math.abs(n[w]);
            for (int iu = uMin; iu <= uMax; iu++)
            {
                for (int iv = vMin; iv <= vMax; iv++)
                {
                    boolean out = false;
                    for (int k = 0; k < 3 && !out; k++)
                    {
                        out = edge[3 * k] * iu + edge[3 * k + 1] * iv + edge[3 * k + 2] < -reach;
                    }
                    if (out)
                    {
                        continue;
                    }
                    // Where the triangle's plane crosses the column
                    double w0 = triangles[at + w] - (n[u] * (iu - triangles[at + u]) + n[v] * (iv - triangles[at + v])) / n[w];
                    int wMin = Math.max(0, (int) Math.ceil(w0 - span));
                    int wMax = Math.min(dims[w] - 1, (int) Math.floor(w0 + span));
                    int column = iu * strides[u] + iv * strides[v];
                    for (int iw = wMin; iw <= wMax; iw++)
                    {
                        cell[u] = iu;
                        cell[v] = iv;
                        cell[w] = iw;
                        double d2 = distanceSquared(triangles, at, cell[0], cell[1], cell[2]);
                        if (d2 <= reach2)
                        {
                            visitor.visit(column + iw * strides[w], f, d2, cell[0], cell[1], cell[2]);
                        }
                    }
                }
            }
        }
    }

    /** Step 7 and back to game units: each triangle colored like the model's triangle nearest its middle. */
    private static Mesh toMesh(String name, Plan plan, MarchingCubes.Surface s, Nearest nearest, int[] colors)
    {
        float[] x = new float[s.vertices];
        float[] y = new float[s.vertices];
        float[] z = new float[s.vertices];
        for (int i = 0; i < s.vertices; i++)
        {
            x[i] = (float) (plan.ox + s.xyz[3 * i] * plan.unit);
            y[i] = (float) (plan.oy + s.xyz[3 * i + 1] * plan.unit);
            z[i] = (float) (plan.oz + s.xyz[3 * i + 2] * plan.unit);
        }
        int[] a = new int[s.triangles];
        int[] b = new int[s.triangles];
        int[] c = new int[s.triangles];
        int[] color = new int[s.triangles];
        for (int t = 0; t < s.triangles; t++)
        {
            a[t] = s.corners[3 * t];
            b[t] = s.corners[3 * t + 1];
            c[t] = s.corners[3 * t + 2];
            double cx = (s.xyz[3 * a[t]] + s.xyz[3 * b[t]] + s.xyz[3 * c[t]]) / 3;
            double cy = (s.xyz[3 * a[t] + 1] + s.xyz[3 * b[t] + 1] + s.xyz[3 * c[t] + 1]) / 3;
            double cz = (s.xyz[3 * a[t] + 2] + s.xyz[3 * b[t] + 2] + s.xyz[3 * c[t] + 2]) / 3;
            int face = nearest.find(cx, cy, cz);
            color[t] = face >= 0 ? colors[face] & 0xFFFFFF : 0xFFFFFF;
        }
        return new Mesh(name, x, y, z, a, b, c, color);
    }

    /** Squared distance from (x, y, z) to the triangle of nine coordinates at triangles[at]. */
    static double distanceSquared(double[] triangles, int at, double x, double y, double z)
    {
        double ax = triangles[at], ay = triangles[at + 1], az = triangles[at + 2];
        double bx = triangles[at + 3], by = triangles[at + 4], bz = triangles[at + 5];
        double cx = triangles[at + 6], cy = triangles[at + 7], cz = triangles[at + 8];
        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double acx = cx - ax, acy = cy - ay, acz = cz - az;
        double apx = x - ax, apy = y - ay, apz = z - az;

        // Which part of the triangle is nearest: a corner, an edge or the inside (Ericson, Real-Time Collision
        // Detection, 5.1.5)
        double d1 = abx * apx + aby * apy + abz * apz;
        double d2 = acx * apx + acy * apy + acz * apz;
        if (d1 <= 0 && d2 <= 0)
        {
            return squared(apx, apy, apz);
        }
        double bpx = x - bx, bpy = y - by, bpz = z - bz;
        double d3 = abx * bpx + aby * bpy + abz * bpz;
        double d4 = acx * bpx + acy * bpy + acz * bpz;
        if (d3 >= 0 && d4 <= d3)
        {
            return squared(bpx, bpy, bpz);
        }
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0)
        {
            double t = d1 / (d1 - d3);
            return squared(apx - t * abx, apy - t * aby, apz - t * abz);
        }
        double cpx = x - cx, cpy = y - cy, cpz = z - cz;
        double d5 = abx * cpx + aby * cpy + abz * cpz;
        double d6 = acx * cpx + acy * cpy + acz * cpz;
        if (d6 >= 0 && d5 <= d6)
        {
            return squared(cpx, cpy, cpz);
        }
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0)
        {
            double t = d2 / (d2 - d6);
            return squared(apx - t * acx, apy - t * acy, apz - t * acz);
        }
        double va = d3 * d6 - d5 * d4;
        if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0)
        {
            double t = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            return squared(bpx - t * (cx - bx), bpy - t * (cy - by), bpz - t * (cz - bz));
        }
        double sum = va + vb + vc;
        if (!(sum > 0))
        {
            return squared(apx, apy, apz);
        }
        double v = vb / sum;
        double w = vc / sum;
        return squared(apx - v * abx - w * acx, apy - v * aby - w * acy, apz - v * abz - w * acz);
    }

    private static double squared(double x, double y, double z)
    {
        return x * x + y * y + z * z;
    }

    /** The solid as voxels, and what's needed to place its surface between them. */
    private static final class Solid
    {
        /** A voxel further from the model than measured. */
        static final int FAR = 255;

        /** Every voxel of the solid. */
        final BitSet all;

        /** The closed parts as shrunk back, without the model's own surface voxels: in or out by the model's surface. */
        final BitSet inside;

        /** Where a thin part's surface is its shell's, half a wall out from the model's. */
        final BitSet thinShell;

        /** Around thin parts, where the voxels are taken as they are. */
        final BitSet nearThin;

        /** The solid's voxels but those only there as a thin part's shell. */
        final BitSet kept;

        private Solid(BitSet all, BitSet inside, BitSet thinShell, BitSet nearThin, BitSet kept)
        {
            this.all = all;
            this.inside = inside;
            this.thinShell = thinShell;
            this.nearThin = nearThin;
            this.kept = kept;
        }

        /** Steps 2 to 4: the shell sealed, filled, shrunk back and thin parts made a wall thick. */
        static Solid of(VoxelGrid grid, Plan plan, BitSet shell, BitSet surface)
        {
            BitSet grown = plan.seal > 0 ? grid.dilate(shell, plan.seal) : shell;
            BitSet filled = grid.fill(grown);

            // Back by as much as it grew; the model's own surface voxels always stay
            grid.measure(grid.complement(filled), plan.shell + plan.seal + plan.halfWall);
            BitSet inside = grid.beyond(plan.shell + plan.seal);
            BitSet solid = (BitSet) inside.clone();
            solid.or(surface);
            BitSet core = grid.beyond(plan.shell + plan.seal + plan.halfWall);

            // Thin: too far from anywhere a ball a wall across fits. A thick part's corners stay in reach
            BitSet thin = (BitSet) solid.clone();
            thin.andNot(grid.dilate(core, plan.cover));
            if (thin.isEmpty())
            {
                BitSet all = grid.fill(solid);
                return new Solid(all, inside, new BitSet(), new BitSet(), all);
            }
            // A thin part's shell reaches as far as the cover, so it is a wall thick right up to the thick part
            grid.measure(thin, plan.cover + plan.halfWall);
            BitSet thinShell = grid.within(plan.cover);
            BitSet nearThin = grid.within(plan.cover + plan.halfWall);
            BitSet thick = (BitSet) thinShell.clone();
            thick.and(shell);
            BitSet joined = (BitSet) solid.clone();
            joined.or(thick);

            // The notches where a thin part meets a thick one, filled
            BitSet notches = grid.erode(grid.dilate(joined, plan.halfWall), plan.halfWall);
            notches.andNot(joined);
            notches.and(nearThin);
            joined.or(notches);

            // No hollows left inside
            BitSet all = grid.fill(joined);
            BitSet onlyShell = thick;
            onlyShell.andNot(solid);
            onlyShell.andNot(notches);
            BitSet kept = (BitSet) all.clone();
            kept.andNot(onlyShell);
            return new Solid(all, inside, thinShell, nearThin, kept);
        }

        /**
         * Each voxel's occupancy for marching cubes, from the distance to the model measured in steps (FAR for
         * further). Where the solid's surface is the model's own, a voxel the distance in or out of it is that far
         * from half; where it is a thin part's shell, a voxel is as far from half as it is from the shell's radius.
         * Elsewhere a voxel is all in or all out.
         */
        MarchingCubes.Occupancy occupancy(VoxelGrid grid, byte[] distance, double step, double shellRadius)
        {
            return (z, out) ->
            {
                int base = z * grid.slice;
                Arrays.fill(out, 0);
                for (int i = all.nextSetBit(base); i >= 0 && i < base + grid.slice; i = all.nextSetBit(i + 1))
                {
                    out[i - base] = MarchingCubes.FULL;
                }
                for (int k = 0; k < grid.slice; k++)
                {
                    int q = distance[base + k] & 0xFF;
                    if (q == FAR)
                    {
                        continue;
                    }
                    int i = base + k;
                    double d = q * step;
                    if (thinShell.get(i))
                    {
                        out[k] = kept.get(i) ? MarchingCubes.FULL : ramp(shellRadius - d);
                    }
                    else if (!nearThin.get(i) && d < 1)
                    {
                        out[k] = ramp(inside.get(i) ? d : -d);
                    }
                }
            };
        }

        /** Occupancy at a signed distance (in voxels, positive in) from the surface. */
        private static int ramp(double in)
        {
            return (int) Math.round(Math.max(0, Math.min(1, 0.5 + in)) * MarchingCubes.FULL);
        }
    }

    /** The voxel grid laid over a model: voxel size, the radii every step uses, and where the grid sits. */
    static final class Plan
    {
        /** Game units to a voxel, and millimetres. */
        final double unit;
        final double voxelMm;

        /** Radii in voxels: half the wall, the shell around every triangle, the seal over gaps, a core's reach. */
        final double halfWall;
        final double shell;
        final double seal;
        final double cover;

        final int nx;
        final int ny;
        final int nz;

        /** Where voxel (0, 0, 0)'s center is, in game units. */
        final double ox;
        final double oy;
        final double oz;

        private Plan(double unit, double voxelMm, double halfWall, double seal, int pad, float[] box)
        {
            this.unit = unit;
            this.voxelMm = voxelMm;
            this.halfWall = halfWall;
            // A quarter voxel more than half the wall, so a thin part's two faces are at least a wall apart
            this.shell = halfWall + 0.25;
            this.seal = seal;
            // A thick part's corner is half a wall times root 3 from its core, give or take a voxel each way
            this.cover = Math.sqrt(3) * halfWall + 2;
            this.nx = cells(box[3] - box[0], unit, pad);
            this.ny = cells(box[4] - box[1], unit, pad);
            this.nz = cells(box[5] - box[2], unit, pad);
            this.ox = box[0] - pad * unit;
            this.oy = box[1] - pad * unit;
            this.oz = box[2] - pad * unit;
        }

        private static int cells(double extent, double unit, int pad)
        {
            return (int) Math.ceil(extent / unit) + 1 + 2 * pad;
        }

        /**
         * The plan for a model with these bounds at mmPerUnit: the voxel asked for, or the next coarser one that
         * keeps within the grid's limits and every radius within a measure's reach.
         */
        static Plan of(float[] box, double mmPerUnit, double voxelMm, double minWallMm)
        {
            double voxel = Math.max(voxelMm, MIN_VOXEL_MM);
            while (true)
            {
                double halfWall = Math.max(MIN_HALF_WALL, minWallMm / 2 / voxel);
                double seal = SEAL_MM / 2 / voxel;
                double shell = halfWall + 0.25;
                // Room for everything grown past the model, and for the smoothing, around it
                int pad = (int) Math.ceil(shell + Math.max(seal, halfWall) + halfWall) + 4;
                Plan plan = new Plan(voxel / mmPerUnit, voxel, halfWall, seal, pad, box);
                boolean reachable = shell + seal + halfWall <= VoxelGrid.MAX_REACH
                    && plan.cover + halfWall <= VoxelGrid.MAX_REACH;
                long voxels = (long) plan.nx * plan.ny * plan.nz;
                if (reachable && plan.nx <= MAX_AXIS && plan.ny <= MAX_AXIS && plan.nz <= MAX_AXIS && voxels <= MAX_VOXELS)
                {
                    return plan;
                }
                voxel *= 1.05;
            }
        }
    }

    /**
     * The model's triangles filed by the cells of 4 by 4 by 4 voxels their surfaces pass through, for finding the
     * one nearest a point.
     */
    private static final class Nearest
    {
        private static final int CELL = 4;

        /** Each triangle's corners in voxel coordinates, nine to a triangle. */
        private final double[] triangles;

        private final int cx;
        private final int cy;
        private final int cz;
        private final int[] lastFace;
        private int[] pairs = new int[1024];
        private int pairCount;
        private int[] start;
        private int[] faces;
        private final int[] seen;
        private int query;

        Nearest(double[] triangles, Plan plan)
        {
            this.triangles = triangles;
            cx = (plan.nx + CELL - 1) / CELL;
            cy = (plan.ny + CELL - 1) / CELL;
            cz = (plan.nz + CELL - 1) / CELL;
            lastFace = new int[cx * cy * cz];
            Arrays.fill(lastFace, -1);
            seen = new int[triangles.length / 9];
        }

        /** Files face f under the cell holding voxel (x, y, z). */
        void add(int f, int x, int y, int z)
        {
            int cell = x / CELL + cx * (y / CELL + cy * (z / CELL));
            if (lastFace[cell] == f)
            {
                return;
            }
            lastFace[cell] = f;
            if (pairCount + 2 > pairs.length)
            {
                pairs = Arrays.copyOf(pairs, pairs.length * 2);
            }
            pairs[pairCount++] = cell;
            pairs[pairCount++] = f;
        }

        /** Sorts the filed faces by cell. */
        void index()
        {
            start = new int[lastFace.length + 1];
            for (int i = 0; i < pairCount; i += 2)
            {
                start[pairs[i] + 1]++;
            }
            for (int i = 0; i < lastFace.length; i++)
            {
                start[i + 1] += start[i];
            }
            faces = new int[pairCount / 2];
            int[] fill = Arrays.copyOf(start, lastFace.length);
            for (int i = 0; i < pairCount; i += 2)
            {
                faces[fill[pairs[i]]++] = pairs[i + 1];
            }
            pairs = null;
        }

        /** The face nearest (x, y, z), in voxel coordinates, or -1 when there are none. */
        int find(double x, double y, double z)
        {
            int px = clamp((int) Math.floor(x / CELL), cx);
            int py = clamp((int) Math.floor(y / CELL), cy);
            int pz = clamp((int) Math.floor(z / CELL), cz);
            int q = ++query;
            double best = Double.MAX_VALUE;
            int bestFace = -1;
            int rings = Math.max(cx, Math.max(cy, cz));
            for (int r = 0; r <= rings; r++)
            {
                // Cells r rings out are at least r - 1 cells away
                if (bestFace >= 0 && (double) (r - 1) * CELL * ((r - 1) * CELL) > best)
                {
                    break;
                }
                for (int k = Math.max(0, pz - r); k <= Math.min(cz - 1, pz + r); k++)
                {
                    for (int j = Math.max(0, py - r); j <= Math.min(cy - 1, py + r); j++)
                    {
                        boolean edge = Math.abs(k - pz) == r || Math.abs(j - py) == r;
                        for (int i = Math.max(0, px - r); i <= Math.min(cx - 1, px + r); i++)
                        {
                            if (!edge && Math.abs(i - px) != r)
                            {
                                continue;
                            }
                            int cell = i + cx * (j + cy * k);
                            for (int at = start[cell]; at < start[cell + 1]; at++)
                            {
                                int f = faces[at];
                                if (seen[f] == q)
                                {
                                    continue;
                                }
                                seen[f] = q;
                                double d = distanceSquared(triangles, 9 * f, x, y, z);
                                if (d < best)
                                {
                                    best = d;
                                    bestFace = f;
                                }
                            }
                        }
                    }
                }
            }
            return bestFace;
        }

        private static int clamp(int value, int count)
        {
            return Math.max(0, Math.min(count - 1, value));
        }
    }
}
