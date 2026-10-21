package com.constsmodels;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Makes a mesh ready for a slicer, for STL and 3MF. In order:
 * <ol>
 * <li>welds corners at the same spot, so the parts of a model join up into shells, and drops the triangles that leave
 * flat or with fewer than three corners;</li>
 * <li>drops doubled triangles: of several on the same corners one stays, wound the way most are, and a pair back to
 * back (a sheet with no thickness, or the wall between two touching boxes) goes;</li>
 * <li>winds each closed shell outward, by the sign of its volume. A shell with holes keeps the game's winding, which
 * faces out already, unless the mesh looks mirrored: most of its closed shells had to be turned over or, with none
 * closed, the open parts together hold clearly negative volume. Then the open parts are turned over too;</li>
 * <li>scales the print so its longest side is {@link PrintSettings#sizeMm}, in millimetres, centers it on x and y and
 * stands it on z = 0;</li>
 * <li>with {@link PrintSettings#base}, stands it on a solid box (about 3 mm thick and 3 mm wider each way than the
 * model), with the model sunk 0.4 mm into it so the two fuse into one print. The base takes the model's darkest
 * color.</li>
 * </ol>
 * Game models aren't all closed: open bottoms, parts that cross each other, edges that three triangles share. Those
 * stay as they are; slicers fill and join them as they slice.
 */
final class PrintPrep
{
    /** Corners closer than this on every axis, in game units, are welded into one. */
    static final float WELD_DISTANCE = 0.01f;
    /** A triangle flatter than this, in game units, is dropped. */
    static final double MIN_HEIGHT = 1e-3;
    static final float BASE_THICKNESS_MM = 3f;
    static final float BASE_MARGIN_MM = 3f;
    static final float BASE_SINK_MM = 0.4f;
    /**
     * With no closed shell to go by, the open parts are turned over when their volume is below minus this much of
     * their area times the longest side. A ball or box wound inside out comes to minus a sixth; a floor facing up,
     * under what stands on it, to a little below 0.
     */
    private static final double OPEN_FLIP_SHARE = 0.05;

    private static final int NONE = -1;
    private static final int SEVERAL = -2;

    /** A mesh ready to print: in millimetres, wound outward, on the plate, with its base (if any) last. */
    static final class Print
    {
        final Mesh mesh;
        /** How many of the mesh's triangles are the model's; any after them are the base. */
        final int modelFaces;
        /**
         * For each of the print's triangles, the model triangle it came from, so it can take that one's filament;
         * -1 for the base.
         */
        final int[] source;
        /**
         * How the model was placed: millimetres per game unit, and the model point that went to the middle of the
         * plate, its x and y, and its lowest z, which went to the base's top, lift above the plate.
         */
        final double scale;
        final double centerX;
        final double centerY;
        final double bottomZ;
        final double lift;

        Print(Mesh mesh, int modelFaces, int[] source)
        {
            this(mesh, modelFaces, source, 1, 0, 0, 0, 0);
        }

        Print(Mesh mesh, int modelFaces, int[] source, double scale, double centerX, double centerY, double bottomZ,
            double lift)
        {
            this.mesh = mesh;
            this.modelFaces = modelFaces;
            this.source = source;
            this.scale = scale;
            this.centerX = centerX;
            this.centerY = centerY;
            this.bottomZ = bottomZ;
            this.lift = lift;
        }

        /**
         * The print put back where the model was, in the game's units, base and all, in these colors (one per
         * triangle): so a view can switch between the two without the camera jumping.
         */
        Mesh inModelUnits(int[] colors)
        {
            int n = mesh.vertexCount();
            float[] x = new float[n];
            float[] y = new float[n];
            float[] z = new float[n];
            for (int v = 0; v < n; v++)
            {
                x[v] = (float) (mesh.x[v] / scale + centerX);
                y[v] = (float) (mesh.y[v] / scale + centerY);
                z[v] = (float) ((mesh.z[v] - lift) / scale + bottomZ);
            }
            return new Mesh(mesh.name, x, y, z, mesh.a, mesh.b, mesh.c, colors);
        }
    }

    private PrintPrep()
    {
    }

    static Print prepare(Mesh mesh, PrintSettings settings)
    {
        if (settings.sizeMm <= 0)
        {
            throw new IllegalArgumentException("the print size must be more than 0 mm");
        }
        // Each step carries a triangle's color along with it untouched, so they run with each triangle's number in
        // place of its color: that's how the print knows which model triangle each of its own came from
        int[] number = new int[mesh.faceCount()];
        for (int f = 0; f < number.length; f++)
        {
            number[f] = f;
        }
        Mesh numbered = new Mesh(mesh.name, mesh.x, mesh.y, mesh.z, mesh.a, mesh.b, mesh.c, number);
        return place(orient(dropDoubles(weld(numbered))), settings, mesh.color);
    }

    /**
     * A mesh from {@link SolidRemesh}, made ready to print: scaled, placed and given its base like any other, but
     * without the welding and turning, which a closed solid doesn't need and which could merge its corners when a
     * small model prints large. The source of each print triangle is the solid's triangle.
     */
    static Print prepareSolid(Mesh solid, PrintSettings settings)
    {
        if (settings.sizeMm <= 0)
        {
            throw new IllegalArgumentException("the print size must be more than 0 mm");
        }
        int[] number = new int[solid.faceCount()];
        for (int f = 0; f < number.length; f++)
        {
            number[f] = f;
        }
        Mesh numbered = new Mesh(solid.name, solid.x, solid.y, solid.z, solid.a, solid.b, solid.c, number);
        return place(numbered, settings, solid.color);
    }

    /**
     * Welds corners closer than {@link #WELD_DISTANCE} into one and drops the triangles that leave with two corners
     * the same, with a corner that isn't a number, or flatter than {@link #MIN_HEIGHT}. Only the corners left in use
     * are kept.
     */
    static Mesh weld(Mesh mesh)
    {
        int vertices = mesh.vertexCount();
        boolean[] used = new boolean[vertices];
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            used[mesh.a[f]] = true;
            used[mesh.b[f]] = true;
            used[mesh.c[f]] = true;
        }

        // Each corner's representative: the first corner found within the weld distance, found through a grid of
        // cells as wide as that distance, so a match is always in the corner's own cell or a neighbouring one
        int[] same = new int[vertices];
        int[] nextInCell = new int[vertices];
        Arrays.fill(same, NONE);
        TripleIndex cells = new TripleIndex(Math.max(16, vertices));
        for (int v = 0; v < vertices; v++)
        {
            float vx = mesh.x[v];
            float vy = mesh.y[v];
            float vz = mesh.z[v];
            if (!used[v] || !Float.isFinite(vx) || !Float.isFinite(vy) || !Float.isFinite(vz))
            {
                continue;
            }
            int cx = cell(vx);
            int cy = cell(vy);
            int cz = cell(vz);
            int found = NONE;
            search:
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dy = -1; dy <= 1; dy++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        for (int r = cells.get(cx + dx, cy + dy, cz + dz); r != NONE; r = nextInCell[r])
                        {
                            if (Math.abs(mesh.x[r] - vx) <= WELD_DISTANCE
                                && Math.abs(mesh.y[r] - vy) <= WELD_DISTANCE
                                && Math.abs(mesh.z[r] - vz) <= WELD_DISTANCE)
                            {
                                found = r;
                                break search;
                            }
                        }
                    }
                }
            }
            if (found == NONE)
            {
                same[v] = v;
                nextInCell[v] = cells.get(cx, cy, cz);
                cells.put(cx, cy, cz, v);
            }
            else
            {
                same[v] = found;
            }
        }

        int faces = mesh.faceCount();
        int[] a = new int[faces];
        int[] b = new int[faces];
        int[] c = new int[faces];
        int[] color = new int[faces];
        int kept = 0;
        for (int f = 0; f < faces; f++)
        {
            int va = same[mesh.a[f]];
            int vb = same[mesh.b[f]];
            int vc = same[mesh.c[f]];
            if (va == NONE || vb == NONE || vc == NONE || va == vb || vb == vc || va == vc
                || !(height(mesh, va, vb, vc) > MIN_HEIGHT))
            {
                continue;
            }
            a[kept] = va;
            b[kept] = vb;
            c[kept] = vc;
            color[kept] = mesh.color[f];
            kept++;
        }
        return compact(mesh.name, mesh.x, mesh.y, mesh.z, a, b, c, color, kept);
    }

    /**
     * Drops doubled triangles, on the same three corners: one stays, wound the way most of them are, or none if as many
     * are wound each way. A pair back to back is a sheet with no thickness, which can't print, or the wall where two
     * boxes touch, which shouldn't.
     */
    static Mesh dropDoubles(Mesh mesh)
    {
        int faces = mesh.faceCount();
        Corners corners = new Corners(mesh);
        boolean[] seen = new boolean[faces];
        boolean[] keep = new boolean[faces];
        int kept = 0;
        for (int f = 0; f < faces; f++)
        {
            if (seen[f])
            {
                continue;
            }
            int va = mesh.a[f];
            int vb = mesh.b[f];
            int vc = mesh.c[f];
            int surplus = 0;
            for (int i = corners.start[va]; i < corners.start[va + 1]; i++)
            {
                int g = corners.faces[i];
                if (has(mesh, g, vb) && has(mesh, g, vc))
                {
                    seen[g] = true;
                    surplus += runsForward(mesh, g, va, vb) ? 1 : -1;
                }
            }
            // One stays, wound the way most are; the faces at a corner are listed in order, so it's the first of them
            for (int i = corners.start[va]; i < corners.start[va + 1] && surplus != 0; i++)
            {
                int g = corners.faces[i];
                if (has(mesh, g, vb) && has(mesh, g, vc) && runsForward(mesh, g, va, vb) == (surplus > 0))
                {
                    keep[g] = true;
                    kept++;
                    surplus = 0;
                }
            }
        }
        if (kept == faces)
        {
            return mesh;
        }

        int[] a = new int[kept];
        int[] b = new int[kept];
        int[] c = new int[kept];
        int[] color = new int[kept];
        int n = 0;
        for (int f = 0; f < faces; f++)
        {
            if (keep[f])
            {
                a[n] = mesh.a[f];
                b[n] = mesh.b[f];
                c[n] = mesh.c[f];
                color[n] = mesh.color[f];
                n++;
            }
        }
        return compact(mesh.name, mesh.x, mesh.y, mesh.z, a, b, c, color, kept);
    }

    /**
     * Winds every closed shell (triangles joined edge to edge, each edge shared by exactly two) outward: consistently
     * from one triangle to the next, then all turned over if the shell's volume comes out negative. Shells with holes,
     * or that can't be wound consistently, keep the game's winding unless the mesh looks mirrored: more of the closed
     * shells' area had to be turned than not or, with no closed shells, the open ones together hold clearly negative
     * volume about the middle of the mesh. Then they're all turned over.
     */
    static Mesh orient(Mesh mesh)
    {
        int faces = mesh.faceCount();
        Corners corners = new Corners(mesh);

        // Each edge's neighbour across it, and whether that neighbour runs along the edge the same way
        int[] across = new int[faces * 3];
        boolean[] sameWay = new boolean[faces * 3];
        for (int f = 0; f < faces; f++)
        {
            for (int e = 0; e < 3; e++)
            {
                int u = corner(mesh, f, e);
                int v = corner(mesh, f, (e + 1) % 3);
                int n = across(mesh, corners, f, u, v);
                across[f * 3 + e] = n;
                sameWay[f * 3 + e] = n >= 0 && runsForward(mesh, n, u, v);
            }
        }

        float[] box = mesh.bounds();
        double ox = (box[0] + box[3]) / 2.0;
        double oy = (box[1] + box[4]) / 2.0;
        double oz = (box[2] + box[5]) / 2.0;

        boolean[] flip = new boolean[faces];
        boolean[] open = new boolean[faces];
        boolean[] reached = new boolean[faces];
        int[] queue = new int[faces];
        int tail = 0;
        double openVolume = 0;
        double openArea = 0;
        double turnedArea = 0;
        double keptArea = 0;
        for (int seed = 0; seed < faces; seed++)
        {
            if (reached[seed])
            {
                continue;
            }
            int first = tail;
            int head = tail;
            queue[tail++] = seed;
            reached[seed] = true;
            boolean closed = true;
            boolean consistent = true;
            while (head < tail)
            {
                int f = queue[head++];
                for (int e = 0; e < 3; e++)
                {
                    int n = across[f * 3 + e];
                    if (n < 0)
                    {
                        closed = false;
                        continue;
                    }
                    // Neighbours wound consistently run along their shared edge in opposite directions
                    boolean want = flip[f] ^ sameWay[f * 3 + e];
                    if (!reached[n])
                    {
                        reached[n] = true;
                        flip[n] = want;
                        queue[tail++] = n;
                    }
                    else if (flip[n] != want)
                    {
                        consistent = false;
                    }
                }
            }

            if (closed && consistent)
            {
                double volume = 0;
                for (int i = first; i < tail; i++)
                {
                    int f = queue[i];
                    volume += flip[f] ? -volume(mesh, f, ox, oy, oz) : volume(mesh, f, ox, oy, oz);
                }
                for (int i = first; i < tail; i++)
                {
                    int f = queue[i];
                    flip[f] ^= volume < 0;
                    if (flip[f])
                    {
                        turnedArea += area(mesh, f);
                    }
                    else
                    {
                        keptArea += area(mesh, f);
                    }
                }
            }
            else
            {
                for (int i = first; i < tail; i++)
                {
                    int f = queue[i];
                    flip[f] = false;
                    open[f] = true;
                    openVolume += volume(mesh, f, ox, oy, oz);
                    openArea += area(mesh, f);
                }
            }
        }

        double longest = Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2]));
        boolean mirrored = turnedArea + keptArea > 0
            ? turnedArea > keptArea
            : openVolume < -OPEN_FLIP_SHARE * openArea * longest;
        if (mirrored)
        {
            for (int f = 0; f < faces; f++)
            {
                flip[f] |= open[f];
            }
        }

        int[] b = mesh.b.clone();
        int[] c = mesh.c.clone();
        boolean any = false;
        for (int f = 0; f < faces; f++)
        {
            if (flip[f])
            {
                b[f] = mesh.c[f];
                c[f] = mesh.b[f];
                any = true;
            }
        }
        return any ? new Mesh(mesh.name, mesh.x, mesh.y, mesh.z, mesh.a, b, c, mesh.color) : mesh;
    }

    /**
     * Scales the mesh into millimetres so the whole print's longest side is {@link PrintSettings#sizeMm}, centers it
     * on x and y and stands it on z = 0, on a base if asked for. Triangles too small to have a direction once in
     * millimetres are dropped. The mesh holds each triangle's number in the model in place of its color; colors are
     * the model's, by those numbers. The base takes the darkest color the print has.
     */
    private static Print place(Mesh mesh, PrintSettings settings, int[] colors)
    {
        float[] box = mesh.bounds();
        double width = box[3] - box[0];
        double depth = box[4] - box[1];
        double height = box[5] - box[2];
        double size = settings.sizeMm;

        double thickness = 0;
        double margin = 0;
        double sink = 0;
        double scale;
        if (settings.base)
        {
            thickness = Math.min(BASE_THICKNESS_MM, size / 10);
            margin = Math.min(BASE_MARGIN_MM, size / 20);
            sink = Math.min(BASE_SINK_MM, thickness / 2);
            scale = Math.min(fit(size - 2 * margin, width),
                Math.min(fit(size - 2 * margin, depth), fit(size - thickness + sink, height)));
        }
        else
        {
            scale = fit(size, Math.max(width, Math.max(depth, height)));
        }
        if (Double.isInfinite(scale))
        {
            // Nothing to measure; only an empty mesh gets here
            scale = 1;
        }

        double cx = (box[0] + box[3]) / 2.0;
        double cy = (box[1] + box[4]) / 2.0;
        double lift = thickness - sink;
        int vertices = mesh.vertexCount();
        float[] x = new float[vertices + 8];
        float[] y = new float[vertices + 8];
        float[] z = new float[vertices + 8];
        for (int v = 0; v < vertices; v++)
        {
            x[v] = (float) ((mesh.x[v] - cx) * scale);
            y[v] = (float) ((mesh.y[v] - cy) * scale);
            z[v] = (float) ((mesh.z[v] - box[2]) * scale + lift);
        }

        int faces = mesh.faceCount();
        int[] a = new int[faces + 12];
        int[] b = new int[faces + 12];
        int[] c = new int[faces + 12];
        int[] color = new int[faces + 12];
        int[] source = new int[faces + 12];
        int kept = 0;
        Set<Integer> used = new HashSet<>();
        for (int f = 0; f < faces; f++)
        {
            int va = mesh.a[f];
            int vb = mesh.b[f];
            int vc = mesh.c[f];
            if (!hasDirection(x, y, z, va, vb, vc))
            {
                continue;
            }
            int rgb = colors[mesh.color[f]];
            a[kept] = va;
            b[kept] = vb;
            c[kept] = vc;
            color[kept] = rgb;
            source[kept] = mesh.color[f];
            kept++;
            used.add(rgb);
        }
        int modelFaces = kept;

        if (settings.base && modelFaces > 0)
        {
            int[] palette = used.stream().mapToInt(Integer::intValue).sorted().toArray();
            int darkest = palette[darkest(palette)];
            float x0 = (float) (-width * scale / 2 - margin);
            float x1 = (float) (width * scale / 2 + margin);
            float y0 = (float) (-depth * scale / 2 - margin);
            float y1 = (float) (depth * scale / 2 + margin);
            float top = (float) thickness;
            float[][] box8 = {
                {x0, y0, 0}, {x1, y0, 0}, {x1, y1, 0}, {x0, y1, 0},
                {x0, y0, top}, {x1, y0, top}, {x1, y1, top}, {x0, y1, top},
            };
            for (int i = 0; i < 8; i++)
            {
                x[vertices + i] = box8[i][0];
                y[vertices + i] = box8[i][1];
                z[vertices + i] = box8[i][2];
            }
            // Each side as two triangles, counterclockwise seen from outside
            int[][] sides = {
                {0, 2, 1}, {0, 3, 2},
                {4, 5, 6}, {4, 6, 7},
                {0, 1, 5}, {0, 5, 4},
                {1, 2, 6}, {1, 6, 5},
                {2, 3, 7}, {2, 7, 6},
                {3, 0, 4}, {3, 4, 7},
            };
            for (int[] side : sides)
            {
                a[kept] = vertices + side[0];
                b[kept] = vertices + side[1];
                c[kept] = vertices + side[2];
                color[kept] = darkest;
                source[kept] = -1;
                kept++;
            }
        }
        Mesh placed = compact(mesh.name, x, y, z, a, b, c, color, kept);
        return new Print(placed, modelFaces, Arrays.copyOf(source, kept), scale, cx, cy, box[2], lift);
    }

    /** Which of the colors looks darkest: the filament a base prints in. */
    static int darkest(int[] palette)
    {
        int best = 0;
        for (int i = 1; i < palette.length; i++)
        {
            if (luma(palette[i]) < luma(palette[best]))
            {
                best = i;
            }
        }
        return best;
    }

    /** The scale that makes a length fit in the room, or infinity for a length of 0. */
    private static double fit(double room, double length)
    {
        return length > 0 ? room / length : Double.POSITIVE_INFINITY;
    }

    private static int cell(float value)
    {
        return (int) Math.floor(value / WELD_DISTANCE);
    }

    /** A triangle's height over its longest side, in the mesh's units. */
    private static double height(Mesh mesh, int va, int vb, int vc)
    {
        double ux = mesh.x[vb] - mesh.x[va];
        double uy = mesh.y[vb] - mesh.y[va];
        double uz = mesh.z[vb] - mesh.z[va];
        double wx = mesh.x[vc] - mesh.x[va];
        double wy = mesh.y[vc] - mesh.y[va];
        double wz = mesh.z[vc] - mesh.z[va];
        double nx = uy * wz - uz * wy;
        double ny = uz * wx - ux * wz;
        double nz = ux * wy - uy * wx;
        double twiceArea = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double sx = wx - ux;
        double sy = wy - uy;
        double sz = wz - uz;
        double longest = Math.sqrt(Math.max(ux * ux + uy * uy + uz * uz,
            Math.max(wx * wx + wy * wy + wz * wz, sx * sx + sy * sy + sz * sz)));
        return twiceArea / longest;
    }

    private static boolean hasDirection(float[] x, float[] y, float[] z, int va, int vb, int vc)
    {
        double[] n = StlWriter.normal(x[va], y[va], z[va], x[vb], y[vb], z[vb], x[vc], y[vc], z[vc]);
        return n[0] != 0 || n[1] != 0 || n[2] != 0;
    }

    /** The signed volume of the tetrahedron from a point to the triangle: positive when it faces away from the point. */
    private static double volume(Mesh mesh, int f, double ox, double oy, double oz)
    {
        double ax = mesh.x[mesh.a[f]] - ox;
        double ay = mesh.y[mesh.a[f]] - oy;
        double az = mesh.z[mesh.a[f]] - oz;
        double bx = mesh.x[mesh.b[f]] - ox;
        double by = mesh.y[mesh.b[f]] - oy;
        double bz = mesh.z[mesh.b[f]] - oz;
        double cx = mesh.x[mesh.c[f]] - ox;
        double cy = mesh.y[mesh.c[f]] - oy;
        double cz = mesh.z[mesh.c[f]] - oz;
        return (ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)) / 6;
    }

    static double area(Mesh mesh, int f)
    {
        int va = mesh.a[f];
        int vb = mesh.b[f];
        int vc = mesh.c[f];
        double ux = mesh.x[vb] - mesh.x[va];
        double uy = mesh.y[vb] - mesh.y[va];
        double uz = mesh.z[vb] - mesh.z[va];
        double wx = mesh.x[vc] - mesh.x[va];
        double wy = mesh.y[vc] - mesh.y[va];
        double wz = mesh.z[vc] - mesh.z[va];
        double nx = uy * wz - uz * wy;
        double ny = uz * wx - ux * wz;
        double nz = ux * wy - uy * wx;
        return Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
    }

    /** How light a color looks: its OKLab lightness. */
    private static double luma(int rgb)
    {
        return PrintColors.toOklab(rgb)[0];
    }

    private static int corner(Mesh mesh, int f, int which)
    {
        return which == 0 ? mesh.a[f] : which == 1 ? mesh.b[f] : mesh.c[f];
    }

    private static boolean has(Mesh mesh, int f, int v)
    {
        return mesh.a[f] == v || mesh.b[f] == v || mesh.c[f] == v;
    }

    /** Whether the triangle runs from u straight to v, going round a, b, c. */
    private static boolean runsForward(Mesh mesh, int f, int u, int v)
    {
        return (mesh.a[f] == u && mesh.b[f] == v) || (mesh.b[f] == u && mesh.c[f] == v)
            || (mesh.c[f] == u && mesh.a[f] == v);
    }

    /** The one other triangle on the edge from u to v, {@link #NONE} if there's none, {@link #SEVERAL} if more. */
    private static int across(Mesh mesh, Corners corners, int f, int u, int v)
    {
        int found = NONE;
        for (int i = corners.start[u]; i < corners.start[u + 1]; i++)
        {
            int g = corners.faces[i];
            if (g != f && has(mesh, g, v))
            {
                if (found != NONE)
                {
                    return SEVERAL;
                }
                found = g;
            }
        }
        return found;
    }

    /** A mesh of the first n triangles, with only the corners they use, renumbered in order of use. */
    private static Mesh compact(String name, float[] x, float[] y, float[] z, int[] a, int[] b, int[] c, int[] color,
        int n)
    {
        int[] index = new int[x.length];
        Arrays.fill(index, NONE);
        int vertices = 0;
        int[][] corners = {a, b, c};
        for (int f = 0; f < n; f++)
        {
            for (int[] corner : corners)
            {
                if (index[corner[f]] == NONE)
                {
                    index[corner[f]] = vertices++;
                }
            }
        }
        float[] nx = new float[vertices];
        float[] ny = new float[vertices];
        float[] nz = new float[vertices];
        for (int v = 0; v < x.length; v++)
        {
            if (index[v] != NONE)
            {
                nx[index[v]] = x[v];
                ny[index[v]] = y[v];
                nz[index[v]] = z[v];
            }
        }
        int[] na = new int[n];
        int[] nb = new int[n];
        int[] nc = new int[n];
        for (int f = 0; f < n; f++)
        {
            na[f] = index[a[f]];
            nb[f] = index[b[f]];
            nc[f] = index[c[f]];
        }
        return new Mesh(name, nx, ny, nz, na, nb, nc, Arrays.copyOf(color, n));
    }

    /** The triangles at each corner, in order, for finding a triangle's neighbours. */
    private static final class Corners
    {
        final int[] start;
        final int[] faces;

        Corners(Mesh mesh)
        {
            int vertices = mesh.vertexCount();
            start = new int[vertices + 1];
            int[][] corners = {mesh.a, mesh.b, mesh.c};
            for (int[] corner : corners)
            {
                for (int v : corner)
                {
                    start[v + 1]++;
                }
            }
            for (int v = 0; v < vertices; v++)
            {
                start[v + 1] += start[v];
            }
            faces = new int[mesh.faceCount() * 3];
            int[] fill = Arrays.copyOf(start, vertices);
            for (int f = 0; f < mesh.faceCount(); f++)
            {
                faces[fill[mesh.a[f]]++] = f;
                faces[fill[mesh.b[f]]++] = f;
                faces[fill[mesh.c[f]]++] = f;
            }
        }
    }
}
