package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OrbitCameraTest
{
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    /** A box from (minX, minY, minZ) to (maxX, maxY, maxZ) as a mesh, twelve triangles. */
    static Mesh box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int rgb)
    {
        MeshBuilder b = new MeshBuilder("Box");
        int[] v = new int[8];
        for (int i = 0; i < 8; i++)
        {
            v[i] = b.addVertex((i & 1) == 0 ? minX : maxX, (i & 2) == 0 ? minY : maxY, (i & 4) == 0 ? minZ : maxZ);
        }
        int[][] quads = {{4, 5, 7, 6}, {0, 2, 3, 1}, {0, 1, 5, 4}, {3, 2, 6, 7}, {1, 3, 7, 5}, {2, 0, 4, 6}};
        for (int[] q : quads)
        {
            b.addTriangle(v[q[0]], v[q[1]], v[q[2]], rgb);
            b.addTriangle(v[q[0]], v[q[2]], v[q[3]], rgb);
        }
        return b.build();
    }

    /** How far out from the centre a point lands, as a share of half the picture, in whichever direction is further. */
    private static double reach(double[] p)
    {
        return Math.max(Math.abs(p[0] - WIDTH / 2.0) / (WIDTH / 2.0), Math.abs(p[1] - HEIGHT / 2.0) / (HEIGHT / 2.0));
    }

    @Test
    public void fittingABoxLooksAtItsMiddleWithEveryCornerJustInside()
    {
        float[] bounds = {0, 0, 0, 200, 100, 300};
        OrbitCamera camera = new OrbitCamera();
        camera.fit(bounds, WIDTH, HEIGHT);

        double[] target = camera.target();
        assertEquals(100, target[0], 1e-9);
        assertEquals(50, target[1], 1e-9);
        assertEquals(150, target[2], 1e-9);

        double furthest = 0;
        for (int corner = 0; corner < 8; corner++)
        {
            double[] p = camera.project((corner & 1) == 0 ? 0 : 200, (corner & 2) == 0 ? 0 : 100,
                (corner & 4) == 0 ? 0 : 300, WIDTH, HEIGHT);
            furthest = Math.max(furthest, reach(p));
        }
        assertEquals(OrbitCamera.FIT_FILL, furthest, 1e-6);
    }

    @Test
    public void fittingAMeshCentresWhatShowsAndFillsThePictureInTheTighterDirection()
    {
        // Tall and thin, off to one side of the origin
        Mesh mesh = box(500, 500, 0, 540, 520, 200, 0xFFFFFF);
        OrbitCamera camera = new OrbitCamera();
        camera.fit(mesh, WIDTH, HEIGHT);

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double furthest = 0;
        for (int v = 0; v < mesh.vertexCount(); v++)
        {
            double[] p = camera.project(mesh.x[v], mesh.y[v], mesh.z[v], WIDTH, HEIGHT);
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
            furthest = Math.max(furthest, reach(p));
        }
        assertEquals(OrbitCamera.FIT_FILL, furthest, 0.01);
        assertEquals(WIDTH / 2.0, (minX + maxX) / 2, 2);
        assertEquals(HEIGHT / 2.0, (minY + maxY) / 2, 2);
        // Taller than wide, so it fills the height
        assertEquals(OrbitCamera.FIT_FILL * HEIGHT, maxY - minY, 3);
    }

    @Test
    public void itOpensFromTheSouthEastAndAbove()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{-10, -10, 0, 10, 10, 40}, WIDTH, HEIGHT);
        double[] eye = camera.eye();
        double[] target = camera.target();
        assertTrue("east", eye[0] > target[0]);
        assertTrue("south", eye[1] < target[1]);
        assertTrue("above", eye[2] > target[2]);
    }

    @Test
    public void theAxesAreSquareAndRightStaysLevel()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.turn(1.1, -0.4);
        double[] b = camera.basis();
        for (int i = 0; i < 3; i++)
        {
            assertEquals(1, b[i * 3] * b[i * 3] + b[i * 3 + 1] * b[i * 3 + 1] + b[i * 3 + 2] * b[i * 3 + 2], 1e-12);
            for (int j = i + 1; j < 3; j++)
            {
                assertEquals(0, b[i * 3] * b[j * 3] + b[i * 3 + 1] * b[j * 3 + 1] + b[i * 3 + 2] * b[j * 3 + 2], 1e-12);
            }
        }
        assertEquals(0, b[2], 0);
        // Ahead points from the camera to the target
        double[] eye = camera.eye();
        double[] target = camera.target();
        for (int k = 0; k < 3; k++)
        {
            assertEquals((target[k] - eye[k]) / camera.distance(), b[6 + k], 1e-12);
        }
    }

    @Test
    public void lookingNorthEastIsRightAndUpIsUp()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{-100, -100, -100, 100, 100, 100}, WIDTH, HEIGHT);
        camera.turn(0, 0);
        double[] east = camera.project(50, 0, 0, WIDTH, HEIGHT);
        double[] up = camera.project(0, 0, 50, WIDTH, HEIGHT);
        double[] centre = camera.project(0, 0, 0, WIDTH, HEIGHT);
        assertEquals(WIDTH / 2.0, centre[0], 1e-9);
        assertEquals(HEIGHT / 2.0, centre[1], 1e-9);
        assertEquals(50 * OrbitCamera.focal(HEIGHT) / camera.distance(), east[0] - centre[0], 1e-9);
        assertEquals(-50 * OrbitCamera.focal(HEIGHT) / camera.distance(), up[1] - centre[1], 1e-9);
    }

    @Test
    public void draggingTurnsByTheDragAndTheTipStopsShortOfStraightDown()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{0, 0, 0, 100, 100, 100}, WIDTH, HEIGHT);
        double distance = camera.distance();
        double[] target = camera.target();

        camera.orbit(40, 0);
        assertEquals(OrbitCamera.DEFAULT_YAW - 40 * OrbitCamera.TURN_PER_PIXEL, camera.yaw(), 1e-12);
        camera.orbit(0, 30);
        assertEquals(OrbitCamera.DEFAULT_PITCH + 30 * OrbitCamera.TURN_PER_PIXEL, camera.pitch(), 1e-12);

        camera.orbit(0, 100_000);
        assertEquals(OrbitCamera.MAX_PITCH, camera.pitch(), 0);
        camera.orbit(0, -100_000);
        assertEquals(-OrbitCamera.MAX_PITCH, camera.pitch(), 0);

        // Turning moves round the target, never nearer or further
        assertEquals(distance, camera.distance(), 0);
        double[] eye = camera.eye();
        double away = Math.sqrt(Math.pow(eye[0] - target[0], 2) + Math.pow(eye[1] - target[1], 2)
            + Math.pow(eye[2] - target[2], 2));
        assertEquals(distance, away, 1e-9);
    }

    @Test
    public void draggingRightTurnsTheFrontOfTheModelRight()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{-100, -100, -100, 100, 100, 100}, WIDTH, HEIGHT);
        camera.turn(0, 0);
        // A point on the front, the south side, facing the camera
        double before = camera.project(0, -100, 0, WIDTH, HEIGHT)[0];
        camera.orbit(20, 0);
        double after = camera.project(0, -100, 0, WIDTH, HEIGHT)[0];
        assertTrue(after > before);
    }

    @Test
    public void scrollingZoomsTowardsThePointUnderTheCursor()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{0, 0, 0, 1000, 1000, 200}, WIDTH, HEIGHT);
        double distance = camera.distance();

        // The point on the target's plane under the cursor at (300, 100)
        double[] b = camera.basis();
        double[] t = camera.target();
        double perPixel = 2 * distance * Math.tan(OrbitCamera.FOV / 2) / HEIGHT;
        double right = (300 - WIDTH / 2.0) * perPixel;
        double up = (HEIGHT / 2.0 - 100) * perPixel;
        double px = t[0] + right * b[0] + up * b[3];
        double py = t[1] + right * b[1] + up * b[4];
        double pz = t[2] + right * b[2] + up * b[5];
        double[] before = camera.project(px, py, pz, WIDTH, HEIGHT);
        assertEquals(300, before[0], 1e-6);
        assertEquals(100, before[1], 1e-6);

        camera.zoom(-3, 300, 100, WIDTH, HEIGHT);
        assertEquals(distance * Math.pow(OrbitCamera.ZOOM_PER_NOTCH, -3), camera.distance(), 1e-9);
        double[] after = camera.project(px, py, pz, WIDTH, HEIGHT);
        assertEquals(300, after[0], 1e-6);
        assertEquals(100, after[1], 1e-6);

        // Out again from the centre leaves the target where it is
        double[] target = camera.target();
        camera.zoom(2, WIDTH / 2.0, HEIGHT / 2.0, WIDTH, HEIGHT);
        assertEquals(target[0], camera.target()[0], 1e-9);
        assertEquals(target[1], camera.target()[1], 1e-9);
        assertEquals(target[2], camera.target()[2], 1e-9);
    }

    @Test
    public void zoomStopsShortOfTheTargetAndOfVanishing()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{0, 0, 0, 100, 100, 100}, WIDTH, HEIGHT);
        camera.zoom(-1000, WIDTH / 2.0, HEIGHT / 2.0, WIDTH, HEIGHT);
        double nearest = camera.distance();
        assertTrue(nearest > 0);
        camera.zoom(-10, WIDTH / 2.0, HEIGHT / 2.0, WIDTH, HEIGHT);
        assertEquals(nearest, camera.distance(), 0);
        camera.zoom(5000, WIDTH / 2.0, HEIGHT / 2.0, WIDTH, HEIGHT);
        assertTrue(camera.distance() < 100 * 1000);
    }

    @Test
    public void panningMovesTheModelWithTheCursor()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{0, 0, 0, 100, 100, 100}, WIDTH, HEIGHT);
        double[] target = camera.target();
        camera.pan(30, -20, HEIGHT);
        double[] p = camera.project(target[0], target[1], target[2], WIDTH, HEIGHT);
        assertEquals(WIDTH / 2.0 + 30, p[0], 1e-6);
        assertEquals(HEIGHT / 2.0 - 20, p[1], 1e-6);
        assertEquals(camera.distance(), p[2], 1e-6);
    }

    @Test
    public void pointsBehindTheCameraLandNowhere()
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{0, 0, 0, 100, 100, 100}, WIDTH, HEIGHT);
        double[] eye = camera.eye();
        double[] b = camera.basis();
        assertNull(camera.project(eye[0] - b[6] * 10, eye[1] - b[7] * 10, eye[2] - b[8] * 10, WIDTH, HEIGHT));
    }
}
