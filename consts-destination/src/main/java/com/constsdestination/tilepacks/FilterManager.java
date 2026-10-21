/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, which saves the filters
 *   in its own config group; lombok written out; the filters can be read again after a profile change)
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

import com.google.common.base.Strings;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

/**
 * This class is responsible for all functions that interface with or modify the configs related to global filters.
 */
public class FilterManager
{
    private static final String CONFIG_GROUP = TilePackManager.CONFIG_GROUP;
    static final String SHOW_VISIBLE = TilePackManager.KEY_PREFIX + "showVisible";
    static final String SHOW_INVISIBLE = TilePackManager.KEY_PREFIX + "showInvisible";

    private boolean showVisible;
    private boolean showInvisible;

    private final ConfigManager configManager;

    @Inject
    FilterManager(ConfigManager configManager)
    {
        this.configManager = configManager;
        this.loadFilters();
    }

    /** Reads the filters again, for another profile's. */
    void reload()
    {
        loadFilters();
    }

    //loads the filters from the config
    private void loadFilters()
    {
        String showVisibleConfig = configManager.getConfiguration(CONFIG_GROUP, SHOW_VISIBLE);

        if (Strings.isNullOrEmpty(showVisibleConfig))
        {
            this.showVisible = true;
        }
        else
        {
            this.showVisible = Boolean.parseBoolean(showVisibleConfig);
        }

        String showInvisibleConfig = configManager.getConfiguration(CONFIG_GROUP, SHOW_INVISIBLE);

        if (Strings.isNullOrEmpty(showInvisibleConfig))
        {
            this.showInvisible = false;
        }
        else
        {
            this.showInvisible = Boolean.parseBoolean(showInvisibleConfig);
        }
    }

    //sets showVisible in the config
    public void setShowVisible(boolean showVisible)
    {
        this.showVisible = showVisible;
        configManager.setConfiguration(CONFIG_GROUP, SHOW_VISIBLE, showVisible);
    }

    //returns the value of showVisible
    public boolean getShowVisible()
    {
        return this.showVisible;
    }

    //sets showInvisible in the config
    public void setShowInvisible(boolean showInvisible)
    {
        this.showInvisible = showInvisible;
        configManager.setConfiguration(CONFIG_GROUP, SHOW_INVISIBLE, showInvisible);
    }

    //returns the value of showVisible
    public boolean getShowInvisible()
    {
        return this.showInvisible;
    }
}
