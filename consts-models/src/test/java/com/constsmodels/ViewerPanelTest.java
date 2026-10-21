package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ViewerPanelTest
{
    @Test
    public void theSizeLineCountsTrianglesAndScalesTheLongestSideToThePrintSize()
    {
        Mesh box = OrbitCameraTest.box(0, 0, 0, 400, 200, 100, 0xFFFFFF);
        assertEquals("12 triangles  ·  prints at 100 × 50 × 25 mm",
            ViewerPanel.sizeText(box, new PrintSettings(100, false, 4)));

        assertEquals("12 triangles  \u00b7  prints at 40 \u00d7 20 \u00d7 10 mm",
            ViewerPanel.sizeText(box, new PrintSettings(40, false, 4)));

        // Tenths of a millimetre below ten, written the player's own way
        assertEquals("12 triangles  \u00b7  prints at 16 \u00d7 " + String.format("%.1f", 8f) + " \u00d7 "
            + String.format("%.1f", 4f) + " mm", ViewerPanel.sizeText(box, new PrintSettings(16, false, 4)));
    }

    @Test
    public void oneTriangleIsATriangleAndAFlatNothingHasNoSize()
    {
        assertEquals("1 triangle  ·  prints at 100 × 100 × 50 mm",
            ViewerPanel.sizeText(CoreTest.triangle(0xFF0000), new PrintSettings(100, false, 4)));
        MeshBuilder b = new MeshBuilder("Point");
        int v = b.addVertex(5, 5, 5);
        b.addTriangle(v, v, v, 0);
        assertEquals("1 triangle", ViewerPanel.sizeText(b.build(), new PrintSettings(100, false, 4)));
        assertEquals("1 triangle", ViewerPanel.sizeText(b.build(), null));
    }

    @Test
    public void theTitleNamesTheModelWithoutTheGamesColorTags()
    {
        assertEquals("consts-Models: NPC Goblin", ViewerWindow.title("NPC <col=ffff00>Goblin</col>"));
        assertEquals("consts-Models: Model", ViewerWindow.title(null));
        assertEquals("consts-Models: Model", ViewerWindow.title("<col=ff0000></col>"));
    }

    @Test
    public void theFilamentsStartAtTheSettingsCountAndTheViewOnTheGamesColors()
    {
        MeshBuilder b = new MeshBuilder("Two colors");
        int v0 = b.addVertex(0, 0, 0);
        int v1 = b.addVertex(100, 0, 0);
        int v2 = b.addVertex(0, 100, 0);
        int v3 = b.addVertex(0, 0, 100);
        b.addTriangle(v0, v1, v2, 0xFF0000);
        b.addTriangle(v0, v1, v3, 0x0000FF);
        b.addTriangle(v0, v2, v3, 0x0000FE);
        Mesh mesh = b.build();

        int[] changes = new int[1];
        FilamentControls controls = new FilamentControls(mesh, 2, false, () -> changes[0]++);
        assertNotNull(controls.colors());
        assertEquals(2, controls.colors().count());
        assertNull(controls.shownColors());
        assertEquals(1, changes[0]);

        // Never more filaments than colors
        assertEquals(3, FilamentControls.distinctColors(mesh, 9));
        assertEquals(1, new FilamentControls(mesh, 1, false, () -> { }).colors().count());
        assertEquals(3, new FilamentControls(mesh, 9, false, () -> { }).colors().count());
    }

    @Test
    public void countingColorsStopsOncePastTheMost()
    {
        MeshBuilder b = new MeshBuilder("Many colors");
        int v = b.addVertex(0, 0, 0);
        for (int i = 0; i < 100; i++)
        {
            b.addTriangle(v, v, v, i);
        }
        assertEquals(10, FilamentControls.distinctColors(b.build(), 9));
        assertEquals("#00A0FF", FilamentControls.hex(0x00A0FF));
        assertEquals(FilamentControls.FILAMENTS.length, FilamentControls.FILAMENT_NAMES.length);
    }
}
