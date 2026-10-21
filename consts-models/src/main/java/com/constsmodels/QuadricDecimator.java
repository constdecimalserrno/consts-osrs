package com.constsmodels;

import java.util.Arrays;

/**
 * Thins out a closed surface's triangles where they add nothing, for {@link SolidRemesh}: edge collapses ordered by
 * quadric error (Garland and Heckbert's), so flat stretches lose the most and detail stays.
 *
 * <p>Each vertex keeps the planes of the triangles it has absorbed, and a collapse costs the sum of squared distances
 * from the merged vertex to all of them, so no plane ends up further than the square root of the cost. A collapse is
 * only made when it keeps the surface a closed manifold (the two ends share exactly the two neighbors across the
 * edge, and neither of those is left with fewer than three triangles), turns no triangle more than about 70 degrees
 * and leaves no new sliver. So what comes out is as closed and as well wound as what went in.
 */
final class QuadricDecimator
{
    /** How far a collapse may turn a triangle: the cosine of the angle. */
    private static final double MIN_TURN_COS = 0.3;

    /** The least shape quality (1 for equilateral) a collapse may leave a triangle with, unless it was worse. */
    private static final double MIN_QUALITY = 0.05;

    private final int vertexCount;
    private final double[] position;
    private final double[] quadric;
    private final int[] corner;
    private final boolean[] deadTriangle;
    private final int[] valence;
    private int live;

    /** Each vertex's corners as a linked list: head per vertex, then next per corner (3 t + k for triangle t). */
    private final int[] head;
    private final int[] link;

    /** A min-heap of vertices by the cost of their cheapest collapse. */
    private final int[] heap;
    private final int[] heapAt;
    private int heapSize;
    private final double[] cost;
    private final int[] target;

    private final int[] mark;
    private int stamp;
    private final double[] sum = new double[10];
    private final double[] trial = new double[3];
    private int[] ring = new int[32];
    private double[] ringCost = new double[32];
    private double[] ringPlace = new double[96];

    private QuadricDecimator(MarchingCubes.Surface in)
    {
        vertexCount = in.vertices;
        int triangles = in.triangles;
        position = in.xyz;
        corner = in.corners;
        quadric = new double[10 * vertexCount];
        deadTriangle = new boolean[triangles];
        valence = new int[vertexCount];
        live = triangles;
        head = new int[vertexCount];
        Arrays.fill(head, -1);
        link = new int[3 * triangles];
        heap = new int[vertexCount];
        heapAt = new int[vertexCount];
        Arrays.fill(heapAt, -1);
        cost = new double[vertexCount];
        target = new int[vertexCount];
        mark = new int[vertexCount];

        for (int t = 0; t < triangles; t++)
        {
            addPlane(t);
            for (int k = 0; k < 3; k++)
            {
                int c = 3 * t + k;
                link[c] = head[corner[c]];
                head[corner[c]] = c;
                valence[corner[c]]++;
            }
        }
    }

    /**
     * The surface with triangles merged until the next collapse would move it more than maxError from where it was.
     * Works in the surface's own arrays, so it is used up.
     */
    static MarchingCubes.Surface decimate(MarchingCubes.Surface in, double maxError)
    {
        QuadricDecimator d = new QuadricDecimator(in);
        d.run(maxError * maxError);
        return d.result();
    }

    private void run(double maxCost)
    {
        for (int v = 0; v < vertexCount; v++)
        {
            if (head[v] >= 0)
            {
                retarget(v, false);
            }
        }
        while (heapSize > 0)
        {
            int v = heap[0];
            if (cost[v] > maxCost)
            {
                break;
            }
            int w = target[v];
            edgeCost(v, w, trial, 0);
            if (canCollapse(v, w, trial))
            {
                collapse(v, w, trial[0], trial[1], trial[2]);
            }
            else
            {
                retarget(v, true);
            }
        }
    }

    private void addPlane(int t)
    {
        int a = corner[3 * t];
        int b = corner[3 * t + 1];
        int c = corner[3 * t + 2];
        double ux = position[3 * b] - position[3 * a];
        double uy = position[3 * b + 1] - position[3 * a + 1];
        double uz = position[3 * b + 2] - position[3 * a + 2];
        double vx = position[3 * c] - position[3 * a];
        double vy = position[3 * c + 1] - position[3 * a + 1];
        double vz = position[3 * c + 2] - position[3 * a + 2];
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-12)
        {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        double d = -(nx * position[3 * a] + ny * position[3 * a + 1] + nz * position[3 * a + 2]);
        for (int v : new int[]{a, b, c})
        {
            int q = 10 * v;
            quadric[q] += nx * nx;
            quadric[q + 1] += nx * ny;
            quadric[q + 2] += nx * nz;
            quadric[q + 3] += nx * d;
            quadric[q + 4] += ny * ny;
            quadric[q + 5] += ny * nz;
            quadric[q + 6] += ny * d;
            quadric[q + 7] += nz * nz;
            quadric[q + 8] += nz * d;
            quadric[q + 9] += d * d;
        }
    }

