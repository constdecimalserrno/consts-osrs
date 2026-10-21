package com.constsmodels;

/**
 * A captured model as plain triangles: what the viewer draws and the exporters write. Every part of consts-Models
 * passes these around.
 *
 * <p>Units are the game's (128 to a tile). Axes are east, north, up: x east, y north, z up, so a model stands on the
 * xy plane. Triangles wind counterclockwise seen from outside, as the game's faces do once turned into these axes.
 * Each triangle has one color, 0xRRGGBB. A mesh never changes once built.
 */
final class Mesh
{
    /** What it is, for the viewer's title and the file name: "NPC Goblin", "Area around Lumbridge". */
    final String name;

    final float[] x;
    final float[] y;
    final float[] z;

    /** Each triangle's three vertex indices. */
    final int[] a;
    final int[] b;
    final int[] c;

    /** Each triangle's color, 0xRRGGBB. */
    final int[] color;

    Mesh(String name, float[] x, float[] y, float[] z, int[] a, int[] b, int[] c, int[] color)
    {
        if (x.length != y.length || x.length != z.length)
        {
            throw new IllegalArgumentException("vertex arrays differ in length");
        }
        if (a.length != b.length || a.length != c.length || a.length != color.length)
        {
            throw new IllegalArgumentException("triangle arrays differ in length");
        }
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.a = a;
        this.b = b;
        this.c = c;
        this.color = color;
    }

    int vertexCount()
    {
        return x.length;
    }

    int faceCount()
    {
        return a.length;
    }

    boolean isEmpty()
    {
        return a.length == 0;
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} over the vertices the triangles use; all zeros for an empty mesh. */
    float[] bounds()
    {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean any = false;
        for (int[] corner : new int[][]{a, b, c})
        {
            for (int v : corner)
            {
                any = true;
                box[0] = Math.min(box[0], x[v]);
                box[1] = Math.min(box[1], y[v]);
                box[2] = Math.min(box[2], z[v]);
                box[3] = Math.max(box[3], x[v]);
                box[4] = Math.max(box[4], y[v]);
                box[5] = Math.max(box[5], z[v]);
            }
        }
        return any ? box : new float[6];
    }

    Mesh withName(String name)
    {
        return new Mesh(name, x, y, z, a, b, c, color);
    }
}
