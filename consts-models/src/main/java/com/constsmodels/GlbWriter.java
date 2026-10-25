package com.constsmodels;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a mesh as binary glTF 2.0 (.glb), with its clips as animations, for Blender and other 3D software.
 *
 * <p>It keeps the game's units (128 to a tile), as OBJ and PLY do, and turns the mesh y up as glTF expects: glTF's
 * x is east, y up and -z north. Every triangle of one color is one primitive, all over the same corners. Each frame
 * of each clip is a morph target, the corners' moves from the still; Blender makes those shape keys. Each clip is an
 * animation that turns its frames' targets fully on, one at a time, at the game's times (STEP), and holds the last to
 * the clip's end. Its extras say the game's animation id and the frame a loop restarts at.
 */
final class GlbWriter
{
    private static final int FLOAT = 5126;
    private static final int UNSIGNED_INT = 5125;
    // What a buffer view holds, for the GPU: corners, triangles, or neither (animation times and weights)
    private static final int CORNERS = 34962;
    private static final int TRIANGLES = 34963;

    private final StringBuilder accessors = new StringBuilder();
    private final StringBuilder views = new StringBuilder();
    private final List<ByteBuffer> chunks = new ArrayList<>();
    private int count;
    private int offset;

    private GlbWriter()
    {
    }

    /** Writes the mesh, which has at least one triangle; leaves the stream open. */
    static void write(Mesh mesh, OutputStream out) throws IOException
    {
        new GlbWriter().writeGlb(mesh, out);
    }

    private void writeGlb(Mesh mesh, OutputStream out) throws IOException
    {
        String name = json(mesh.name == null || mesh.name.isEmpty() ? "Model" : mesh.name);
        int position = positions(mesh.x, mesh.y, mesh.z, null);

        // The triangles of each color, in their own order
        Map<Integer, List<Integer>> byColor = new LinkedHashMap<>();
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            byColor.computeIfAbsent(mesh.color[f], c -> new ArrayList<>()).add(f);
        }

        // One target per frame of every clip, in order
        StringBuilder targets = new StringBuilder();
        StringBuilder targetNames = new StringBuilder();
        int targetCount = 0;
        for (Clip clip : mesh.clips)
        {
            for (int f = 0; f < clip.frameCount(); f++)
            {
                int target = positions(clip.x[f], clip.y[f], clip.z[f], mesh);
                targets.append(targetCount == 0 ? "" : ",").append("{\"POSITION\":").append(target).append('}');
                targetNames.append(targetCount == 0 ? "" : ",").append(json(clip.label + " " + (f + 1)));
                targetCount++;
            }
        }

        StringBuilder primitives = new StringBuilder();
        StringBuilder materials = new StringBuilder();
        int materialCount = 0;
        for (Map.Entry<Integer, List<Integer>> group : byColor.entrySet())
        {
            List<Integer> faces = group.getValue();
            ByteBuffer indices = buffer(faces.size() * 3);
            for (int f : faces)
            {
                indices.putInt(mesh.a[f]).putInt(mesh.b[f]).putInt(mesh.c[f]);
            }
            int index = accessor(indices, TRIANGLES, UNSIGNED_INT, faces.size() * 3, "SCALAR", null, null);
            int material = materialCount++;
            primitives.append(material == 0 ? "" : ",").append("{\"attributes\":{\"POSITION\":").append(position)
                .append("},\"indices\":").append(index).append(",\"material\":").append(material);
            if (targetCount > 0)
            {
                primitives.append(",\"targets\":[").append(targets).append(']');
            }
            primitives.append('}');
            int rgb = group.getKey();
            materials.append(material == 0 ? "" : ",").append("{\"name\":\"").append(String.format("c_%06X", rgb))
                .append("\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[").append(linear(rgb >> 16))
                .append(',').append(linear(rgb >> 8)).append(',').append(linear(rgb)).append(",1],")
                .append("\"metallicFactor\":0,\"roughnessFactor\":1}}");
        }

        StringBuilder animations = new StringBuilder();
        int first = 0;
        for (Clip clip : mesh.clips)
        {
            int frames = clip.frameCount();
            // Each frame's start, then the clip's end, with the last frame held to it
            ByteBuffer times = buffer(frames + 1);
            ByteBuffer weights = buffer((frames + 1) * targetCount);
            int tick = 0;
            for (int k = 0; k <= frames; k++)
            {
                times.putFloat(tick * Clip.TICK_MS / 1000f);
                tick += k < frames ? clip.ticks[k] : 0;
                int on = first + Math.min(k, frames - 1);
                for (int t = 0; t < targetCount; t++)
                {
                    weights.putFloat(t == on ? 1 : 0);
                }
            }
            String end = Float.toString(tick * Clip.TICK_MS / 1000f);
            int input = accessor(times, 0, FLOAT, frames + 1, "SCALAR", "0", end);
            int output = accessor(weights, 0, FLOAT, (frames + 1) * targetCount, "SCALAR", null, null);
            animations.append(animations.length() == 0 ? "" : ",").append("{\"name\":").append(json(clip.label))
                .append(",\"channels\":[{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"weights\"}}]")
                .append(",\"samplers\":[{\"input\":").append(input).append(",\"output\":").append(output)
                .append(",\"interpolation\":\"STEP\"}],\"extras\":{\"animation\":").append(clip.animation)
                .append(",\"loopFrom\":").append(clip.loopFrom).append("}}");
            first += frames;
        }

