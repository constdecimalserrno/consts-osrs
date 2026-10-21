/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, where the migration
 *   brings Tile Packs' saved data over instead of migrating it in place; tests for each kind of data brought over,
 *   for data consts-Destination already has, and that Tile Packs' data is only read)
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.constsdestination.ConstsDestinationConfig;
import com.constsdestination.tilepacks.data.TilePack;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

public class TilePackMigrationsTest extends TilePacksTest
{
    private static final String OURS = ConstsDestinationConfig.GROUP;
    private static final String THEIRS = TilePackMigrations.TILE_PACKS_GROUP;
    private static final String THEIR_SETTINGS = TilePackMigrations.TILE_PACKS_CONFIG_GROUP;

    @Test
    public void migratesCustomPacksAndBuiltInConfigs()
    {
        // Tile Packs' saved data from before its version 2
        SavedConfig saved = new SavedConfig();
        saved.put(THEIRS, "customPacks", "{\"10000\":{\"id\":10000,\"packName\":\"Custom\",\"packTiles\":\"[]\"}}");
        saved.put(THEIRS, "tilePackConfigs", "{\"10000\":{\"packId\":10000,\"visible\":false},\"42\":{\"packId\":42,\"visible\":false}}");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        TilePack customPack = gson.fromJson(saved.get(OURS, "tilePacks_pack_10000"), TilePack.class);
        TilePack builtInConfig = gson.fromJson(saved.get(OURS, "tilePacks_pack_42"), TilePack.class);
        assertEquals("Custom", customPack.packName);
        assertFalse(customPack.visible);
        assertEquals(Integer.valueOf(42), builtInConfig.id);
        assertFalse(builtInConfig.visible);
        assertEquals("1", saved.get(OURS, "tilePacks_version"));
        // Tile Packs' own data stays as it was.
        assertNotNull(saved.get(THEIRS, "customPacks"));
        assertNotNull(saved.get(THEIRS, "tilePackConfigs"));
        assertNull(saved.get(THEIRS, "tilePacksVersion"));
    }

    @Test
    public void missingLegacyVisibilityDefaultsToVisible()
    {
        SavedConfig saved = new SavedConfig();
        saved.put(THEIRS, "customPacks", "{\"10000\":{\"id\":10000,\"packName\":\"Custom\",\"packTiles\":\"[]\"}}");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        TilePack pack = gson.fromJson(saved.get(OURS, "tilePacks_pack_10000"), TilePack.class);
        assertTrue(pack.visible);
    }

    @Test
    public void bringsOverEverythingTilePacksSavesNow()
    {
        SavedConfig saved = tilePacksVersion2();

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals("Custom", gson.fromJson(saved.get(OURS, "tilePacks_pack_10000"), TilePack.class).packName);
        assertFalse(gson.fromJson(saved.get(OURS, "tilePacks_pack_42"), TilePack.class).visible);
        assertEquals("[1,10000]", saved.get(OURS, "tilePacks_packs"));
        assertEquals("10000", saved.get(OURS, "tilePacks_customId"));
        assertEquals("false", saved.get(OURS, "tilePacks_showVisible"));
        assertEquals("true", saved.get(OURS, "tilePacks_showInvisible"));
        assertEquals("true", saved.get(OURS, "tilePacksDrawOnMinimap"));
        assertEquals("-16711936", saved.get(OURS, "tilePacksOverrideColor"));
        assertEquals("3.5", saved.get(OURS, "tilePacksBorderWidth"));
        assertEquals("1", saved.get(OURS, "tilePacks_version"));
    }

    @Test
    public void whatIsBroughtOverIsWhatTheTilePacksTabShows()
    {
        SavedConfig saved = tilePacksVersion2();

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();
        TilePackManager manager = new TilePackManager(gson, saved.configManager);
        FilterManager filters = new FilterManager(saved.configManager);

        assertTrue(manager.isPackEnabled(1));
        assertTrue(manager.isPackEnabled(10000));
        assertFalse(manager.isPackEnabled(42));
        assertEquals("Custom", manager.getTilePacks().get(10000).packName);
        assertFalse(manager.getTilePacks().get(42).visible);
        assertFalse(filters.getShowVisible());
        assertTrue(filters.getShowInvisible());
        // The next custom pack doesn't take an id Tile Packs used.
        manager.addCustomPack("Next", "[]");
        assertEquals("Next", manager.getTilePacks().get(10001).packName);
    }

    @Test
    public void tilePacksDataIsOnlyRead()
    {
        SavedConfig saved = tilePacksVersion2();
        Map<String, String> before = new HashMap<>(saved.values);

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        for (String group : new String[]{THEIRS, THEIR_SETTINGS})
        {
            verify(saved.configManager, never()).setConfiguration(eq(group), anyString(), anyString());
            verify(saved.configManager, never()).setConfiguration(eq(group), anyString(), (Object) any());
            verify(saved.configManager, never()).unsetConfiguration(eq(group), anyString());
        }
        for (Map.Entry<String, String> value : before.entrySet())
        {
            assertEquals(value.getKey(), value.getValue(), saved.values.get(value.getKey()));
        }
    }

