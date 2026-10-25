/*
 * Copyright (c) 2024, Trevor <https://github.com/TrevorMDev>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, which saves the packs
 *   in its own config group; lombok written out; a custom pack can be edited; the packs can be read again after a
 *   profile change; only your own packs, without the built-in ones, which TilePackMigrations makes yours if they
 *   were on)
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
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for all functions that interface with or modify tile packs
 *
 * <p>The packs are your own: made in the Tile packs tab from tiles you marked, pasted from someone else's, or made
 * from the built-in packs you had on before there were only your own (see {@link TilePackMigrations}). Each is a name
 * and its tiles, on or off, numbered from 10000 in the order they were made.
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

    /** The first pack's number: Tile Packs numbered its built-in packs below it, and custom packs from it. */
    public static final int FIRST_ID = 10000;

    private Map<Integer, TilePack> tilePacks = new TreeMap<Integer, TilePack>();
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

    /** Your packs, by number, so in the order they were made. */
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

    //loads your packs, each from its own key
    private void loadPacks()
    {
        Map<Integer, TilePack> packs = new TreeMap<Integer, TilePack>();
        String wholePrefix = ConfigManager.getWholeKey(CONFIG_GROUP, null, PACK_PREFIX);
        for (String key : configManager.getConfigurationKeys(wholePrefix))
        {
            String packId = key.substring(wholePrefix.length());
            try
            {
                Integer id = Integer.valueOf(packId);
                String json = configManager.getConfiguration(CONFIG_GROUP, PACK_PREFIX + id);
                TilePack pack = gson.fromJson(json, TilePack.class);
                if (pack != null && id >= FIRST_ID && pack.packTiles != null)
                {
                    pack.id = id;
                    packs.put(id, pack);
                }
            }
            catch (RuntimeException e)
            {
                log.warn("Unable to load tile pack config {}", key, e);
            }
        }
        tilePacks = packs;
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
        Integer[] ids = gson.fromJson(json, Integer[].class);
        enabledPacks = ids == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(ids));
    }

    //gets the custom id the user is currently on
    //each pack the user adds needs a unique index, so we need to manage that
    public Integer loadCustomId()
    {
        String json = configManager.getConfiguration(CONFIG_GROUP, CUSTOM_ID);

        if (Strings.isNullOrEmpty(json))
        {
            //default to 9999 because we add 1, and I want it to start at an even 10k.
            return FIRST_ID - 1;
        }
        return gson.fromJson(json, Integer.class);
    }

    //saves a new pack to the users config, turned on
    public void addCustomPack(String name, String tiles)
    {
        Integer customId = loadCustomId() + 1;
        TilePack pack = new TilePack(customId, name, tiles);
        savePack(pack);
        configManager.setConfiguration(CONFIG_GROUP, CUSTOM_ID, customId);

        addEnabledPack(pack.id);
        tilePacks.put(pack.id, pack);
    }

    /** Renames a pack or changes its tiles, and saves it. */
    public void editCustomPack(TilePack pack, String name, String tiles)
    {
        pack.packName = name;
        pack.packTiles = tiles;
        savePack(pack);
    }

    //removes a pack from the users config
    public void removeCustomPack(Integer packId)
    {
        //unsubscribe the pack before removing it.
        removeEnabledPack(packId);
        configManager.unsetConfiguration(CONFIG_GROUP, PACK_PREFIX + packId);
        tilePacks.remove(packId);
    }

    public boolean isPackEnabled(Integer packId)
    {
        return enabledPacks.contains(packId);
    }
}
