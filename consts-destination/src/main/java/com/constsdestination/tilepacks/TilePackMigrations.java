/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination; instead of migrating
 *   Tile Packs' saved data in place, it brings it over into consts-Destination's once, from either of the formats
 *   Tile Packs has saved, and leaves Tile Packs' own data as it was; lombok written out)
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

import com.constsdestination.tilepacks.data.TilePack;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings Tile Packs' saved data over into consts-Destination's, the first time consts-Destination starts with tile
 * packs: the packs that were on, the custom packs, which packs were hidden from the list, the list's filters and Tile
 * Packs' settings. Tile Packs' data is only read: nothing of it is changed or deleted, so Tile Packs works as before
 * for as long as it's installed.
 *
 * <p>Tile Packs has saved its packs in two formats. Before its version 2, the custom packs were in one key and the
 * built-in packs' visibility in another; since, each pack has a key of its own. Its own migration from the first to the
 * second is the conversion here, so either is brought over, whether or not Tile Packs has migrated it yet.
 *
 * <p>Like Tile Packs' migrations, it writes and reads back before it counts as done, and a failure throws, so it's
 * tried again at the next start.
 */
public class TilePackMigrations
{
    private static final Logger log = LoggerFactory.getLogger(TilePackMigrations.class);

    /** Tile Packs' saved data. Read, never written. */
    static final String TILE_PACKS_GROUP = "tilePacks";
    /** Tile Packs' settings. Read, never written. */
    static final String TILE_PACKS_CONFIG_GROUP = "tilepacks";

    private static final String PACK_PREFIX = "pack_";
    private static final String PACKS = "packs";
    private static final String CUSTOM_ID = "customId";
    private static final String SHOW_VISIBLE = "showVisible";
    private static final String SHOW_INVISIBLE = "showInvisible";
    private static final String CUSTOM_PACKS = "customPacks";
    private static final String TILE_PACK_CONFIGS = "tilePackConfigs";

    /** Tile Packs' settings, by its key, and the consts-Destination settings that take their place. */
    static final Map<String, String> SETTINGS = ImmutableMap.<String, String>builder()
        .put("showLabels", "tilePacksShowLabels")
        .put("overrideColorActive", "tilePacksOverrideColorActive")
        .put("overrideColor", "tilePacksOverrideColor")
        .put("drawTilesOnMinimmap", "tilePacksDrawOnMinimap")
        .put("borderWidth", "tilePacksBorderWidth")
        .put("fillOpacity", "tilePacksFillOpacity")
        .build();

    /** The version of consts-Destination's tile pack data: 1 once Tile Packs' has been brought over. */
    static final String TILE_PACKS_VERSION = TilePackManager.KEY_PREFIX + "version";
    // More for reference than actual use.
    private static final int CURRENT_VERSION = 1;

    private static final String GROUP = TilePackManager.CONFIG_GROUP;

    private final Gson gson;
    private final ConfigManager configManager;

    @Inject
    TilePackMigrations(Gson gson, ConfigManager configManager)
    {
        this.gson = gson;
        this.configManager = configManager;
    }

    void migrateToLatest()
    {
        int version = loadVersion();

        if (version < 1)
        {
            importFromTilePacks();
            saveVersion(1);
        }
    }

    private int loadVersion()
    {
        String version = configManager.getConfiguration(GROUP, TILE_PACKS_VERSION);
        return Strings.isNullOrEmpty(version) ? 0 : Integer.parseInt(version);
    }

    private void saveVersion(int version)
    {
        configManager.setConfiguration(GROUP, TILE_PACKS_VERSION, version);
    }

    private void importFromTilePacks()
    {
        Map<Integer, TilePack> packs = loadTilePacksPacks();
        Set<Integer> imported = new HashSet<>();
        for (TilePack pack : packs.values())
        {
            // A pack consts-Destination already has stays as it is.
            if (Strings.isNullOrEmpty(configManager.getConfiguration(GROUP, TilePackManager.PACK_PREFIX + pack.id)))
            {
                configManager.setConfiguration(GROUP, TilePackManager.PACK_PREFIX + pack.id, gson.toJson(pack));
                imported.add(pack.id);
            }
        }
        verifySavedPacks(imported);

        importEnabledPacks();
        importCustomId();
        importIfUnset(SHOW_VISIBLE, FilterManager.SHOW_VISIBLE);
        importIfUnset(SHOW_INVISIBLE, FilterManager.SHOW_INVISIBLE);

        // consts-Destination's own tile pack settings are new, so Tile Packs' replace them.
        for (Map.Entry<String, String> setting : SETTINGS.entrySet())
        {
            String value = configManager.getConfiguration(TILE_PACKS_CONFIG_GROUP, setting.getKey());
            if (value != null)
            {
                configManager.setConfiguration(GROUP, setting.getValue(), value);
            }
        }
    }

