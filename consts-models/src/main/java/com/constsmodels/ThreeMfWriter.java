package com.constsmodels;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a mesh as a 3MF, in millimetres, as one object with every triangle on a filament. A 3MF is a zip of XML: the
 * content types, a relationship pointing at the model, and the model.
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
    private static final int OBJECT_ID = 3;
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
        writeModel(mesh, palette, filaments, text);
        text.flush();
        zip.closeEntry();
        zip.finish();
        zip.flush();
    }

    private static void writeModel(Mesh mesh, int[] palette, int[] filaments, Writer out) throws IOException
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
        line.append("  </m:colorgroup>\n")
            .append("  <object id=\"").append(OBJECT_ID).append("\" name=\"").append(name)
            .append("\" type=\"model\" pid=\"").append(BASE_MATERIALS_ID).append("\" pindex=\"0\">\n")
            .append("   <mesh>\n")
            .append("    <vertices>\n");
        out.append(line);

        for (int v = 0; v < mesh.vertexCount(); v++)
        {
            line.setLength(0);
            line.append("     <vertex x=\"");
            DecimalText.append(line, mesh.x[v]).append("\" y=\"");
            DecimalText.append(line, mesh.y[v]).append("\" z=\"");
            DecimalText.append(line, mesh.z[v]).append("\"/>\n");
            out.append(line);
        }
        out.write("    </vertices>\n    <triangles>\n");

        boolean painted = palette.length > 1;
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            int filament = filaments[f];
            if (filament < 0 || filament >= palette.length)
            {
                throw new IllegalArgumentException("triangle " + f + " is on filament " + filament
                    + " of " + palette.length);
            }
            line.setLength(0);
            line.append("     <triangle v1=\"").append(mesh.a[f])
                .append("\" v2=\"").append(mesh.b[f])
                .append("\" v3=\"").append(mesh.c[f])
                .append("\" pid=\"").append(COLOR_GROUP_ID)
                .append("\" p1=\"").append(filament).append('"');
            if (painted && filament < MAX_PAINTED)
            {
                line.append(" slic3rpe:mmu_segmentation=\"").append(paintCode(filament + 1)).append('"');
            }
            line.append("/>\n");
            out.append(line);
        }
        out.write("    </triangles>\n   </mesh>\n  </object>\n </resources>\n"
            + " <build>\n  <item objectid=\"" + OBJECT_ID + "\"/>\n </build>\n</model>\n");
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
