package com.constsmodels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A model's colors merged down to the filaments a print uses: from 1 color up to {@link #MAX}. Each triangle gets one
 * of them, and each one is a color you can change to the filament you'll load, so you choose which colors are which.
 *
 * <p>The merge weighs every color by how much of the model's surface it covers, so a big cloak keeps its own color
 * and a few stray pixels of trim join their nearest neighbor. It measures color difference in OKLab, which follows
 * how different colors look. Filament 1 is the color covering the most surface. It never changes once built: changing
 * a color makes a new one.
 */
final class PrintColors
{
    static final int MAX = 9;

    private static final int ROUNDS = 24;

    /** Each filament's color, 0xRRGGBB, the most-used first. */
    private final int[] palette;

    /** Each filament's color as the merge made it, before any was changed: what other meshes are matched against. */
    private final int[] merged;

    /** Each triangle's filament: an index into the palette. */
    private final int[] faceIndex;

    private PrintColors(int[] palette, int[] merged, int[] faceIndex)
    {
        this.palette = palette;
        this.merged = merged;
        this.faceIndex = faceIndex;
    }

    /** Merges the mesh's colors down to count filaments (1 to {@link #MAX}), fewer when it has fewer colors. */
    static PrintColors of(Mesh mesh, int count)
    {
        count = Math.max(1, Math.min(MAX, count));

        // Each distinct color and how much surface it covers
        Map<Integer, Double> areas = new HashMap<>();
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            areas.merge(mesh.color[f], Math.max(area(mesh, f), 1e-6), Double::sum);
        }
        int n = areas.size();
        if (n == 0)
        {
            return new PrintColors(new int[]{0xFFFFFF}, new int[]{0xFFFFFF}, new int[0]);
        }

        int[] colors = new int[n];
        double[] weight = new double[n];
        double[][] lab = new double[n][];
        int i = 0;
        for (Map.Entry<Integer, Double> e : areas.entrySet())
        {
            colors[i] = e.getKey();
            weight[i] = e.getValue();
            i++;
        }
        // A fixed order, so the same model always gives the same filaments
        Integer[] order = new Integer[n];
        for (int k = 0; k < n; k++)
        {
            order[k] = k;
        }
        Arrays.sort(order, (p, q) -> weight[p] != weight[q] ? Double.compare(weight[q], weight[p]) : Integer.compare(colors[p], colors[q]));
        int[] sortedColors = new int[n];
        double[] sortedWeight = new double[n];
        for (int k = 0; k < n; k++)
        {
            sortedColors[k] = colors[order[k]];
            sortedWeight[k] = weight[order[k]];
            lab[k] = toOklab(sortedColors[k]);
        }

        int k = Math.min(count, n);
        double[][] centers = seed(lab, sortedWeight, k);
        int[] assignment = new int[n];
        for (int round = 0; round < ROUNDS; round++)
        {
            boolean moved = false;
            for (int c = 0; c < n; c++)
            {
                int nearest = nearest(centers, lab[c]);
                moved |= nearest != assignment[c];
                assignment[c] = nearest;
            }
            double[][] sums = new double[k][3];
            double[] totals = new double[k];
            for (int c = 0; c < n; c++)
            {
                int a = assignment[c];
                for (int d = 0; d < 3; d++)
                {
                    sums[a][d] += lab[c][d] * sortedWeight[c];
                }
                totals[a] += sortedWeight[c];
            }
            for (int a = 0; a < k; a++)
            {
                if (totals[a] > 0)
                {
                    for (int d = 0; d < 3; d++)
                    {
                        centers[a][d] = sums[a][d] / totals[a];
                    }
                }
            }
            if (!moved && round > 0)
            {
                break;
            }
        }

        // Filaments by the surface they cover, most first; any left empty are dropped
        double[] covered = new double[k];
        for (int c = 0; c < n; c++)
        {
            covered[assignment[c]] += sortedWeight[c];
        }
        List<Integer> used = new ArrayList<>();
        for (int a = 0; a < k; a++)
        {
            if (covered[a] > 0)
            {
                used.add(a);
            }
        }
        used.sort((p, q) -> Double.compare(covered[q], covered[p]));
        int[] rank = new int[k];
        int[] palette = new int[used.size()];
        for (int r = 0; r < used.size(); r++)
        {
            rank[used.get(r)] = r;
            palette[r] = fromOklab(centers[used.get(r)]);
        }