    /** Tile Packs' saved packs, from a key per pack, or else from its keys from before version 2. */
    private Map<Integer, TilePack> loadTilePacksPacks()
    {
        Map<Integer, TilePack> packs = migrateV1ToV2();

        String wholePrefix = ConfigManager.getWholeKey(TILE_PACKS_GROUP, null, PACK_PREFIX);
        for (String key : configManager.getConfigurationKeys(wholePrefix))
        {
            // As Tile Packs reads them: a pack it can't read is left out.
            try
            {
                Integer id = Integer.valueOf(key.substring(wholePrefix.length()));
                String json = configManager.getConfiguration(TILE_PACKS_GROUP, PACK_PREFIX + id);
                TilePack pack = gson.fromJson(json, TilePack.class);
                if (pack != null && id.equals(pack.id))
                {
                    packs.put(id, pack);
                }
            }
            catch (RuntimeException e)
            {
                log.warn("Unable to bring over tile pack config {}", key, e);
            }
        }
        return packs;
    }

    /** Tile Packs' packs as its version 2 saves them, from what it saved before. */
    private Map<Integer, TilePack> migrateV1ToV2()
    {
        Map<Integer, TilePack> packs = new HashMap<>();
        String customPacksJson = configManager.getConfiguration(TILE_PACKS_GROUP, CUSTOM_PACKS);
        String tilePackConfigsJson = configManager.getConfiguration(TILE_PACKS_GROUP, TILE_PACK_CONFIGS);

        if (Strings.isNullOrEmpty(customPacksJson) && Strings.isNullOrEmpty(tilePackConfigsJson))
        {
            return packs;
        }

        Map<Integer, LegacyTilePack> customPacks = parseMap(customPacksJson,
            new TypeToken<Map<Integer, LegacyTilePack>>()
            {
            }.getType());
        Map<Integer, LegacyTilePackConfig> tilePackConfigs = parseMap(tilePackConfigsJson,
            new TypeToken<Map<Integer, LegacyTilePackConfig>>()
            {
            }.getType());

        Set<Integer> ids = new HashSet<>();
        ids.addAll(customPacks.keySet());
        ids.addAll(tilePackConfigs.keySet());

        for (Integer id : ids)
        {
            LegacyTilePack customPack = customPacks.get(id);
            LegacyTilePackConfig config = tilePackConfigs.get(id);
            boolean visible = config == null || config.visible;

            TilePack pack;
            if (customPack == null)
            {
                pack = new TilePack(id, visible);
            }
            else
            {
                pack = new TilePack(id, customPack.packName, customPack.link, customPack.packTiles);
                pack.visible = visible;
            }

            packs.put(id, pack);
        }
        return packs;
    }

    /** The packs that were on in Tile Packs are on here too, after any that already are. */
    private void importEnabledPacks()
    {
        List<Integer> theirs = parseList(configManager.getConfiguration(TILE_PACKS_GROUP, PACKS));
        if (theirs.isEmpty())
        {
            return;
        }
        Set<Integer> enabled = new LinkedHashSet<>(parseList(configManager.getConfiguration(GROUP, TilePackManager.PACKS_PREFIX)));
        enabled.addAll(theirs);
        configManager.setConfiguration(GROUP, TilePackManager.PACKS_PREFIX, gson.toJson(new ArrayList<>(enabled)));
    }

    /** The next custom pack gets an id neither plugin has used. */
    private void importCustomId()
    {
        String theirs = configManager.getConfiguration(TILE_PACKS_GROUP, CUSTOM_ID);
        if (Strings.isNullOrEmpty(theirs))
        {
            return;
        }
        String ours = configManager.getConfiguration(GROUP, TilePackManager.CUSTOM_ID);
        int customId = Integer.parseInt(theirs);
        if (!Strings.isNullOrEmpty(ours))
        {
            customId = Math.max(customId, Integer.parseInt(ours));
        }
        configManager.setConfiguration(GROUP, TilePackManager.CUSTOM_ID, customId);
    }

    private void importIfUnset(String theirKey, String ourKey)
    {
        String value = configManager.getConfiguration(TILE_PACKS_GROUP, theirKey);
        if (!Strings.isNullOrEmpty(value) && Strings.isNullOrEmpty(configManager.getConfiguration(GROUP, ourKey)))
        {
            configManager.setConfiguration(GROUP, ourKey, value);
        }
    }

    private void verifySavedPacks(Set<Integer> ids)
    {
        for (Integer id : ids)
        {
            String json = configManager.getConfiguration(GROUP, TilePackManager.PACK_PREFIX + id);
            if (Strings.isNullOrEmpty(json))
            {
                throw new IllegalStateException("Saved tile pack config is missing for id " + id);
            }

            TilePack pack = gson.fromJson(json, TilePack.class);
            if (pack == null || !id.equals(pack.id))
            {
                throw new IllegalStateException("Saved tile pack config is invalid for id " + id);
            }
        }
    }

    private List<Integer> parseList(String json)
    {
        if (Strings.isNullOrEmpty(json))
        {
            return new ArrayList<>();
        }
        List<Integer> parsed = gson.fromJson(json, new TypeToken<List<Integer>>()
        {
        }.getType());
        return parsed == null ? new ArrayList<>() : parsed;
    }

    private <T> Map<Integer, T> parseMap(String json, Type type)
    {
        if (Strings.isNullOrEmpty(json))
        {
            return new HashMap<>();
        }
        Map<Integer, T> parsed = gson.fromJson(json, type);
        return parsed == null ? new HashMap<>() : parsed;
    }

    private static class LegacyTilePack
    {
        private Integer id;
        private String packName;
        private String link;
        private String packTiles;
    }

    private static class LegacyTilePackConfig
    {
        private Integer packId;
        private boolean visible;
    }
}
