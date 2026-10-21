/*
 * Copyright (c) 2020, Bram91
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: the Model Exporter plugin's face colors, from its
 *   OBJExporter, into a Mesh for consts-Models, with hidden and see-through faces left out)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsmodels;

import net.runelite.api.Model;
import net.runelite.api.Renderable;

/**
 * Copies a game model into a {@link Mesh}: its triangles, turned into east, north, up, and a color for each, as the
 * Model Exporter plugin colors them. A textured face takes its texture's average color; a flat-shaded face its one
 * color; any other face the average of its three corners. Faces the game hides, or draws fully see-through, are left
 * out. Use it on the client thread.
 */
final class ModelCapture
{
    private static final int[] PALETTE = createPalette();

    private ModelCapture()
    {
    }

    private static int[] createPalette()
    {
        int[] palette = new int[65536];
        for (int i = 0; i < palette.length; i++)
        {
            palette[i] = JagexColor.hslToRgb((short) i, JagexColor.BRIGHTNESS_MIN);
        }
        return palette;
    }

    /** The renderable's model, or null when it has none right now. */
    static Model modelOf(Renderable renderable)
    {
        if (renderable == null)
        {
            return null;
        }
        return renderable instanceof Model ? (Model) renderable : renderable.getModel();
    }

    /** The model as a mesh; an empty mesh when there's no model. */
    static Mesh capture(Model model, String name, TextureColors textures)
    {
        MeshBuilder mesh = new MeshBuilder(name);
        if (model == null)
        {
            return mesh.build();
        }

        // Game models are x east, y down, z north; meshes are x east, y north, z up. The turn keeps the winding.
        float[] vx = model.getVerticesX();
        float[] vy = model.getVerticesY();
        float[] vz = model.getVerticesZ();
        for (int v = 0; v < model.getVerticesCount(); v++)
        {
            mesh.addVertex(vx[v], vz[v], -vy[v]);
        }

        int[] i1 = model.getFaceIndices1();
        int[] i2 = model.getFaceIndices2();
        int[] i3 = model.getFaceIndices3();
        int[] c1 = model.getFaceColors1();
        int[] c2 = model.getFaceColors2();
        int[] c3 = model.getFaceColors3();
        short[] faceTextures = model.getFaceTextures();
        byte[] transparencies = model.getFaceTransparencies();

        for (int f = 0; f < model.getFaceCount(); f++)
        {
            // -2 marks a face the game doesn't draw
            if (c3[f] == -2)
            {
                continue;
            }
            if (transparencies != null && (transparencies[f] & 0xFF) >= 254)
            {
                continue;
            }

            int rgb;
            int texture = faceTextures == null ? -1 : faceTextures[f];
            if (texture != -1 && textures != null)
            {
                rgb = textures.color(texture);
            }
            else if (c3[f] == -1)
            {
                // A face shaded flat: one color
                rgb = PALETTE[c1[f] & 0xFFFF];
            }
            else
            {
                rgb = JagexColor.hslToRgbAverage(c1[f], c2[f], c3[f]);
            }
            mesh.addTriangle(i1[f], i2[f], i3[f], rgb);
        }
        return mesh.build();
    }
}
