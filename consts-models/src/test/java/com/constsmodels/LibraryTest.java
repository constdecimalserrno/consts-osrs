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
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import net.runelite.client.util.Filepath;
import org.junit.AssumptionViolatedException;
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
    public void aCaptureFileKeepsTheClips() throws IOException
    {
        Mesh animated = TestMeshes.animated(TestMeshes.box(1, 2, 3, 40, 50, 60, RED));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MeshFile.write(animated, 1234L, out);

        assertEquals(animated.faceCount(), MeshFile.readHeader(new ByteArrayInputStream(out.toByteArray())).faces);
        Mesh back = MeshFile.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(2, back.clips.size());
        for (int i = 0; i < 2; i++)
        {
            Clip clip = animated.clips.get(i);
            Clip read = back.clips.get(i);
            assertEquals(clip.label, read.label);
            assertEquals(clip.animation, read.animation);
            assertArrayEquals(clip.ticks, read.ticks);
            assertEquals(clip.loopFrom, read.loopFrom);
            for (int f = 0; f < clip.frameCount(); f++)
            {
                assertArrayEquals(clip.x[f], read.x[f], 0f);
                assertArrayEquals(clip.y[f], read.y[f], 0f);
                assertArrayEquals(clip.z[f], read.z[f], 0f);
            }
        }
    }

    @Test
    public void aCaptureKeptBeforeClipsStillOpens() throws IOException
    {
        // CMD1, as 1.1 wrote it: one triangle and nothing after it
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(out)))
        {
            data.writeInt(0x434D4431);
            data.writeUTF("NPC Cow");
            data.writeLong(99L);
            data.writeInt(1);
            data.writeInt(3);
            for (int i = 0; i < 9; i++)
            {
                data.writeFloat(i);
            }
            data.writeInt(0);
            data.writeInt(1);
            data.writeInt(2);
            data.writeInt(RED);
        }
        assertEquals("NPC Cow", MeshFile.readHeader(new ByteArrayInputStream(out.toByteArray())).name);
        Mesh back = MeshFile.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(1, back.faceCount());
        assertEquals(5f, back.z[1], 0f);
        assertTrue(back.clips.isEmpty());
    }

    @Test
    public void aCaptureAskingForEndlessFramesIsDamaged() throws IOException
    {
        // CMD2 with no corners and a clip of two billion frames: refused, not a try at the memory
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(out)))
        {
            data.writeInt(0x434D4432);
            data.writeUTF("NPC Cow");
            data.writeLong(99L);
            data.writeInt(0);
            data.writeInt(0);
            data.writeInt(1);
            data.writeUTF("Idle");
            data.writeInt(808);
            data.writeInt(0);
            data.writeInt(2_000_000_000);
        }
        try
        {
            MeshFile.read(new ByteArrayInputStream(out.toByteArray()));
            fail();
        }
        catch (IOException expected)
        {
            assertEquals("it's damaged", expected.getMessage());
        }
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

        Mesh back = listed.get(1).load();
        assertArrayEquals(first.x, back.x, 0f);

        assertTrue(goblin.delete());
        assertFalse(goblin.meshFile.exists());
        assertFalse(goblin.pictureFile.exists());
        assertEquals(1, library.list().size());
    }

    @Test
    public void aFolderThatsALinkToAnotherIsUsedAsItIs() throws IOException
    {
        // A player's folder can be a link to one elsewhere, and Java 11 won't make a folder that's a link already
        Path elsewhere = folder.newFolder("elsewhere").toPath();
        Path link;
        try
        {
            link = Files.createSymbolicLink(elsewhere.resolveSibling("link"), elsewhere);
        }
        catch (IOException | UnsupportedOperationException e)
        {
            // Windows makes links only in Developer Mode or for an administrator
            throw new AssumptionViolatedException("this computer can't make a link", e);
        }
        ModelLibrary library = new ModelLibrary(Filepath.Unchecked.getRooted(link));
        ModelLibrary.Capture cow = library.keep(TestMeshes.box(0, 0, 0, 10, 10, 10, RED), 1_000L);
        assertTrue(Files.isRegularFile(elsewhere.resolve(cow.id + ".cmesh")));
        assertTrue(Files.isRegularFile(elsewhere.resolve(cow.id + ".png")));
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
    public void aFileThatGoesWhileTheyreListedIsLeftOutNotTheRest()
    {
        Filepath cow = root.joinSegment("cow.cmesh");
        Filepath goblin = root.joinSegment("goblin.cmesh");
        List<Object> walk = List.of(cow, new UncheckedIOException(new IOException("gone")), root.joinSegment("cow.png"),
            goblin);
        Iterator<Filepath> entries = new Iterator<>()
        {
            private int at;

            @Override
            public boolean hasNext()
            {
                // As Files.walk does for a file deleted between being found and being read, then it goes on
                if (at < walk.size() && walk.get(at) instanceof UncheckedIOException)
                {
                    throw (UncheckedIOException) walk.get(at++);
                }
                return at < walk.size();
            }

            @Override
            public Filepath next()
            {
                return (Filepath) walk.get(at++);
            }
        };
        assertEquals(List.of(cow, goblin), ModelLibrary.meshFiles(entries));

        // A walk that never moves past its failure ends all the same
        Iterator<Filepath> stuck = new Iterator<>()
        {
            @Override
            public boolean hasNext()
            {
                throw new UncheckedIOException(new IOException("stuck"));
            }

            @Override
            public Filepath next()
            {
                throw new AssertionError();
            }
        };
        assertEquals(List.of(), ModelLibrary.meshFiles(stuck));
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
