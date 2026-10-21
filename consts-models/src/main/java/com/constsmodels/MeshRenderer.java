package com.constsmodels;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.runelite.client.ui.ColorScheme;

/**
 * Draws a mesh into a picture on the CPU: perspective through an {@link OrbitCamera}, a depth buffer, and each
 * triangle flat in its own color under a soft ambient light and a key light from over the viewer's left shoulder.
 * Both sides of every triangle are drawn, since the game's models aren't closed.
 *
 * <p>Corners are snapped to a sixteenth of a pixel and filled by the top-left rule, so triangles that share an edge
 * meet without gaps or double pixels. Triangles that reach behind the camera, or far outside the picture, are cut to
 * fit first.
 *
 * <p>One renderer draws one picture at a time; it keeps its buffers between pictures. The mesh is never changed.
 */
final class MeshRenderer
{
    /** How bright a face is that the key light doesn't reach, and how much the key light adds at most. */
    static final float AMBIENT = 0.46f;
    static final float KEY = 0.62f;

    /** The background, a gentle fall from the top of the picture to the bottom. */
    static final int BACKGROUND_TOP = 0x323232;
    static final int BACKGROUND_BOTTOM = ColorScheme.DARKER_GRAY_COLOR.getRGB() & 0xFFFFFF;

    // Corners are snapped to 1/16 of a pixel
    private static final int SUBPIXEL_BITS = 4;
    private static final int ONE = 1 << SUBPIXEL_BITS;
    private static final float TO_FIXED = ONE;

    // How far outside the picture a corner may land before its triangle is cut, in picture widths and heights
    private static final double GUARD = 3;

    // Which planes a corner is outside of
    private static final int OUT_NEAR = 1;
    private static final int OUT_LEFT = 2;
    private static final int OUT_RIGHT = 4;
    private static final int OUT_BOTTOM = 8;
    private static final int OUT_TOP = 16;

    // How often a long drawing checks whether it is still wanted, a power of two
    private static final int CANCEL_CHECK = 8192;

    private final Mesh mesh;

    // Each triangle's normal, of unit length
    private final float[] normalX;
    private final float[] normalY;
    private final float[] normalZ;

    private int backgroundTop = BACKGROUND_TOP;
    private int backgroundBottom = BACKGROUND_BOTTOM;

    // Each triangle's color as drawn: the mesh's own, or others in its place
    private int[] faceColors;

    // Each corner in the camera's axes, and where it lands: x and y in 1/16 pixels, 1/depth, and which planes it is
    // outside of
    private float[] camRight = new float[0];
    private float[] camUp = new float[0];
    private float[] camAhead = new float[0];
    private int[] screenX = new int[0];
    private int[] screenY = new int[0];
    private float[] screenW = new float[0];
    private int[] outside = new int[0];

    // The picture being drawn: its pixels, each pixel's 1/depth (0 is nothing yet), and its size
    private int[] pixels;
    private float[] depth = new float[0];
    private int width;
    private int height;

    // A cut triangle's corners as it is cut, plane after plane: right, up, ahead
    private final double[] polyA = new double[3 * 12];
    private final double[] polyB = new double[3 * 12];

