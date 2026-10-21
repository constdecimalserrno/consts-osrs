package com.constsmodels;

import java.util.Arrays;

/**
 * A model's pieces: the parts of it that don't touch, each the triangles joined corner to corner once corners at the
 * same spot are welded. The viewer picks and takes out whole pieces, such as a flat shadow under an object, and a 3MF
 * saves each as a part of its own.
 */
final class Pieces
{
    /** Each triangle's piece, numbered from 0 in the order of each piece's first triangle. */
    final int[] piece;
    /** How many pieces. */
    final int count;

    private Pieces(int[] piece, int count)
    {
        this.piece = piece;
        this.count = count;
    }

    /** The pieces of a mesh, its corners welded first. */
    static Pieces of(Mesh mesh)
    {
        return joined(mesh, PrintPrep.welded(mesh));
    }

    /** The pieces of a mesh whose triangles already share their corners, such as a print's. */
    static Pieces ofCorners(Mesh mesh)
    {
        int[] same = new int[mesh.vertexCount()];
        for (int v = 0; v < same.length; v++)
        {
            same[v] = v;
        }
        return joined(mesh, same);
    }

    /** Triangles joined through the corners they share, each corner standing for the corner it's welded to. */
    private static Pieces joined(Mesh mesh, int[] same)
    {
        int[] parent = new int[mesh.vertexCount()];
        for (int v = 0; v < parent.length; v++)
        {
            parent[v] = v;
        }
        int faces = mesh.faceCount();
        for (int f = 0; f < faces; f++)
        {
            int a = corner(same, mesh.a[f]);
            union(parent, a, corner(same, mesh.b[f]));
            union(parent, a, corner(same, mesh.c[f]));
        }

        int[] number = new int[parent.length];
        Arrays.fill(number, -1);
        int[] piece = new int[faces];
        int count = 0;
        for (int f = 0; f < faces; f++)
        {
            int root = find(parent, corner(same, mesh.a[f]));
            if (number[root] < 0)
            {
                number[root] = count++;
            }
            piece[f] = number[root];
        }
        return new Pieces(piece, count);
    }

    /** How many triangles each piece has. */
    int[] sizes()
    {
        int[] sizes = new int[count];
        for (int p : piece)
        {
            sizes[p]++;
        }
        return sizes;
    }

    /**
     * The numbers of the mesh's triangles on pieces kept, in order: what's left of the mesh with the other pieces taken
     * out.
     */
    int[] kept(boolean[] removed)
    {
        int n = 0;
        for (int p : piece)
        {
            n += removed[p] ? 0 : 1;
        }
        int[] kept = new int[n];
        n = 0;
        for (int f = 0; f < piece.length; f++)
        {
            if (!removed[piece[f]])
            {
                kept[n++] = f;
            }
        }
        return kept;
    }

    /** The mesh of only these of its triangles, by number, with only the corners they use. */
    static Mesh only(Mesh mesh, int[] faces)
    {
        int[] index = new int[mesh.vertexCount()];
        Arrays.fill(index, -1);
        int vertices = 0;
        int[] a = new int[faces.length];
        int[] b = new int[faces.length];
        int[] c = new int[faces.length];
        int[] color = new int[faces.length];
        for (int i = 0; i < faces.length; i++)
        {
            int f = faces[i];
            for (int v : new int[]{mesh.a[f], mesh.b[f], mesh.c[f]})
            {
                if (index[v] < 0)
                {
                    index[v] = vertices++;
                }
            }
            a[i] = index[mesh.a[f]];
            b[i] = index[mesh.b[f]];
            c[i] = index[mesh.c[f]];
            color[i] = mesh.color[f];
        }
        float[] x = new float[vertices];
        float[] y = new float[vertices];
        float[] z = new float[vertices];
        for (int v = 0; v < index.length; v++)
        {
            if (index[v] >= 0)
            {
                x[index[v]] = mesh.x[v];
                y[index[v]] = mesh.y[v];
                z[index[v]] = mesh.z[v];
            }
        }
        return new Mesh(mesh.name, x, y, z, a, b, c, color);
    }

    private static int corner(int[] same, int v)
    {
        return same[v] >= 0 ? same[v] : v;
    }

    private static int find(int[] parent, int v)
    {
        while (parent[v] != v)
        {
            parent[v] = parent[parent[v]];
            v = parent[v];
        }
        return v;
    }

    private static void union(int[] parent, int u, int v)
    {
        int ru = find(parent, u);
        int rv = find(parent, v);
        if (ru != rv)
        {
            parent[Math.max(ru, rv)] = Math.min(ru, rv);
        }
    }
}
