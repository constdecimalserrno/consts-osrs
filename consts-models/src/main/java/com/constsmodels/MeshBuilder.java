package com.constsmodels;

import java.util.Arrays;

/**
 * Builds a {@link Mesh} a vertex and a triangle at a time, or a whole mesh at a time, turned and moved into place: how
 * an area capture puts every object, actor and tile into one scene.
 */
final class MeshBuilder
{
    /** The game's angles: 2048 to a full turn. */
    static final int FULL_TURN = 2048;

    private final String name;
    private float[] x = new float[256];
    private float[] y = new float[256];
    private float[] z = new float[256];
    private int vertices;
    private int[] a = new int[256];
    private int[] b = new int[256];
    private int[] c = new int[256];
    private int[] color = new int[256];
    private int faces;

    MeshBuilder(String name)
    {
        this.name = name;
    }

    int addVertex(float vx, float vy, float vz)
    {
        if (vertices == x.length)
        {
            x = Arrays.copyOf(x, vertices * 2);
            y = Arrays.copyOf(y, vertices * 2);
            z = Arrays.copyOf(z, vertices * 2);
        }
        x[vertices] = vx;
        y[vertices] = vy;
        z[vertices] = vz;
        return vertices++;
    }

    /** A triangle over vertices already added, wound counterclockwise seen from outside. */
    void addTriangle(int va, int vb, int vc, int rgb)
    {
        if (faces == a.length)
        {
            a = Arrays.copyOf(a, faces * 2);
            b = Arrays.copyOf(b, faces * 2);
            c = Arrays.copyOf(c, faces * 2);
            color = Arrays.copyOf(color, faces * 2);
        }
        a[faces] = va;
        b[faces] = vb;
        c[faces] = vc;
        color[faces] = rgb & 0xFFFFFF;
        faces++;
    }

    /**
     * Adds a whole mesh, turned about the up axis by a game orientation (0 to 2047, as on objects and actors) and
     * then moved by (dx, dy, dz). The turn is the game's own: a model's (x, north) becomes
     * (x cos + north sin, north cos - x sin), with the angle at 2048 to a full turn.
     */
    MeshBuilder add(Mesh mesh, int orientation, float dx, float dy, float dz)
    {
        double angle = (orientation & (FULL_TURN - 1)) * 2 * Math.PI / FULL_TURN;
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        int first = vertices;
        for (int v = 0; v < mesh.vertexCount(); v++)
        {
            float mx = mesh.x[v];
            float my = mesh.y[v];
            addVertex(mx * cos + my * sin + dx, my * cos - mx * sin + dy, mesh.z[v] + dz);
        }
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            addTriangle(first + mesh.a[f], first + mesh.b[f], first + mesh.c[f], mesh.color[f]);
        }
        return this;
    }

    int faceCount()
    {
        return faces;
    }

    Mesh build()
    {
        return new Mesh(name,
            Arrays.copyOf(x, vertices), Arrays.copyOf(y, vertices), Arrays.copyOf(z, vertices),
            Arrays.copyOf(a, faces), Arrays.copyOf(b, faces), Arrays.copyOf(c, faces), Arrays.copyOf(color, faces));
    }
}
