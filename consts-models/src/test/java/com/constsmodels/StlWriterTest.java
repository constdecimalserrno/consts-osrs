package com.constsmodels;

import static com.constsmodels.TestMeshes.GREEN;
import static com.constsmodels.TestMeshes.RED;
import static com.constsmodels.TestMeshes.box;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StlWriterTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** A binary STL read back: corners, and normals as written, each triangle's nine floats then three. */
    static final class Stl
    {
        String header;
        int count;
        float[][] corners;
        float[][] normals;
    }

    static Stl read(byte[] bytes)
    {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        Stl stl = new Stl();
        stl.header = new String(bytes, 0, StlWriter.HEADER_BYTES, StandardCharsets.US_ASCII);
        in.position(StlWriter.HEADER_BYTES);
        stl.count = in.getInt();
        assertEquals(StlWriter.HEADER_BYTES + 4 + StlWriter.TRIANGLE_BYTES * stl.count, bytes.length);
        stl.corners = new float[stl.count][9];
        stl.normals = new float[stl.count][3];
        for (int f = 0; f < stl.count; f++)
        {
            for (int i = 0; i < 3; i++)
            {
                stl.normals[f][i] = in.getFloat();
            }
            for (int i = 0; i < 9; i++)
            {
                stl.corners[f][i] = in.getFloat();
            }
            assertEquals(0, in.getShort());
        }
        return stl;
    }

    @Test
    public void aSavedStlIsBinaryInMillimetresOnThePlateWithRealNormals() throws IOException
    {
        Mesh model = box(-300, 500, 20, -200, 560, 60, RED, GREEN);
        File file = MeshExporter.export(model, ExportFormat.STL, folder.getRoot(), new PrintSettings(120, false, 4));
        assertTrue(file.getName().endsWith(".stl"));
        Stl stl = read(Files.readAllBytes(file.toPath()));

        assertFalse(stl.header.startsWith("solid"));
        assertTrue(stl.header.startsWith("consts-Models"));
        assertEquals(12, stl.count);
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int f = 0; f < stl.count; f++)
        {
            float[] n = stl.normals[f];
            float[] p = stl.corners[f];
            assertEquals(1, Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]), 1e-5);
            // The normal is the one the corners' winding gives, and it points out of the box
            double[] wound = StlWriter.normal(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8]);
            assertEquals(1, n[0] * wound[0] + n[1] * wound[1] + n[2] * wound[2], 1e-5);
            double cz = (p[2] + p[5] + p[8]) / 3 - 24;
            double cx = (p[0] + p[3] + p[6]) / 3;
            double cy = (p[1] + p[4] + p[7]) / 3;
            assertTrue(n[0] * cx + n[1] * cy + n[2] * cz > 0);
            for (int i = 0; i < 9; i++)
            {
                min[i % 3] = Math.min(min[i % 3], p[i]);
                max[i % 3] = Math.max(max[i % 3], p[i]);
            }
        }
        assertEquals(0, min[2], 0f);
        assertEquals(120, max[0] - min[0], 1e-3);
        assertEquals(72, max[1] - min[1], 1e-3);
        assertEquals(48, max[2] - min[2], 1e-3);
        assertEquals(0, min[0] + max[0], 1e-3);
        assertEquals(0, min[1] + max[1], 1e-3);
    }

    @Test
    public void aBaseMakesTheWholePrintTheSizeAskedFor() throws IOException
    {
        File file = MeshExporter.export(box(0, 0, 0, 50, 50, 100, RED), ExportFormat.STL, folder.getRoot(),
            new PrintSettings(60, true, 4));
        Stl stl = read(Files.readAllBytes(file.toPath()));
        assertEquals(24, stl.count);
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (float[] p : stl.corners)
        {
            for (int i = 2; i < 9; i += 3)
            {
                low = Math.min(low, p[i]);
                high = Math.max(high, p[i]);
            }
        }
        assertEquals(0, low, 0f);
        assertEquals(60, high, 1e-3);
    }

    @Test
    public void aTriangleWithNoAreaGetsANormalOfZeros() throws IOException
    {
        MeshBuilder b = new MeshBuilder(null);
        b.addTriangle(b.addVertex(0, 0, 0), b.addVertex(1, 0, 0), b.addVertex(2, 0, 0), RED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StlWriter.write(b.build(), out);
        Stl stl = read(out.toByteArray());
        assertEquals(1, stl.count);
        assertEquals(0, stl.normals[0][0], 0f);
        assertEquals(0, stl.normals[0][1], 0f);
        assertEquals(0, stl.normals[0][2], 0f);
    }
}