    /**
     * Where merging a and b puts the vertex, into at, and what it costs. The best place by the summed planes, pulled
     * a little toward the edge's middle so flat and creased stretches (where the planes don't pin a point) stay put.
     */
    private double edgeCost(int a, int b, double[] at, int atIndex)
    {
        double[] q = sum;
        for (int k = 0; k < 10; k++)
        {
            q[k] = quadric[10 * a + k] + quadric[10 * b + k];
        }
        double mx = (position[3 * a] + position[3 * b]) / 2;
        double my = (position[3 * a + 1] + position[3 * b + 1]) / 2;
        double mz = (position[3 * a + 2] + position[3 * b + 2]) / 2;
        double pull = 1e-3 * (q[0] + q[4] + q[7]) + 1e-12;

        // (A + pull I) p = pull m - b, A symmetric
        double a00 = q[0] + pull;
        double a01 = q[1];
        double a02 = q[2];
        double a11 = q[4] + pull;
        double a12 = q[5];
        double a22 = q[7] + pull;
        double rx = pull * mx - q[3];
        double ry = pull * my - q[6];
        double rz = pull * mz - q[8];
        double c00 = a11 * a22 - a12 * a12;
        double c01 = a02 * a12 - a01 * a22;
        double c02 = a01 * a12 - a02 * a11;
        double det = a00 * c00 + a01 * c01 + a02 * c02;
        double x = mx;
        double y = my;
        double z = mz;
        if (Math.abs(det) > 1e-30)
        {
            double c11 = a00 * a22 - a02 * a02;
            double c12 = a01 * a02 - a00 * a12;
            double c22 = a00 * a11 - a01 * a01;
            x = (c00 * rx + c01 * ry + c02 * rz) / det;
            y = (c01 * rx + c11 * ry + c12 * rz) / det;
            z = (c02 * rx + c12 * ry + c22 * rz) / det;
        }
        at[atIndex] = x;
        at[atIndex + 1] = y;
        at[atIndex + 2] = z;
        double error = q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x
            + q[4] * y * y + 2 * q[5] * y * z + 2 * q[6] * y
            + q[7] * z * z + 2 * q[8] * z + q[9];
        return Math.max(0, error);
    }

    /**
     * Finds v's cheapest collapse (one that can be made, when checking) and files v in the heap by it. Leaves v's
     * neighbors in ring and each one's cost and place in ringCost and ringPlace, and returns how many.
     */
    private int retarget(int v, boolean checking)
    {
        int n = neighbors(v);
        if (ringCost.length < n)
        {
            ringCost = new double[ring.length];
            ringPlace = new double[3 * ring.length];
        }
        int pick = -1;
        for (int k = 0; k < n; k++)
        {
            ringCost[k] = edgeCost(v, ring[k], ringPlace, 3 * k);
            if (pick < 0 || ringCost[k] < ringCost[pick])
            {
                pick = k;
            }
        }
        if (checking)
        {
            // Cheapest first, the first that can be made
            pick = -1;
            boolean[] tried = new boolean[n];
            for (int tries = 0; tries < n && pick < 0; tries++)
            {
                int next = -1;
                for (int k = 0; k < n; k++)
                {
                    if (!tried[k] && (next < 0 || ringCost[k] < ringCost[next]))
                    {
                        next = k;
                    }
                }
                tried[next] = true;
                trial[0] = ringPlace[3 * next];
                trial[1] = ringPlace[3 * next + 1];
                trial[2] = ringPlace[3 * next + 2];
                if (canCollapse(v, ring[next], trial))
                {
                    pick = next;
                }
            }
        }
        if (pick < 0)
        {
            heapRemove(v);
            return n;
        }
        target[v] = ring[pick];
        heapSet(v, ringCost[pick]);
        return n;
    }

