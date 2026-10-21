package com.constsmodels;

import static com.constsmodels.TestMeshes.RED;
import static com.constsmodels.TestMeshes.box;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MeshExporterTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final PrintSettings SETTINGS = new PrintSettings(100, true, 4);

    @Test
    public void filesAreNamedAfterTheModelAndTheTime() throws IOException
    {
        Mesh mesh = box(0, 0, 0, 10, 10, 10, RED).withName("NPC <col=ffff00>Goblin</col>");
        for (ExportFormat format : ExportFormat.values())
        {
            String before = ConstsModelsPlugin.fileName(mesh.name, new Date());
            File file = MeshExporter.export(mesh, format, folder.getRoot(), SETTINGS);
            String after = ConstsModelsPlugin.fileName(mesh.name, new Date());
            String name = file.getName();
            assertTrue(name, name.equals(before + "." + format.extension())
                || name.equals(after + "." + format.extension()));
            assertEquals(folder.getRoot(), file.getParentFile());
            assertTrue(file.length() > 0);
        }
    }

    @Test
    public void aTakenNameGetsANumber() throws IOException
    {
        File root = folder.getRoot();
        assertTrue(new File(root, "Box.stl").createNewFile());
        assertEquals("Box", MeshExporter.unusedName(root, "Box", ExportFormat.PLY));
        assertEquals("Box (2)", MeshExporter.unusedName(root, "Box", ExportFormat.STL));
        assertTrue(new File(root, "Box (2).stl").createNewFile());
        assertEquals("Box (3)", MeshExporter.unusedName(root, "Box", ExportFormat.STL));
        // An OBJ's materials file counts too
        assertTrue(new File(root, "Box.mtl").createNewFile());
        assertEquals("Box (2)", MeshExporter.unusedName(root, "Box", ExportFormat.OBJ));
    }

    @Test
    public void savingTwiceKeepsBothFiles() throws IOException
    {
        Mesh mesh = box(0, 0, 0, 10, 10, 10, RED);
        File first = MeshExporter.export(mesh, ExportFormat.STL, folder.getRoot(), SETTINGS);
        File second = MeshExporter.export(mesh, ExportFormat.STL, folder.getRoot(), SETTINGS);
        assertNotEquals(first, second);
        assertTrue(first.isFile() && second.isFile());
    }

    @Test
    public void anEmptyMeshSavesNothing()
    {
        for (ExportFormat format : ExportFormat.values())
        {
            try
            {
                MeshExporter.export(new MeshBuilder("Nothing").build(), format, folder.getRoot(), SETTINGS);
                fail(format + " saved an empty mesh");
            }
            catch (IOException expected)
            {
                assertEquals(0, folder.getRoot().list().length);
            }
        }
    }

    @Test
    public void aMeshOfOnlyDegenerateTrianglesPrintsNothing()
    {
        MeshBuilder b = new MeshBuilder("Flat");
        b.addTriangle(b.addVertex(0, 0, 0), b.addVertex(1, 0, 0), b.addVertex(2, 0, 0), RED);
        try
        {
            MeshExporter.export(b.build(), ExportFormat.STL, folder.getRoot(), SETTINGS);
            fail("saved a print with nothing in it");
        }
        catch (IOException expected)
        {
            assertEquals(0, folder.getRoot().list().length);
        }
    }

    @Test
    public void colorsPickedForAnotherModelAreRefused()
    {
        MeshBuilder small = new MeshBuilder("Small");
        small.addTriangle(small.addVertex(0, 0, 0), small.addVertex(1, 0, 0), small.addVertex(0, 1, 0), RED);
        PrintColors other = PrintColors.of(small.build(), 1);
        try
        {
            MeshExporter.export(box(0, 0, 0, 10, 10, 10, RED), ExportFormat.THREE_MF, folder.getRoot(), SETTINGS,
                other);
            fail("saved with colors for another model");
        }
        catch (IOException expected)
        {
            assertEquals(0, folder.getRoot().list().length);
        }
    }
}
