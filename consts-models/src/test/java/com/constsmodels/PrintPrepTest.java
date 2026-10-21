package com.constsmodels;

import static com.constsmodels.TestMeshes.BLACK;
import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.GREEN;
import static com.constsmodels.TestMeshes.RED;
import static com.constsmodels.TestMeshes.box;
import static com.constsmodels.TestMeshes.closed;
import static com.constsmodels.TestMeshes.facesAwayFrom;
import static com.constsmodels.TestMeshes.longest;
import static com.constsmodels.TestMeshes.size;
import static com.constsmodels.TestMeshes.turned;
import static com.constsmodels.TestMeshes.volume;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrintPrepTest
{
    private static final float EPSILON = 1e-3f;

    @Test
    public void weldingJoinsSeparateSidesIntoOneClosedShell()
    {
        Mesh box = box(0, 0, 0, 100, 60, 40, RED);
        assertEquals(24, box.vertexCount());
        assertFalse(closed(box));

        Mesh welded = PrintPrep.weld(box);
        assertEquals(8, welded.vertexCount());
        assertEquals(12, welded.faceCount());
        assertTrue(closed(welded));
    }

    @Test
    public void cornersAHairApartWeldButNotFurther()
    {
        MeshBuilder b = new MeshBuilder("Near");
        int v0 = b.addVertex(0, 0, 0);
        int v1 = b.addVertex(10, 0, 0);
        int v2 = b.addVertex(0, 10, 0);
        // The same corner, off by float noise across a weld cell's edge, and one a tenth of a unit away
        int near = b.addVertex(10.004f, -0.004f, 0.009f);
        int far = b.addVertex(10.1f, 0, 0);
        int v3 = b.addVertex(10, 10, 0);
        b.addTriangle(v0, v1, v2, RED);
        b.addTriangle(near, v3, v2, RED);
        b.addTriangle(far, v3, v2, RED);
        Mesh welded = PrintPrep.weld(b.build());
        assertEquals(5, welded.vertexCount());
        assertEquals(welded.b[0], welded.a[1]);
    }

    @Test
    public void degenerateTrianglesAreDropped()
    {
        MeshBuilder b = new MeshBuilder("Degenerate");
        int v0 = b.addVertex(0, 0, 0);
        int v1 = b.addVertex(10, 0, 0);
        int v2 = b.addVertex(0, 10, 0);
        int onLine = b.addVertex(20, 0, 0);
        int same = b.addVertex(10, 0, 0.001f);
        int lost = b.addVertex(Float.NaN, 0, 0);
        b.addTriangle(v0, v1, v2, RED);
        // Two corners the same, all three on a line, two corners welded into one, and a corner that isn't a number
        b.addTriangle(v0, v0, v2, RED);
        b.addTriangle(v0, v1, onLine, RED);
        b.addTriangle(v1, same, v2, RED);
        b.addTriangle(lost, v1, v2, RED);
        Mesh welded = PrintPrep.weld(b.build());
        assertEquals(1, welded.faceCount());
        assertEquals(3, welded.vertexCount());
    }

    @Test
    public void doubledTrianglesKeepOneAndBackToBackPairsCancel()
    {
        MeshBuilder b = new MeshBuilder("Doubled");
        box(b, 0, 0, 0, 10, 10, 10, true, RED);
        // The top again, the same way round, then a sheet: one quad both ways round
        TestMeshes.quad(b, new float[]{0, 0, 10}, new float[]{10, 0, 10}, new float[]{10, 10, 10},
            new float[]{0, 10, 10}, BLUE);
        TestMeshes.quad(b, new float[]{0, 0, 20}, new float[]{10, 0, 20}, new float[]{10, 10, 20},
            new float[]{0, 10, 20}, GREEN);
        TestMeshes.quad(b, new float[]{0, 0, 20}, new float[]{0, 10, 20}, new float[]{10, 10, 20},
            new float[]{10, 0, 20}, GREEN);
        Mesh clean = PrintPrep.dropDoubles(PrintPrep.weld(b.build()));
        assertEquals(12, clean.faceCount());
        assertTrue(closed(clean));
        // The first of the doubles stays, with its color
        for (int f = 0; f < clean.faceCount(); f++)
        {
            assertEquals(RED, clean.color[f]);
        }
    }

    @Test
    public void twoBoxesTouchingBecomeOneClosedShell()
    {
        // Two boxes side by side: where they touch, each has a side on the same corners, wound against the other
        MeshBuilder b = new MeshBuilder("Two boxes");
        box(b, 0, 0, 0, 10, 10, 10, true, RED);
        TestMeshes.quad(b, new float[]{10, 0, 0}, new float[]{20, 0, 0}, new float[]{20, 0, 10},
            new float[]{10, 0, 10}, BLUE);
        TestMeshes.quad(b, new float[]{20, 10, 0}, new float[]{10, 10, 0}, new float[]{10, 10, 10},
            new float[]{20, 10, 10}, BLUE);
        TestMeshes.quad(b, new float[]{20, 0, 0}, new float[]{20, 10, 0}, new float[]{20, 10, 10},
            new float[]{20, 0, 10}, BLUE);
        TestMeshes.quad(b, new float[]{10, 0, 0}, new float[]{10, 10, 0}, new float[]{20, 10, 0},
            new float[]{20, 0, 0}, BLUE);
        TestMeshes.quad(b, new float[]{10, 0, 10}, new float[]{20, 0, 10}, new float[]{20, 10, 10},
            new float[]{10, 10, 10}, BLUE);
        // The second box's west side, on the first box's east corners and diagonal, the other way round
        TestMeshes.quad(b, new float[]{10, 0, 0}, new float[]{10, 0, 10}, new float[]{10, 10, 10},
            new float[]{10, 10, 0}, BLUE);
        Mesh clean = PrintPrep.dropDoubles(PrintPrep.weld(b.build()));
        assertEquals(20, clean.faceCount());
        assertTrue(closed(clean));
    }

    @Test
    public void aClosedShellInsideOutIsTurnedRightWayOut()
    {
        Mesh inside = turned(PrintPrep.weld(box(0, 0, 0, 30, 20, 10, RED)), false);
        assertTrue(volume(inside) < 0);
        Mesh oriented = PrintPrep.orient(inside);
        assertTrue(volume(oriented) > 0);
        assertTrue(facesAwayFrom(oriented, 15, 10, 5));
    }

    @Test
    public void aShellWoundEveryWhichWayIsWoundOutward()
    {
        Mesh mixed = turned(PrintPrep.weld(box(0, 0, 0, 30, 20, 10, RED)), true);
        assertFalse(closed(mixed));
        Mesh oriented = PrintPrep.orient(mixed);
        assertTrue(closed(oriented));
        assertTrue(facesAwayFrom(oriented, 15, 10, 5));
    }

    @Test
    public void eachClosedShellIsTurnedOnItsOwn()
    {
        MeshBuilder b = new MeshBuilder("Two shells");
        b.add(box(0, 0, 0, 10, 10, 10, RED), 0, 0, 0, 0);
        b.add(turned(box(0, 0, 0, 10, 10, 10, BLUE), false), 0, 50, 0, 0);
        Mesh oriented = PrintPrep.orient(PrintPrep.weld(b.build()));
        assertTrue(facesAwayFrom(part(oriented, 0, 12), 5, 5, 5));
        assertTrue(facesAwayFrom(part(oriented, 12, 24), 55, 5, 5));
    }

    @Test
    public void openShellsKeepTheGamesWinding()
    {
        // A box with no bottom, wound outward, and a floor facing up: neither has a volume to go by
        MeshBuilder b = new MeshBuilder("Open");
        box(b, 0, 0, 0, 10, 10, 10, false, RED);
        TestMeshes.quad(b, new float[]{-50, -50, 0}, new float[]{50, -50, 0}, new float[]{50, 50, 0},
            new float[]{-50, 50, 0}, GREEN);
        Mesh welded = PrintPrep.weld(b.build());
        Mesh oriented = PrintPrep.orient(welded);
        assertTrue(java.util.Arrays.equals(welded.b, oriented.b));
        assertTrue(java.util.Arrays.equals(welded.c, oriented.c));
    }

    @Test
    public void anOpenShellInsideOutIsTurnedOverWhenNothingIsClosed()
    {
        MeshBuilder b = new MeshBuilder("Open");
        box(b, 0, 0, 0, 10, 10, 10, false, RED);
        Mesh inside = turned(PrintPrep.weld(b.build()), false);
        Mesh oriented = PrintPrep.orient(inside);
        assertTrue(facesAwayFrom(oriented, 5, 5, 5));
    }

    @Test
    public void aMirroredModelTurnsItsOpenPartsLikeItsClosedOnes()
    {
        // A closed box and an open one, both inside out, as a mirrored capture would come
        MeshBuilder b = new MeshBuilder("Mirrored");
        box(b, 0, 0, 0, 10, 10, 10, true, RED);
        box(b, 40, 0, 0, 50, 10, 10, false, BLUE);
        Mesh oriented = PrintPrep.orient(turned(PrintPrep.weld(b.build()), false));
        assertTrue(facesAwayFrom(part(oriented, 0, 12), 5, 5, 5));
        assertTrue(facesAwayFrom(part(oriented, 12, 22), 45, 5, 5));
    }

    @Test
    public void openPartsStayPutWhenTheClosedOnesFacedOutAlready()
    {
        // A closed box the right way out next to a wall wound inward: the game's winding is trusted
        MeshBuilder b = new MeshBuilder("Mixed");
        box(b, 0, 0, 0, 10, 10, 10, true, RED);
        TestMeshes.quad(b, new float[]{40, 0, 0}, new float[]{40, 0, 10}, new float[]{50, 0, 10},
            new float[]{50, 0, 0}, BLUE);
        Mesh welded = PrintPrep.weld(b.build());
        Mesh oriented = PrintPrep.orient(welded);
        assertTrue(java.util.Arrays.equals(welded.b, oriented.b));
    }

    @Test
    public void thePrintIsScaledToSizeCenteredAndOnThePlate()
    {
        Mesh model = box(1000, 2000, 30, 1100, 2050, 50, RED);
        PrintPrep.Print print = PrintPrep.prepare(model, new PrintSettings(80, false, 4));
        Mesh mesh = print.mesh;
        float[] box = mesh.bounds();
        assertEquals(80, longest(size(mesh)), EPSILON);
        assertEquals(40, size(mesh)[1], EPSILON);
        assertEquals(16, size(mesh)[2], EPSILON);
        assertEquals(0, box[2], 0f);
        assertEquals(0, box[0] + box[3], EPSILON);
        assertEquals(0, box[1] + box[4], EPSILON);
        assertEquals(12, print.modelFaces);
        assertEquals(12, mesh.faceCount());
        assertTrue(closed(mesh));
        assertTrue(volume(mesh) > 0);
    }

    @Test
    public void aTallModelFitsItsHeightToSize()
    {
        Mesh model = box(0, 0, 0, 20, 10, 200, RED);
        Mesh mesh = PrintPrep.prepare(model, new PrintSettings(150, false, 4)).mesh;
        assertEquals(150, size(mesh)[2], EPSILON);
        assertEquals(15, size(mesh)[0], EPSILON);
    }

    @Test
    public void theBaseIsAClosedBoxTheModelSinksInto()
    {
        Mesh model = box(0, 0, 0, 100, 50, 30, RED, GREEN, BLACK);
        PrintPrep.Print print = PrintPrep.prepare(model, new PrintSettings(100, true, 4));
        Mesh mesh = print.mesh;
        assertEquals(12, print.modelFaces);
        assertEquals(24, mesh.faceCount());

        // The base: the last twelve triangles, closed on their own, wound outward, in the model's darkest color
        assertTrue(closed(mesh, 12, 24));
        Mesh base = part(mesh, 12, 24);
        float[] baseBox = base.bounds();
        assertEquals(0, baseBox[2], 0f);
        assertEquals(PrintPrep.BASE_THICKNESS_MM, baseBox[5], EPSILON);
        assertTrue(volume(base) > 0);
        assertTrue(facesAwayFrom(base, 0, 0, 1.5));
        for (int f = 12; f < 24; f++)
        {
            assertEquals(BLACK, mesh.color[f]);
            assertEquals(-1, print.source[f]);
        }

        // The model: sunk into the base, a margin in from its edges; the whole print is the size asked for
        Mesh top = part(mesh, 0, 12);
        float[] modelBox = top.bounds();
        assertEquals(PrintPrep.BASE_THICKNESS_MM - PrintPrep.BASE_SINK_MM, modelBox[2], EPSILON);
        assertEquals(PrintPrep.BASE_MARGIN_MM, modelBox[0] - baseBox[0], EPSILON);
        assertEquals(PrintPrep.BASE_MARGIN_MM, baseBox[4] - modelBox[4], EPSILON);
        assertEquals(100, longest(size(mesh)), EPSILON);
        assertEquals(0, mesh.bounds()[2], 0f);
        assertTrue(closed(mesh, 0, 12));
    }

    @Test
    public void aSmallPrintGetsAThinnerBase()
    {
        Mesh mesh = PrintPrep.prepare(box(0, 0, 0, 10, 10, 10, RED), new PrintSettings(10, true, 4)).mesh;
        assertEquals(10, longest(size(mesh)), EPSILON);
        float[] baseBox = part(mesh, 12, 24).bounds();
        assertEquals(1, baseBox[5], EPSILON);
        assertTrue(part(mesh, 0, 12).bounds()[0] > baseBox[0]);
    }

    @Test
    public void everyPrintedTriangleKnowsWhereItCameFrom()
    {
        MeshBuilder b = new MeshBuilder("Sources");
        int v0 = b.addVertex(0, 0, 0);
        b.addTriangle(v0, v0, v0, RED);
        b.add(box(0, 0, 0, 10, 10, 10, RED, GREEN, BLUE), 0, 0, 0, 0);
        Mesh model = b.build();
        PrintPrep.Print print = PrintPrep.prepare(model, new PrintSettings(50, true, 4));
        for (int f = 0; f < print.modelFaces; f++)
        {
            int source = print.source[f];
            assertTrue(source >= 1);
            assertEquals(model.color[source], print.mesh.color[f]);
        }
    }

    @Test
    public void theDarkestColorIsTheOneThatLooksDarkest()
    {
        assertEquals(2, PrintPrep.darkest(new int[]{0xFFFFFF, 0x0000FF, 0x303030}));
        assertEquals(0, PrintPrep.darkest(new int[]{0x123456}));
    }

    private static Mesh part(Mesh mesh, int from, int to)
    {
        return new Mesh(mesh.name, mesh.x, mesh.y, mesh.z,
            java.util.Arrays.copyOfRange(mesh.a, from, to), java.util.Arrays.copyOfRange(mesh.b, from, to),
            java.util.Arrays.copyOfRange(mesh.c, from, to), java.util.Arrays.copyOfRange(mesh.color, from, to));
    }
}
