package com.constsmodels;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every capture, kept so it can be opened again in any session: each one a {@link MeshFile} and a small picture of
 * it for the sidebar, side by side in a folder of their own. Deleting a capture deletes the two. Files saved from the
 * viewer are elsewhere and stay. Nothing here runs on the Swing thread.
 */
final class ModelLibrary
{
    private static final Logger log = LoggerFactory.getLogger(ModelLibrary.class);

    /** How big a capture's picture is drawn, in pixels: twice its size in the sidebar, for sharp screens. */
    static final int PICTURE = 72;

    private static final String MESH = ".cmesh";
    private static final String PNG = ".png";

    private final Filepath folder;

    /** A capture kept: what it is, when it was captured, and its two files. */
    static final class Capture
    {
        final String id;
        final String name;
        final long time;
        final int faces;
        final Filepath meshFile;
        final Filepath pictureFile;
        /** Its picture, or null when it has none. */
        final BufferedImage picture;

        Capture(String id, String name, long time, int faces, Filepath meshFile, Filepath pictureFile,
            BufferedImage picture)
        {
            this.id = id;
            this.name = name;
            this.time = time;
            this.faces = faces;
            this.meshFile = meshFile;
            this.pictureFile = pictureFile;
            this.picture = picture;
        }

        /** Reads its mesh back. */
        Mesh load() throws IOException
        {
            try (InputStream in = meshFile.openInputStream())
            {
                return MeshFile.read(in);
            }
        }

        /** Deletes its two files; false if the mesh is still there. */
        boolean delete()
        {
            deleteQuietly(pictureFile);
            deleteQuietly(meshFile);
            return !meshFile.exists();
        }
    }

    ModelLibrary(Filepath folder)
    {
        this.folder = folder;
    }

    /** Keeps a capture: writes its mesh and its picture, and returns it. */
    Capture keep(Mesh mesh, long time) throws IOException
    {
        makeFolder(folder);
        String id = unusedId(time);
        Filepath meshFile = folder.joinSegment(id + MESH);
        Filepath pictureFile = folder.joinSegment(id + PNG);
        BufferedImage picture = picture(mesh);
        try
        {
            try (OutputStream out = meshFile.openOutputStream())
            {
                MeshFile.write(mesh, time, out);
            }
            try (OutputStream out = pictureFile.openOutputStream())
            {
                ImageIO.write(picture, "png", out);
            }
        }
        catch (IOException | RuntimeException e)
        {
            deleteQuietly(meshFile);
            deleteQuietly(pictureFile);
            throw e;
        }
        return new Capture(id, mesh.name, time, mesh.faceCount(), meshFile, pictureFile, picture);
    }

    /** Every capture kept, newest first. A file that can't be read is left out. */
    List<Capture> list()
    {
        List<Capture> captures = new ArrayList<>();
        if (!folder.isDirectory())
        {
            return captures;
        }
        List<Filepath> meshFiles;
        try (Stream<Filepath> files = folder.walk(1))
        {
            meshFiles = meshFiles(files.iterator());
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Couldn't list the captures in {}", folder, e);
            return captures;
        }
        for (Filepath meshFile : meshFiles)
        {
            String id = meshFile.getFileName().substring(0, meshFile.getFileName().length() - MESH.length());
            try
            {
                MeshFile.Header header;
                try (InputStream in = meshFile.openInputStream())
                {
                    header = MeshFile.readHeader(in);
                }
                Filepath pictureFile = folder.joinSegment(id + PNG);
                BufferedImage picture = null;
                if (pictureFile.isFile())
                {
                    try (InputStream in = pictureFile.openInputStream())
                    {
                        picture = ImageIO.read(in);
                    }
                    catch (IOException e)
                    {
                        log.debug("Couldn't read the picture of {}", meshFile, e);
                    }
                }
                captures.add(new Capture(id, header.name, header.time, header.faces, meshFile, pictureFile, picture));
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't read the capture {}", meshFile, e);
            }
        }
        captures.sort(Comparator.comparingLong((Capture c) -> c.time).thenComparing(c -> c.id).reversed());
        return captures;
    }

    /** The capture files among a folder's entries. One that goes while they're read is left out, not the rest. */
    static List<Filepath> meshFiles(Iterator<Filepath> entries)
    {
        List<Filepath> found = new ArrayList<>();
        int failedInARow = 0;
        while (true)
        {
            try
            {
                if (!entries.hasNext())
                {
                    return found;
                }
                Filepath entry = entries.next();
                failedInARow = 0;
                if (entry.getFileName().endsWith(MESH))
                {
                    found.add(entry);
                }
            }
            catch (UncheckedIOException e)
            {
                // Java's walk moves on past the file that failed; should one ever not, stop rather than spin forever
                if (++failedInARow >= 1000)
                {
                    log.warn("Gave up listing the captures", e);
                    return found;
                }
                log.debug("A file went while the captures were listed", e);
            }
        }
    }

    /**
     * Makes the folder unless it's there. One that's a link to a folder counts as there: Java 11's createDirectories
     * refuses a link that's already there.
     */
    static void makeFolder(Filepath folder) throws IOException
    {
        if (!folder.isDirectory())
        {
            folder.createDirectories();
        }
    }

    /** Deletes the file if it's there; one that won't go is left, and logged. */
    static void deleteQuietly(Filepath file)
    {
        try
        {
            file.deleteIfExists();
        }
        catch (IOException e)
        {
            log.debug("Couldn't delete {}", file, e);
        }
    }

    /** The capture's picture: framed as the viewer first frames it, on the viewer's own background. */
    static BufferedImage picture(Mesh mesh)
    {
        OrbitCamera camera = new OrbitCamera();
        camera.fit(mesh, PICTURE, PICTURE);
        return new MeshRenderer(mesh).render(camera, PICTURE, PICTURE);
    }

    /** A name for a capture's files from when it was captured, with -2, -3 and so on after it if that's taken. */
    private String unusedId(long time)
    {
        String base = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date(time));
        String id = base;
        for (int n = 2; folder.joinSegment(id + MESH).exists(); n++)
        {
            id = base + "-" + n;
        }
        return id;
    }
}