    /** Fills ring with v's neighbors, each once, dropping dead corners from v's list on the way; returns how many. */
    private int neighbors(int v)
    {
        int s = ++stamp;
        int n = 0;
        int previous = -1;
        for (int c = head[v]; c >= 0; c = link[c])
        {
            int t = c / 3;
            if (deadTriangle[t])
            {
                if (previous < 0)
                {
                    head[v] = link[c];
                }
                else
                {
                    link[previous] = link[c];
                }
                continue;
            }
            previous = c;
            int k = c - 3 * t;
            for (int j = 1; j <= 2; j++)
            {
                int u = corner[3 * t + (k + j) % 3];
                if (mark[u] != s)
                {
                    mark[u] = s;
                    if (n == ring.length)
                    {
                        ring = Arrays.copyOf(ring, n * 2);
                    }
                    ring[n++] = u;
                }
            }
        }
        return n;
    }

    private boolean canCollapse(int a, int b, double[] at)
    {
        // The two triangles on edge ab, and the corners across from it
        int s = ++stamp;
        stamp++;
        int shared = 0;
        int far0 = -1;
        int far1 = -1;
        for (int c = head[a]; c >= 0; c = link[c])
        {
            int t = c / 3;
            if (deadTriangle[t])
            {
                continue;
            }
            int k = c - 3 * t;
            int p = corner[3 * t + (k + 1) % 3];
            int r = corner[3 * t + (k + 2) % 3];
            mark[p] = s;
            mark[r] = s;
            if (p == b || r == b)
            {
                shared++;
                if (far0 < 0)
                {
                    far0 = p == b ? r : p;
                }
                else
                {
                    far1 = p == b ? r : p;
                }
            }
        }
        if (shared != 2 || far0 == far1 || valence[far0] <= 3 || valence[far1] <= 3)
        {
            return false;
        }

        // The ends may share no neighbors but those two
        int common = 0;
        for (int c = head[b]; c >= 0; c = link[c])
        {
            int t = c / 3;
            if (deadTriangle[t])
            {
                continue;
            }
            int k = c - 3 * t;
            for (int j = 1; j <= 2; j++)
            {
                int u = corner[3 * t + (k + j) % 3];
                if (u != a && mark[u] == s)
                {
                    mark[u] = s + 1;
                    common++;
                }
            }
        }
        if (common != 2)
        {
            return false;
        }

        // No triangle that stays may turn too far or become a sliver
        return keepsShape(a, b, at) && keepsShape(b, a, at);
    }

