package com.constsmodels;

import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrintViewTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final int BLUE = 0x2030C0;

    /** A box with no bottom, as a game model's body often comes: it can't print as it is. */
    private static Mesh openBox()
    {
        MeshBuilder b = new MeshBuilder("Open box");
        TestMeshes.box(b, 0, 0, 0, 40, 40, 40, false, RED, BLUE);
        return b.build();
    }

    @Test
    public void thePrintIsOneClosedSolidAtThePrintSize()
    {
        Mesh mesh = openBox();
        assertFalse(TestMeshes.closed(PrintPrep.prepare(mesh, new PrintSettings(40, false, 2)).mesh));

        Printable printable = Printable.of(mesh, new PrintSettings(40, false, 2));
        assertFalse(printable.isEmpty());
        assertTrue(TestMeshes.closed(printable.print.mesh));
        assertTrue(TestMeshes.volume(printable.print.mesh) > 0);
        assertEquals(40, TestMeshes.longest(TestMeshes.size(printable.print.mesh)), 1.5);
    }

    @Test
    public void thePrintIsShownWhereTheModelIsSoTheViewDoesNotJump()
    {
        Mesh mesh = openBox();
        Printable printable = Printable.of(mesh, new PrintSettings(80, true, 2));
        float[] model = mesh.bounds();
        float[] shown = printable.shown.bounds();
        // Side to side and front to back the base is a little wider than the model; it stands under it
        assertEquals((model[0] + model[3]) / 2, (shown[0] + shown[3]) / 2, 1);
        assertEquals((model[1] + model[4]) / 2, (shown[1] + shown[4]) / 2, 1);
        assertEquals(model[5], shown[5], 2);
        assertTrue(shown[2] < model[2]);
        assertEquals(printable.print.mesh.faceCount(), printable.shown.faceCount());
    }

    @Test
    public void putBackInModelUnitsAPrintLandsOnTheModel()
    {
        Mesh box = TestMeshes.box(10, 20, 30, 50, 60, 90, RED);
        PrintPrep.Print print = PrintPrep.prepare(box, new PrintSettings(120, false, 1));
        float[] back = print.inModelUnits(print.mesh.color).bounds();
        float[] model = box.bounds();
        for (int i = 0; i < 6; i++)
        {
            assertEquals(model[i], back[i], 0.01);
        }
    }

    @Test
    public void aPrint3mfKeepsTheViewersFilaments() throws IOException
    {
        Mesh mesh = openBox();
        // Red changed to white: the solid's red triangles still go to that filament, now white
        PrintColors colors = PrintColors.of(mesh, 2);
        int red = colors.filament(0);
        colors = colors.withColor(red, 0xFFFFFF);

        Printable printable = Printable.of(mesh, new PrintSettings(40, false, 2));
        File file = MeshExporter.export(printable, colors, ExportFormat.THREE_MF, folder.getRoot());
        assertTrue(file.getName(), file.getName().startsWith("Open box print "));
        String model;
        try (ZipFile zip = new ZipFile(file))
        {
            ZipEntry entry = zip.getEntry("3D/3dmodel.model");
            try (InputStream in = zip.getInputStream(entry))
            {
                model = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        assertTrue(model, model.toUpperCase().contains("#FFFFFF"));
        assertTrue(model.contains("p1=\"" + red + "\""));
        assertTrue(model.contains("p1=\"" + (1 - red) + "\""));
    }

    @Test
    public void everyFormatFromThePrintViewSavesTheSolid() throws IOException
    {
        Mesh mesh = openBox();
        PrintColors colors = PrintColors.of(mesh, 2);
        Printable printable = Printable.of(mesh, new PrintSettings(40, false, 2));
        int vertices = printable.print.mesh.vertexCount();
        int faces = printable.print.mesh.faceCount();

        // PLY gives every triangle its own corners, for their colors
        File ply = MeshExporter.export(printable, colors, ExportFormat.PLY, folder.getRoot());
        String header = new String(Files.readAllBytes(ply.toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(header.contains("element face " + faces + "\n"));

        File obj = MeshExporter.export(printable, colors, ExportFormat.OBJ, folder.getRoot());
        long lines = Files.readAllLines(obj.toPath()).stream().filter(l -> l.startsWith("v ")).count();
        assertEquals(vertices, lines);

        File stl = MeshExporter.export(printable, colors, ExportFormat.STL, folder.getRoot());
        assertEquals(84 + 50L * printable.print.mesh.faceCount(), stl.length());
    }

    @Test
    public void theGameViewSavesTheModelAsItIs() throws IOException
    {
        Mesh mesh = openBox();
        File ply = MeshExporter.export(mesh, ExportFormat.PLY, folder.getRoot(), new PrintSettings(40, false, 2));
        String header = new String(Files.readAllBytes(ply.toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(header.contains("element face " + mesh.faceCount() + "\n"));
    }

    @Test
    public void thePrintStandsOnAFlatBottomWithOrWithoutABase()
    {
        Mesh ball = SolidRemeshTest.ball(50, 50, 60, 50, RED);
        Printable bare = Printable.of(ball, new PrintSettings(40, false, 1));
        Printable onBase = bare.placed(new PrintSettings(40, true, 1));
        for (Printable printable : new Printable[]{bare, onBase})
        {
            PrintPrep.Print print = printable.print;
            Mesh mesh = print.mesh;
            assertTrue(TestMeshes.closed(mesh, 0, print.modelFaces));

            // The model's lowest corners are all on one plane, and a disc of it lies flat there
            float low = Float.MAX_VALUE;
            for (int f = 0; f < print.modelFaces; f++)
            {
                low = Math.min(low, Math.min(mesh.z[mesh.a[f]], Math.min(mesh.z[mesh.b[f]], mesh.z[mesh.c[f]])));
            }
            double flat = 0;
            float[] footprint = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int f = 0; f < print.modelFaces; f++)
            {
                int[] corners = {mesh.a[f], mesh.b[f], mesh.c[f]};
                int on = 0;
                for (int v : corners)
                {
                    assertTrue(mesh.z[v] == low || mesh.z[v] > low + 0.02);
                    on += mesh.z[v] == low ? 1 : 0;
                }
                if (on == 3)
                {
                    flat += PrintPrep.area(mesh, f);
                    for (int v : corners)
                    {
                        footprint[0] = Math.min(footprint[0], mesh.x[v]);
                        footprint[1] = Math.min(footprint[1], mesh.y[v]);
                        footprint[2] = Math.max(footprint[2], mesh.x[v]);
                        footprint[3] = Math.max(footprint[3], mesh.y[v]);
                    }
                }
            }
            assertTrue("flat " + flat + " mm2", flat > Math.PI * 2 * 2);

            if (printable == bare)
            {
                // On the plate itself
                assertEquals(0, low, 1e-5);
            }
            else
            {
                // On the base: sunk into it a little, all of the flat bottom over it
                float[] base = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                for (int f = print.modelFaces; f < mesh.faceCount(); f++)
                {
                    for (int v : new int[]{mesh.a[f], mesh.b[f], mesh.c[f]})
                    {
                        base[0] = Math.min(base[0], mesh.x[v]);
                        base[1] = Math.min(base[1], mesh.y[v]);
                        base[2] = Math.max(base[2], mesh.x[v]);
                        base[3] = Math.max(base[3], mesh.y[v]);
                        base[4] = Math.max(base[4], mesh.z[v]);
                    }
                }
                assertEquals(print.lift, low, 1e-4);
                assertTrue(low < base[4] && base[4] - low <= PrintPrep.BASE_SINK_MM + 1e-4);
                assertTrue(footprint[0] > base[0] && footprint[1] > base[1]);
                assertTrue(footprint[2] < base[2] && footprint[3] < base[3]);
            }
        }
    }

    @Test
    public void theLineAboutThePrintSaysWhatsHappening()
    {
        assertTrue(ViewerPanel.printText(null, true, null).startsWith("Making it printable"));
        assertEquals("Couldn't make it printable: it's too thin to hold a wall",
            ViewerPanel.printText(null, false, "it's too thin to hold a wall"));
        Printable printable = Printable.of(openBox(), new PrintSettings(40, false, 2));
        String line = ViewerPanel.printText(printable, false, null);
        assertTrue(line, line.contains("one closed solid"));
        assertTrue(line, line.endsWith(" mm"));
    }

    @Test
    public void theBarHasBackgroundAndNoMakeSolidAndSaveSavesTheGameModel()
    {
        Mesh mesh = openBox();
        List<String> saved = new ArrayList<>();
        int[] background = {ViewerCanvas.DEFAULT_BACKGROUND};
        ViewerActions actions = new ViewerActions()
        {
            @Override
            public void save(Mesh m, ExportFormat format, PrintColors colors, PrintSettings settings)
            {
                saved.add("game " + format);
            }

            @Override
            public void savePrint(Printable printable, ExportFormat format, PrintColors colors)
            {
                saved.add("print " + format);
            }

            @Override
            public void saveImage(Mesh m, BufferedImage image)
            {
            }

            @Override
            public void openFolder()
            {
            }

            @Override
            public ExportFormat defaultFormat()
            {
                return ExportFormat.STL;
            }

            @Override
            public PrintSettings printSettings()
            {
                return new PrintSettings(40, false, 2);
            }

            @Override
            public int background()
            {
                return background[0];
            }

            @Override
            public void setBackground(int rgb)
            {
                background[0] = rgb;
            }
        };
        ViewerPanel panel = new ViewerPanel(mesh, actions);

        List<AbstractButton> buttons = new ArrayList<>();
        collect(panel, buttons);
        assertTrue(buttons.stream().noneMatch(b -> b instanceof JCheckBox));
        BackgroundButton bg = (BackgroundButton) buttons.stream().filter(b -> b instanceof BackgroundButton).findFirst().orElse(null);
        assertNotNull(bg);
        JButton save = (JButton) buttons.stream().filter(b -> "Save".equals(b.getText())).findFirst().orElse(null);
        assertNotNull(save);
        assertTrue(save.isEnabled());
        save.doClick();
        assertEquals(List.of("game STL"), saved);

        // A background picked elsewhere shows when the window comes back
        background[0] = 0xFFFFFF;
        panel.refresh();
        assertEquals(0xFFFFFF, panel.canvas().backgroundColor());
        assertTrue(bg.getToolTipText().contains("White"));
    }

    @Test
    public void theBaseSwitchStartsAsTheSettingAndStandsThePrintOnABaseOrNot() throws Exception
    {
        PrintSettings[] settings = {new PrintSettings(40, true, 2)};
        ViewerPanel panel = new ViewerPanel(openBox(), actions(settings));
        FilamentControls filaments = panel.filaments();
        FilamentControls.Switch base = find(panel, FilamentControls.Switch.class);
        assertNotNull(base);
        assertTrue(filaments.base());
        assertFalse(filaments.showsPrint());

        // Turning it off shows the print, made without a base
        press(base);
        assertTrue(filaments.showsPrint());
        assertFalse(filaments.base());
        Printable bare = waitForPrint(panel);
        assertFalse(bare.settings.base);
        assertEquals(bare.print.mesh.faceCount(), bare.print.modelFaces);

        // Back on, the same solid stands on a base at once
        press(base);
        Printable onBase = panel.printable();
        assertTrue(onBase.settings.base);
        assertSame(bare.solid, onBase.solid);
        assertEquals(onBase.print.modelFaces + 12, onBase.print.mesh.faceCount());
        assertTrue(TestMeshes.closed(onBase.print.mesh, onBase.print.modelFaces, onBase.print.mesh.faceCount()));

        // The switch is the viewer's own while the setting stays as it was, and follows the setting when it changes
        press(base);
        panel.refresh();
        assertFalse(filaments.base());
        settings[0] = new PrintSettings(40, false, 2);
        panel.refresh();
        assertFalse(filaments.base());
        settings[0] = new PrintSettings(40, true, 2);
        panel.refresh();
        assertTrue(filaments.base());
        assertTrue(panel.printable().settings.base);
    }

    /** Actions that save nothing, with the settings in the array, which a test may change. */
    static ViewerActions actions(PrintSettings[] settings)
    {
        return new ViewerActions()
        {
            @Override
            public void save(Mesh m, ExportFormat format, PrintColors colors, PrintSettings settings)
            {
            }

            @Override
            public void savePrint(Printable printable, ExportFormat format, PrintColors colors)
            {
            }

            @Override
            public void saveImage(Mesh m, BufferedImage image)
            {
            }

            @Override
            public void openFolder()
            {
            }

            @Override
            public ExportFormat defaultFormat()
            {
                return ExportFormat.THREE_MF;
            }

            @Override
            public PrintSettings printSettings()
            {
                return settings[0];
            }

            @Override
            public int background()
            {
                return ViewerCanvas.DEFAULT_BACKGROUND;
            }

            @Override
            public void setBackground(int rgb)
            {
            }
        };
    }

    /** Waits for the panel's print to be made, which happens off the Swing thread. */
    static Printable waitForPrint(ViewerPanel panel) throws Exception
    {
        Printable[] made = new Printable[1];
        for (int i = 0; i < 600 && made[0] == null; i++)
        {
            Thread.sleep(50);
            SwingUtilities.invokeAndWait(() -> made[0] = panel.printable());
        }
        assertNotNull("the print was never made", made[0]);
        return made[0];
    }

    /** Presses the left mouse button on a component, as a click starts. */
    static void press(java.awt.Component c)
    {
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            InputEvent.BUTTON1_DOWN_MASK, 2, 2, 1, false, MouseEvent.BUTTON1));
    }

    static <T> T find(Container root, Class<T> type)
    {
        for (Component c : root.getComponents())
        {
            if (type.isInstance(c))
            {
                return type.cast(c);
            }
            if (c instanceof Container)
            {
                T found = find((Container) c, type);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void lightBackgroundsGetDarkText()
    {
        assertFalse(ViewerCanvas.light(ViewerCanvas.DEFAULT_BACKGROUND));
        assertFalse(ViewerCanvas.light(0x000000));
        assertTrue(ViewerCanvas.light(0xFFFFFF));
        assertTrue(ViewerCanvas.light(0x87CEEB));
        assertEquals(BackgroundButton.BACKGROUNDS.length, BackgroundButton.BACKGROUND_NAMES.length);
        assertEquals("#123456", BackgroundButton.name(0x123456));
    }

    private static void collect(Container root, List<AbstractButton> out)
    {
        for (Component c : root.getComponents())
        {
            if (c instanceof AbstractButton)
            {
                out.add((AbstractButton) c);
            }
            if (c instanceof Container)
            {
                collect((Container) c, out);
            }
        }
    }
}
