package com.constsmodels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Closes the holes in a model's shells, so they print solid: a tree's leaf clumps are domes open underneath, a body
 * is open at the neck, a boot at the ankle. Once corners at the same spot are welded, an edge only one triangle has
 * runs along a hole's rim; the rims are followed round, and each is capped with triangles of its own, in the color
 * of the triangles along it and wound as they are. {@link SolidRemesh} then fills what a shell and its caps close
 * off.
 *
 * <p>Only a rim that lies near one plane is capped, as round the bottom of a dome: the outline of a sheet that bends,
 * like a cape round a back, would fill the space it bends round, so it's left as it is. A flat sheet's outline is
 * capped, but the cap lies on the sheet, so nothing changes. So is a rim of more than {@link #MAX_RIM} corners, more
 * like the edge of the ground than a hole.
 */
final class ShellCaps
{
    /** A rim is capped if its corners are no further from one plane than this share of its size. */
    static final double MAX_BOW = 0.2;

    /** A rim with more corners than this is left open. */
    static final int MAX_RIM = 256;

    private ShellCaps()
    {
    }

    /** A model with its holes capped, and which of its triangles each of the new ones stands for. */
    static final class Capped
    {
        /** The model's triangles, then the caps. */
        final Mesh mesh;
        /** For each triangle, the model's triangle it is or stands for: a cap's is one along its rim. */
        final int[] from;

        Capped(Mesh mesh, int[] from)
        {
            this.mesh = mesh;
            this.from = from;
        }
    }

    /** The model with every hole that lies near a plane capped. */
    static Capped cap(Mesh mesh)
    {
        int[] same = PrintPrep.welded(mesh);
        List<int[]> rims = rims(mesh, same);

        MeshBuilder out = new MeshBuilder(mesh.name);
        for (int v = 0; v < mesh.vertexCount(); v++)
        {
            out.addVertex(mesh.x[v], mesh.y[v], mesh.z[v]);
        }
        int[] from = new int[mesh.faceCount() + 64];
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            out.addTriangle(mesh.a[f], mesh.b[f], mesh.c[f], mesh.color[f]);
            from[f] = f;
        }
        for (int[] rim : rims)
        {
            // A rim is its corners, then for each edge from one to the next, the triangle that edge is on
            int n = rim.length / 2;
            int[] corners = Arrays.copyOf(rim, n);
            int[] faces = Arrays.copyOfRange(rim, n, 2 * n);
            List<int[]> triangles = capRim(mesh, corners);
            if (triangles == null)
            {
                continue;
            }
            int face = mostCommonColor(mesh, faces);
            int rgb = mesh.color[face];
            // Wound against the triangles along the rim, as a cap closing their shell is
            boolean turn = runsForward(mesh, same, faces[0], corners[0], corners[1]);
            int centre = -1;
            for (int[] t : triangles)
            {
                int[] at = new int[3];
                for (int k = 0; k < 3; k++)
                {
                    if (t[k] >= 0)
                    {
                        at[k] = t[k];
                    }
                    else
                    {
                        if (centre < 0)
                        {
                            double[] middle = middle(mesh, corners);
                            centre = out.addVertex((float) middle[0], (float) middle[1], (float) middle[2]);
                        }
                        at[k] = centre;
                    }
                }
                if (out.faceCount() == from.length)
                {
                    from = Arrays.copyOf(from, from.length * 2);
                }
                from[out.faceCount()] = face;
                out.addTriangle(at[0], turn ? at[2] : at[1], turn ? at[1] : at[2], rgb);
            }
        }
        Mesh capped = out.build();
        return new Capped(capped, Arrays.copyOf(from, capped.faceCount()));
    }

    /**
     * The rims of the mesh's holes, corners welded: each as its corners in order round it, then for each edge from
     * one corner to the next the model triangle it's on. An edge only one triangle has is on a rim; edges two or more
     * have aren't.
     */
    static List<int[]> rims(Mesh mesh, int[] same)
    {
        // Every edge, by its welded ends, smaller first, sorted: those there once are on a rim
        long[] keys = new long[3 * mesh.faceCount()];
        int n = 0;
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            int[] v = corners(mesh, same, f);
            for (int k = 0; k < 3 && v != null; k++)
            {
                keys[n++] = key(v[k], v[(k + 1) % 3]);
            }
        }
        keys = Arrays.copyOf(keys, n);
        Arrays.sort(keys);
        long[] once = new long[16];
        int rimEdges = 0;
        for (int i = 0; i < n; )
        {
            int j = i + 1;
            while (j < n && keys[j] == keys[i])
            {
                j++;
            }
            if (j == i + 1)
            {
                if (rimEdges == once.length)
                {
                    once = Arrays.copyOf(once, rimEdges * 2);
                }
                once[rimEdges++] = keys[i];
            }
            i = j;
        }
        keys = null;

        // The rim edges at each corner, with the triangle each is on
        Map<Integer, List<long[]>> at = new HashMap<>();
        for (int f = 0; f < mesh.faceCount() && rimEdges > 0; f++)
        {
            int[] v = corners(mesh, same, f);
            for (int k = 0; k < 3 && v != null; k++)
            {
                if (Arrays.binarySearch(once, 0, rimEdges, key(v[k], v[(k + 1) % 3])) >= 0)
                {
                    long[] edge = {v[k], v[(k + 1) % 3], f, 0};
                    at.computeIfAbsent(v[k], x -> new ArrayList<>()).add(edge);
                    at.computeIfAbsent(v[(k + 1) % 3], x -> new ArrayList<>()).add(edge);
                }
            }
        }

        // Round each rim from a corner, an unused edge at a time, until back at the start
        List<int[]> rims = new ArrayList<>();
        int[] corners = new int[16];
        int[] faces = new int[16];
        for (Map.Entry<Integer, List<long[]>> start : at.entrySet())
        {
            for (long[] first : start.getValue())
            {
                if (first[3] != 0)
                {
                    continue;
                }
                int origin = start.getKey();
                int corner = origin;
                long[] edge = first;
                int length = 0;
                boolean closed = false;
                while (edge != null)
                {
                    edge[3] = 1;
                    if (length == corners.length)
                    {
                        corners = Arrays.copyOf(corners, length * 2);
                        faces = Arrays.copyOf(faces, length * 2);
                    }
                    corners[length] = corner;
                    faces[length] = (int) edge[2];
                    length++;
                    corner = (int) (edge[0] == corner ? edge[1] : edge[0]);
                    if (corner == origin)
                    {
                        closed = true;
                        break;
                    }
                    edge = null;
                    for (long[] next : at.get(corner))
                    {
                        if (next[3] == 0)
                        {
                            edge = next;
                            break;
                        }
                    }
                }
                if (closed && length >= 3)
                {
                    int[] rim = new int[2 * length];
                    System.arraycopy(corners, 0, rim, 0, length);
                    System.arraycopy(faces, 0, rim, length, length);
                    rims.add(rim);
                }
            }
        }
        return rims;
    }

    /**
     * The triangles that cap a rim, by its corners, -1 for a corner added in the middle of it; null if the rim is
     * too big, or bows too far from a plane, to cap. A rim lying flat is cut into ears; one that isn't simple seen
     * flat is capped from its middle.
     */
    static List<int[]> capRim(Mesh mesh, int[] corners)
    {
        int n = corners.length;
        if (n > MAX_RIM)
        {
            return null;
        }
        double[] middle = middle(mesh, corners);

        // The plane it lies nearest: through its middle, square to its normal by Newell's method
        double nx = 0;
        double ny = 0;
        double nz = 0;
        for (int i = 0; i < n; i++)
        {
            int p = corners[i];
            int q = corners[(i + 1) % n];
            nx += (mesh.y[p] - mesh.y[q]) * (mesh.z[p] + mesh.z[q]);
            ny += (mesh.z[p] - mesh.z[q]) * (mesh.x[p] + mesh.x[q]);
            nz += (mesh.x[p] - mesh.x[q]) * (mesh.y[p] + mesh.y[q]);
        }
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(length > 0))
        {
            return null;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        double size = 0;
        double bow = 0;
        for (int corner : corners)
        {
            double dx = mesh.x[corner] - middle[0];
            double dy = mesh.y[corner] - middle[1];
            double dz = mesh.z[corner] - middle[2];
            size += Math.sqrt(dx * dx + dy * dy + dz * dz);
            bow = Math.max(bow, Math.abs(dx * nx + dy * ny + dz * nz));
        }
        size /= n;
        if (!(size > 0) || bow > MAX_BOW * size)
        {
            return null;
        }

        // Seen flat on the plane
        double[] u = Math.abs(nx) < 0.9 ? cross(nx, ny, nz, 1, 0, 0) : cross(nx, ny, nz, 0, 1, 0);
        double[] w = cross(nx, ny, nz, u[0], u[1], u[2]);
        double[] px = new double[n];
        double[] py = new double[n];
        for (int i = 0; i < n; i++)
        {
            double dx = mesh.x[corners[i]] - middle[0];
            double dy = mesh.y[corners[i]] - middle[1];
            double dz = mesh.z[corners[i]] - middle[2];
            px[i] = dx * u[0] + dy * u[1] + dz * u[2];
            py[i] = dx * w[0] + dy * w[1] + dz * w[2];
        }
        List<int[]> ears = ears(px, py);
        List<int[]> out = new ArrayList<>();
        if (ears != null)
        {
            for (int[] ear : ears)
            {
                out.add(new int[]{corners[ear[0]], corners[ear[1]], corners[ear[2]]});
            }
        }
        else
        {
            for (int i = 0; i < n; i++)
            {
                out.add(new int[]{corners[i], corners[(i + 1) % n], -1});
            }
        }
        return out;
    }

    /**
     * Cuts a simple polygon into triangles by clipping ears, each wound the way the polygon is; null if it isn't
     * simple enough to.
     */
    static List<int[]> ears(double[] px, double[] py)
    {
        int n = px.length;
        double area = 0;
        double scale = 0;
        for (int i = 0; i < n; i++)
        {
            int j = (i + 1) % n;
            area += px[i] * py[j] - px[j] * py[i];
            scale = Math.max(scale, Math.max(Math.abs(px[i]), Math.abs(py[i])));
        }
        if (!(Math.abs(area) > 1e-12 * scale * scale))
        {
            return null;
        }
        double turn = Math.signum(area);
        double tiny = 1e-12 * scale * scale;

        int[] left = new int[n];
        for (int i = 0; i < n; i++)
        {
            left[i] = i;
        }
        int count = n;
        List<int[]> out = new ArrayList<>();
        while (count > 3)
        {
            boolean clipped = false;
            for (int k = 0; k < count && !clipped; k++)
            {
                int a = left[(k + count - 1) % count];
                int b = left[k];
                int c = left[(k + 1) % count];
                if (turn * cross(px, py, a, b, c) <= tiny)
                {
                    continue;
                }
                boolean empty = true;
                for (int m = 0; m < count && empty; m++)
                {
                    int p = left[m];
                    if (p == a || p == b || p == c || (px[p] == px[b] && py[p] == py[b]))
                    {
                        continue;
                    }
                    empty = !(turn * cross(px, py, a, b, p) >= 0 && turn * cross(px, py, b, c, p) >= 0
                        && turn * cross(px, py, c, a, p) >= 0);
                }
                if (empty)
                {
                    out.add(new int[]{a, b, c});
                    System.arraycopy(left, k + 1, left, k, count - k - 1);
                    count--;
                    clipped = true;
                }
            }
            if (!clipped)
            {
                return null;
            }
        }
        out.add(new int[]{left[0], left[1], left[2]});
        return out;
    }

    private static double cross(double[] px, double[] py, int a, int b, int c)
    {
        return (px[b] - px[a]) * (py[c] - py[a]) - (py[b] - py[a]) * (px[c] - px[a]);
    }

    private static double[] cross(double ax, double ay, double az, double bx, double by, double bz)
    {
        double x = ay * bz - az * by;
        double y = az * bx - ax * bz;
        double z = ax * by - ay * bx;
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / length, y / length, z / length};
    }

    private static double[] middle(Mesh mesh, int[] corners)
    {
        double x = 0;
        double y = 0;
        double z = 0;
        for (int corner : corners)
        {
            x += mesh.x[corner];
            y += mesh.y[corner];
            z += mesh.z[corner];
        }
        return new double[]{x / corners.length, y / corners.length, z / corners.length};
    }

    /** One of the triangles along a rim in the color most of them have. */
    private static int mostCommonColor(Mesh mesh, int[] faces)
    {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = faces[0];
        int most = 0;
        for (int f : faces)
        {
            int count = counts.merge(mesh.color[f], 1, Integer::sum);
            if (count > most)
            {
                most = count;
                best = f;
            }
        }
        return best;
    }

    /** A triangle's welded corners, or null if two are the same or one isn't a number. */
    private static int[] corners(Mesh mesh, int[] same, int f)
    {
        int[] v = {same[mesh.a[f]], same[mesh.b[f]], same[mesh.c[f]]};
        return v[0] < 0 || v[1] < 0 || v[2] < 0 || v[0] == v[1] || v[1] == v[2] || v[0] == v[2] ? null : v;
    }

    /** Whether the triangle runs from welded corner u straight to v. */
    private static boolean runsForward(Mesh mesh, int[] same, int f, int u, int v)
    {
        int[] c = corners(mesh, same, f);
        for (int k = 0; k < 3 && c != null; k++)
        {
            if (c[k] == u && c[(k + 1) % 3] == v)
            {
                return true;
            }
        }
        return false;
    }

    private static long key(int u, int v)
    {
        return u < v ? (long) u << 32 | v : (long) v << 32 | u;
    }
}
