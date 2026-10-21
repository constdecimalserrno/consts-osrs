package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class MeshRendererTest
{
    private static final int SIZE = 200;
    private static final int BACKGROUND = 0x000000;
    private static final int RED = 0xFF0000;
    private static final int BLUE = 0x0000FF;

    /** A square of side 2 half, upright in the plane y = y, facing south towards a camera at yaw 0. */
    private static void square(MeshBuilder b, float half, float y, int rgb)
    {
        int sw = b.addVertex(-half, y, -half);
        int se = b.addVertex(half, y, -half);
        int ne = b.addVertex(half, y, half);
        int nw = b.addVertex(-half, y, half);
        b.addTriangle(sw, se, ne, rgb);
        b.addTriangle(sw, ne, nw, rgb);
    }

    /** A camera looking north, level, at the origin from this far away. */
    private static OrbitCamera lookingNorth(float extent, double distance)
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(new float[]{-extent, -extent, -extent, extent, extent, extent}, SIZE, SIZE);
        camera.turn(0, 0);
        camera.zoom(Math.log(distance / camera.distance()) / Math.log(OrbitCamera.ZOOM_PER_NOTCH),
            SIZE / 2.0, SIZE / 2.0, SIZE, SIZE);
        assertEquals(distance, camera.distance(), 1e-6);
        return camera;
    }

    private static BufferedImage render(Mesh mesh, OrbitCamera camera)
    {
        MeshRenderer renderer = new MeshRenderer(mesh);
        renderer.setBackground(BACKGROUND, BACKGROUND);
        return renderer.render(camera, SIZE, SIZE);
    }

    private static int at(BufferedImage image, double x, double y)
    {
        return image.getRGB((int) Math.floor(x), (int) Math.floor(y)) & 0xFFFFFF;
    }

    private static boolean isRed(int rgb)
    {
        return (rgb >> 16 & 0xFF) > 100 && (rgb & 0xFFFF) == 0;
    }

    private static boolean isBlue(int rgb)
    {
        return (rgb & 0xFF) > 100 && (rgb & 0xFFFF00) == 0;
    }

    @Test
    public void aSquareFacingTheCameraCoversJustTheSquareItProjectsTo()
    {
        MeshBuilder b = new MeshBuilder("Square");
        square(b, 50, 0, RED);
        OrbitCamera camera = lookingNorth(50, 500);
        BufferedImage image = render(b.build(), camera);

        double half = 50 * OrbitCamera.focal(SIZE) / 500;
        double c = SIZE / 2.0;
        assertTrue(isRed(at(image, c, c)));
        for (double inside : new double[]{c - half + 1.5, c + half - 1.5})
        {
            assertTrue(isRed(at(image, inside, c)));
            assertTrue(isRed(at(image, c, inside)));
        }
        for (double outside : new double[]{c - half - 1.5, c + half + 1.5})
        {
            assertEquals(BACKGROUND, at(image, outside, c));
            assertEquals(BACKGROUND, at(image, c, outside));
        }
        assertEquals(BACKGROUND, at(image, 0, 0));
        assertEquals(BACKGROUND, at(image, SIZE - 1, SIZE - 1));

        // Every pixel whose centre is inside the square is drawn, and none outside
        int drawn = 0;
        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                if (at(image, x, y) != BACKGROUND)
                {
                    drawn++;
                }
            }
        }
        long across = (long) Math.floor(c + half - 0.5) - (long) Math.ceil(c - half - 0.5) + 1;
        assertEquals(across * across, drawn);
    }

    @Test
    public void colorsInPlaceOfTheMeshsOwnAreDrawnUntilTakenAway()
    {
        MeshBuilder b = new MeshBuilder("Square");
        square(b, 50, 0, RED);
        Mesh mesh = b.build();
        MeshRenderer renderer = new MeshRenderer(mesh);
        renderer.setBackground(BACKGROUND, BACKGROUND);
        OrbitCamera camera = lookingNorth(50, 500);

        renderer.useColors(new int[]{BLUE, BLUE});
        assertTrue(isBlue(at(renderer.render(camera, SIZE, SIZE), SIZE / 2.0, SIZE / 2.0)));
        renderer.useColors(null);
        assertTrue(isRed(at(renderer.render(camera, SIZE, SIZE), SIZE / 2.0, SIZE / 2.0)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void colorsInPlaceOfTheMeshsOwnAreOneForEachTriangle()
    {
        MeshBuilder b = new MeshBuilder("Square");
        square(b, 50, 0, RED);
        new MeshRenderer(b.build()).useColors(new int[]{BLUE});
    }

    @Test
    public void theNearerTriangleHidesTheOneBehindWhicheverIsDrawnFirst()
    {
        for (boolean nearFirst : new boolean[]{true, false})
        {
            MeshBuilder b = new MeshBuilder("Two squares");
            if (nearFirst)
            {
                square(b, 20, -30, BLUE);
                square(b, 50, 0, RED);
            }
            else
            {
                square(b, 50, 0, RED);
                square(b, 20, -30, BLUE);
            }
            BufferedImage image = render(b.build(), lookingNorth(50, 500));
            assertTrue(isBlue(at(image, SIZE / 2.0, SIZE / 2.0)));
            double redOnly = SIZE / 2.0 + 35 * OrbitCamera.focal(SIZE) / 500;
            assertTrue(isRed(at(image, redOnly, SIZE / 2.0)));
        }
    }

    @Test
    public void bothSidesOfATriangleAreDrawn()
    {
        MeshBuilder b = new MeshBuilder("Square");
        square(b, 50, 0, RED);
        OrbitCamera camera = lookingNorth(50, 500);
        camera.turn(Math.PI, 0);
        BufferedImage image = render(b.build(), camera);
        assertTrue(isRed(at(image, SIZE / 2.0, SIZE / 2.0)));
    }

    @Test
    public void trianglesSharingEdgesLeaveNoGaps()
    {
        // A fan of thin triangles at odd angles round a point, making up a disc facing the camera
        MeshBuilder b = new MeshBuilder("Fan");
        int centre = b.addVertex(0.37f, 0, -0.21f);
        int spokes = 97;
        int[] rim = new int[spokes];
        for (int i = 0; i < spokes; i++)
        {
            double angle = 2 * Math.PI * i / spokes + 0.013 * (i % 5);
            rim[i] = b.addVertex((float) (60 * Math.cos(angle)), 0, (float) (60 * Math.sin(angle)));
        }
        for (int i = 0; i < spokes; i++)
        {
            b.addTriangle(centre, rim[i], rim[(i + 1) % spokes], RED);
        }
        OrbitCamera camera = lookingNorth(60, 400);
        BufferedImage image = render(b.build(), camera);

        double radius = 60 * Math.cos(Math.PI / spokes * 1.2) * OrbitCamera.focal(SIZE) / 400 - 1.5;
        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                double dx = x + 0.5 - SIZE / 2.0;
                double dy = y + 0.5 - SIZE / 2.0;
                if (dx * dx + dy * dy < radius * radius)
                {
                    assertTrue("gap at " + x + "," + y, isRed(at(image, x, y)));
                }
            }
        }
    }

    @Test
    public void pixelsLandWhereTheCameraProjectsThem()
    {
        MeshBuilder b = new MeshBuilder("Scattered");
        int[] colors = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF};
        float[][] centres = {{-80, 10, 40}, {60, -40, -30}, {0, 50, 70}, {-30, -60, -60}, {70, 30, 20}};
        for (int i = 0; i < centres.length; i++)
        {
            float[] p = centres[i];
            b.addTriangle(b.addVertex(p[0] - 12, p[1], p[2] - 8), b.addVertex(p[0] + 12, p[1], p[2] - 8),
                b.addVertex(p[0], p[1] + 3, p[2] + 14), colors[i]);
        }
        Mesh mesh = b.build();
        OrbitCamera camera = new OrbitCamera();
        camera.fit(mesh, SIZE, SIZE);
        camera.orbit(25, -15);
        BufferedImage image = render(mesh, camera);

        for (int i = 0; i < centres.length; i++)
        {
            float cx = (mesh.x[i * 3] + mesh.x[i * 3 + 1] + mesh.x[i * 3 + 2]) / 3;
            float cy = (mesh.y[i * 3] + mesh.y[i * 3 + 1] + mesh.y[i * 3 + 2]) / 3;
            float cz = (mesh.z[i * 3] + mesh.z[i * 3 + 1] + mesh.z[i * 3 + 2]) / 3;
            double[] p = camera.project(cx, cy, cz, SIZE, SIZE);
            int rgb = at(image, p[0], p[1]);
            assertNotEquals(BACKGROUND, rgb);
            // Shaded, but the same hue: the channels that are off stay off
            for (int shift = 0; shift <= 16; shift += 8)
            {
                assertEquals("triangle " + i, (colors[i] >> shift & 0xFF) == 0, (rgb >> shift & 0xFF) == 0);
            }
        }
    }

    @Test
    public void theKeyLightComesFromAboveAndTheLeft()
    {
        Mesh cube = OrbitCameraTest.box(-50, -50, -50, 50, 50, 50, 0xC0C0C0);
        OrbitCamera camera = new OrbitCamera();
        camera.fit(cube, SIZE, SIZE);
        camera.turn(Math.toRadians(45), Math.toRadians(30));
        BufferedImage image = render(cube, camera);

        // From the south-east the south side is on the left of the picture and the east side on the right
        double[] top = camera.project(0, 0, 50, SIZE, SIZE);
        double[] left = camera.project(0, -50, 0, SIZE, SIZE);
        double[] right = camera.project(50, 0, 0, SIZE, SIZE);
        int lit = at(image, top[0], top[1]) & 0xFF;
        int leftSide = at(image, left[0], left[1]) & 0xFF;
        int rightSide = at(image, right[0], right[1]) & 0xFF;
        assertTrue(lit > leftSide);
        assertTrue(leftSide > rightSide);
        // Nothing is darker than the ambient light leaves it
        assertTrue(rightSide >= (int) (0xC0 * MeshRenderer.AMBIENT) - 1);
    }

    @Test
    public void aGroundReachingBehindTheCameraIsCutNotLost()
    {
        // A wide floor, and the camera close above it looking a little down
        MeshBuilder b = new MeshBuilder("Floor");
        int n = 40;
        float tile = 500;
        for (int i = 0; i < n; i++)
        {
            for (int j = 0; j < n; j++)
            {
                float x = (i - n / 2) * tile;
                float y = (j - n / 2) * tile;
                int v0 = b.addVertex(x, y, 0);
                int v1 = b.addVertex(x + tile, y, 0);
                int v2 = b.addVertex(x + tile, y + tile, 0);
                int v3 = b.addVertex(x, y + tile, 0);
                b.addTriangle(v0, v1, v2, RED);
                b.addTriangle(v0, v2, v3, RED);
            }
        }
        Mesh floor = b.build();
        // Looking at the middle of the floor itself, so the camera stays above it as it closes in
        OrbitCamera camera = new OrbitCamera();
        camera.fit(floor.bounds(), SIZE, SIZE);
        camera.turn(0.3, Math.toRadians(10));
        camera.zoom(-1000, SIZE / 2.0, SIZE / 2.0, SIZE, SIZE);
        assertTrue(camera.distance() < tile);
        BufferedImage image = render(floor, camera);

        double horizon = SIZE / 2.0 - Math.tan(Math.toRadians(10)) * OrbitCamera.focal(SIZE);
        for (int x = 0; x < SIZE; x++)
        {
            assertEquals(BACKGROUND, at(image, x, horizon - 3));
            assertTrue("hole at " + x, isRed(at(image, x, SIZE - 1)));
            assertTrue("hole at " + x, isRed(at(image, x, (horizon + SIZE) / 2)));
        }
    }

    @Test
    public void aPictureNoLongerWantedStopsPartWay()
    {
        MeshBuilder b = new MeshBuilder("Many");
        for (int i = 0; i < 20_000; i++)
        {
            square(b, 1 + i % 50, -i * 0.001f, RED);
        }
        MeshRenderer renderer = new MeshRenderer(b.build());
        OrbitCamera camera = lookingNorth(50, 500);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        assertFalse(renderer.render(camera, image, () -> true));
        assertTrue(renderer.render(camera, image, () -> false));
    }

    @Test
    public void theBackgroundFallsFromTopToBottom()
    {
        MeshRenderer renderer = new MeshRenderer(new MeshBuilder("Empty").build());
        renderer.setBackground(0x404040, 0x202020);
        BufferedImage image = renderer.render(new OrbitCamera(), 30, 11);
        assertEquals(30, image.getWidth());
        assertEquals(11, image.getHeight());
        assertEquals(0x404040, image.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0x303030, image.getRGB(29, 5) & 0xFFFFFF);
        assertEquals(0x202020, image.getRGB(15, 10) & 0xFFFFFF);
    }
}
