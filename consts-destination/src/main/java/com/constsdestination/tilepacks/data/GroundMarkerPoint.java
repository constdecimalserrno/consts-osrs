/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
 *
 * Source: https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/groundmarkers/GroundMarkerPoint.java
 */
package com.constsdestination.tilepacks.data;

import java.awt.Color;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Used for serialization of ground marker points.
 */
public class GroundMarkerPoint
{
    private final int regionId;
    private final int regionX;
    private final int regionY;
    private final int z;
    @Nullable
    private final Color color;
    @Nullable
    private final String label;

    public GroundMarkerPoint(int regionId, int regionX, int regionY, int z, @Nullable Color color, @Nullable String label)
    {
        this.regionId = regionId;
        this.regionX = regionX;
        this.regionY = regionY;
        this.z = z;
        this.color = color;
        this.label = label;
    }

    public int getRegionId()
    {
        return regionId;
    }

    public int getRegionX()
    {
        return regionX;
    }

    public int getRegionY()
    {
        return regionY;
    }

    public int getZ()
    {
        return z;
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

    /** The same tile is the same point, whatever its color or label. */
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof GroundMarkerPoint))
        {
            return false;
        }
        GroundMarkerPoint other = (GroundMarkerPoint) o;
        return regionId == other.regionId && regionX == other.regionX && regionY == other.regionY && z == other.z;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(regionId, regionX, regionY, z);
    }
}
