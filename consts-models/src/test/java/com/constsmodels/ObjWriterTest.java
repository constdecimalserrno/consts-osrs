package com.constsmodels;

import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ObjWriterTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void anObjKeepsGameUnitsTurnedYUpWithAMaterialPerColor() throws IOException
    {
        MeshBuilder b = new MeshBuilder("NPC Goblin");
        int v0 = b.addVertex(10, 20, 30);
        int v1 = b.addVertex(138, 20, 30);
        int v2 = b.addVertex(10, 148, 30.5f);
        int v3 = b.addVertex(10, 20, -64);
        b.addTriangle(v0, v1, v2, RED);
        b.addTriangle(v0, v2, v3, BLUE);
        b.addTriangle(v0, v3, v1, RED);
        Mesh mesh = b.build();

        File file = MeshExporter.export(mesh, ExportFormat.OBJ, folder.getRoot());
        assertTrue(file.getName().startsWith("NPC Goblin "));
        assertTrue(file.getName().endsWith(".obj"));
        String base = file.getName().substring(0, file.getName().length() - 4);
        File mtlFile = new File(folder.getRoot(), base + ".mtl");
        assertTrue(mtlFile.isFile());

        List<String> obj = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(obj.contains("mtllib " + base + ".mtl"));
        List<String> vertices = new ArrayList<>();
        List<String> faces = new ArrayList<>();
        Map<String, Integer> facesPerMaterial = new HashMap<>();
        String material = null;
        for (String line : obj)
        {
            if (line.startsWith("v "))
            {
                vertices.add(line);
            }
            else if (line.startsWith("usemtl "))
            {
                material = line.substring(7);
                assertTrue("each material is set once", !facesPerMaterial.containsKey(material));
                facesPerMaterial.put(material, 0);
            }
            else if (line.startsWith("f "))
            {
                faces.add(line);
                facesPerMaterial.merge(material, 1, Integer::sum);
            }
        }
        assertEquals(4, vertices.size());
        assertEquals(3, faces.size());
        // x stays east, the up axis becomes y and north becomes -z
        assertEquals("v 10 30 -20", vertices.get(0));
        assertEquals("v 10 30.5 -148", vertices.get(2));
        assertEquals("v 10 -64 -20", vertices.get(3));
        assertEquals(2, (int) facesPerMaterial.get("c_C02020"));
        assertEquals(1, (int) facesPerMaterial.get("c_2030C0"));
        // Faces keep their winding, counted from 1, grouped by color and in their own order within one
        assertEquals("f 1 3 4", faces.get(0));
        assertEquals("f 1 2 3", faces.get(1));
        assertEquals("f 1 4 2", faces.get(2));

        List<String> mtl = Files.readAllLines(mtlFile.toPath(), StandardCharsets.UTF_8);
        int red = mtl.indexOf("newmtl c_C02020");
        assertTrue(red >= 0);
        assertEquals("Kd 0.7529 0.1255 0.1255", mtl.get(red + 1));
        assertTrue(mtl.contains("newmtl c_2030C0"));
    }
}
