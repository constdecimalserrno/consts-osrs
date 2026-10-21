/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, where the migration
 *   brings Tile Packs' saved data over instead of migrating it in place; tests for each kind of data brought over,
 *   for data consts-Destination already has, that Tile Packs' data is only read, and for the built-in packs that are
 *   on becoming your own)
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
import com.google.common.reflect.TypeToken;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

public class TilePackMigrationsTest extends TilePacksTest
{
    private static final String OURS = ConstsDestinationConfig.GROUP;
    private static final String THEIRS = TilePackMigrations.TILE_PACKS_GROUP;
    private static final String THEIR_SETTINGS = TilePackMigrations.TILE_PACKS_CONFIG_GROUP;

    @Test
    public void bringsOverTheCustomPacksTilePacksSavedBeforeItsVersion2()
    {
        SavedConfig saved = new SavedConfig();
        saved.put(THEIRS, "customPacks", "{\"10000\":{\"id\":10000,\"packName\":\"Custom\",\"packTiles\":\"[]\"}}");
        saved.put(THEIRS, "tilePackConfigs", "{\"10000\":{\"packId\":10000,\"visible\":false},\"42\":{\"packId\":42,\"visible\":false}}");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals("Custom", pack(saved, 10000).packName);
        assertNull("whether a built-in pack was hidden doesn't come over", saved.get(OURS, "tilePacks_pack_42"));
        assertEquals("2", saved.get(OURS, "tilePacks_version"));
        // Tile Packs' own data stays as it was.
        assertNotNull(saved.get(THEIRS, "customPacks"));
        assertNotNull(saved.get(THEIRS, "tilePackConfigs"));
        assertNull(saved.get(THEIRS, "tilePacksVersion"));
    }

    @Test
    public void bringsOverWhatTilePacksSavesNowAndTheBuiltInPacksThatAreOnBecomeYours()
    {
        SavedConfig saved = tilePacksVersion2();

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals("Custom", pack(saved, 10000).packName);
        // Abyssal Sire was on first, so it's still first, as a pack of your own
        assertEquals("Abyssal Sire", pack(saved, 10001).packName);
        assertEquals(builtIn(1).packTiles, pack(saved, 10001).packTiles);
        assertEquals("[10001,10000]", saved.get(OURS, "tilePacks_packs"));
        assertEquals("10001", saved.get(OURS, "tilePacks_customId"));
        assertNull(saved.get(OURS, "tilePacks_pack_42"));
        assertNull(saved.get(OURS, "tilePacks_showVisible"));
        assertNull(saved.get(OURS, "tilePacks_showInvisible"));
        assertEquals("true", saved.get(OURS, "tilePacksDrawOnMinimap"));
        assertEquals("-16711936", saved.get(OURS, "tilePacksOverrideColor"));
        assertEquals("3.5", saved.get(OURS, "tilePacksBorderWidth"));
        assertEquals("2", saved.get(OURS, "tilePacks_version"));
    }

    @Test
    public void whatIsBroughtOverIsWhatTheTilePacksTabShows()
    {
        SavedConfig saved = tilePacksVersion2();

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();
        TilePackManager manager = new TilePackManager(gson, saved.configManager);

        assertEquals(List.of(10000, 10001), new ArrayList<>(manager.getTilePacks().keySet()));
        assertTrue(manager.isPackEnabled(10000));
        assertTrue(manager.isPackEnabled(10001));
        assertEquals("Custom", manager.getTilePacks().get(10000).packName);
        // The next pack doesn't take a number already used
        manager.addCustomPack("Next", "[]");
        assertEquals("Next", manager.getTilePacks().get(10002).packName);
    }

    @Test
    public void theBuiltInPacksYouHadOnBecomeYoursInTheOrderTheyWereOn()
    {
        // Tile Packs' data was brought over before there were only your own packs
        SavedConfig saved = new SavedConfig();
        saved.put(OURS, "tilePacks_version", "1");
        saved.put(OURS, "tilePacks_packs", "[84,21]");
        saved.put(OURS, "tilePacks_pack_42", "{\"id\":42,\"visible\":false}");
        saved.put(OURS, "tilePacks_showInvisible", "true");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals("Tithe farm", pack(saved, 10000).packName);
        assertEquals(builtIn(84).packTiles, pack(saved, 10000).packTiles);
        assertEquals("Hallowed Sepulchre", pack(saved, 10001).packName);
        assertEquals("[10000,10001]", saved.get(OURS, "tilePacks_packs"));
        assertEquals("10001", saved.get(OURS, "tilePacks_customId"));
        assertNull(saved.get(OURS, "tilePacks_pack_42"));
        assertNull(saved.get(OURS, "tilePacks_showInvisible"));
        assertEquals("2", saved.get(OURS, "tilePacks_version"));
    }

