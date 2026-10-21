/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, where the packs are
 *   saved in its own config group; tests for the built-in packs, turning packs on and off, editing a custom pack and
 *   reading the packs again)
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
        assertTrue(savedPack.visible);
        assertEquals("10000", saved.get(GROUP, "tilePacks_customId"));

        savedPack.visible = false;
        manager.updatePack(savedPack);
        assertFalse(gson.fromJson(saved.get(GROUP, "tilePacks_pack_10000"), TilePack.class).visible);

        manager.removeCustomPack(10000);
        assertNull(saved.get(GROUP, "tilePacks_pack_10000"));
    }

    @Test
    public void builtInVisibilitySavesWithoutReplacingResourcePack()
    {
        SavedConfig saved = new SavedConfig();
        TilePackManager manager = new TilePackManager(gson, saved.configManager);

        TilePack resourcePack = manager.getTilePacks().get(1);
        String originalTiles = resourcePack.packTiles;
        resourcePack.visible = false;
        manager.updatePack(resourcePack);

        TilePack savedConfig = gson.fromJson(saved.get(GROUP, "tilePacks_pack_1"), TilePack.class);
        assertEquals(Integer.valueOf(1), savedConfig.id);
        assertFalse(savedConfig.visible);
        assertNull(savedConfig.packTiles);
        assertEquals(originalTiles, manager.getTilePacks().get(1).packTiles);
    }

    @Test
    public void loadsSavedCustomPackFromIndividualKey()
    {
        SavedConfig saved = new SavedConfig();
        saved.put(GROUP, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"Saved\",\"packTiles\":\"[]\",\"visible\":false}");

        TilePack pack = new TilePackManager(gson, saved.configManager).getTilePacks().get(10000);

        assertNotNull(pack);
        assertEquals("Saved", pack.packName);
        assertFalse(pack.visible);
    }

    @Test
    public void theBuiltInPacksLoadWithTheirLinks()
    {
        TilePackManager manager = new TilePackManager(gson, new SavedConfig().configManager);

        assertTrue(manager.getTilePacks().size() > 80);
        TilePack sire = manager.getTilePacks().get(1);
        assertEquals("Abyssal Sire", sire.packName);
        assertEquals("https://www.youtube.com/watch?v=9p9ft1fGFng", sire.link);
    }

    @Test
    public void turningPacksOnAndOffSavesWhichAreOn()
    {
        SavedConfig saved = new SavedConfig();
        TilePackManager manager = new TilePackManager(gson, saved.configManager);

        manager.addEnabledPack(1);
        manager.addEnabledPack(3);
        assertEquals("[1,3]", saved.get(GROUP, "tilePacks_packs"));
        assertTrue(manager.isPackEnabled(3));

        manager.removeEnabledPack(1);
        manager.removeEnabledPack(3);
        assertNull(saved.get(GROUP, "tilePacks_packs"));
        assertFalse(manager.isPackEnabled(3));
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
        manager.addEnabledPack(1);

        // Another profile: pack 3 on, and a custom pack.
        saved.values.clear();
        saved.put(GROUP, "tilePacks_packs", "[3]");
        saved.put(GROUP, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"Theirs\",\"packTiles\":\"[]\",\"visible\":true}");
        manager.reload();

        assertFalse(manager.isPackEnabled(1));
        assertTrue(manager.isPackEnabled(3));
        assertEquals("Theirs", manager.getTilePacks().get(10000).packName);
    }
}
