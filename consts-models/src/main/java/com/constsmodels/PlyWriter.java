package com.constsmodels;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Writes a mesh as binary little-endian PLY: float x, y, z and uchar red, green, blue for each corner, then each
 * face as three corner indices. Every face gets corners of its own, so it keeps its one color. It keeps the game's
 * units and the mesh's axes (x east, y north, z up), which Blender's importer takes as they are.
 */
final class PlyWriter
{
    static final int VERTEX_BYTES = 15;
    static final int FACE_BYTES = 13;

    private PlyWriter()
    {
    }

    static void write(Mesh mesh, OutputStream out) throws IOException
    {
        int faces = mesh.faceCount();
        String name = mesh.name == null ? "" : mesh.name.replaceAll("[^\\x20-\\x7E]", "?");
        String header = "ply\n"
            + "format binary_little_endian 1.0\n"
            + "comment consts-Models: " + name + "\n"
            + "comment Game units, 128 to a tile; z up\n"
            + "element vertex " + (faces * 3L) + "\n"
            + "property float x\n"
            + "property float y\n"
            + "property float z\n"
            + "property uchar red\n"
            + "property uchar green\n"
            + "property uchar blue\n"
            + "element face " + faces + "\n"
            + "property list uchar int vertex_indices\n"
            + "end_header\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));

        ByteBuffer buffer = ByteBuffer.allocate(VERTEX_BYTES * 3 * 1024).order(ByteOrder.LITTLE_ENDIAN);
        for (int f = 0; f < faces; f++)
        {
            if (buffer.remaining() < VERTEX_BYTES * 3)
            {
                drain(buffer, out);
            }
            int rgb = mesh.color[f];
            for (int v : new int[]{mesh.a[f], mesh.b[f], mesh.c[f]})
            {
                buffer.putFloat(mesh.x[v]).putFloat(mesh.y[v]).putFloat(mesh.z[v]);
                buffer.put((byte) (rgb >> 16)).put((byte) (rgb >> 8)).put((byte) rgb);
            }
        }
        for (int f = 0; f < faces; f++)
        {
            if (buffer.remaining() < FACE_BYTES)
            {
                drain(buffer, out);
            }
            buffer.put((byte) 3).putInt(f * 3).putInt(f * 3 + 1).putInt(f * 3 + 2);
        }
        drain(buffer, out);
    }

    private static void drain(ByteBuffer buffer, OutputStream out) throws IOException
    {
        out.write(buffer.array(), 0, buffer.position());
        buffer.clear();
    }
}