    @Test
    public void aPackThatIsntInTheBuiltInListIsLeftOut()
    {
        SavedConfig saved = new SavedConfig();
        saved.put(OURS, "tilePacks_version", "1");
        saved.put(OURS, "tilePacks_packs", "[999]");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertNull(saved.get(OURS, "tilePacks_packs"));
        assertNull(saved.get(OURS, "tilePacks_pack_10000"));
        assertEquals("2", saved.get(OURS, "tilePacks_version"));
    }

    @Test
    public void startingOverAfterAStopMakesNothingTwice()
    {
        // A pack made, but the run stopped before the packs that are on were saved
        SavedConfig saved = new SavedConfig();
        saved.put(OURS, "tilePacks_version", "1");
        saved.put(OURS, "tilePacks_packs", "[84]");
        saved.put(OURS, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"Tithe farm\",\"packTiles\":\"[]\"}");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals(builtIn(84).packTiles, pack(saved, 10000).packTiles);
        assertNull(saved.get(OURS, "tilePacks_pack_10001"));
        assertEquals("[10000]", saved.get(OURS, "tilePacks_packs"));
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
        saved.put(OURS, "tilePacks_version", "2");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertNull(saved.get(OURS, "tilePacks_pack_10000"));
        assertEquals("2", saved.get(OURS, "tilePacks_version"));
        verify(saved.configManager, never()).setConfiguration(anyString(), anyString(), anyString());
        verify(saved.configManager, never()).setConfiguration(anyString(), anyString(), (Object) any());
    }

    @Test
    public void withoutTilePacksDataNothingButTheVersionIsSaved()
    {
        SavedConfig saved = new SavedConfig();

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals(1, saved.values.size());
        assertEquals("2", saved.get(OURS, "tilePacks_version"));
    }

    @Test
    public void packsConstsDestinationAlreadyHasAreKept()
    {
        SavedConfig saved = tilePacksVersion2();
        saved.put(OURS, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"Mine\",\"packTiles\":\"[]\"}");
        saved.put(OURS, "tilePacks_packs", "[3,1]");
        saved.put(OURS, "tilePacks_customId", "10004");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertEquals("Mine", pack(saved, 10000).packName);
        assertEquals("Alchemical Hydra (range)", pack(saved, 10005).packName);
        assertEquals("Abyssal Sire", pack(saved, 10006).packName);
        assertEquals("[10005,10006,10000]", saved.get(OURS, "tilePacks_packs"));
        assertEquals("10006", saved.get(OURS, "tilePacks_customId"));
    }

    @Test
    public void anUnreadablePackIsLeftOut()
    {
        SavedConfig saved = tilePacksVersion2();
        saved.put(THEIRS, "pack_7", "not json");

        new TilePackMigrations(gson, saved.configManager).migrateToLatest();

        assertNull(saved.get(OURS, "tilePacks_pack_7"));
        assertNotNull(saved.get(OURS, "tilePacks_pack_10000"));
        assertEquals("2", saved.get(OURS, "tilePacks_version"));
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

    private TilePack pack(SavedConfig saved, int id)
    {
        String json = saved.get(OURS, "tilePacks_pack_" + id);
        assertNotNull("no pack " + id, json);
        return gson.fromJson(json, TilePack.class);
    }

    /** A pack from Tile Packs' built-in list. */
    private TilePack builtIn(int id)
    {
        Map<Integer, TilePack> packs = gson.fromJson(new InputStreamReader(
            TilePackMigrations.class.getResourceAsStream("tilePacks.jsonc"), StandardCharsets.UTF_8),
            new TypeToken<Map<Integer, TilePack>>()
            {
            }.getType());
        return packs.get(id);
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
