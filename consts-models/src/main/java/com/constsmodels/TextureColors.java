/*
 * Copyright (c) 2020, Bram91
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: from the Model Exporter plugin's TextureColor into
 *   consts-Models, without its static client)
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

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.TextureProvider;

/**
 * One color for each game texture, the average of its pixels: what a textured face (water, lava, a cape) is drawn and
 * saved in. Use it on the client thread.
 */
class TextureColors
{
    private static final int FALLBACK = 0xFFFFFF;

    private final Client client;
    private final Map<Integer, Integer> cache = new HashMap<>();

    TextureColors(Client client)
    {
        this.client = client;
    }

    int color(int textureId)
    {
        Integer known = cache.get(textureId);
        if (known != null)
        {
            return known;
        }

        int rgb = FALLBACK;
        TextureProvider provider = client == null ? null : client.getTextureProvider();
        int[] pixels = provider == null ? null : provider.load(textureId);
        if (pixels != null)
        {
            rgb = average(pixels);
        }
        cache.put(textureId, rgb);
        return rgb;
    }

    /** The average of a texture's pixels, skipping the black ones, which are its transparent parts. */
    static int average(int[] pixels)
    {
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int pixel : pixels)
        {
            if ((pixel & 0xFFFFFF) == 0)
            {
                continue;
            }
            r += pixel >> 16 & 255;
            g += pixel >> 8 & 255;
            b += pixel & 255;
            n++;
        }
        if (n == 0)
        {
            return FALLBACK;
        }
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }
}
