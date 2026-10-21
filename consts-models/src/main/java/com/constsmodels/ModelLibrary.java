package com.constsmodels;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;
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

    private final File folder;

    /** A capture kept: what it is, when it was captured, and its two files. */
    static final class Capture
    {
        final String id;
        final String name;
        final long time;
        final int faces;
        final File meshFile;
        final File pictureFile;
        /** Its picture, or null when it has none. */
        final BufferedImage picture;

        Capture(String id, String name, long time, int faces, File meshFile, File pictureFile, BufferedImage picture)
        {
            this.id = id;
            this.name = name;
            this.time = time;
            this.faces = faces;
            this.meshFile = meshFile;
            this.pictureFile = pictureFile;
            this.picture = picture;
        }
    }

    ModelLibrary(File folder)
    {
        this.folder = folder;
    }

    File folder()
    {
        return folder;
    }

    /** Keeps a capture: writes its mesh and its picture, and returns it. */
    Capture keep(Mesh mesh, long time) throws IOException
    {
        if (!folder.isDirectory() && !folder.mkdirs())
        {
            throw new IOException("couldn't make " + folder);
        }
        String id = unusedId(time);
        File meshFile = new File(folder, id + MESH);
        File pictureFile = new File(folder, id + PNG);
        BufferedImage picture = picture(mesh);
        try
        {
            MeshFile.write(mesh, time, new FileOutputStream(meshFile));
            ImageIO.write(picture, "png", pictureFile);
        }
        catch (IOException | RuntimeException e)
        {
            meshFile.delete();
            pictureFile.delete();
            throw e;
        }
        return new Capture(id, mesh.name, time, mesh.faceCount(), meshFile, pictureFile, picture);
    }

    /** Every capture kept, newest first. A file that can't be read is left out. */
    List<Capture> list()
    {
        List<Capture> captures = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(MESH));
        if (files == null)
        {
            return captures;
        }
        for (File meshFile : files)
        {
            String id = meshFile.getName().substring(0, meshFile.getName().length() - MESH.length());
            try
            {
                MeshFile.Header header = MeshFile.readHeader(new FileInputStream(meshFile));
                File pictureFile = new File(folder, id + PNG);
                BufferedImage picture = null;
                if (pictureFile.isFile())
                {
                    try
                    {
                        picture = ImageIO.read(pictureFile);
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

    /** Reads a capture's mesh back. */
    Mesh load(Capture capture) throws IOException
    {
        return MeshFile.read(new FileInputStream(capture.meshFile));
    }

    /** Deletes a capture's two files; false if the mesh is still there. */
    boolean delete(Capture capture)
    {
        capture.pictureFile.delete();
        return capture.meshFile.delete() || !capture.meshFile.exists();
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
        for (int n = 2; new File(folder, id + MESH).exists(); n++)
        {
            id = base + "-" + n;
        }
        return id;
    }
}
