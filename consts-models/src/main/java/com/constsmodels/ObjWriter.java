package com.constsmodels;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a mesh as Wavefront OBJ with an MTL beside it, for Blender and other 3D software. It keeps the game's units
 * (128 to a tile) and turns the mesh Y up, as OBJ expects: OBJ's x is east, y up and -z north, which Blender's
 * importer turns back into x east, y north, z up. Each color is a material named after it, {@code c_RRGGBB}.
 */
final class ObjWriter
{
    private ObjWriter()
    {
    }

    /** Writes the OBJ and its MTL; the OBJ names the MTL as mtlFileName, which should sit beside it. */
    static void write(Mesh mesh, String mtlFileName, Writer obj, Writer mtl) throws IOException
    {
        String name = oneLine(mesh.name == null || mesh.name.isEmpty() ? "Model" : mesh.name);
        StringBuilder line = new StringBuilder(128);
        line.append("# consts-Models: ").append(name).append('\n')
            .append("# Game units, 128 to a tile; y up\n")
            .append("mtllib ").append(oneLine(mtlFileName)).append('\n')
            .append("o ").append(name).append('\n');
        obj.append(line);

        for (int v = 0; v < mesh.vertexCount(); v++)
        {
            line.setLength(0);
            line.append("v ");
            DecimalText.append(line, mesh.x[v]).append(' ');
            DecimalText.append(line, mesh.z[v]).append(' ');
            DecimalText.append(line, -mesh.y[v]).append('\n');
            obj.append(line);
        }

        // Faces grouped by color, in their own order within each, so each material is set once
        int faces = mesh.faceCount();
        long[] order = new long[faces];
        for (int f = 0; f < faces; f++)
        {
            order[f] = ((long) (mesh.color[f] & 0xFFFFFF) << 32) | f;
        }
        Arrays.sort(order);
        Map<Integer, String> materials = new LinkedHashMap<>();
        int current = -1;
        for (long entry : order)
        {
            int f = (int) entry;
            int rgb = (int) (entry >>> 32);
            line.setLength(0);
            if (rgb != current)
            {
                String material = String.format("c_%06X", rgb);
                materials.put(rgb, material);
                line.append("usemtl ").append(material).append('\n');
                current = rgb;
            }
            line.append("f ").append(mesh.a[f] + 1).append(' ').append(mesh.b[f] + 1).append(' ')
                .append(mesh.c[f] + 1).append('\n');
            obj.append(line);
        }

        mtl.write("# consts-Models: colors for " + name + "\n");
        for (Map.Entry<Integer, String> material : materials.entrySet())
        {
            int rgb = material.getKey();
            line.setLength(0);
            line.append("\nnewmtl ").append(material.getValue()).append('\n').append("Kd ");
            DecimalText.append(line, ((rgb >> 16) & 0xFF) / 255.0).append(' ');
            DecimalText.append(line, ((rgb >> 8) & 0xFF) / 255.0).append(' ');
            DecimalText.append(line, (rgb & 0xFF) / 255.0).append('\n')
                .append("Ka 0 0 0\nKs 0 0 0\nd 1\nillum 1\n");
            mtl.append(line);
        }
    }

    private static String oneLine(String text)
    {
        return text.replaceAll("[\\r\\n\\t]+", " ").trim();
    }
}