    /** Whether moving v to at turns none of its triangles but those on edge v other too far, nor leaves a sliver. */
    private boolean keepsShape(int v, int other, double[] at)
    {
        for (int c = head[v]; c >= 0; c = link[c])
        {
            int t = c / 3;
            if (deadTriangle[t])
            {
                continue;
            }
            int k = c - 3 * t;
            int p = corner[3 * t + (k + 1) % 3];
            int r = corner[3 * t + (k + 2) % 3];
            if (p == other || r == other)
            {
                continue;
            }
            double px = position[3 * p];
            double py = position[3 * p + 1];
            double pz = position[3 * p + 2];
            double rx = position[3 * r];
            double ry = position[3 * r + 1];
            double rz = position[3 * r + 2];
            // The edge across from v stays; v's two edges change
            double ex = rx - px;
            double ey = ry - py;
            double ez = rz - pz;
            double ox = position[3 * v] - px;
            double oy = position[3 * v + 1] - py;
            double oz = position[3 * v + 2] - pz;
            double nx = at[0] - px;
            double ny = at[1] - py;
            double nz = at[2] - pz;
            // Normals as (r - p) x (v - p), the triangle's own winding
            double bx = ey * oz - ez * oy;
            double by = ez * ox - ex * oz;
            double bz = ex * oy - ey * ox;
            double ax = ey * nz - ez * ny;
            double ay = ez * nx - ex * nz;
            double az = ex * ny - ey * nx;
            double before = Math.sqrt(bx * bx + by * by + bz * bz);
            double after = Math.sqrt(ax * ax + ay * ay + az * az);
            if (after < 1e-12)
            {
                return false;
            }
            if (before > 1e-12 && bx * ax + by * ay + bz * az < MIN_TURN_COS * before * after)
            {
                return false;
            }
            double across = ex * ex + ey * ey + ez * ez;
            double qualityAfter = quality(after, across, nx, ny, nz, rx - at[0], ry - at[1], rz - at[2]);
            if (qualityAfter < MIN_QUALITY
                && qualityAfter < quality(before, across, ox, oy, oz, ex - ox, ey - oy, ez - oz))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * A triangle's shape: 1 when equilateral, toward 0 as it flattens. Given twice its area, the squared length of
     * one edge and the other two edges as vectors.
     */
    private static double quality(double twiceArea, double edge, double ux, double uy, double uz, double vx, double vy,
        double vz)
    {
        double sum = edge + ux * ux + uy * uy + uz * uz + vx * vx + vy * vy + vz * vz;
        return sum <= 0 ? 0 : 2 * Math.sqrt(3) * twiceArea / sum;
    }

    /** Merges b into a, at (x, y, z). */
    private void collapse(int a, int b, double x, double y, double z)
    {
        for (int c = head[b]; c >= 0; )
        {
            int next = link[c];
            int t = c / 3;
            if (!deadTriangle[t])
            {
                int k = c - 3 * t;
                int p = corner[3 * t + (k + 1) % 3];
                int r = corner[3 * t + (k + 2) % 3];
                if (p == a || r == a)
                {
                    deadTriangle[t] = true;
                    live--;
                    valence[p == a ? r : p]--;
                }
                else
                {
                    corner[c] = a;
                    link[c] = head[a];
                    head[a] = c;
                }
            }
            c = next;
        }
        valence[a] += valence[b] - 4;
        valence[b] = 0;
        head[b] = -1;
        heapRemove(b);
        position[3 * a] = x;
        position[3 * a + 1] = y;
        position[3 * a + 2] = z;
        for (int k = 0; k < 10; k++)
        {
            quadric[10 * a + k] += quadric[10 * b + k];
        }

        // a's collapses all changed. A neighbor's changed only if it was headed for a or b, or now toward a
        int n = retarget(a, false);
        int[] around = Arrays.copyOf(ring, n);
        double[] costs = Arrays.copyOf(ringCost, n);
        for (int k = 0; k < n; k++)
        {
            int u = around[k];
            if (heapAt[u] < 0 || target[u] == a || target[u] == b)
            {
                retarget(u, false);
            }
            else if (costs[k] < cost[u])
            {
                target[u] = a;
                heapSet(u, costs[k]);
            }
        }
    }

    private MarchingCubes.Surface result()
    {
        MarchingCubes.Surface out = new MarchingCubes.Surface();
        int[] renumber = new int[vertexCount];
        Arrays.fill(renumber, -1);
        int[] v = new int[3];
        for (int t = 0; t < deadTriangle.length; t++)
        {
            if (deadTriangle[t])
            {
                continue;
            }
            for (int k = 0; k < 3; k++)
            {
                int old = corner[3 * t + k];
                if (renumber[old] < 0)
                {
                    renumber[old] = out.addVertex(position[3 * old], position[3 * old + 1], position[3 * old + 2]);
                }
                v[k] = renumber[old];
            }
            out.addTriangle(v[0], v[1], v[2]);
        }
        return out;
    }

    private void heapSet(int v, double c)
    {
        if (heapAt[v] < 0)
        {
            cost[v] = c;
            heapAt[v] = heapSize;
            heap[heapSize++] = v;
            up(heapAt[v]);
        }
        else if (c < cost[v])
        {
            cost[v] = c;
            up(heapAt[v]);
        }
        else
        {
            cost[v] = c;
            down(heapAt[v]);
        }
    }

    private void heapRemove(int v)
    {
        int at = heapAt[v];
        if (at < 0)
        {
            return;
        }
        heapAt[v] = -1;
        int last = heap[--heapSize];
        if (at < heapSize)
        {
            heap[at] = last;
            heapAt[last] = at;
            up(at);
            down(heapAt[last]);
        }
    }

    private void up(int at)
    {
        int v = heap[at];
        while (at > 0)
        {
            int parent = (at - 1) / 2;
            if (cost[heap[parent]] <= cost[v])
            {
                break;
            }
            heap[at] = heap[parent];
            heapAt[heap[at]] = at;
            at = parent;
        }
        heap[at] = v;
        heapAt[v] = at;
    }

    private void down(int at)
    {
        int v = heap[at];
        while (true)
        {
            int child = 2 * at + 1;
            if (child >= heapSize)
            {
                break;
            }
            if (child + 1 < heapSize && cost[heap[child + 1]] < cost[heap[child]])
            {
                child++;
            }
            if (cost[heap[child]] >= cost[v])
            {
                break;
            }
            heap[at] = heap[child];
            heapAt[heap[at]] = at;
            at = child;
        }
        heap[at] = v;
        heapAt[v] = at;
    }
}
