/*
 * Copyright (c) 2019, Jordan Atwood <nightfirecat@protonmail.com>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, lombok written out)
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
package com.constsdestination.tilepacks.data;

import java.awt.Color;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * Used to denote marked tiles and their colors.
 * Note: This is not used for serialization of ground markers; see {@link GroundMarkerPoint}
 */
public class ColorTileMarker
{
    private final WorldPoint worldPoint;
    @Nullable
    private final Color color;
    @Nullable
    private final String label;

    public ColorTileMarker(WorldPoint worldPoint, @Nullable Color color, @Nullable String label)
    {
        this.worldPoint = worldPoint;
        this.color = color;
        this.label = label;
    }

    public WorldPoint getWorldPoint()
    {
        return worldPoint;
    }

    @Nullable
    public Color getColor()
    {
        return color;
    }

    @Nullable
    public String getLabel()
    {
        return label;
    }
}
