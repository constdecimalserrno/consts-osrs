package com.constsmodels;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A capture kept on disk, gzipped: its name, when it was captured and how many triangles it has, then the mesh
 * itself, corners then triangles with their colors. The first three come first so a list of captures can be read
 * without reading their meshes.
 */
final class MeshFile
{
    // "CMD1": consts-Models, the first way of writing one
    private static final int MAGIC = 0x434D4431;
    // More than any capture holds, so a damaged file can't ask for all the memory there is
    private static final int MOST = 20_000_000;

    /** What a file says about its capture before the mesh. */
    static final class Header
    {
        final String name;
        final long time;
        final int faces;

        Header(String name, long time, int faces)
        {
            this.name = name;
            this.time = time;
            this.faces = faces;
        }
    }

    private MeshFile()
    {
    }

    /** Writes the capture, and closes the stream. */
    static void write(Mesh mesh, long time, OutputStream out) throws IOException
    {
        try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(out, 1 << 16),
            1 << 16)))
        {
            data.writeInt(MAGIC);
            data.writeUTF(mesh.name == null ? "" : mesh.name);
            data.writeLong(time);
            data.writeInt(mesh.faceCount());
            data.writeInt(mesh.vertexCount());
            for (int v = 0; v < mesh.vertexCount(); v++)
            {
                data.writeFloat(mesh.x[v]);
                data.writeFloat(mesh.y[v]);
                data.writeFloat(mesh.z[v]);
            }
            for (int f = 0; f < mesh.faceCount(); f++)
            {
                data.writeInt(mesh.a[f]);
                data.writeInt(mesh.b[f]);
                data.writeInt(mesh.c[f]);
                data.writeInt(mesh.color[f]);
            }
        }
    }

    /** Reads the name, time and triangle count, and closes the stream. */
    static Header readHeader(InputStream in) throws IOException
    {
        try (DataInputStream data = open(in))
        {
            return header(data);
        }
    }

    /** Reads the mesh, and closes the stream. */
    static Mesh read(InputStream in) throws IOException
    {
        try (DataInputStream data = open(in))
        {
            Header header = header(data);
            int vertices = data.readInt();
            if (vertices < 0 || vertices > MOST)
            {
                throw new IOException("it isn't a capture");
            }
            float[] x = new float[vertices];
            float[] y = new float[vertices];
            float[] z = new float[vertices];
            for (int v = 0; v < vertices; v++)
            {
                x[v] = data.readFloat();
                y[v] = data.readFloat();
                z[v] = data.readFloat();
            }
            int faces = header.faces;
            int[] a = new int[faces];
            int[] b = new int[faces];
            int[] c = new int[faces];
            int[] color = new int[faces];
            for (int f = 0; f < faces; f++)
            {
                a[f] = corner(data.readInt(), vertices);
                b[f] = corner(data.readInt(), vertices);
                c[f] = corner(data.readInt(), vertices);
                color[f] = data.readInt();
            }
            return new Mesh(header.name, x, y, z, a, b, c, color);
        }
    }

    private static DataInputStream open(InputStream in) throws IOException
    {
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(in, 1 << 16), 1 << 16));
    }

    private static Header header(DataInputStream data) throws IOException
    {
        if (data.readInt() != MAGIC)
        {
            throw new IOException("it isn't a capture");
        }
        String name = data.readUTF();
        long time = data.readLong();
        int faces = data.readInt();
        if (faces < 0 || faces > MOST)
        {
            throw new IOException("it isn't a capture");
        }
        return new Header(name, time, faces);
    }

    private static int corner(int index, int vertices) throws IOException
    {
        if (index < 0 || index >= vertices)
        {
            throw new IOException("it's damaged");
        }
        return index;
    }
}
