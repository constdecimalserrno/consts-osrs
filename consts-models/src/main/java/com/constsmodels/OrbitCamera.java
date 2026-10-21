package com.constsmodels;

/**
 * Where the viewer looks from: a camera circling a point, the target, at some distance, turned about the up axis
 * (yaw) and tipped above or below the horizon (pitch). It works in the mesh's axes, x east, y north, z up.
 *
 * <p>At a yaw of 0 the camera stands south of the target looking north, facing a model that faces south as the
 * game's models do. A positive yaw moves it counterclockwise seen from above, round to the east; a positive pitch
 * lifts it. The picture is a perspective one with {@link #FOV} from its top edge to its bottom edge.
 */
final class OrbitCamera
{
    /** The field of view from the top of the picture to the bottom. */
    static final double FOV = Math.toRadians(35);

    /** The view a capture opens on: from the south-east and a little above, so the front and one side show. */
    static final double DEFAULT_YAW = Math.toRadians(35);
    static final double DEFAULT_PITCH = Math.toRadians(22);

    /** How far the camera can tip: just short of straight down or straight up. */
    static final double MAX_PITCH = Math.toRadians(89);

    /** How far a one-pixel drag turns the camera. */
    static final double TURN_PER_PIXEL = Math.toRadians(0.5);

    /** How much one notch of the scroll wheel moves the camera in or out. */
    static final double ZOOM_PER_NOTCH = 1.15;

    /** How much of the picture's height or width a model fills once fitted, in whichever is tighter. */
    static final double FIT_FILL = 0.74;

    private double yaw = DEFAULT_YAW;
    private double pitch = DEFAULT_PITCH;
    private double distance = 1000;
    private double targetX;
    private double targetY;
    private double targetZ;

    /** Half the diagonal of the box last fitted: what zoom limits and the near plane are measured against. */
    private double radius = 100;

    OrbitCamera copy()
    {
        OrbitCamera copy = new OrbitCamera();
        copy.yaw = yaw;
        copy.pitch = pitch;
        copy.distance = distance;
        copy.targetX = targetX;
        copy.targetY = targetY;
        copy.targetZ = targetZ;
        copy.radius = radius;
        return copy;
    }

    double yaw()
    {
        return yaw;
    }

    double pitch()
    {
        return pitch;
    }

    double distance()
    {
        return distance;
    }

    double[] target()
    {
        return new double[]{targetX, targetY, targetZ};
    }

    /** How close to the camera something can be and still be drawn. */
    double near()
    {
        return radius * 1e-3;
    }

    /**
     * Looks at a box, {minX, minY, minZ, maxX, maxY, maxZ} as {@link Mesh#bounds()} gives it, from the default
     * view, near enough that the box fills {@link #FIT_FILL} of a picture of this size in the tighter direction.
     */
    void fit(float[] bounds, int width, int height)
    {
        targetX = (bounds[0] + bounds[3]) / 2.0;
        targetY = (bounds[1] + bounds[4]) / 2.0;
        targetZ = (bounds[2] + bounds[5]) / 2.0;
        double sx = bounds[3] - bounds[0];
        double sy = bounds[4] - bounds[1];
        double sz = bounds[5] - bounds[2];
        radius = Math.max(1, Math.sqrt(sx * sx + sy * sy + sz * sz) / 2);
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;

        float[] cx = new float[8];
        float[] cy = new float[8];
        float[] cz = new float[8];
        for (int corner = 0; corner < 8; corner++)
        {
            cx[corner] = (corner & 1) == 0 ? bounds[0] : bounds[3];
            cy[corner] = (corner & 2) == 0 ? bounds[1] : bounds[4];
            cz[corner] = (corner & 4) == 0 ? bounds[2] : bounds[5];
        }
        distance = clampDistance(fitDistance(cx, cy, cz, null, 1, width, height));
    }

    /**
     * Looks at a mesh from the default view, with what shows centred and near enough that it fills
     * {@link #FIT_FILL} of a picture of this size in the tighter direction: tighter than fitting its box, which
     * leaves a model small when it is turned to the camera.
     */
    void fit(Mesh mesh, int width, int height)
    {
        fit(mesh.bounds(), width, height);
        if (mesh.isEmpty())
        {
            return;
        }

        // Only the corners the triangles use, and of a big mesh only some of them, which is plenty to frame it by
        boolean[] used = new boolean[mesh.vertexCount()];
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            used[mesh.a[f]] = true;
            used[mesh.b[f]] = true;
            used[mesh.c[f]] = true;
        }
        int step = Math.max(1, mesh.vertexCount() / 200_000);