    @Test
    public void alreadyMigratedConfigurationIsNotChanged()
    {
        SavedConfig saved = tilePacksVersion2();
        saved.put(OURS, "tilePacks_version", "1");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertNull(saved.get(OURS, "tilePacks_pack_10000"));
        assertEquals("1", saved.get(OURS, "tilePacks_version"));
        verify(saved.configManager, never()).setConfiguration(anyString(), anyString(), anyString());
        verify(saved.configManager, never()).setConfiguration(anyString(), anyString(), (Object) any());
    }

    @Test
    public void withoutTilePacksDataNothingButTheVersionIsSaved()
    {
        SavedConfig saved = new SavedConfig();

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals(1, saved.values.size());
        assertEquals("1", saved.get(OURS, "tilePacks_version"));
    }

    @Test
    public void packsConstsDestinationAlreadyHasAreKept()
    {
        SavedConfig saved = tilePacksVersion2();
        saved.put(OURS, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"Mine\",\"packTiles\":\"[]\",\"visible\":true}");
        saved.put(OURS, "tilePacks_packs", "[3,1]");
        saved.put(OURS, "tilePacks_customId", "10004");
        saved.put(OURS, "tilePacks_showVisible", "true");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals("Mine", gson.fromJson(saved.get(OURS, "tilePacks_pack_10000"), TilePack.class).packName);
        assertEquals("[3,1,10000]", saved.get(OURS, "tilePacks_packs"));
        assertEquals("10004", saved.get(OURS, "tilePacks_customId"));
        assertEquals("true", saved.get(OURS, "tilePacks_showVisible"));
    }

    @Test
    public void anUnreadablePackIsLeftOut()
    {
        SavedConfig saved = tilePacksVersion2();
        saved.put(THEIRS, "pack_7", "not json");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertNull(saved.get(OURS, "tilePacks_pack_7"));
        assertNotNull(saved.get(OURS, "tilePacks_pack_10000"));
        assertEquals("1", saved.get(OURS, "tilePacks_version"));
    }

    @Test(expected = RuntimeException.class)
    public void malformedLegacyConfigurationKeepsOldValues()
    {
        SavedConfig saved = new SavedConfig();
        saved.put(THEIRS, "customPacks", "not json");

        try
        {
            new TilePackMigrations(gson, saved.configManager).migrateToLatest();
        }
        finally
        {
            assertEquals("not json", saved.get(THEIRS, "customPacks"));
            assertNull(saved.get(OURS, "tilePacks_version"));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void failedReadBackKeepsOldValues()
    {
        SavedConfig saved = new SavedConfig();
        saved.put(THEIRS, "customPacks", "{\"10000\":{\"id\":10000,\"packName\":\"Custom\",\"packTiles\":\"[]\"}}");
        doAnswer(invocation -> null)
            .when(saved.configManager).setConfiguration(anyString(), startsWith("tilePacks_pack_"), anyString());

        try
        {
            new TilePackMigrations(gson, saved.configManager).migrateToLatest();
        }
        finally
        {
            assertNotNull(saved.get(THEIRS, "customPacks"));
            assertNull(saved.get(OURS, "tilePacks_version"));
        }
    }

    @Test
    public void everyTilePacksSettingHasOneOfOurs() throws Exception
    {
        for (Map.Entry<String, String> setting : TilePackMigrations.SETTINGS.entrySet())
        {
            boolean found = false;
            for (Method method : ConstsDestinationConfig.class.getMethods())
            {
                ConfigItem item = method.getAnnotation(ConfigItem.class);
                if (item != null && item.keyName().equals(setting.getValue()))
                {
                    assertEquals(setting.getValue(), "tilePacks", item.section());
                    found = true;
                }
            }
            assertTrue(setting.getValue(), found);
        }
    }

    /** Tile Packs' data as it saves it since version 2: a key per pack, which are on, the filters, the settings. */
    private static SavedConfig tilePacksVersion2()
    {
        SavedConfig saved = new SavedConfig();
        saved.put(THEIRS, "tilePacksVersion", "2");
        saved.put(THEIRS, "pack_10000", "{\"id\":10000,\"packName\":\"Custom\",\"packTiles\":\"[]\",\"visible\":true}");
        saved.put(THEIRS, "pack_42", "{\"id\":42,\"visible\":false}");
        saved.put(THEIRS, "packs", "[1,10000]");
        saved.put(THEIRS, "customId", "10000");
        saved.put(THEIRS, "showVisible", "false");
        saved.put(THEIRS, "showInvisible", "true");
        saved.put(THEIR_SETTINGS, "drawTilesOnMinimmap", "true");
        saved.put(THEIR_SETTINGS, "overrideColor", "-16711936");
        saved.put(THEIR_SETTINGS, "borderWidth", "3.5");
        saved.put(THEIR_SETTINGS, "hidePlugin", "true");
        return saved;
    }
}
