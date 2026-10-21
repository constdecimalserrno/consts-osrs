package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrintColorsTest
{
    /** Squares side by side, one per color, each as big as its weight. */
    private static Mesh squares(int[] colors, int[] sizes)
    {
        MeshBuilder b = new MeshBuilder("Squares");
        float x = 0;
        for (int i = 0; i < colors.length; i++)
        {
            float s = sizes[i];
            int v0 = b.addVertex(x, 0, 0);
            int v1 = b.addVertex(x + s, 0, 0);
            int v2 = b.addVertex(x + s, s, 0);
            int v3 = b.addVertex(x, s, 0);
            b.addTriangle(v0, v1, v2, colors[i]);
            b.addTriangle(v0, v2, v3, colors[i]);
            x += s + 1;
        }
        return b.build();
    }

    private static final int RED = 0xC02020;
    private static final int DARK_RED = 0xB01818;
    private static final int BLUE = 0x2030C0;
    private static final int YELLOW = 0xF0E040;

    @Test
    public void similarColorsMergeAndTheBiggestComesFirst()
    {
        Mesh mesh = squares(new int[]{RED, DARK_RED, BLUE, YELLOW}, new int[]{10, 9, 30, 2});
        PrintColors two = PrintColors.of(mesh, 2);
        assertEquals(2, two.count());
        // Blue covers the most surface, so it's filament 1
        assertEquals(two.filament(4), 0);
        // The two reds print in the same filament
        assertEquals(two.filament(0), two.filament(2));
        assertNotEquals(two.filament(0), two.filament(4));
    }

    @Test
    public void oneToNineColorsAndNeverMoreThanTheModelHas()
    {
        Mesh mesh = squares(new int[]{RED, BLUE, YELLOW}, new int[]{5, 5, 5});
        assertEquals(1, PrintColors.of(mesh, 1).count());
        assertEquals(1, PrintColors.of(mesh, 0).count());
        assertEquals(3, PrintColors.of(mesh, 9).count());
        assertEquals(3, PrintColors.of(mesh, 20).count());
        // With as many filaments as colors, every color keeps its own, unchanged
        PrintColors exact = PrintColors.of(mesh, 3);
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            assertEquals(mesh.color[f], exact.color(exact.filament(f)), 0x020202);
        }
    }

    @Test
    public void youChooseEachFilamentsColor()
    {
        Mesh mesh = squares(new int[]{RED, BLUE}, new int[]{5, 9});
        PrintColors colors = PrintColors.of(mesh, 2).withColor(0, 0x000000);
        assertEquals(0x000000, colors.color(0));
        Mesh preview = colors.applyTo(mesh);
        assertEquals(0x000000, preview.color[2]);
        assertEquals(colors.color(1), preview.color[0]);
    }

    @Test
    public void anotherMeshLandsOnTheNearestFilament()
    {
        PrintColors colors = PrintColors.of(squares(new int[]{RED, BLUE}, new int[]{5, 5}), 2);
        int[] filaments = colors.filamentsFor(squares(new int[]{DARK_RED, 0x1828B0}, new int[]{1, 1}));
        assertEquals(colors.filament(0), filaments[0]);
        assertEquals(colors.filament(2), filaments[2]);
    }

    @Test
    public void aChangedFilamentStillTakesTheTrianglesItTookBefore()
    {
        PrintColors colors = PrintColors.of(squares(new int[]{RED, BLUE}, new int[]{5, 9}), 2);
        int red = colors.filament(0);
        // Red changed to green: a mesh made from the model still puts its red on that filament
        PrintColors changed = colors.withColor(red, 0x20C020);
        assertEquals(red, changed.filamentsFor(squares(new int[]{DARK_RED}, new int[]{1}))[0]);
        assertEquals(4, changed.faceCount());
    }

    @Test
    public void colorsSurviveTheTripThroughOklab()
    {
        for (int rgb : new int[]{0x000000, 0xFFFFFF, RED, BLUE, YELLOW, 0x7F7F7F})
        {
            int back = PrintColors.fromOklab(PrintColors.toOklab(rgb));
            for (int shift = 0; shift <= 16; shift += 8)
            {
                assertTrue(Math.abs((rgb >> shift & 255) - (back >> shift & 255)) <= 1);
            }
        }
    }

    @Test
    public void theSameModelAlwaysGivesTheSameFilaments()
    {
        Mesh mesh = squares(new int[]{RED, DARK_RED, BLUE, YELLOW, 0x20A040, 0x804020}, new int[]{4, 6, 8, 3, 5, 7});
        assertEquals(java.util.Arrays.toString(PrintColors.of(mesh, 3).palette()),
            java.util.Arrays.toString(PrintColors.of(mesh, 3).palette()));
    }
}
