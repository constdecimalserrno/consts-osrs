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
 * Writes a mesh to a file. STL and 3MF are made ready to print by {@link PrintPrep}: in millimetres, the longest side
 * {@link PrintSettings#sizeMm}, centered and stood on the plate, on a base if asked. A 3MF prints in filament colors
 * from {@link PrintColors}, its base in the darkest of them. OBJ (with its MTL) and PLY keep the game's units and the
 * model's own colors, for 3D software.
 */
final class MeshExporter
{
    private MeshExporter()
    {
    }

    /** Writes the mesh into the folder, named after it and the time, and returns the file written. */
    static File export(Mesh mesh, ExportFormat format, File folder, PrintSettings settings) throws IOException
    {
        return export(mesh, format, folder, settings, null);
    }

    /**
     * Writes the mesh into the folder, named after it and the time, and returns the file written. A 3MF prints in
     * the colors given, which must have been made for this mesh; null merges the model's colors down to
     * {@link PrintSettings#maxColors}. Other formats ignore them.
     */
    static File export(Mesh mesh, ExportFormat format, File folder, PrintSettings settings, PrintColors colors)
        throws IOException
    {
        if (mesh.isEmpty())
        {
            throw new IOException("there's nothing in it");
        }
        String name = unusedName(folder, ConstsModelsPlugin.fileName(mesh.name, new Date()), format);
        File file = new File(folder, name + "." + format.extension());

        PrintPrep.Print print = null;
        // With Make solid, the print's triangles come from the solid, not the model
        Mesh solid = null;
        if (format == ExportFormat.STL || format == ExportFormat.THREE_MF)
        {
            if (settings.solid)
            {
                solid = SolidRemesh.makeSolid(mesh, settings.sizeMm);
                print = PrintPrep.prepareSolid(solid, settings);
            }
            else
            {
                print = PrintPrep.prepare(mesh, settings);
            }
            if (print.modelFaces == 0)
            {
                throw new IOException("it has no faces solid enough to print");
            }
        }

        File mtl = format == ExportFormat.OBJ ? new File(folder, name + ".mtl") : null;
        try
        {
            switch (format)
            {
                case STL:
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16))
                    {
                        StlWriter.write(print.mesh, out);
                    }
                    break;
                case THREE_MF:
                    PrintColors filaments = colors != null ? colors : PrintColors.of(mesh, settings.maxColors);
                    int[] palette = filaments.palette();
                    int base = PrintPrep.darkest(palette);
                    if (filaments.faceCount() != mesh.faceCount())
                    {
                        throw new IOException("its colors were picked for a different model");
                    }
                    int[] solidFilaments = solid == null ? null : filaments.filamentsFor(solid);
                    int[] onFilament = new int[print.mesh.faceCount()];
                    for (int f = 0; f < onFilament.length; f++)
                    {
                        int source = print.source[f];
                        if (source < 0)
                        {
                            onFilament[f] = base;
                        }
                        else
                        {
                            onFilament[f] = solidFilaments != null ? solidFilaments[source] : filaments.filament(source);
                        }
                    }
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16))
                    {
                        ThreeMfWriter.write(print.mesh, palette, onFilament, out);
                    }
                    break;
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
