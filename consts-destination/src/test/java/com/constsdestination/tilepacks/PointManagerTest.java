/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination; a test for a pack
 *   without tiles)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsdestination.tilepacks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.constsdestination.tilepacks.data.GroundMarkerPoint;
import com.constsdestination.tilepacks.data.TilePack;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import org.junit.Test;

public class PointManagerTest extends TilePacksTest
{
    @Test
    public void getActivePointsCachesPointsByRegion()
    {
        TilePackManager tilePackManager = mock(TilePackManager.class);
        TilePack pack = new TilePack(1, "test", "["
            + "{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0},"
            + "{\"regionId\":1,\"regionX\":4,\"regionY\":5,\"z\":0},"
            + "{\"regionId\":2,\"regionX\":6,\"regionY\":7,\"z\":1}"
            + "]");
        when(tilePackManager.getEnabledPacks()).thenReturn(Collections.singletonList(1));
        when(tilePackManager.getTilePacks()).thenReturn(Collections.singletonMap(1, pack));

        PointManager pointManager = new PointManager(tilePackManager, gson, null);
        List<GroundMarkerPoint> first = pointManager.getActivePoints(1);
        List<GroundMarkerPoint> second = pointManager.getActivePoints(1);

        assertEquals(2, first.size());
        assertSame(first, second);
        assertEquals(1, pointManager.getActivePoints(2).size());
    }

    @Test
    public void loadPointsInvalidatesActivePointsCache()
    {
        TilePackManager tilePackManager = mock(TilePackManager.class);
        TilePack pack = new TilePack(1, "test", "["
            + "{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0}"
            + "]");
        when(tilePackManager.getEnabledPacks()).thenReturn(Collections.singletonList(1));
        when(tilePackManager.getTilePacks()).thenReturn(Collections.singletonMap(1, pack));
        Client client = mock(Client.class);
        when(client.getTopLevelWorldView()).thenReturn(null);

        PointManager pointManager = new PointManager(tilePackManager, gson, client);
        List<GroundMarkerPoint> first = pointManager.getActivePoints(1);
        pointManager.loadPoints();
        List<GroundMarkerPoint> second = pointManager.getActivePoints(1);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertNotSame(first, second);
    }

    @Test
    public void aPackWithoutTilesIsSkipped()
    {
        TilePackManager tilePackManager = mock(TilePackManager.class);
        Map<Integer, TilePack> packs = new HashMap<>();
        packs.put(1, new TilePack(1, "empty", null));
        packs.put(2, new TilePack(2, "test", "[{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0}]"));
        when(tilePackManager.getEnabledPacks()).thenReturn(Arrays.asList(1, 2));
        when(tilePackManager.getTilePacks()).thenReturn(packs);

        PointManager pointManager = new PointManager(tilePackManager, gson, null);

        assertEquals(1, pointManager.getActivePoints(1).size());
    }
}
