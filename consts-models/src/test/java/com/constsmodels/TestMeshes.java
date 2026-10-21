package com.constsmodels;

import java.util.HashMap;
import java.util.Map;

/** Shapes and checks for the exporter tests. */
final class TestMeshes
{
    static final int RED = 0xC02020;
    static final int GREEN = 0x20A040;
    static final int BLUE = 0x2030C0;
    static final int BLACK = 0x101010;

    private TestMeshes()
    {
    }

    /** A quad as two triangles over four corners of its own, wound p0, p1, p2, p3; the diagonal runs p0 to p2. */
    static void quad(MeshBuilder b, float[] p0, float[] p1, float[] p2, float[] p3, int rgb)
    {
        int v0 = b.addVertex(p0[0], p0[1], p0[2]);
        int v1 = b.addVertex(p1[0], p1[1], p1[2]);
        int v2 = b.addVertex(p2[0], p2[1], p2[2]);
        int v3 = b.addVertex(p3[0], p3[1], p3[2]);
        b.addTriangle(v0, v1, v2, rgb);
        b.addTriangle(v0, v2, v3, rgb);
    }

    /**
     * A box, wound outward, each side two triangles on corners of its own, as game models often come: it only closes
     * up once welded. Sides in order: bottom, top, front (south), back (north), left (west), right (east), each in the
     * next of the colors, round again if there are fewer.
     */
    static void box(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, boolean bottom,
        int... colors)
    {
        float[][][] sides = {
            {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}},
            {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
            {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
            {{x1, y1, z0}, {x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}},
            {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}},
            {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}},
        };
        for (int s = bottom ? 0 : 1; s < sides.length; s++)
        {
            quad(b, sides[s][0], sides[s][1], sides[s][2], sides[s][3], colors[s % colors.length]);
        }
    }

    static Mesh box(float x0, float y0, float z0, float x1, float y1, float z1, int... colors)
    {
        MeshBuilder b = new MeshBuilder("Box");
        box(b, x0, y0, z0, x1, y1, z1, true, colors);
        return b.build();
    }

    /** The mesh with every triangle (or every other one) wound the other way. */
    static Mesh turned(Mesh mesh, boolean everyOther)
    {
        int[] b = mesh.b.clone();
        int[] c = mesh.c.clone();
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            if (!everyOther || f % 2 == 0)
            {
                b[f] = mesh.c[f];
                c[f] = mesh.b[f];
            }
        }
        return new Mesh(mesh.name, mesh.x, mesh.y, mesh.z, mesh.a, b, c, mesh.color);
    }

    /** Whether every edge of triangles from to to - 1 is shared by exactly two of them, running opposite ways. */
    static boolean closed(Mesh m, int from, int to)
    {
        Map<Long, Integer> edges = new HashMap<>();
        for (int f = from; f < to; f++)
        {
            int[] corners = {m.a[f], m.b[f], m.c[f]};
            for (int e = 0; e < 3; e++)
            {
                edges.merge(edge(corners[e], corners[(e + 1) % 3]), 1, Integer::sum);
            }
        }
        for (Map.Entry<Long, Integer> entry : edges.entrySet())
        {
            long key = entry.getKey();
            int u = (int) (key >>> 32);
            int v = (int) key;
            if (entry.getValue() != 1 || edges.getOrDefault(edge(v, u), 0) != 1)
            {
                return false;
            }
        }
        return true;
    }

    static boolean closed(Mesh m)
    {
        return closed(m, 0, m.faceCount());
    }

    private static long edge(int u, int v)
    {
        return ((long) u << 32) | (v & 0xFFFFFFFFL);
    }

    /** The volume the mesh encloses: positive when it's wound outward. */
    static double volume(Mesh m)
    {
        double total = 0;
        for (int f = 0; f < m.faceCount(); f++)
        {
            int a = m.a[f];
            int b = m.b[f];
            int c = m.c[f];
            total += m.x[a] * ((double) m.y[b] * m.z[c] - (double) m.z[b] * m.y[c])
                - m.y[a] * ((double) m.x[b] * m.z[c] - (double) m.z[b] * m.x[c])
                + m.z[a] * ((double) m.x[b] * m.y[c] - (double) m.y[b] * m.x[c]);
        }
        return total / 6;
    }

    /** {width, depth, height} of the mesh. */
    static float[] size(Mesh m)
    {
        float[] box = m.bounds();
        return new float[]{box[3] - box[0], box[4] - box[1], box[5] - box[2]};
    }

    static float longest(float[] size)
    {
        return Math.max(size[0], Math.max(size[1], size[2]));
    }
}