    MeshRenderer(Mesh mesh)
    {
        this.mesh = mesh;
        this.faceColors = mesh.color;
        int faces = mesh.faceCount();
        normalX = new float[faces];
        normalY = new float[faces];
        normalZ = new float[faces];
        for (int f = 0; f < faces; f++)
        {
            int a = mesh.a[f];
            int b = mesh.b[f];
            int c = mesh.c[f];
            double ux = mesh.x[b] - mesh.x[a];
            double uy = mesh.y[b] - mesh.y[a];
            double uz = mesh.z[b] - mesh.z[a];
            double vx = mesh.x[c] - mesh.x[a];
            double vy = mesh.y[c] - mesh.y[a];
            double vz = mesh.z[c] - mesh.z[a];
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0)
            {
                normalX[f] = (float) (nx / length);
                normalY[f] = (float) (ny / length);
                normalZ[f] = (float) (nz / length);
            }
            else
            {
                normalZ[f] = 1;
            }
        }
    }

    /**
     * Draws the triangles in these colors instead of the mesh's own, one 0xRRGGBB for each triangle, as the print
     * colors preview does; null goes back to the mesh's own.
     */
    void useColors(int[] colors)
    {
        if (colors != null && colors.length != mesh.faceCount())
        {
            throw new IllegalArgumentException("a color for each triangle");
        }
        faceColors = colors == null ? mesh.color : colors;
    }

    /**
     * The triangle drawn at a point of a picture this size through the camera: the nearest one there, either side of
     * it, or -1 for none. The point is in the picture's own units, as the mouse gives them, so its pixel's middle is
     * half a unit on.
     */
    static int pick(Mesh mesh, OrbitCamera camera, int width, int height, double x, double y)
    {
        double[] eye = camera.eye();
        double[] basis = camera.basis();
        double focal = OrbitCamera.focal(height);
        double right = (x + 0.5 - width * 0.5) / focal;
        double up = (height * 0.5 - y - 0.5) / focal;
        // Along the ray, as far ahead as it goes: the camera's axes are square to each other and of unit length
        double dx = basis[6] + right * basis[0] + up * basis[3];
        double dy = basis[7] + right * basis[1] + up * basis[4];
        double dz = basis[8] + right * basis[2] + up * basis[5];
        double nearest = Double.MAX_VALUE;
        int found = -1;
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            int a = mesh.a[f];
            int b = mesh.b[f];
            int c = mesh.c[f];
            // Moller and Trumbore
            double e1x = mesh.x[b] - mesh.x[a];
            double e1y = mesh.y[b] - mesh.y[a];
            double e1z = mesh.z[b] - mesh.z[a];
            double e2x = mesh.x[c] - mesh.x[a];
            double e2y = mesh.y[c] - mesh.y[a];
            double e2z = mesh.z[c] - mesh.z[a];
            double hx = dy * e2z - dz * e2y;
            double hy = dz * e2x - dx * e2z;
            double hz = dx * e2y - dy * e2x;
            double det = e1x * hx + e1y * hy + e1z * hz;
            if (det == 0)
            {
                continue;
            }
            double sx = eye[0] - mesh.x[a];
            double sy = eye[1] - mesh.y[a];
            double sz = eye[2] - mesh.z[a];
            double u = (sx * hx + sy * hy + sz * hz) / det;
            if (u < 0 || u > 1)
            {
                continue;
            }
            double qx = sy * e1z - sz * e1y;
            double qy = sz * e1x - sx * e1z;
            double qz = sx * e1y - sy * e1x;
            double v = (dx * qx + dy * qy + dz * qz) / det;
            if (v < 0 || u + v > 1)
            {
                continue;
            }
            double t = (e2x * qx + e2y * qy + e2z * qz) / det;
            if (t >= camera.near() && t < nearest)
            {
                nearest = t;
                found = f;
            }
        }
        return found;
    }

    /** Sets the background, 0xRRGGBB at the top of the picture and at the bottom. */
    void setBackground(int top, int bottom)
    {
        backgroundTop = top & 0xFFFFFF;
        backgroundBottom = bottom & 0xFFFFFF;
    }

    /** A new picture of the mesh, this size, through the camera. */
    BufferedImage render(OrbitCamera camera, int width, int height)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        render(camera, image, () -> false);
        return image;
    }

    /**
     * Draws the mesh through the camera into a picture made as {@link BufferedImage#TYPE_INT_RGB}, filling all of
     * it. Every few thousand triangles it asks whether the picture is still wanted, and stops if not.
     *
     * @return whether the picture was finished
     */
    boolean render(OrbitCamera camera, BufferedImage image, BooleanSupplier cancelled)
    {
        width = image.getWidth();
        height = image.getHeight();
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int size = width * height;
        if (depth.length < size)
        {
            depth = new float[size];
        }
        clear();

        double[] eye = camera.eye();
        double[] basis = camera.basis();
        project(eye, basis, camera.near(), OrbitCamera.focal(height));

        // The key light, in the camera's axes over the viewer's left shoulder, then in the mesh's
        double lr = -0.45;
        double lu = 0.62;
        double la = -0.64;
        double lightLength = Math.sqrt(lr * lr + lu * lu + la * la);
        lr /= lightLength;
        lu /= lightLength;
        la /= lightLength;
        float lightX = (float) (lr * basis[0] + lu * basis[3] + la * basis[6]);
        float lightY = (float) (lr * basis[1] + lu * basis[4] + la * basis[7]);
        float lightZ = (float) (lr * basis[2] + lu * basis[5] + la * basis[8]);
        float eyeX = (float) eye[0];
        float eyeY = (float) eye[1];
        float eyeZ = (float) eye[2];

        int[] ia = mesh.a;
        int[] ib = mesh.b;
        int[] ic = mesh.c;
        int faces = mesh.faceCount();
        for (int f = 0; f < faces; f++)
        {
            if ((f & (CANCEL_CHECK - 1)) == CANCEL_CHECK - 1 && cancelled.getAsBoolean())
            {
                return false;
            }
            int a = ia[f];
            int b = ib[f];
            int c = ic[f];
            int oa = outside[a];
            int ob = outside[b];
            int oc = outside[c];
            if ((oa & ob & oc) != 0)
            {
                continue;
            }

            if ((oa | ob | oc) == 0)
            {
                // Wholly in front of the camera and near the picture: skip it if it covers no pixel centre
                int xa = screenX[a];
                int xb = screenX[b];
                int xc = screenX[c];
                int minX = Math.max(0, (Math.min(xa, Math.min(xb, xc)) - ONE / 2 + ONE - 1) >> SUBPIXEL_BITS);
                int maxX = Math.min(width - 1, (Math.max(xa, Math.max(xb, xc)) - ONE / 2) >> SUBPIXEL_BITS);
                if (minX > maxX)
                {
                    continue;
                }
                int ya = screenY[a];
                int yb = screenY[b];
                int yc = screenY[c];
                int minY = Math.max(0, (Math.min(ya, Math.min(yb, yc)) - ONE / 2 + ONE - 1) >> SUBPIXEL_BITS);
                int maxY = Math.min(height - 1, (Math.max(ya, Math.max(yb, yc)) - ONE / 2) >> SUBPIXEL_BITS);
                if (minY > maxY)
                {
                    continue;
                }
                int rgb = shade(f, a, lightX, lightY, lightZ, eyeX, eyeY, eyeZ);
                fill(xa, ya, screenW[a], xb, yb, screenW[b], xc, yc, screenW[c], minX, maxX, minY, maxY, rgb);
            }
            else
            {
                cut(f, a, b, c, oa | ob | oc, camera.near(), OrbitCamera.focal(height),
                    shade(f, a, lightX, lightY, lightZ, eyeX, eyeY, eyeZ));
            }
        }
        return true;
    }

    private void clear()
    {
        int r0 = backgroundTop >> 16 & 0xFF;
        int g0 = backgroundTop >> 8 & 0xFF;
        int b0 = backgroundTop & 0xFF;
        int r1 = backgroundBottom >> 16 & 0xFF;
        int g1 = backgroundBottom >> 8 & 0xFF;
        int b1 = backgroundBottom & 0xFF;
        int last = Math.max(1, height - 1);
        for (int y = 0; y < height; y++)
        {
            int r = r0 + (r1 - r0) * y / last;
            int g = g0 + (g1 - g0) * y / last;
            int b = b0 + (b1 - b0) * y / last;
            Arrays.fill(pixels, y * width, (y + 1) * width, r << 16 | g << 8 | b);
        }
        Arrays.fill(depth, 0, width * height, 0f);
    }

    /** Puts every corner in the camera's axes, and where it lands in the picture if it is near enough to it. */
    private void project(double[] eye, double[] basis, double near, double focal)
    {
        int count = mesh.vertexCount();
        if (camRight.length < count)
        {
            camRight = new float[count];
            camUp = new float[count];
            camAhead = new float[count];
            screenX = new int[count];
            screenY = new int[count];
            screenW = new float[count];
            outside = new int[count];
        }
        float ex = (float) eye[0];
        float ey = (float) eye[1];
        float ez = (float) eye[2];
        float rx = (float) basis[0];
        float ry = (float) basis[1];
        float rz = (float) basis[2];
        float ux = (float) basis[3];
        float uy = (float) basis[4];
        float uz = (float) basis[5];
        float ax = (float) basis[6];
        float ay = (float) basis[7];
        float az = (float) basis[8];
        float centreX = width * 0.5f;
        float centreY = height * 0.5f;
        float f = (float) focal;
        float nearPlane = (float) near;
        float guardX = (float) (centreX / focal * (1 + 2 * GUARD));
        float guardY = (float) (centreY / focal * (1 + 2 * GUARD));
        float[] mx = mesh.x;
        float[] my = mesh.y;
        float[] mz = mesh.z;
        for (int v = 0; v < count; v++)
        {
            float dx = mx[v] - ex;
            float dy = my[v] - ey;
            float dz = mz[v] - ez;
            float right = dx * rx + dy * ry + dz * rz;
            float up = dx * ux + dy * uy + dz * uz;
            float ahead = dx * ax + dy * ay + dz * az;
            camRight[v] = right;
            camUp[v] = up;
            camAhead[v] = ahead;
            int out = 0;
            if (ahead < nearPlane)
            {
                out |= OUT_NEAR;
            }
            if (right < -guardX * ahead)
            {
                out |= OUT_LEFT;
            }
            if (right > guardX * ahead)
            {
                out |= OUT_RIGHT;
            }
            if (up < -guardY * ahead)
            {
                out |= OUT_BOTTOM;
            }
            if (up > guardY * ahead)
            {
                out |= OUT_TOP;
            }
            outside[v] = out;
            if (out == 0)
            {
                float w = 1 / ahead;
                screenX[v] = (int) Math.floor((centreX + right * f * w) * TO_FIXED + 0.5f);
                screenY[v] = (int) Math.floor((centreY - up * f * w) * TO_FIXED + 0.5f);
                screenW[v] = w;
            }
        }
    }

    /** A triangle's color, lit on whichever side faces the camera. */
    private int shade(int f, int a, float lightX, float lightY, float lightZ, float eyeX, float eyeY, float eyeZ)
    {
        float nx = normalX[f];
        float ny = normalY[f];
        float nz = normalZ[f];
        float lit = nx * lightX + ny * lightY + nz * lightZ;
        if (nx * (eyeX - mesh.x[a]) + ny * (eyeY - mesh.y[a]) + nz * (eyeZ - mesh.z[a]) < 0)
        {
            lit = -lit;
        }
        int k = (int) ((AMBIENT + KEY * Math.max(0, lit)) * 256);
        int rgb = faceColors[f];
        int r = Math.min(255, ((rgb >> 16 & 0xFF) * k) >> 8);
        int g = Math.min(255, ((rgb >> 8 & 0xFF) * k) >> 8);
        int b = Math.min(255, ((rgb & 0xFF) * k) >> 8);
        return r << 16 | g << 8 | b;
    }

    /**
     * Cuts a triangle down to the part in front of the near plane and inside the guard band around the picture, then
     * fills what is left as a fan. Each edge is cut from its inside end, so two triangles sharing an edge cut it at
     * the same point.
     */
    private void cut(int f, int a, int b, int c, int planes, double near, double focal, int rgb)
    {
        double[] in = polyA;
        double[] out = polyB;
        int[] corners = {a, b, c};
        for (int i = 0; i < 3; i++)
        {
            in[i * 3] = camRight[corners[i]];
            in[i * 3 + 1] = camUp[corners[i]];
            in[i * 3 + 2] = camAhead[corners[i]];
        }
        int count = 3;
        double guardX = width * 0.5 / focal * (1 + 2 * GUARD);
        double guardY = height * 0.5 / focal * (1 + 2 * GUARD);
        for (int plane = OUT_NEAR; plane <= OUT_TOP && count >= 3; plane <<= 1)
        {
            if ((planes & plane) == 0)
            {
                continue;
            }
            int kept = 0;
            for (int i = 0; i < count; i++)
            {
                int j = (i + 1) % count;
                double di = side(plane, in, i, near, guardX, guardY);
                double dj = side(plane, in, j, near, guardX, guardY);
                if (di >= 0)
                {
                    System.arraycopy(in, i * 3, out, kept * 3, 3);
                    kept++;
                }
                if ((di >= 0) != (dj >= 0))
                {
                    int from = di >= 0 ? i : j;
                    int to = di >= 0 ? j : i;
                    double t = side(plane, in, from, near, guardX, guardY)
                        / (side(plane, in, from, near, guardX, guardY) - side(plane, in, to, near, guardX, guardY));
                    for (int k = 0; k < 3; k++)
                    {
                        out[kept * 3 + k] = in[from * 3 + k] + (in[to * 3 + k] - in[from * 3 + k]) * t;
                    }
                    kept++;
                }
            }
            double[] swap = in;
            in = out;
            out = swap;
            count = kept;
        }
        if (count < 3)
        {
            return;
        }

        int[] xs = new int[count];
        int[] ys = new int[count];
        float[] ws = new float[count];
        for (int i = 0; i < count; i++)
        {
            double ahead = Math.max(near, in[i * 3 + 2]);
            double w = 1 / ahead;
            xs[i] = (int) Math.floor((width * 0.5 + in[i * 3] * focal * w) * TO_FIXED + 0.5);
            ys[i] = (int) Math.floor((height * 0.5 - in[i * 3 + 1] * focal * w) * TO_FIXED + 0.5);
            ws[i] = (float) w;
        }
        for (int i = 1; i + 1 < count; i++)
        {
            int minX = Math.max(0, (Math.min(xs[0], Math.min(xs[i], xs[i + 1])) - ONE / 2 + ONE - 1) >> SUBPIXEL_BITS);
            int maxX = Math.min(width - 1, (Math.max(xs[0], Math.max(xs[i], xs[i + 1])) - ONE / 2) >> SUBPIXEL_BITS);
            int minY = Math.max(0, (Math.min(ys[0], Math.min(ys[i], ys[i + 1])) - ONE / 2 + ONE - 1) >> SUBPIXEL_BITS);
            int maxY = Math.min(height - 1, (Math.max(ys[0], Math.max(ys[i], ys[i + 1])) - ONE / 2) >> SUBPIXEL_BITS);
            if (minX <= maxX && minY <= maxY)
            {
                fill(xs[0], ys[0], ws[0], xs[i], ys[i], ws[i], xs[i + 1], ys[i + 1], ws[i + 1],
                    minX, maxX, minY, maxY, rgb);
            }
        }
    }

    /** How far inside a cutting plane a corner is: at least 0 is inside. */
    private static double side(int plane, double[] poly, int i, double near, double guardX, double guardY)
    {
        double right = poly[i * 3];
        double up = poly[i * 3 + 1];
        double ahead = poly[i * 3 + 2];
        switch (plane)
        {
            case OUT_NEAR:
                return ahead - near;
            case OUT_LEFT:
                return right + guardX * ahead;
            case OUT_RIGHT:
                return guardX * ahead - right;
            case OUT_BOTTOM:
                return up + guardY * ahead;
            default:
                return guardY * ahead - up;
        }
    }

    /**
     * Fills a triangle given by its corners in 1/16 pixels and their 1/depth, over the pixels in [minX, maxX] by
     * [minY, maxY], wherever it is nearer than what is there.
     */
    private void fill(int x0, int y0, float w0, int x1, int y1, float w1, int x2, int y2, float w2,
        int minX, int maxX, int minY, int maxY, int rgb)
    {
        long area = (long) (x1 - x0) * (y2 - y0) - (long) (y1 - y0) * (x2 - x0);
        if (area == 0)
        {
            return;
        }
        if (area < 0)
        {
            // Wound the other way on screen: swap two corners so that inside is where every edge is positive
            int t = x1;
            x1 = x2;
            x2 = t;
            t = y1;
            y1 = y2;
            y2 = t;
            float tw = w1;
            w1 = w2;
            w2 = tw;
            area = -area;
        }

        // 1/depth across the picture, as a plane: w = w0 + dwdx (x - x0) + dwdy (y - y0), in pixels
        float fx0 = x0 / TO_FIXED;
        float fy0 = y0 / TO_FIXED;
        float fx1 = x1 / TO_FIXED;
        float fy1 = y1 / TO_FIXED;
        float fx2 = x2 / TO_FIXED;
        float fy2 = y2 / TO_FIXED;
        float denominator = area / (TO_FIXED * TO_FIXED);
        float dwdx = ((w1 - w0) * (fy2 - fy0) - (w2 - w0) * (fy1 - fy0)) / denominator;
        float dwdy = ((w2 - w0) * (fx1 - fx0) - (w1 - w0) * (fx2 - fx0)) / denominator;

        // Edge functions at the first pixel centre, and how they change a pixel across and a pixel down
        long px = ((long) minX << SUBPIXEL_BITS) + ONE / 2;
        long py = ((long) minY << SUBPIXEL_BITS) + ONE / 2;
        long e0 = (long) (x2 - x1) * (py - y1) - (long) (y2 - y1) * (px - x1) + bias(x2 - x1, y2 - y1);
        long e1 = (long) (x0 - x2) * (py - y2) - (long) (y0 - y2) * (px - x2) + bias(x0 - x2, y0 - y2);
        long e2 = (long) (x1 - x0) * (py - y0) - (long) (y1 - y0) * (px - x0) + bias(x1 - x0, y1 - y0);
        long step0X = -(long) (y2 - y1) << SUBPIXEL_BITS;
        long step1X = -(long) (y0 - y2) << SUBPIXEL_BITS;
        long step2X = -(long) (y1 - y0) << SUBPIXEL_BITS;
        long step0Y = (long) (x2 - x1) << SUBPIXEL_BITS;
        long step1Y = (long) (x0 - x2) << SUBPIXEL_BITS;
        long step2Y = (long) (x1 - x0) << SUBPIXEL_BITS;
        float rowW = w0 + dwdx * (minX + 0.5f - fx0) + dwdy * (minY + 0.5f - fy0);

        int[] pix = pixels;
        float[] zs = depth;
        int stride = width;
        for (int y = minY; y <= maxY; y++)
        {
            long a0 = e0;
            long a1 = e1;
            long a2 = e2;
            float w = rowW;
            int i = y * stride + minX;
            int end = y * stride + maxX;
            for (; i <= end; i++)
            {
                if ((a0 | a1 | a2) >= 0 && w > zs[i])
                {
                    zs[i] = w;
                    pix[i] = rgb;
                }
                a0 += step0X;
                a1 += step1X;
                a2 += step2X;
                w += dwdx;
            }
            e0 += step0Y;
            e1 += step1Y;
            e2 += step2Y;
            rowW += dwdy;
        }
    }

    /** The top-left rule: a pixel centre exactly on an edge belongs to the triangle only for top and left edges. */
    private static long bias(int dx, int dy)
    {
        return dy < 0 || (dy == 0 && dx > 0) ? 0 : -1;
    }
}
