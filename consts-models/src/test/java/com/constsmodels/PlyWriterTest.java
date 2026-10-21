package com.constsmodels;

import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PlyWriterTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aPlyGivesEachFaceCornersOfItsOwnInItsColor() throws IOException
    {
        // Two faces sharing an edge, in different colors
        MeshBuilder b = new MeshBuilder("Object Chair");
        int v0 = b.addVertex(0, 0, 0);
        int v1 = b.addVertex(64, 0, 0);
        int v2 = b.addVertex(64, 64, 12.5f);
        int v3 = b.addVertex(0, 64, 0);
        b.addTriangle(v0, v1, v2, RED);
        b.addTriangle(v0, v2, v3, BLUE);
        Mesh mesh = b.build();

        File file = MeshExporter.export(mesh, ExportFormat.PLY, folder.getRoot(), new PrintSettings(100, true, 4));
        assertTrue(file.getName().endsWith(".ply"));
        byte[] bytes = Files.readAllBytes(file.toPath());
        String text = new String(bytes, StandardCharsets.US_ASCII);
        int end = text.indexOf("end_header\n") + "end_header\n".length();
        String[] header = text.substring(0, end).split("\n");
        assertEquals("ply", header[0]);
        assertEquals("format binary_little_endian 1.0", header[1]);
        assertTrue(text.substring(0, end).contains("element vertex 6\n"));
        assertTrue(text.substring(0, end).contains("property float x\nproperty float y\nproperty float z\n"
            + "property uchar red\nproperty uchar green\nproperty uchar blue\n"));
        assertTrue(text.substring(0, end).contains("element face 2\nproperty list uchar int vertex_indices\n"));
        assertEquals(end + 6 * PlyWriter.VERTEX_BYTES + 2 * PlyWriter.FACE_BYTES, bytes.length);

        ByteBuffer in = ByteBuffer.wrap(bytes, end, bytes.length - end).order(ByteOrder.LITTLE_ENDIAN);
        int[] corners = {v0, v1, v2, v0, v2, v3};
        int[] colors = {RED, RED, RED, BLUE, BLUE, BLUE};
        for (int i = 0; i < 6; i++)
        {
            int v = corners[i];
            assertEquals(mesh.x[v], in.getFloat(), 0f);
            assertEquals(mesh.y[v], in.getFloat(), 0f);
            assertEquals(mesh.z[v], in.getFloat(), 0f);
            int rgb = (in.get() & 0xFF) << 16 | (in.get() & 0xFF) << 8 | (in.get() & 0xFF);
            assertEquals(colors[i], rgb);
        }
        for (int f = 0; f < 2; f++)
        {
            assertEquals(3, in.get());
            assertEquals(f * 3, in.getInt());
            assertEquals(f * 3 + 1, in.getInt());
            assertEquals(f * 3 + 2, in.getInt());
        }
        assertEquals(0, in.remaining());
    }
}