        Map<Integer, Integer> filamentOf = new HashMap<>();
        for (int c = 0; c < n; c++)
        {
            filamentOf.put(sortedColors[c], rank[assignment[c]]);
        }
        int[] faceIndex = new int[mesh.faceCount()];
        for (int f = 0; f < faceIndex.length; f++)
        {
            faceIndex[f] = filamentOf.get(mesh.color[f]);
        }
        return new PrintColors(palette, palette.clone(), faceIndex);
    }

    /** How many filaments. */
    int count()
    {
        return palette.length;
    }

    /** Filament i's color, 0xRRGGBB. */
    int color(int i)
    {
        return palette[i];
    }

    /** Each filament's color, the most-used first; a copy. */
    int[] palette()
    {
        return palette.clone();
    }

    /** How many triangles the colors were made for: the mesh's face count. */
    int faceCount()
    {
        return faceIndex.length;
    }

    /** The filament triangle f prints in. */
    int filament(int f)
    {
        return faceIndex[f];
    }

    /** The same filaments with filament i changed to the color you'll load. */
    PrintColors withColor(int i, int rgb)
    {
        int[] changed = palette.clone();
        changed[i] = rgb & 0xFFFFFF;
        return new PrintColors(changed, merged, faceIndex);
    }

    /** The mesh as it will print: every triangle in its filament's color. For the viewer's preview. */
    Mesh applyTo(Mesh mesh)
    {
        int[] color = new int[mesh.faceCount()];
        for (int f = 0; f < color.length; f++)
        {
            color[f] = palette[faceIndex[f]];
        }
        return new Mesh(mesh.name, mesh.x, mesh.y, mesh.z, mesh.a, mesh.b, mesh.c, color);
    }

    /**
     * Puts another mesh's triangles on these filaments, each on the one nearest its own color: for a mesh made from
     * this one, like a solid remesh, whose triangles don't line up with these. It matches against the colors as
     * merged, so a filament changed to another color still takes the triangles it took before.
     */
    int[] filamentsFor(Mesh other)
    {
        double[][] centers = new double[merged.length][];
        for (int i = 0; i < merged.length; i++)
        {
            centers[i] = toOklab(merged[i]);
        }
        Map<Integer, Integer> cache = new HashMap<>();
        int[] filaments = new int[other.faceCount()];
        for (int f = 0; f < filaments.length; f++)
        {
            filaments[f] = cache.computeIfAbsent(other.color[f], rgb -> nearest(centers, toOklab(rgb)));
        }
        return filaments;
    }

    private static double area(Mesh m, int f)
    {
        int a = m.a[f];
        int b = m.b[f];
        int c = m.c[f];
        double ux = m.x[b] - m.x[a], uy = m.y[b] - m.y[a], uz = m.z[b] - m.z[a];
        double vx = m.x[c] - m.x[a], vy = m.y[c] - m.y[a], vz = m.z[c] - m.z[a];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return Math.sqrt(cx * cx + cy * cy + cz * cz) / 2;
    }

    /** Starting centers: the color covering the most, then each time the color furthest from those chosen, by weight. */
    private static double[][] seed(double[][] lab, double[] weight, int k)
    {
        double[][] centers = new double[k][];
        centers[0] = lab[0].clone();
        double[] distance = new double[lab.length];
        Arrays.fill(distance, Double.MAX_VALUE);
        for (int chosen = 1; chosen < k; chosen++)
        {
            int best = -1;
            double bestScore = -1;
            for (int c = 0; c < lab.length; c++)
            {
                distance[c] = Math.min(distance[c], squared(lab[c], centers[chosen - 1]));
                double score = distance[c] * Math.sqrt(weight[c]);
                if (score > bestScore)
                {
                    bestScore = score;
                    best = c;
                }
            }
            centers[chosen] = lab[best].clone();
        }
        return centers;
    }

    private static int nearest(double[][] centers, double[] point)
    {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < centers.length; i++)
        {
            double d = squared(centers[i], point);
            if (d < bestDistance)
            {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private static double squared(double[] p, double[] q)
    {
        double dl = p[0] - q[0], da = p[1] - q[1], db = p[2] - q[2];
        return dl * dl + da * da + db * db;
    }

    static double[] toOklab(int rgb)
    {
        double r = linear((rgb >> 16 & 255) / 255.0);
        double g = linear((rgb >> 8 & 255) / 255.0);
        double b = linear((rgb & 255) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        };
    }

    static int fromOklab(double[] lab)
    {
        double l = lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2];
        double m = lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2];
        double s = lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2];
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        int r = channel(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
        int g = channel(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s);
        int b = channel(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
        return r << 16 | g << 8 | b;
    }

    private static double linear(double c)
    {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int channel(double linear)
    {
        double c = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, c)) * 255);
    }
}
