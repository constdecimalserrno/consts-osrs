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
 * Writes a mesh to a file, as the viewer's Game view or Print view shows it.
 *
 * <p>From the Game view, the model as the game has it: STL and 3MF are scaled and placed by {@link PrintPrep}, in
 * millimetres, the longest side {@link PrintSettings#sizeMm}, stood on the plate, on a base if asked, a 3MF in
 * filament colors from {@link PrintColors} with its base in the darkest; OBJ (with its MTL) and PLY keep the game's
 * units and the model's own colors, for 3D software.
 *
 * <p>From the Print view, a {@link Printable}: the closed solid that prints as is, in millimetres, in the filaments'
 * colors, whatever the format.
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
        if (format == ExportFormat.STL || format == ExportFormat.THREE_MF)
        {
            print = PrintPrep.prepare(mesh, settings);
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
                    int[] onFilament = new int[print.mesh.faceCount()];
                    for (int f = 0; f < onFilament.length; f++)
                    {
                        int source = print.source[f];
                        onFilament[f] = source < 0 ? base : filaments.filament(source);
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

    /**
     * Writes the print, as the Print view shows it, into the folder, named after the model, "print" and the time,
     * and returns the file written. STL and 3MF are ready for a slicer, a 3MF on the filaments chosen; OBJ and PLY
     * hold the same solid in millimetres, in the filaments' colors.
     */
    static File export(Printable printable, PrintColors colors, ExportFormat format, File folder) throws IOException
    {
        if (printable.isEmpty())
        {
            throw new IOException("it has no faces solid enough to print");
        }
        String name = unusedName(folder, ConstsModelsPlugin.fileName(printable.model.name + " print", new Date()), format);
        File file = new File(folder, name + "." + format.extension());
        File mtl = format == ExportFormat.OBJ ? new File(folder, name + ".mtl") : null;
        Mesh mesh = printable.print.mesh;
        try
        {
            switch (format)
            {
                case STL:
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16))
                    {
                        StlWriter.write(mesh, out);
                    }
                    break;
                case THREE_MF:
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16))
                    {
                        ThreeMfWriter.write(mesh, colors.palette(), printable.filaments(colors), out);
                    }
                    break;
                case OBJ:
                    try (Writer obj = writer(file); Writer materials = writer(mtl))
                    {
                        ObjWriter.write(printable.colored(colors), mtl.getName(), obj, materials);
                    }
                    break;
                case PLY:
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16))
                    {
                        PlyWriter.write(printable.colored(colors), out);
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