        StringBuilder gltf = new StringBuilder(4096);
        gltf.append("{\"asset\":{\"version\":\"2.0\",\"generator\":\"consts-Models\"},")
            .append("\"scene\":0,\"scenes\":[{\"nodes\":[0]}],")
            .append("\"nodes\":[{\"name\":").append(name).append(",\"mesh\":0}],")
            .append("\"meshes\":[{\"name\":").append(name).append(",\"primitives\":[").append(primitives).append(']');
        if (targetCount > 0)
        {
            float[] zeros = new float[targetCount];
            gltf.append(",\"weights\":").append(Arrays.toString(zeros).replace(" ", ""))
                .append(",\"extras\":{\"targetNames\":[").append(targetNames).append("]}");
        }
        gltf.append("}],\"materials\":[").append(materials).append(']');
        if (animations.length() > 0)
        {
            gltf.append(",\"animations\":[").append(animations).append(']');
        }
        gltf.append(",\"buffers\":[{\"byteLength\":").append(offset).append("}]")
            .append(",\"bufferViews\":[").append(views).append(']')
            .append(",\"accessors\":[").append(accessors).append("]}");

        byte[] text = gltf.toString().getBytes(StandardCharsets.UTF_8);
        int jsonLength = (text.length + 3) & ~3;
        ByteBuffer head = ByteBuffer.allocate(20 + jsonLength + 8).order(ByteOrder.LITTLE_ENDIAN);
        head.putInt(0x46546C67).putInt(2).putInt(12 + 8 + jsonLength + 8 + offset);
        head.putInt(jsonLength).putInt(0x4E4F534A).put(text);
        while (head.position() < 20 + jsonLength)
        {
            head.put((byte) ' ');
        }
        head.putInt(offset).putInt(0x004E4942);
        out.write(head.array());
        for (ByteBuffer chunk : chunks)
        {
            out.write(chunk.array());
        }
    }

    /**
     * An accessor of corners in glTF's axes: the positions themselves, or with a still given, how far each moved from
     * it. glTF wants the bounds of either.
     */
    private int positions(float[] x, float[] y, float[] z, Mesh still)
    {
        int vertices = x.length;
        ByteBuffer data = buffer(vertices * 3);
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int v = 0; v < vertices; v++)
        {
            float[] p = {x[v], z[v], -y[v]};
            if (still != null)
            {
                p[0] -= still.x[v];
                p[1] -= still.z[v];
                p[2] += still.y[v];
            }
            for (int i = 0; i < 3; i++)
            {
                data.putFloat(p[i]);
                min[i] = Math.min(min[i], p[i]);
                max[i] = Math.max(max[i], p[i]);
            }
        }
        return accessor(data, CORNERS, FLOAT, vertices, "VEC3", join(min), join(max));
    }

    /** Adds the data as a buffer view and an accessor over it, and returns the accessor's index. */
    private int accessor(ByteBuffer data, int target, int componentType, int elements, String type, String min,
        String max)
    {
        int index = count++;
        views.append(index == 0 ? "" : ",").append("{\"buffer\":0,\"byteOffset\":").append(offset)
            .append(",\"byteLength\":").append(data.capacity());
        if (target != 0)
        {
            views.append(",\"target\":").append(target);
        }
        views.append('}');
        accessors.append(index == 0 ? "" : ",").append("{\"bufferView\":").append(index)
            .append(",\"componentType\":").append(componentType).append(",\"count\":").append(elements)
            .append(",\"type\":\"").append(type).append('"');
        if (min != null)
        {
            accessors.append(",\"min\":[").append(min).append("],\"max\":[").append(max).append(']');
        }
        accessors.append('}');
        chunks.add(data);
        offset += data.capacity();
        return index;
    }

    /** Room for this many 4-byte values, little-endian. */
    private static ByteBuffer buffer(int values)
    {
        return ByteBuffer.allocate(values * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static String join(float[] values)
    {
        return values[0] + "," + values[1] + "," + values[2];
    }

    /** A color channel, 0 to 255 in its low byte, from the game's sRGB into glTF's linear 0 to 1. */
    private static String linear(int channel)
    {
        double c = (channel & 0xFF) / 255.0;
        return DecimalText.of(c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
    }

    /** Text as a JSON string, quoted. */
    private static String json(String text)
    {
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (char ch : text.toCharArray())
        {
            if (ch == '"' || ch == '\\')
            {
                out.append('\\').append(ch);
            }
            else if (ch < 0x20)
            {
                out.append(String.format("\\u%04x", (int) ch));
            }
            else
            {
                out.append(ch);
            }
        }
        return out.append('"').toString();
    }
}
