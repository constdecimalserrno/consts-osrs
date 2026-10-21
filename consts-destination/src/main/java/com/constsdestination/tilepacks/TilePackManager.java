/*
 * Copyright (c) 2024, Trevor <https://github.com/TrevorMDev>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, which saves the packs
 *   in its own config group; lombok written out; a custom pack can be edited; the packs can be read again after a
 *   profile change)
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
package com.constsdestination.tilepacks;

import com.constsdestination.ConstsDestinationConfig;
import com.constsdestination.tilepacks.data.TilePack;
import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for all functions that interface with or modify tile packs
 *
 * <p>The packs are saved in consts-Destination's config group, under Tile Packs' own key names with "tilePacks_" in
 * front: raw values, not settings.
 */
public class TilePackManager
{
    private static final Logger log = LoggerFactory.getLogger(TilePackManager.class);

    static final String CONFIG_GROUP = ConstsDestinationConfig.GROUP;
    static final String KEY_PREFIX = "tilePacks_";
    static final String CUSTOM_ID = KEY_PREFIX + "customId";
    static final String PACK_PREFIX = KEY_PREFIX + "pack_";
    static final String PACKS_PREFIX = KEY_PREFIX + "packs";

    private Map<Integer, TilePack> tilePacks = new HashMap<Integer, TilePack>();
    private List<Integer> enabledPacks = new ArrayList<Integer>();

    private final Gson gson;
    private final ConfigManager configManager;

    @Inject
    TilePackManager(Gson gson, ConfigManager configManager)
    {
        this.gson = gson;
        this.configManager = configManager;

        this.loadEnabledPacks();
        this.loadPacks();
    }

    public Map<Integer, TilePack> getTilePacks()
    {
        return tilePacks;
    }

    public List<Integer> getEnabledPacks()
    {
        return enabledPacks;
    }

    /** Reads the saved packs again, for another profile's. */
    void reload()
    {
        enabledPacks = new ArrayList<Integer>();
        loadEnabledPacks();
        loadPacks();
    }

    //loads the packs from the json file
    private void loadPacks()
    {
        try (InputStream in = getClass().getResourceAsStream("tilePacks.jsonc"))
        {
            final InputStreamReader data = new InputStreamReader(in, StandardCharsets.UTF_8);
            final Type type = new TypeToken<Map<Integer, TilePack>>()
            {
            }.getType();
            Map<Integer, TilePack> parsed = gson.fromJson(data, type);
            for (TilePack savedPack : loadSavedPacks().values())
            {
                TilePack resourcePack = parsed.get(savedPack.id);
                if (savedPack.packTiles != null)
                {
                    parsed.put(savedPack.id, savedPack);
                }
                else if (resourcePack != null)
                {
                    resourcePack.visible = savedPack.visible;
                }
            }

            tilePacks = parsed;
        }
        catch (Exception e)
        {
            log.error("error loading packs from json, this is likely due to a bad json file.", e);
        }
    }

    private Map<Integer, TilePack> loadSavedPacks()
    {
        Map<Integer, TilePack> savedPacks = new HashMap<Integer, TilePack>();
        String wholePrefix = ConfigManager.getWholeKey(CONFIG_GROUP, null, PACK_PREFIX);
        for (String key : configManager.getConfigurationKeys(wholePrefix))
        {
            String packId = key.substring(wholePrefix.length());
            try
            {
                Integer id = Integer.valueOf(packId);
                String json = configManager.getConfiguration(CONFIG_GROUP, PACK_PREFIX + id);
                TilePack pack = gson.fromJson(json, TilePack.class);
                if (pack != null)
                {
                    savedPacks.put(id, pack);
                }
            }
            catch (RuntimeException e)
            {
                log.warn("Unable to load tile pack config {}", key, e);
            }
        }
        return savedPacks;
    }

    private void savePack(TilePack pack)
    {
        configManager.setConfiguration(CONFIG_GROUP, PACK_PREFIX + pack.id, gson.toJson(pack));
    }

    //saves a pack id to the saved config of enabled packs
    public void addEnabledPack(Integer packId)
    {
        enabledPacks.add(packId);

        String json = gson.toJson(enabledPacks);
        configManager.setConfiguration(CONFIG_GROUP, PACKS_PREFIX, json);
    }

    //removes a pack id from the saved config of enabled packs
    public void removeEnabledPack(Integer packId)
    {
        enabledPacks.remove(packId);
        if (enabledPacks.isEmpty())
        {
            configManager.unsetConfiguration(CONFIG_GROUP, PACKS_PREFIX);
            return;
        }

        String json = gson.toJson(enabledPacks);
        configManager.setConfiguration(CONFIG_GROUP, PACKS_PREFIX, json);
    }

    //gets a list of all enabled pack ids
    private void loadEnabledPacks()
    {
        String json = configManager.getConfiguration(CONFIG_GROUP, PACKS_PREFIX);

        log.debug("json {}", json);
        if (Strings.isNullOrEmpty(json))
        {
            return;
        }
        enabledPacks = gson.fromJson(json, new TypeToken<List<Integer>>()
        {
        }.getType());
    }

    //gets the custom id the user is currently on
    //each pack the user adds needs a unique index, so we need to manage that
    public Integer loadCustomId()
    {
        String json = configManager.getConfiguration(CONFIG_GROUP, CUSTOM_ID);

        if (Strings.isNullOrEmpty(json))
        {
            //default to 9999 because we add 1, and I want it to start at an even 10k.
            return 9999;
        }
        return gson.fromJson(json, new TypeToken<Integer>()
        {
        }.getType());
    }

    //saves a custom pack to the users config
    public void addCustomPack(String name, String tiles)
    {
        Integer customId = loadCustomId() + 1;
        TilePack pack = new TilePack(customId, name, tiles);
        savePack(pack);
        configManager.setConfiguration(CONFIG_GROUP, CUSTOM_ID, customId);

        addEnabledPack(pack.id);
        tilePacks.put(pack.id, pack);
    }

    /** Renames a custom pack or changes its tiles, and saves it. */
    public void editCustomPack(TilePack pack, String name, String tiles)
    {
        pack.packName = name;
        pack.packTiles = tiles;
        savePack(pack);
    }

    //removes a custom pack from the users config
    public void removeCustomPack(Integer packId)
    {
        //unsubscribe the pack before removing it.
        removeEnabledPack(packId);
        configManager.unsetConfiguration(CONFIG_GROUP, PACK_PREFIX + packId);
        tilePacks.remove(packId);
    }

    public void updatePack(TilePack pack)
    {
        if (pack.id >= 10000)
        {
            savePack(pack);
        }
        else
        {
            savePack(new TilePack(pack.id, pack.visible));
        }
    }

    public boolean isPackEnabled(Integer packId)
    {
        return enabledPacks.contains(packId);
    }
}