        for (int pass = 0; pass < 2; pass++)
        {
            distance = clampDistance(fitDistance(mesh.x, mesh.y, mesh.z, used, step, width, height));

            // Where it lands across and up the picture, as tangents of the view's angle, to centre it
            double[] basis = basis();
            double minAcross = Double.MAX_VALUE;
            double maxAcross = -Double.MAX_VALUE;
            double minUp = Double.MAX_VALUE;
            double maxUp = -Double.MAX_VALUE;
            for (int v = 0; v < mesh.vertexCount(); v += step)
            {
                if (!used[v])
                {
                    continue;
                }
                double dx = mesh.x[v] - targetX;
                double dy = mesh.y[v] - targetY;
                double dz = mesh.z[v] - targetZ;
                double depth = distance + dx * basis[6] + dy * basis[7] + dz * basis[8];
                if (depth <= 0)
                {
                    continue;
                }
                double across = (dx * basis[0] + dy * basis[1] + dz * basis[2]) / depth;
                double up = (dx * basis[3] + dy * basis[4] + dz * basis[5]) / depth;
                minAcross = Math.min(minAcross, across);
                maxAcross = Math.max(maxAcross, across);
                minUp = Math.min(minUp, up);
                maxUp = Math.max(maxUp, up);
            }
            if (minAcross > maxAcross)
            {
                return;
            }
            double right = (minAcross + maxAcross) / 2 * distance;
            double up = (minUp + maxUp) / 2 * distance;
            targetX += right * basis[0] + up * basis[3];
            targetY += right * basis[1] + up * basis[4];
            targetZ += right * basis[2] + up * basis[5];
        }
        distance = clampDistance(fitDistance(mesh.x, mesh.y, mesh.z, used, step, width, height));
    }

    /**
     * The distance from the target at which every point, or every used one, lands inside the filled part of a
     * picture of this size, seen from the current yaw and pitch.
     */
    private double fitDistance(float[] xs, float[] ys, float[] zs, boolean[] used, int step, int width, int height)
    {
        double[] basis = basis();
        double tanUp = Math.tan(FOV / 2) * FIT_FILL;
        double tanAcross = tanUp * Math.max(1, width) / Math.max(1, height);
        double fitted = 0;
        for (int v = 0; v < xs.length; v += step)
        {
            if (used != null && !used[v])
            {
                continue;
            }
            double dx = xs[v] - targetX;
            double dy = ys[v] - targetY;
            double dz = zs[v] - targetZ;
            double right = dx * basis[0] + dy * basis[1] + dz * basis[2];
            double up = dx * basis[3] + dy * basis[4] + dz * basis[5];
            double ahead = dx * basis[6] + dy * basis[7] + dz * basis[8];
            // A point lands inside when |right| <= tanAcross (distance + ahead), and likewise up
            fitted = Math.max(fitted, Math.abs(right) / tanAcross - ahead);
            fitted = Math.max(fitted, Math.abs(up) / tanUp - ahead);
            fitted = Math.max(fitted, radius * 0.05 - ahead);
        }
        return fitted;
    }

    /** Turns the camera for a drag of (dx, dy) pixels: right turns the model right, down tips its top towards you. */
    void orbit(double dx, double dy)
    {
        yaw -= dx * TURN_PER_PIXEL;
        yaw = Math.IEEEremainder(yaw, 2 * Math.PI);
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + dy * TURN_PER_PIXEL));
    }

    /** Turns the camera to a yaw and pitch, the pitch kept short of straight up or down. */
    void turn(double yaw, double pitch)
    {
        this.yaw = Math.IEEEremainder(yaw, 2 * Math.PI);
        this.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
    }

    /**
     * Moves in for negative notches and out for positive ones, as the scroll wheel turns, keeping the point under the
     * cursor at (mouseX, mouseY) where it is in a picture of this size.
     */
    void zoom(double notches, double mouseX, double mouseY, int width, int height)
    {
        double before = distance;
        distance = clampDistance(distance * Math.pow(ZOOM_PER_NOTCH, notches));
        double factor = distance / before;

        // The point under the cursor on the plane through the target, which the target moves towards or away from
        double perPixel = 2 * before * Math.tan(FOV / 2) / Math.max(1, height);
        double right = (mouseX - width / 2.0) * perPixel;
        double up = (height / 2.0 - mouseY) * perPixel;
        double[] basis = basis();
        double move = 1 - factor;
        targetX += (right * basis[0] + up * basis[3]) * move;
        targetY += (right * basis[1] + up * basis[4]) * move;
        targetZ += (right * basis[2] + up * basis[5]) * move;
    }

    /** Slides the view for a drag of (dx, dy) pixels in a picture this tall, so the target moves with the cursor. */
    void pan(double dx, double dy, int height)
    {
        double perPixel = 2 * distance * Math.tan(FOV / 2) / Math.max(1, height);
        double[] basis = basis();
        double right = -dx * perPixel;
        double up = dy * perPixel;
        targetX += right * basis[0] + up * basis[3];
        targetY += right * basis[1] + up * basis[4];
        targetZ += right * basis[2] + up * basis[5];
    }

    /** Where the camera is. */
    double[] eye()
    {
        double out = Math.cos(pitch) * distance;
        return new double[]{
            targetX + Math.sin(yaw) * out,
            targetY - Math.cos(yaw) * out,
            targetZ + Math.sin(pitch) * distance};
    }

    /**
     * The camera's axes, as {right x, y, z, up x, y, z, ahead x, y, z}: right and up across the picture, ahead into
     * it. Right stays level.
     */
    double[] basis()
    {
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        return new double[]{
            cy, sy, 0,
            -sy * sp, cy * sp, cp,
            -sy * cp, cy * cp, -sp};
    }

    /** How many pixels across one unit is at a distance of one unit, in a picture this tall. */
    static double focal(int height)
    {
        return height / 2.0 / Math.tan(FOV / 2);
    }

    /**
     * Where a point lands in a picture of this size, as {x, y, depth} with x and y in pixels from the top left
     * corner, or null if it is behind the near plane.
     */
    double[] project(double x, double y, double z, int width, int height)
    {
        double[] eye = eye();
        double[] basis = basis();
        double dx = x - eye[0];
        double dy = y - eye[1];
        double dz = z - eye[2];
        double right = dx * basis[0] + dy * basis[1] + dz * basis[2];
        double up = dx * basis[3] + dy * basis[4] + dz * basis[5];
        double ahead = dx * basis[6] + dy * basis[7] + dz * basis[8];
        if (ahead < near())
        {
            return null;
        }
        double f = focal(height);
        return new double[]{width / 2.0 + right * f / ahead, height / 2.0 - up * f / ahead, ahead};
    }

    private double clampDistance(double d)
    {
        return Math.max(radius * 0.02, Math.min(radius * 40, d));
    }
}
