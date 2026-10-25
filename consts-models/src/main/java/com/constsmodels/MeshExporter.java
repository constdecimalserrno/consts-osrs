package com.constsmodels;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Date;
import net.runelite.client.util.Filepath;

/**
 * Writes a mesh to a file as the game has it, in the game's units: OBJ with its colors in an MTL beside it, PLY with
 * a color on every corner, or GLB with an actor's animations too, for 3D software.
 */
final class MeshExporter
{
    private MeshExporter()
    {
    }

    /** Writes the mesh into the folder, named after it and the time, and returns the file written. */
    static Filepath export(Mesh mesh, ExportFormat format, Filepath folder) throws IOException
    {
        if (mesh.isEmpty())
        {
            throw new IOException("there's nothing in it");
        }
        String name = unusedName(folder, ConstsModelsPlugin.fileName(mesh.name, new Date()), format);
        Filepath file = folder.joinSegment(name + "." + format.extension());
        Filepath mtl = format == ExportFormat.OBJ ? folder.joinSegment(name + ".mtl") : null;
        try
        {
            switch (format)
            {
                case OBJ:
                    try (Writer obj = writer(file); Writer materials = writer(mtl))
                    {
                        ObjWriter.write(mesh, mtl.getFileName(), obj, materials);
                    }
                    break;
                case PLY:
                    try (OutputStream out = new BufferedOutputStream(file.openOutputStream(), 1 << 16))
                    {
                        PlyWriter.write(mesh, out);
                    }
                    break;
                case GLB:
                    try (OutputStream out = new BufferedOutputStream(file.openOutputStream(), 1 << 16))
                    {
                        GlbWriter.write(mesh, out);
                    }
                    break;
                default:
                    throw new IOException("can't save as " + format);
            }
        }
        catch (IOException | RuntimeException e)
        {
            // Leave no half-written files behind
            ModelLibrary.deleteQuietly(file);
            if (mtl != null)
            {
                ModelLibrary.deleteQuietly(mtl);
            }
            throw e;
        }
        return file;
    }

    /** The name, or the name with " (2)", " (3)" and so on, whichever has no file of this format in the folder yet. */
    static String unusedName(Filepath folder, String name, ExportFormat format)
    {
        String candidate = name;
        for (int n = 2; taken(folder, candidate, format); n++)
        {
            candidate = name + " (" + n + ")";
        }
        return candidate;
    }

    private static boolean taken(Filepath folder, String name, ExportFormat format)
    {
        return folder.joinSegment(name + "." + format.extension()).exists()
            || (format == ExportFormat.OBJ && folder.joinSegment(name + ".mtl").exists());
    }

    /** A UTF-8 writer on the file, as Filepath opens one, buffered. */
    private static Writer writer(Filepath file) throws IOException
    {
        return new BufferedWriter(file.openWriter(), 1 << 16);
    }
}
