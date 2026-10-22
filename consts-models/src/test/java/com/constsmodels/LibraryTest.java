package com.constsmodels;

import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LibraryTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** The temporary folder as a plugin gets its own from RuneLite: a Filepath that can't leave it. */
    private Filepath root;

    @Before
    public void setUp()
    {
        root = Filepath.Unchecked.getRooted(folder.getRoot().toPath());
    }

    @Test
    public void aCaptureFileGivesBackTheSameMesh() throws IOException
    {
        Mesh box = TestMeshes.box(1, 2, 3, 40, 50, 60, RED, 0x2030C0).withName("NPC <col=ff0000>Goblin</col>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MeshFile.write(box, 1234L, out);

        MeshFile.Header header = MeshFile.readHeader(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(box.name, header.name);
        assertEquals(1234L, header.time);
        assertEquals(box.faceCount(), header.faces);

        Mesh back = MeshFile.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(box.name, back.name);
        assertArrayEquals(box.x, back.x, 0f);
        assertArrayEquals(box.y, back.y, 0f);
        assertArrayEquals(box.z, back.z, 0f);
        assertArrayEquals(box.a, back.a);
        assertArrayEquals(box.b, back.b);
        assertArrayEquals(box.c, back.c);
        assertArrayEquals(box.color, back.color);
    }

    @Test
    public void somethingThatIsntACaptureIsRefused()
    {
        try
        {
            MeshFile.read(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
            fail();
        }
        catch (IOException expected)
        {
            // Not even gzip
        }
    }

    @Test
    public void theLibraryKeepsListsOpensAndDeletes() throws IOException
    {
        // A folder not made yet lists nothing, and the first capture makes it
        Filepath captures = root.joinSegment("captures");
        ModelLibrary library = new ModelLibrary(captures);
        assertTrue(library.list().isEmpty());

        Mesh first = TestMeshes.box(0, 0, 0, 10, 10, 10, RED).withName("NPC Cow");
        Mesh second = TestMeshes.box(0, 0, 0, 20, 10, 10, RED).withName("NPC Goblin");
        ModelLibrary.Capture cow = library.keep(first, 1_000L);
        ModelLibrary.Capture goblin = library.keep(second, 2_000L);
        assertTrue(captures.isDirectory());
        assertTrue(cow.meshFile.isFile());
        assertTrue(cow.pictureFile.isFile());
        assertNotNull(cow.picture);
        assertEquals(ModelLibrary.PICTURE, cow.picture.getWidth());

        List<ModelLibrary.Capture> listed = library.list();
        assertEquals(2, listed.size());
        assertEquals("NPC Goblin", listed.get(0).name);
        assertEquals("NPC Cow", listed.get(1).name);
        assertEquals(first.faceCount(), listed.get(1).faces);
        assertNotNull(listed.get(1).picture);

        Mesh back = library.load(listed.get(1));
        assertArrayEquals(first.x, back.x, 0f);

        assertTrue(library.delete(goblin));
        assertFalse(goblin.meshFile.exists());
        assertFalse(goblin.pictureFile.exists());
        assertEquals(1, library.list().size());
    }

    @Test
    public void twoCapturesInTheSameMillisecondBothStay() throws IOException
    {
        ModelLibrary library = new ModelLibrary(root);
        Mesh box = TestMeshes.box(0, 0, 0, 10, 10, 10, RED);
        ModelLibrary.Capture one = library.keep(box, 5_000L);
        ModelLibrary.Capture two = library.keep(box, 5_000L);
        assertFalse(one.id.equals(two.id));
        assertEquals(2, library.list().size());
    }

    @Test
    public void aDamagedFileIsLeftOutOfTheList() throws IOException
    {
        ModelLibrary library = new ModelLibrary(root);
        library.keep(TestMeshes.box(0, 0, 0, 10, 10, 10, RED), 1_000L);
        root.joinSegment("broken.cmesh").write(new byte[]{9, 9, 9});
        assertEquals(1, library.list().size());
    }
}
