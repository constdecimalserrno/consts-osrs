package com.constsmodels;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Writes a mesh as binary STL: an 80-byte header, the triangle count, then each triangle's unit normal and corners as
 * little-endian floats. STL has no units; slicers take them as millimetres, so give it a mesh from {@link PrintPrep}.
 */
final class StlWriter
{
    static final int HEADER_BYTES = 80;
    static final int TRIANGLE_BYTES = 50;

    private StlWriter()
    {
    }

    static void write(Mesh mesh, OutputStream out) throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(TRIANGLE_BYTES * 1024).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(header(mesh.name));
        buffer.putInt(mesh.faceCount());
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            if (buffer.remaining() < TRIANGLE_BYTES)
            {
                out.write(buffer.array(), 0, buffer.position());
                buffer.clear();
            }
            int a = mesh.a[f];
            int b = mesh.b[f];
            int c = mesh.c[f];
            double[] n = normal(mesh.x[a], mesh.y[a], mesh.z[a], mesh.x[b], mesh.y[b], mesh.z[b],
                mesh.x[c], mesh.y[c], mesh.z[c]);
            buffer.putFloat((float) n[0]).putFloat((float) n[1]).putFloat((float) n[2]);
            buffer.putFloat(mesh.x[a]).putFloat(mesh.y[a]).putFloat(mesh.z[a]);
            buffer.putFloat(mesh.x[b]).putFloat(mesh.y[b]).putFloat(mesh.z[b]);
            buffer.putFloat(mesh.x[c]).putFloat(mesh.y[c]).putFloat(mesh.z[c]);
            buffer.putShort((short) 0);
        }
        out.write(buffer.array(), 0, buffer.position());
    }

    /**
     * The unit normal of the triangle a, b, c, pointing the way it's seen counterclockwise from; all zeros for a
     * triangle with no area.
     */
    static double[] normal(double ax, double ay, double az, double bx, double by, double bz,
        double cx, double cy, double cz)
    {
        double ux = bx - ax;
        double uy = by - ay;
        double uz = bz - az;
        double wx = cx - ax;
        double wy = cy - ay;
        double wz = cz - az;
        double nx = uy * wz - uz * wy;
        double ny = uz * wx - ux * wz;
        double nz = ux * wy - uy * wx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(length > 0) || Double.isInfinite(length))
        {
            return new double[3];
        }
        return new double[]{nx / length, ny / length, nz / length};
    }

    /** 80 bytes of plain ASCII naming the model; never starting "solid", which would make it look like a text STL. */
    private static byte[] header(String name)
    {
        String text = "consts-Models binary STL, millimetres: " + (name == null ? "" : name);
        byte[] header = new byte[HEADER_BYTES];
        byte[] ascii = text.replaceAll("[^\\x20-\\x7E]", "?").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ascii, 0, header, 0, Math.min(ascii.length, HEADER_BYTES));
        return header;
    }
}
