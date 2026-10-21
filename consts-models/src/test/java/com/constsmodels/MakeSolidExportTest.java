package com.constsmodels;

import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MakeSolidExportTest
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
    public void anOpenModelPrintsClosedOnceMadeSolid()
    {
        Mesh mesh = openBox();
        assertFalse(TestMeshes.closed(PrintPrep.prepare(mesh, new PrintSettings(40, false, 2)).mesh));

        Mesh solid = SolidRemesh.makeSolid(mesh, 40);
        PrintPrep.Print print = PrintPrep.prepareSolid(solid, new PrintSettings(40, false, 2, true));
        assertTrue(TestMeshes.closed(print.mesh));
        assertTrue(TestMeshes.volume(print.mesh) > 0);
        assertEquals(40, TestMeshes.longest(TestMeshes.size(print.mesh)), 1.5);
    }

    @Test
    public void aSolid3mfKeepsTheViewersFilaments() throws IOException
    {
        Mesh mesh = openBox();
        // Red changed to white: the solid's red triangles still go to that filament, now white
        PrintColors colors = PrintColors.of(mesh, 2);
        int red = colors.filament(0);
        colors = colors.withColor(red, 0xFFFFFF);

        File file = MeshExporter.export(mesh, ExportFormat.THREE_MF, folder.getRoot(),
            new PrintSettings(40, false, 2, true), colors);
        String model;
        try (ZipFile zip = new ZipFile(file))
        {
            ZipEntry entry = zip.getEntry("3D/3dmodel.model");
            try (InputStream in = zip.getInputStream(entry))
            {
                model = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        assertTrue(model, model.toUpperCase().contains("#FFFFFF"));
        assertTrue(model.contains("p1=\"" + red + "\""));
        assertTrue(model.contains("p1=\"" + (1 - red) + "\""));
    }

    @Test
    public void makeSolidLeavesObjAndPlyAlone() throws IOException
    {
        Mesh mesh = openBox();
        File plain = MeshExporter.export(mesh, ExportFormat.PLY, folder.getRoot(), new PrintSettings(40, false, 2));
        File solid = MeshExporter.export(mesh, ExportFormat.PLY, folder.getRoot(), new PrintSettings(40, false, 2, true));
        assertEquals(plain.length(), solid.length());
    }

    @Test
    public void onlyPrintFormatsOfferMakeSolid()
    {
        assertTrue(ViewerPanel.printable(ExportFormat.STL));
        assertTrue(ViewerPanel.printable(ExportFormat.THREE_MF));
        assertFalse(ViewerPanel.printable(ExportFormat.OBJ));
        assertFalse(ViewerPanel.printable(ExportFormat.PLY));
    }
}
