package com.constsmodels;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A capture kept on disk, gzipped: its name, when it was captured and how many triangles it has, then the mesh
 * itself, corners then triangles with their colors, then its clips. The first three come first so a list of captures
 * can be read without reading their meshes. Everything is big-endian, as {@link DataOutputStream} writes it:
 *
 * <pre>
 * int    magic "CMD2" (0x434D4432); "CMD1" files, from before clips, end after the triangles
 * UTF    name
 * long   time captured, ms since 1970
 * int    triangles T
 * int    corners V, then V x (float x, float y, float z)
 * T x    (int a, int b, int c, int color 0xRRGGBB)
 * int    clips C, then C x clip:
 *          UTF label, int animation id, int loopFrom, int frames F,
 *          F x (int ticks, V x (float x, float y, float z))
 * </pre>
 */
final class MeshFile
{
    // "CMD1": consts-Models, the first way of writing one; "CMD2" adds the clips
    private static final int CMD1 = 0x434D4431;
    private static final int CMD2 = 0x434D4432;
    // More than any capture holds, so a damaged file can't ask for all the memory there is
    private static final int MOST = 20_000_000;

    /** What a file says about its capture before the mesh. */
    static final class Header
    {
        final String name;
        final long time;
        final int faces;
        final boolean hasClips;

        Header(String name, long time, int faces, boolean hasClips)
        {
            this.name = name;
            this.time = time;
            this.faces = faces;
            this.hasClips = hasClips;
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
            data.writeInt(CMD2);
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
            data.writeInt(mesh.clips.size());
            for (Clip clip : mesh.clips)
            {
                data.writeUTF(clip.label);
                data.writeInt(clip.animation);
                data.writeInt(clip.loopFrom);
                data.writeInt(clip.frameCount());
                for (int f = 0; f < clip.frameCount(); f++)
                {
                    data.writeInt(clip.ticks[f]);
                    for (int v = 0; v < clip.vertexCount(); v++)
                    {
                        data.writeFloat(clip.x[f][v]);
                        data.writeFloat(clip.y[f][v]);
                        data.writeFloat(clip.z[f][v]);
                    }
                }
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
            List<Clip> clips = header.hasClips ? clips(data, vertices) : List.of();
            return new Mesh(header.name, x, y, z, a, b, c, color, clips);
        }
    }

    private static List<Clip> clips(DataInputStream data, int vertices) throws IOException
    {
        int count = data.readInt();
        if (count < 0 || count > MOST)
        {
            throw new IOException("it's damaged");
        }
        List<Clip> clips = new ArrayList<>();
        // Corner positions read so far, all clips together, so no file can ask for more than a capture holds
        long read = 0;
        for (int i = 0; i < count; i++)
        {
            String label = data.readUTF();
            int animation = data.readInt();
            int loopFrom = data.readInt();
            int frames = data.readInt();
            read += (long) frames * Math.max(1, vertices);
            if (frames < 1 || read > MOST || loopFrom < 0 || loopFrom >= frames)
            {
                throw new IOException("it's damaged");
            }
            int[] ticks = new int[frames];
            float[][] x = new float[frames][vertices];
            float[][] y = new float[frames][vertices];
            float[][] z = new float[frames][vertices];
            for (int f = 0; f < frames; f++)
            {
                ticks[f] = data.readInt();
                if (ticks[f] < 1)
                {
                    throw new IOException("it's damaged");
                }
                for (int v = 0; v < vertices; v++)
                {
                    x[f][v] = data.readFloat();
                    y[f][v] = data.readFloat();
                    z[f][v] = data.readFloat();
                }
            }
            clips.add(new Clip(label, animation, ticks, loopFrom, x, y, z));
        }
        return clips;
    }

    private static DataInputStream open(InputStream in) throws IOException
    {
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(in, 1 << 16), 1 << 16));
    }

    private static Header header(DataInputStream data) throws IOException
    {
        int magic = data.readInt();
        if (magic != CMD1 && magic != CMD2)
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
        return new Header(name, time, faces, magic == CMD2);
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
