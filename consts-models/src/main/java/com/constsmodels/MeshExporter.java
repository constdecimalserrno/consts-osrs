package com.constsmodels;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Writes a mesh to a file as the game has it, in the game's units: OBJ with its colors in an MTL beside it, or PLY with
 * a color on every corner, for 3D software.
 */
final class MeshExporter
{
    private MeshExporter()
    {
    }

    /** Writes the mesh into the folder, named after it and the time, and returns the file written. */
    static File export(Mesh mesh, ExportFormat format, File folder) throws IOException
    {
        if (mesh.isEmpty())
        {
            throw new IOException("there's nothing in it");
        }
        String name = unusedName(folder, ConstsModelsPlugin.fileName(mesh.name, new Date()), format);
        File file = new File(folder, name + "." + format.extension());
        File mtl = format == ExportFormat.OBJ ? new File(folder, name + ".mtl") : null;
        try
        {
            switch (format)
            {
                case OBJ:
                    try (Writer obj = writer(file); Writer materials = writer(mtl))
                    {
                        ObjWriter.write(mesh, mtl.getName(), obj, materials);
                    }
                    break;
                case PLY:
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16))
                    {
                        PlyWriter.write(mesh, out);
                    }
                    break;
                default:
                    throw new IOException("can't save as " + format);
            }
        }
        catch (IOException | RuntimeException e)
        {
            // Leave no half-written files behind
            file.delete();
            if (mtl != null)
            {
                mtl.delete();
            }
            throw e;
        }
        return file;
    }

    /** The name, or the name with " (2)", " (3)" and so on, whichever has no file of this format in the folder yet. */
    static String unusedName(File folder, String name, ExportFormat format)
    {
        String candidate = name;
        for (int n = 2; taken(folder, candidate, format); n++)
        {
            candidate = name + " (" + n + ")";
        }
        return candidate;
    }

    private static boolean taken(File folder, String name, ExportFormat format)
    {
        return new File(folder, name + "." + format.extension()).exists()
            || (format == ExportFormat.OBJ && new File(folder, name + ".mtl").exists());
    }

    private static Writer writer(File file) throws IOException
    {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), 1 << 16);
    }
}
