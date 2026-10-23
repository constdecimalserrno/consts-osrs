package com.constsmodels;

import static com.constsmodels.TestMeshes.RED;
import static com.constsmodels.TestMeshes.box;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Date;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MeshExporterTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** The temporary folder as RuneLite hands a plugin a folder: a Filepath that can't leave it. */
    private Filepath root;

    @Before
    public void setUp()
    {
        root = Filepath.Unchecked.getRooted(folder.getRoot().toPath());
    }

    @Test
    public void filesAreNamedAfterTheModelAndTheTime() throws IOException
    {
        Mesh mesh = box(0, 0, 0, 10, 10, 10, RED).withName("NPC <col=ffff00>Goblin</col>");
        for (ExportFormat format : ExportFormat.values())
        {
            String before = ConstsModelsPlugin.fileName(mesh.name, new Date());
            Filepath file = MeshExporter.export(mesh, format, root);
            String after = ConstsModelsPlugin.fileName(mesh.name, new Date());
            String name = file.getFileName();
            assertTrue(name, name.equals(before + "." + format.extension())
                || name.equals(after + "." + format.extension()));
            assertEquals(root, file.getParent());
            assertTrue(file.size() > 0);
        }
    }

    @Test
    public void aTakenNameGetsANumber() throws IOException
    {
        root.joinSegment("Box.ply").write(new byte[0]);
        assertEquals("Box", MeshExporter.unusedName(root, "Box", ExportFormat.OBJ));
        assertEquals("Box (2)", MeshExporter.unusedName(root, "Box", ExportFormat.PLY));
        root.joinSegment("Box (2).ply").write(new byte[0]);
        assertEquals("Box (3)", MeshExporter.unusedName(root, "Box", ExportFormat.PLY));
        // An OBJ's materials file counts too
        root.joinSegment("Box.mtl").write(new byte[0]);
        assertEquals("Box (2)", MeshExporter.unusedName(root, "Box", ExportFormat.OBJ));
    }

    @Test
    public void savingTwiceKeepsBothFiles() throws IOException
    {
        Mesh mesh = box(0, 0, 0, 10, 10, 10, RED);
        Filepath first = MeshExporter.export(mesh, ExportFormat.PLY, root);
        Filepath second = MeshExporter.export(mesh, ExportFormat.PLY, root);
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
                MeshExporter.export(new MeshBuilder("Nothing").build(), format, root);
                fail(format + " saved an empty mesh");
            }
            catch (IOException expected)
            {
                assertEquals(0, folder.getRoot().list().length);
            }
        }
    }
}
