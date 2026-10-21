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

    @Test
    public void filesAreNamedAfterTheModelAndTheTime() throws IOException
    {
        Mesh mesh = box(0, 0, 0, 10, 10, 10, RED).withName("NPC <col=ffff00>Goblin</col>");
        for (ExportFormat format : ExportFormat.values())
        {
            String before = ConstsModelsPlugin.fileName(mesh.name, new Date());
            File file = MeshExporter.export(mesh, format, folder.getRoot());
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
        assertTrue(new File(root, "Box.ply").createNewFile());
        assertEquals("Box", MeshExporter.unusedName(root, "Box", ExportFormat.OBJ));
        assertEquals("Box (2)", MeshExporter.unusedName(root, "Box", ExportFormat.PLY));
        assertTrue(new File(root, "Box (2).ply").createNewFile());
        assertEquals("Box (3)", MeshExporter.unusedName(root, "Box", ExportFormat.PLY));
        // An OBJ's materials file counts too
        assertTrue(new File(root, "Box.mtl").createNewFile());
        assertEquals("Box (2)", MeshExporter.unusedName(root, "Box", ExportFormat.OBJ));
    }

    @Test
    public void savingTwiceKeepsBothFiles() throws IOException
    {
        Mesh mesh = box(0, 0, 0, 10, 10, 10, RED);
        File first = MeshExporter.export(mesh, ExportFormat.PLY, folder.getRoot());
        File second = MeshExporter.export(mesh, ExportFormat.PLY, folder.getRoot());
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
                MeshExporter.export(new MeshBuilder("Nothing").build(), format, folder.getRoot());
                fail(format + " saved an empty mesh");
            }
            catch (IOException expected)
            {
                assertEquals(0, folder.getRoot().list().length);
            }
        }
    }
}
