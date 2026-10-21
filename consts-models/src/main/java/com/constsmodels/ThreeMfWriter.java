package com.constsmodels;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a mesh as a 3MF, in millimetres, as one object with every triangle on a filament. A 3MF is a zip of XML: the
 * content types, a relationship pointing at the model, and the model.
 *
 * <p>Each piece of the mesh (its triangles joined by shared corners, {@link Pieces}) is a part of its own, and the base
 * one more, so a slicer can move or delete each: an object of its own in the file, all of them the components of the
 * one object that's printed, each triangle keeping its colors below. Bambu Studio and the newest PrusaSlicer load
 * those as the parts of one object; PrusaSlicer 2.x as objects of their own, offering to join them into one when they
 * don't all stand on the plate. A mesh in one piece with no base is written as one object, as it always was, and so is a model
 * in more than {@link #MAX_PARTS} pieces, as an area is, which would be more parts than anyone could pick through.
 *
 * <p>Slicers each read colors their own way, so each filament's color is written three times, all valid 3MF:
 * <ul>
 * <li>a core-spec {@code basematerials} group, one base per filament, which the object defaults to;</li>
 * <li>a Materials extension {@code m:colorgroup} with the same colors, which every triangle points at by its
 * filament's index. Bambu Studio reads this (not basematerials) and offers to map the colors to its filaments, as it
 * does for a colored OBJ;</li>
 * <li>a {@code slic3rpe:mmu_segmentation} on each triangle, in PrusaSlicer's own namespace, which PrusaSlicer 2.x reads
 * as multi-material painting: filament 1 on extruder 1 and so on.</li>
 * </ul>
 */
final class ThreeMfWriter
{
    static final String CORE = "http://schemas.microsoft.com/3dmanufacturing/core/2015/02";
    static final String MATERIAL = "http://schemas.microsoft.com/3dmanufacturing/material/2015/02";
    static final String SLIC3RPE = "http://schemas.slic3r.org/3mf/2017/06";
    static final String MODEL_PATH = "3D/3dmodel.model";

    private static final int BASE_MATERIALS_ID = 1;
    private static final int COLOR_GROUP_ID = 2;
    /** The first object; with parts, each is an object from here on, and the one made of them comes last. */
    private static final int OBJECT_ID = 3;
    /** The most pieces a model is saved in as parts; with more it's one part. */
    static final int MAX_PARTS = 100;

    /** PrusaSlicer's painting codes reach filament 16 in the format every 2.x version reads. */
    static final int MAX_PAINTED = 16;

    private ThreeMfWriter()
    {
    }

    /**
     * Writes the mesh with each filament's color (0xRRGGBB, filament 1 first) and each triangle's filament (an index
     * into the palette). The stream is finished as a zip but left open.
     */
    static void write(Mesh mesh, int[] palette, int[] filaments, OutputStream out) throws IOException
    {
        write(mesh, palette, filaments, mesh.faceCount(), out);
    }

    /**
     * Writes the mesh as {@link #write(Mesh, int[], int[], OutputStream)} does, its triangles from modelFaces on a
     * base: a part of its own.
     */
    static void write(Mesh mesh, int[] palette, int[] filaments, int modelFaces, OutputStream out) throws IOException
    {
        if (palette.length == 0)
        {
            throw new IllegalArgumentException("a 3MF needs at least one color");
        }
        if (filaments.length != mesh.faceCount())
        {
            throw new IllegalArgumentException("every triangle needs a filament");
        }
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        Writer text = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8), 1 << 16);

        zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
        text.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n"
            + " <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
            + " <Default Extension=\"model\" ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/>\n"
            + "</Types>\n");
        text.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("_rels/.rels"));
        text.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
            + " <Relationship Target=\"/" + MODEL_PATH + "\" Id=\"rel0\""
            + " Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"/>\n"
            + "</Relationships>\n");
        text.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry(MODEL_PATH));
        writeModel(mesh, palette, filaments, parts(mesh, modelFaces), modelFaces < mesh.faceCount(), text);
        text.flush();
        zip.closeEntry();
        zip.finish();
        zip.flush();
    }

    /**
     * Each triangle's part: the model's pieces in the order their first triangles come, or the whole model in one if
     * it has more than {@link #MAX_PARTS}, then the base, from modelFaces on, if there is one.
     */
    static int[] parts(Mesh mesh, int modelFaces)
    {
        Mesh model = Pieces.only(mesh, range(Math.min(modelFaces, mesh.faceCount())));
        Pieces pieces = Pieces.ofCorners(model);
        boolean split = pieces.count <= MAX_PARTS;
        int modelParts = split ? pieces.count : Math.min(1, model.faceCount());
        int[] part = new int[mesh.faceCount()];
        for (int f = 0; f < part.length; f++)
        {
            part[f] = f >= model.faceCount() ? modelParts : split ? pieces.piece[f] : 0;
        }
        return part;
    }

    private static int[] range(int n)
    {
        int[] range = new int[n];
        for (int i = 0; i < n; i++)
        {
            range[i] = i;
        }
        return range;
    }

    private static void writeModel(Mesh mesh, int[] palette, int[] filaments, int[] part, boolean base, Writer out)
        throws IOException
    {
        String name = xml(mesh.name == null ? "Model" : mesh.name);
        StringBuilder line = new StringBuilder(256);
        line.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"").append(CORE)
            .append("\" xmlns:m=\"").append(MATERIAL)
            .append("\" xmlns:slic3rpe=\"").append(SLIC3RPE).append("\">\n")
            .append(" <metadata name=\"Title\">").append(name).append("</metadata>\n")
            .append(" <metadata name=\"Application\">consts-Models</metadata>\n")
            .append(" <resources>\n")
            .append("  <basematerials id=\"").append(BASE_MATERIALS_ID).append("\">\n");
        for (int i = 0; i < palette.length; i++)
        {
            line.append("   <base name=\"Filament ").append(i + 1).append("\" displaycolor=\"")
                .append(hex(palette[i])).append("\"/>\n");
        }
        line.append("  </basematerials>\n")
            .append("  <m:colorgroup id=\"").append(COLOR_GROUP_ID).append("\">\n");
        for (int rgb : palette)
        {
            line.append("   <m:color color=\"").append(hex(rgb)).append("\"/>\n");
        }
        line.append("  </m:colorgroup>\n");
        out.append(line);

        for (int f = 0; f < mesh.faceCount(); f++)
        {
            if (filaments[f] < 0 || filaments[f] >= palette.length)
            {
                throw new IllegalArgumentException("triangle " + f + " is on filament " + filaments[f]
                    + " of " + palette.length);
            }
        }

        // Each part's triangles, in order
        int parts = 0;
        for (int p : part)
        {
            parts = Math.max(parts, p + 1);
        }
        int[] start = new int[parts + 1];
        for (int p : part)
        {
            start[p + 1]++;
        }
        for (int p = 0; p < parts; p++)
        {
            start[p + 1] += start[p];
        }
        int[] faces = new int[part.length];
        int[] fill = Arrays.copyOf(start, parts);
        for (int f = 0; f < part.length; f++)
        {
            faces[fill[part[f]]++] = f;
        }

        int[] local = new int[mesh.vertexCount()];
        Arrays.fill(local, -1);
        for (int p = 0; p < parts; p++)
        {
            String partName = parts == 1 ? name : base && p == parts - 1 ? "Base" : name + " " + (p + 1);
            writeObject(mesh, palette, filaments, Arrays.copyOfRange(faces, start[p], start[p + 1]), OBJECT_ID + p,
                partName, local, out);
        }

        int printed = OBJECT_ID;
        if (parts > 1)
        {
            printed = OBJECT_ID + parts;
            out.write("  <object id=\"" + printed + "\" name=\"" + name + "\" type=\"model\">\n   <components>\n");
            for (int p = 0; p < parts; p++)
            {
                out.write("    <component objectid=\"" + (OBJECT_ID + p) + "\"/>\n");
            }
            out.write("   </components>\n  </object>\n");
        }
        out.write(" </resources>\n <build>\n  <item objectid=\"" + printed + "\"/>\n </build>\n</model>\n");
    }

    /** One object of the mesh's triangles given, its corners numbered afresh from 0 (local is left all -1 again). */
    private static void writeObject(Mesh mesh, int[] palette, int[] filaments, int[] faces, int id, String name,
        int[] local, Writer out) throws IOException
    {
        StringBuilder line = new StringBuilder(256);
        line.append("  <object id=\"").append(id).append("\" name=\"").append(name)
            .append("\" type=\"model\" pid=\"").append(BASE_MATERIALS_ID).append("\" pindex=\"0\">\n")
            .append("   <mesh>\n")
            .append("    <vertices>\n");
        out.append(line);

        int[] order = new int[3 * faces.length];
        int vertices = 0;
        for (int f : faces)
        {
            for (int v : new int[]{mesh.a[f], mesh.b[f], mesh.c[f]})
            {
                if (local[v] < 0)
                {
                    local[v] = vertices;
                    order[vertices++] = v;
                }
            }
        }
        for (int i = 0; i < vertices; i++)
        {
            int v = order[i];
            line.setLength(0);
            line.append("     <vertex x=\"");
            DecimalText.append(line, mesh.x[v]).append("\" y=\"");
            DecimalText.append(line, mesh.y[v]).append("\" z=\"");
            DecimalText.append(line, mesh.z[v]).append("\"/>\n");
            out.append(line);
        }
        out.write("    </vertices>\n    <triangles>\n");

        boolean painted = palette.length > 1;
        for (int f : faces)
        {
            int filament = filaments[f];
            line.setLength(0);
            line.append("     <triangle v1=\"").append(local[mesh.a[f]])
                .append("\" v2=\"").append(local[mesh.b[f]])
                .append("\" v3=\"").append(local[mesh.c[f]])
                .append("\" pid=\"").append(COLOR_GROUP_ID)
                .append("\" p1=\"").append(filament).append('"');
            if (painted && filament < MAX_PAINTED)
            {
                line.append(" slic3rpe:mmu_segmentation=\"").append(paintCode(filament + 1)).append('"');
            }
            line.append("/>\n");
            out.append(line);
        }
        out.write("    </triangles>\n   </mesh>\n  </object>\n");
        for (int i = 0; i < vertices; i++)
        {
            local[order[i]] = -1;
        }
    }

    /**
     * PrusaSlicer's code for a whole triangle painted with an extruder, 1 to {@link #MAX_PAINTED}: its painting
     * stream (2 bits for no split, then the extruder in 2 bits, or 0b11 and 4 more bits for extruder 3 and up) as hex
     * digits, last digit first. Bambu Studio's paint_color uses the same codes.
     */
    static String paintCode(int extruder)
    {
        if (extruder < 1 || extruder > MAX_PAINTED)
        {
            throw new IllegalArgumentException("extruder " + extruder);
        }
        if (extruder < 3)
        {
            return Integer.toString(extruder << 2);
        }
        return Character.toUpperCase(Character.forDigit(extruder - 3, 16)) + "C";
    }

    static String hex(int rgb)
    {
        String digits = Integer.toHexString((rgb & 0xFFFFFF) | 0x1000000).substring(1).toUpperCase();
        return "#" + digits;
    }

    /** Text safe inside XML: markup characters escaped and characters XML can't hold left out. */
    static String xml(String text)
    {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            switch (ch)
            {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    if (ch >= 0x20 && ch != 0xFFFE && ch != 0xFFFF && !Character.isSurrogate(ch))
                    {
                        out.append(ch);
                    }
                    else if (Character.isHighSurrogate(ch) && i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1)))
                    {
                        out.append(ch).append(text.charAt(++i));
                    }
                    break;
            }
        }
        return out.toString();
    }
}
