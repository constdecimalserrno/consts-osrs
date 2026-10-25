package com.constsmodels;

import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class GlbWriterTest
{
    /** A written GLB: its JSON and its binary chunk, after checking the container is as the spec has it. */
    private static final class Glb
    {
        final JsonObject json;
        final ByteBuffer bin;

        Glb(byte[] bytes)
        {
            ByteBuffer file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0x46546C67, file.getInt());
            assertEquals(2, file.getInt());
            assertEquals(bytes.length, file.getInt());
            int jsonLength = file.getInt();
            assertEquals(0, jsonLength % 4);
            assertEquals(0x4E4F534A, file.getInt());
            json = new JsonParser().parse(new String(bytes, 20, jsonLength, StandardCharsets.UTF_8)).getAsJsonObject();
            file.position(20 + jsonLength);
            int binLength = file.getInt();
            assertEquals(0, binLength % 4);
            assertEquals(0x004E4942, file.getInt());
            assertEquals(bytes.length, 28 + jsonLength + binLength);
            bin = ByteBuffer.wrap(bytes, 28 + jsonLength, binLength).slice().order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(binLength, json.getAsJsonArray("buffers").get(0).getAsJsonObject().get("byteLength").getAsInt());
        }

        JsonObject accessor(int index)
        {
            return json.getAsJsonArray("accessors").get(index).getAsJsonObject();
        }

        /** The accessor's i-th float. */
        float get(int accessor, int i)
        {
            JsonObject view = json.getAsJsonArray("bufferViews").get(accessor(accessor).get("bufferView").getAsInt())
                .getAsJsonObject();
            return bin.getFloat(view.get("byteOffset").getAsInt() + 4 * i);
        }
    }

    private static Glb write(Mesh mesh) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GlbWriter.write(mesh, out);
        return new Glb(out.toByteArray());
    }

    @Test
    public void aStillIsOnePrimitivePerColorOverOneSetOfCorners() throws IOException
    {
        Mesh box = TestMeshes.box(0, 0, 0, 40, 50, 60, RED, BLUE);
        Glb glb = write(box);

        JsonObject mesh = glb.json.getAsJsonArray("meshes").get(0).getAsJsonObject();
        JsonArray primitives = mesh.getAsJsonArray("primitives");
        assertEquals(2, primitives.size());
        int position = primitives.get(0).getAsJsonObject().getAsJsonObject("attributes").get("POSITION").getAsInt();
        assertEquals(position,
            primitives.get(1).getAsJsonObject().getAsJsonObject("attributes").get("POSITION").getAsInt());
        assertEquals(box.vertexCount(), glb.accessor(position).get("count").getAsInt());
        // glTF is y up: the mesh's (x, y, z) goes in as (x, z, -y), with the bounds glTF asks for
        assertEquals(box.x[2], glb.get(position, 6), 0f);
        assertEquals(box.z[2], glb.get(position, 7), 0f);
        assertEquals(-box.y[2], glb.get(position, 8), 0f);
        assertEquals(60f, glb.accessor(position).getAsJsonArray("max").get(1).getAsFloat(), 0f);
        assertEquals(-50f, glb.accessor(position).getAsJsonArray("min").get(2).getAsFloat(), 0f);

        int triangles = 0;
        for (int p = 0; p < primitives.size(); p++)
        {
            triangles += glb.accessor(primitives.get(p).getAsJsonObject().get("indices").getAsInt()).get("count")
                .getAsInt() / 3;
        }
        assertEquals(box.faceCount(), triangles);
        // The game's sRGB red in glTF's linear color
        JsonArray red = glb.json.getAsJsonArray("materials").get(0).getAsJsonObject()
            .getAsJsonObject("pbrMetallicRoughness").getAsJsonArray("baseColorFactor");
        assertEquals(Math.pow((0xC0 / 255.0 + 0.055) / 1.055, 2.4), red.get(0).getAsDouble(), 1e-4);
        assertFalse(glb.json.has("animations"));
        assertFalse(primitives.get(0).getAsJsonObject().has("targets"));
    }

    @Test
    public void eachClipIsAnAnimationThatStepsThroughItsFramesAsMorphTargets() throws IOException
    {
        Mesh mesh = TestMeshes.animated(TestMeshes.box(0, 0, 0, 40, 50, 60, RED).withName("NPC \"Goblin\""));
        Glb glb = write(mesh);

        JsonObject gltfMesh = glb.json.getAsJsonArray("meshes").get(0).getAsJsonObject();
        assertEquals("NPC \"Goblin\"", gltfMesh.get("name").getAsString());
        JsonArray targets = gltfMesh.getAsJsonArray("primitives").get(0).getAsJsonObject().getAsJsonArray("targets");
        // Idle's 2 frames, then Walk's 3, named for Blender's shape keys
        assertEquals(5, targets.size());
        assertEquals("Walk 3", gltfMesh.getAsJsonObject("extras").getAsJsonArray("targetNames").get(4).getAsString());
        assertEquals(5, gltfMesh.getAsJsonArray("weights").size());

        // A target holds how far each corner moves from the still: Idle's frame 2 is 10 east; Walk's frame 3, 10 up
        int idle2 = targets.get(1).getAsJsonObject().get("POSITION").getAsInt();
        assertEquals(10f, glb.get(idle2, 0), 0f);
        assertEquals(0f, glb.get(idle2, 1), 0f);
        int walk3 = targets.get(4).getAsJsonObject().get("POSITION").getAsInt();
        assertEquals(10f, glb.get(walk3, 1), 0f);
        assertTrue(glb.accessor(walk3).has("min"));

        JsonArray animations = glb.json.getAsJsonArray("animations");
        assertEquals(2, animations.size());
        JsonObject walk = animations.get(1).getAsJsonObject();
        assertEquals("Walk", walk.get("name").getAsString());
        assertEquals(819, walk.getAsJsonObject("extras").get("animation").getAsInt());
        assertEquals(1, walk.getAsJsonObject("extras").get("loopFrom").getAsInt());
        JsonObject channel = walk.getAsJsonArray("channels").get(0).getAsJsonObject();
        assertEquals("weights", channel.getAsJsonObject("target").get("path").getAsString());
        JsonObject sampler = walk.getAsJsonArray("samplers").get(0).getAsJsonObject();
        assertEquals("STEP", sampler.get("interpolation").getAsString());

        // Walk's frames start at 0, 0.12 and 0.24 s, and it ends at 0.36 s on its last frame
        int input = sampler.get("input").getAsInt();
        assertEquals(4, glb.accessor(input).get("count").getAsInt());
        assertEquals(0.12f, glb.get(input, 1), 1e-6f);
        assertEquals(0.36f, glb.get(input, 3), 1e-6f);
        assertEquals(0.36f, glb.accessor(input).getAsJsonArray("max").get(0).getAsFloat(), 1e-6f);
        // At each time one target is fully on: Walk's own, the 3rd, 4th and 5th, the last held to the end
        int output = sampler.get("output").getAsInt();
        assertEquals(4 * 5, glb.accessor(output).get("count").getAsInt());
        float[] expected = {0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals("weight " + i, expected[i], glb.get(output, i), 0f);
        }
    }
}
