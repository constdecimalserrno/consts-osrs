/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, where the packs are
 *   saved in its own config group; tests for only your own packs being listed, turning packs on and off, editing a
 *   pack and reading the packs again)
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.constsdestination.ConstsDestinationConfig;
import com.constsdestination.tilepacks.data.TilePack;
import org.junit.Test;

public class TilePackManagerTest extends TilePacksTest
{
    private static final String GROUP = ConstsDestinationConfig.GROUP;

    @Test
    public void customPackSaveUpdateAndDeleteUseIndividualKey()
    {
        SavedConfig saved = new SavedConfig();
        TilePackManager manager = new TilePackManager(gson, saved.configManager);

        manager.addCustomPack("Custom", "[]");

        TilePack savedPack = gson.fromJson(saved.get(GROUP, "tilePacks_pack_10000"), TilePack.class);
        assertEquals("Custom", savedPack.packName);
        assertEquals("10000", saved.get(GROUP, "tilePacks_customId"));
        assertTrue("a new pack is on", manager.isPackEnabled(10000));

        manager.removeCustomPack(10000);
        assertNull(saved.get(GROUP, "tilePacks_pack_10000"));
        assertFalse(manager.isPackEnabled(10000));
        assertTrue(manager.getTilePacks().isEmpty());
    }

    @Test
    public void onlyYourOwnPacksAreListedInTheOrderTheyWereMade()
    {
        SavedConfig saved = new SavedConfig();
        saved.put(GROUP, "tilePacks_pack_10001", "{\"id\":10001,\"packName\":\"Second\",\"packTiles\":\"[]\"}");
        saved.put(GROUP, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"First\",\"packTiles\":\"[]\"}");
        // What Tile Packs saved about a built-in pack: no tiles of its own
        saved.put(GROUP, "tilePacks_pack_42", "{\"id\":42,\"visible\":false}");

        TilePackManager manager = new TilePackManager(gson, saved.configManager);

        assertEquals(java.util.List.of(10000, 10001), new java.util.ArrayList<>(manager.getTilePacks().keySet()));
        assertEquals("First", manager.getTilePacks().get(10000).packName);
    }

    @Test
    public void loadsSavedCustomPackFromIndividualKey()
    {
        SavedConfig saved = new SavedConfig();
        // Saved by Tile Packs, with a link and whether it was hidden: those are let be
        saved.put(GROUP, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"Saved\",\"link\":\"x\",\"packTiles\":\"[]\",\"visible\":false}");

        TilePack pack = new TilePackManager(gson, saved.configManager).getTilePacks().get(10000);

        assertNotNull(pack);
        assertEquals("Saved", pack.packName);
        assertEquals("[]", pack.packTiles);
    }

    @Test
    public void turningPacksOnAndOffSavesWhichAreOn()
    {
        SavedConfig saved = new SavedConfig();
        TilePackManager manager = new TilePackManager(gson, saved.configManager);

        manager.addEnabledPack(10001);
        manager.addEnabledPack(10003);
        assertEquals("[10001,10003]", saved.get(GROUP, "tilePacks_packs"));
        assertTrue(manager.isPackEnabled(10003));

        manager.removeEnabledPack(10001);
        manager.removeEnabledPack(10003);
        assertNull(saved.get(GROUP, "tilePacks_packs"));
        assertFalse(manager.isPackEnabled(10003));
    }

    @Test
    public void editingACustomPackSavesItsNameAndTiles()
    {
        SavedConfig saved = new SavedConfig();
        TilePackManager manager = new TilePackManager(gson, saved.configManager);
        manager.addCustomPack("Custom", "[]");
        TilePack pack = manager.getTilePacks().get(10000);

        manager.editCustomPack(pack, "Renamed", "[{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0}]");

        TilePack savedPack = gson.fromJson(saved.get(GROUP, "tilePacks_pack_10000"), TilePack.class);
        assertEquals("Renamed", savedPack.packName);
        assertEquals("[{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0}]", savedPack.packTiles);
        assertEquals("Renamed", new TilePackManager(gson, saved.configManager).getTilePacks().get(10000).packName);
    }

    @Test
    public void reloadReadsWhatIsSavedNow()
    {
        SavedConfig saved = new SavedConfig();
        TilePackManager manager = new TilePackManager(gson, saved.configManager);
        manager.addCustomPack("Mine", "[]");

        // Another profile, with a pack of its own, on
        saved.values.clear();
        saved.put(GROUP, "tilePacks_packs", "[10003]");
        saved.put(GROUP, "tilePacks_pack_10003", "{\"id\":10003,\"packName\":\"Theirs\",\"packTiles\":\"[]\"}");
        manager.reload();

        assertFalse(manager.isPackEnabled(10000));
        assertTrue(manager.isPackEnabled(10003));
        assertNull(manager.getTilePacks().get(10000));
        assertEquals("Theirs", manager.getTilePacks().get(10003).packName);
    }
}
