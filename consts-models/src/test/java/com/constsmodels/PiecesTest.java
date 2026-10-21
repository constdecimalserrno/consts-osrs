package com.constsmodels;

import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.GREEN;
import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PiecesTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** A box and, apart from it, a second box, then a flat square floating beside them: three pieces. */
    static Mesh scene()
    {
        MeshBuilder b = new MeshBuilder("Scene");
        // Each side on corners of its own, as game models come: a piece once welded
        TestMeshes.box(b, 0, 0, 0, 40, 40, 40, true, RED);
        TestMeshes.box(b, 60, 0, 0, 100, 40, 60, true, BLUE);
        TestMeshes.quad(b, new float[]{0, 60, 2}, new float[]{40, 60, 2}, new float[]{40, 100, 2},
            new float[]{0, 100, 2}, GREEN);
        return b.build();
    }

    @Test
    public void aModelSplitsIntoThePiecesThatDontTouch()
    {
        Mesh scene = scene();
        Pieces pieces = Pieces.of(scene);
        assertEquals(3, pieces.count);
        assertArrayEquals(new int[]{12, 12, 2}, pieces.sizes());
        for (int f = 0; f < scene.faceCount(); f++)
        {
            assertEquals(f < 12 ? 0 : f < 24 ? 1 : 2, pieces.piece[f]);
        }

        // Unwelded, every side of the boxes is a piece of its own
        assertEquals(13, Pieces.ofCorners(scene).count);
    }

    @Test
    public void takingAPieceOutLeavesTheRest()
    {
        Mesh scene = scene();
        Pieces pieces = Pieces.of(scene);
        int[] kept = pieces.kept(new boolean[]{false, true, false});
        assertEquals(14, kept.length);
        assertEquals(11, kept[11]);
        assertEquals(24, kept[12]);
        Mesh left = Pieces.only(scene, kept);
        assertEquals(14, left.faceCount());
        assertEquals(24 + 4, left.vertexCount());
        assertEquals(GREEN, left.color[13]);
        float[] box = left.bounds();
        assertEquals(40, box[3], 0f);
        assertEquals(40, box[5], 0f);
    }

    @Test
    public void theViewerPicksRemovesAndBringsBackPieces() throws Exception
    {
        Mesh scene = scene();
        List<String> saved = new ArrayList<>();
        PrintSettings[] settings = {new PrintSettings(40, false, 3)};
        ViewerActions actions = new ForwardingActions(PrintViewTest.actions(settings))
        {
            @Override
            public void save(Mesh m, ExportFormat format, PrintColors colors)
            {
                assertEquals(m.faceCount(), colors.faceCount());
                saved.add(m.faceCount() + " " + colors.color(colors.filament(m.faceCount() - 1)));
            }
        };
        ViewerPanel panel = new ViewerPanel(scene, actions);
        assertSame(scene, panel.model());

        // A click on the blue box picks it; Delete takes it out of the model, and Save saves the rest
        panel.picked(15);
        panel.removePicked();
        assertEquals(14, panel.model().faceCount());
        PrintColors colors = panel.filaments().colors();
        panel.getActionMap().get("save").actionPerformed(null);
        assertEquals(List.of("14 " + colors.color(colors.filament(25))), saved);

        // Clicking a piece again lets it go; clicking nothing does too
        panel.picked(0);
        panel.picked(0);
        panel.removePicked();
        assertEquals(14, panel.model().faceCount());
        panel.picked(0);
        panel.picked(-1);
        panel.removePicked();
        assertEquals(14, panel.model().faceCount());

        // The last piece left can't go
        panel.picked(0);
        panel.removePicked();
        assertEquals(2, panel.model().faceCount());
        panel.picked(0);
        panel.removePicked();
        assertEquals(2, panel.model().faceCount());

        // The print is of what's left, and made again when pieces come back. (The Base switch shows the print.)
        PrintViewTest.press(PrintViewTest.find(panel, FilamentControls.Switch.class));
        Printable square = PrintViewTest.waitForPrint(panel);
        assertSame(panel.model(), square.model);
        assertEquals(1, modelPieces(square));
        panel.restoreAll();
        assertSame(scene, panel.model());
        assertNull(panel.printable());
        Printable all = PrintViewTest.waitForPrint(panel);
        assertSame(scene, all.model);
        assertEquals(3, modelPieces(all));

        // In the Print view a click picks the piece the print's triangle came from
        int blue = -1;
        for (int t = 0; t < all.print.mesh.faceCount() && blue < 0; t++)
        {
            int face = all.modelFace(t);
            blue = face >= 12 && face < 24 ? t : -1;
        }
        assertTrue(blue >= 0);
        panel.picked(blue);
        panel.removePicked();
        assertEquals(14, panel.model().faceCount());
    }

    @Test
    public void a3mfSavesEachPieceAsAPartOfOneObject() throws Exception
    {
        File file = MeshExporter.export(scene(), ExportFormat.THREE_MF, folder.getRoot(), new PrintSettings(100, true, 3));
        ThreeMfWriterTest.ThreeMf read = ThreeMfWriterTest.read(file);
        Element model = read.model.getDocumentElement();
        NodeList objects = model.getElementsByTagNameNS(ThreeMfWriter.CORE, "object");

        // Three pieces and the base, each an object with a mesh of its own, then the one made of them all
        assertEquals(5, objects.getLength());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 4; i++)
        {
            Element part = (Element) objects.item(i);
            names.add(part.getAttribute("name"));
            assertEquals(1, part.getElementsByTagNameNS(ThreeMfWriter.CORE, "mesh").getLength());
            int vertices = part.getElementsByTagNameNS(ThreeMfWriter.CORE, "vertex").getLength();
            NodeList triangles = part.getElementsByTagNameNS(ThreeMfWriter.CORE, "triangle");
            assertTrue(triangles.getLength() > 0);
            for (int t = 0; t < triangles.getLength(); t++)
            {
                Element triangle = (Element) triangles.item(t);
                for (String corner : new String[]{"v1", "v2", "v3"})
                {
                    int v = Integer.parseInt(triangle.getAttribute(corner));
                    assertTrue(v >= 0 && v < vertices);
                }
                // Every part keeps its colors, for Bambu Studio and for PrusaSlicer
                assertEquals("2", triangle.getAttribute("pid"));
                assertFalse(triangle.getAttribute("p1").isEmpty());
                assertFalse(triangle.getAttributeNS(ThreeMfWriter.SLIC3RPE, "mmu_segmentation").isEmpty());
            }
        }
        assertEquals(List.of("Scene 1", "Scene 2", "Scene 3", "Base"), names);
        Element whole = (Element) objects.item(4);
        assertEquals("Scene", whole.getAttribute("name"));
        NodeList components = whole.getElementsByTagNameNS(ThreeMfWriter.CORE, "component");
        assertEquals(4, components.getLength());
        for (int i = 0; i < 4; i++)
        {
            assertEquals(((Element) objects.item(i)).getAttribute("id"), ((Element) components.item(i)).getAttribute("objectid"));
        }
        NodeList items = model.getElementsByTagNameNS(ThreeMfWriter.CORE, "item");
        assertEquals(1, items.getLength());
        assertEquals(whole.getAttribute("id"), ((Element) items.item(0)).getAttribute("objectid"));

        // One piece and no base is one object, as ever
        File one = MeshExporter.export(TestMeshes.box(0, 0, 0, 10, 10, 10, RED, BLUE), ExportFormat.THREE_MF,
            folder.getRoot(), new PrintSettings(50, false, 2));
        Element single = ThreeMfWriterTest.read(one).model.getDocumentElement();
        assertEquals(1, single.getElementsByTagNameNS(ThreeMfWriter.CORE, "object").getLength());
        assertEquals(0, single.getElementsByTagNameNS(ThreeMfWriter.CORE, "components").getLength());
    }

    @Test
    public void aModelInTooManyPiecesIsOnePart()
    {
        MeshBuilder b = new MeshBuilder("Grass");
        for (int i = 0; i < ThreeMfWriter.MAX_PARTS + 2; i++)
        {
            b.addTriangle(b.addVertex(i * 10, 0, 0), b.addVertex(i * 10 + 5, 0, 0), b.addVertex(i * 10, 0, 5), GREEN);
        }
        Mesh grass = b.build();
        int[] parts = ThreeMfWriter.parts(grass, grass.faceCount() - 1);
        for (int f = 0; f < grass.faceCount() - 1; f++)
        {
            assertEquals(0, parts[f]);
        }
        assertEquals(1, parts[grass.faceCount() - 1]);
    }

    /** How many pieces the print's model part is in, its base left out. */
    private static int modelPieces(Printable printable)
    {
        int[] faces = new int[printable.print.modelFaces];
        for (int f = 0; f < faces.length; f++)
        {
            faces[f] = f;
        }
        return Pieces.ofCorners(Pieces.only(printable.print.mesh, faces)).count;
    }

    /** Actions that pass every call on, for a test to change one. */
    private static class ForwardingActions implements ViewerActions
    {
        private final ViewerActions to;

        ForwardingActions(ViewerActions to)
        {
            this.to = to;
        }

        @Override
        public void save(Mesh mesh, ExportFormat format, PrintColors colors)
        {
            to.save(mesh, format, colors);
        }

        @Override
        public void savePrint(Printable printable, ExportFormat format, PrintColors colors)
        {
            to.savePrint(printable, format, colors);
        }

        @Override
        public void saveImage(Mesh mesh, BufferedImage image)
        {
            to.saveImage(mesh, image);
        }

        @Override
        public void openFolder()
        {
            to.openFolder();
        }

        @Override
        public ExportFormat defaultFormat()
        {
            return to.defaultFormat();
        }

        @Override
        public PrintSettings printSettings()
        {
            return to.printSettings();
        }

        @Override
        public int background()
        {
            return to.background();
        }

        @Override
        public void setBackground(int rgb)
        {
            to.setBackground(rgb);
        }
    }
}
