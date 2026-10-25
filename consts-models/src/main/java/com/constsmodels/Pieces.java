package com.constsmodels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A model's pieces: the parts of it that don't touch, each the triangles joined corner to corner once corners at the
 * same spot are welded. The viewer picks and takes out whole pieces, such as a flat shadow under an object.
 */
final class Pieces
{
    /** Corners closer than this on every axis, in the game's units, are the same corner. */
    static final float WELD_DISTANCE = 0.01f;

    private static final int NONE = -1;

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
        return joined(mesh, welded(mesh));
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

    /** The mesh of only these of its triangles, by number, with only the corners they use, its clips too. */
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
        List<Clip> clips = new ArrayList<>();
        for (Clip clip : mesh.clips)
        {
            float[][] cx = new float[clip.frameCount()][vertices];
            float[][] cy = new float[clip.frameCount()][vertices];
            float[][] cz = new float[clip.frameCount()][vertices];
            for (int f = 0; f < clip.frameCount(); f++)
            {
                for (int v = 0; v < index.length; v++)
                {
                    if (index[v] >= 0)
                    {
                        cx[f][index[v]] = clip.x[f][v];
                        cy[f][index[v]] = clip.y[f][v];
                        cz[f][index[v]] = clip.z[f][v];
                    }
                }
            }
            clips.add(new Clip(clip.label, clip.animation, clip.ticks, clip.loopFrom, cx, cy, cz));
        }
        return new Mesh(mesh.name, x, y, z, a, b, c, color, clips);
    }

    /**
     * Each corner's stand-in once corners closer than {@link #WELD_DISTANCE} on every axis are welded: the first
     * such corner, itself if none came before. -1 for a corner no triangle uses or that isn't a number.
     */
    private static int[] welded(Mesh mesh)
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
        return same;
    }

    private static int cell(float value)
    {
        return (int) Math.floor(value / WELD_DISTANCE);
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
