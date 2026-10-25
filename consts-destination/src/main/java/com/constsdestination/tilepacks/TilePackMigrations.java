/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination; instead of migrating
 *   Tile Packs' saved data in place, it brings it over into consts-Destination's once, from either of the formats
 *   Tile Packs has saved, and leaves Tile Packs' own data as it was; lombok written out; a second step makes the
 *   built-in packs that are on your own, as consts-Destination has no built-in packs to pick from)
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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
 * packs: the packs that were on, the custom packs and Tile Packs' settings. Tile Packs' data is only read: nothing of
 * it is changed or deleted, so Tile Packs works as before for as long as it's installed.
 *
 * <p>Then, once, the built-in packs that are on become your own (version 2): consts-Destination has no list of
 * built-in packs to pick from, only yours. Each keeps its name and tiles and stays on, in the order they were turned
 * on; the built-in packs that were off, and what was saved about them, go. Their tiles come from Tile Packs' list in
 * tilePacks.jsonc, which is read for nothing else.
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
    private static final String CUSTOM_PACKS = "customPacks";

    /** Tile Packs' settings, by its key, and the consts-Destination settings that take their place. */
    static final Map<String, String> SETTINGS = ImmutableMap.<String, String>builder()
        .put("showLabels", "tilePacksShowLabels")
        .put("overrideColorActive", "tilePacksOverrideColorActive")
        .put("overrideColor", "tilePacksOverrideColor")
        .put("drawTilesOnMinimmap", "tilePacksDrawOnMinimap")
        .put("borderWidth", "tilePacksBorderWidth")
        .put("fillOpacity", "tilePacksFillOpacity")
        .build();

    /**
     * The version of consts-Destination's tile pack data: 1 once Tile Packs' has been brought over, 2 once the
     * built-in packs that were on are your own.
     */
    static final String TILE_PACKS_VERSION = TilePackManager.KEY_PREFIX + "version";
    // More for reference than actual use.
    private static final int CURRENT_VERSION = 2;

    /** Where the list of built-in packs filtered by on or off was kept, before there were only your own packs. */
    private static final String[] OLD_FILTERS = {
        TilePackManager.KEY_PREFIX + "showVisible", TilePackManager.KEY_PREFIX + "showInvisible"
    };

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
        if (version < 2)
        {
            ownBuiltInPacks();
            saveVersion(2);
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

    /**
     * Tile Packs' custom packs, from a key per pack, or else from its keys from before version 2. What it saved about
     * its built-in packs, hidden from its list or not, doesn't matter here: there's no such list.
     */
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
                if (pack != null && id.equals(pack.id) && pack.packTiles != null)
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

    /** Tile Packs' custom packs as its version 2 saves them, from what it saved before. */
    private Map<Integer, TilePack> migrateV1ToV2()
    {
        Map<Integer, TilePack> packs = new HashMap<>();
        String customPacksJson = configManager.getConfiguration(TILE_PACKS_GROUP, CUSTOM_PACKS);
        if (Strings.isNullOrEmpty(customPacksJson))
        {
            return packs;
        }

        Map<Integer, LegacyTilePack> customPacks = parseMap(customPacksJson, LegacyTilePack.class);
        for (Map.Entry<Integer, LegacyTilePack> customPack : customPacks.entrySet())
        {
            LegacyTilePack legacy = customPack.getValue();
            if (legacy != null && legacy.packTiles != null)
            {
                packs.put(customPack.getKey(), new TilePack(customPack.getKey(), legacy.packName, legacy.packTiles));
            }
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

    /**
     * Version 2: each built-in pack that's on becomes a pack of your own, with the next free number, its name and its
     * tiles, and stays on where it was among the packs that are on. If this stops part way, it's done again from the
     * start at the next start, with the same numbers, so nothing is made twice.
     */
    private void ownBuiltInPacks()
    {
        List<Integer> enabled = parseList(configManager.getConfiguration(GROUP, TilePackManager.PACKS_PREFIX));
        Map<Integer, TilePack> builtIn = null;
        String savedId = configManager.getConfiguration(GROUP, TilePackManager.CUSTOM_ID);
        int customId = Strings.isNullOrEmpty(savedId) ? TilePackManager.FIRST_ID - 1 : Integer.parseInt(savedId);

        Set<Integer> made = new HashSet<>();
        Set<Integer> stillOn = new LinkedHashSet<>();
        for (Integer id : enabled)
        {
            if (id == null)
            {
                continue;
            }
            if (id >= TilePackManager.FIRST_ID)
            {
                stillOn.add(id);
                continue;
            }
            if (builtIn == null)
            {
                builtIn = loadBuiltInPacks();
            }
            TilePack pack = builtIn.get(id);
            if (pack == null || Strings.isNullOrEmpty(pack.packTiles))
            {
                log.warn("Tile pack {} was on but isn't a built-in pack; it's left out", id);
                continue;
            }
            customId++;
            configManager.setConfiguration(GROUP, TilePackManager.PACK_PREFIX + customId,
                gson.toJson(new TilePack(customId, pack.packName, pack.packTiles)));
            made.add(customId);
            stillOn.add(customId);
        }
        verifySavedPacks(made);

        if (!made.isEmpty())
        {
            configManager.setConfiguration(GROUP, TilePackManager.CUSTOM_ID, customId);
        }
        if (stillOn.isEmpty())
        {
            configManager.unsetConfiguration(GROUP, TilePackManager.PACKS_PREFIX);
        }
        else
        {
            configManager.setConfiguration(GROUP, TilePackManager.PACKS_PREFIX, gson.toJson(new ArrayList<>(stillOn)));
        }

        // What was saved about the built-in packs, hidden or not, and how their list was filtered, is let go
        String wholePrefix = ConfigManager.getWholeKey(GROUP, null, TilePackManager.PACK_PREFIX);
        for (String key : new ArrayList<>(configManager.getConfigurationKeys(wholePrefix)))
        {
            String id = key.substring(wholePrefix.length());
            try
            {
                if (Integer.parseInt(id) < TilePackManager.FIRST_ID)
                {
                    configManager.unsetConfiguration(GROUP, TilePackManager.PACK_PREFIX + id);
                }
            }
            catch (NumberFormatException e)
            {
                log.warn("Unable to read tile pack config {}", key);
            }
        }
        for (String filter : OLD_FILTERS)
        {
            configManager.unsetConfiguration(GROUP, filter);
        }
    }

    /** Tile Packs' built-in packs, by number, from its list. */
    private Map<Integer, TilePack> loadBuiltInPacks()
    {
        try (InputStream in = TilePackMigrations.class.getResourceAsStream("tilePacks.jsonc"))
        {
            if (in == null)
            {
                throw new IllegalStateException("tilePacks.jsonc is missing");
            }
            return toMap(gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class),
                TilePack.class);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to read tilePacks.jsonc", e);
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
        Integer[] parsed = gson.fromJson(json, Integer[].class);
        return parsed == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(parsed));
    }

    private <T> Map<Integer, T> parseMap(String json, Class<T> type)
    {
        if (Strings.isNullOrEmpty(json))
        {
            return new HashMap<>();
        }
        return toMap(gson.fromJson(json, JsonObject.class), type);
    }

    /** A JSON object keyed by pack number, as a map, each value read as type. */
    private <T> Map<Integer, T> toMap(JsonObject json, Class<T> type)
    {
        Map<Integer, T> map = new HashMap<>();
        if (json != null)
        {
            for (Map.Entry<String, JsonElement> entry : json.entrySet())
            {
                map.put(Integer.valueOf(entry.getKey()), gson.fromJson(entry.getValue(), type));
            }
        }
        return map;
    }

    private static class LegacyTilePack
    {
        private Integer id;
        private String packName;
        private String packTiles;
    }
}
