/*
 * Copyright (c) 2022, Trevor <https://github.com/TrevorMDev>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, where it's the Tile
 *   packs tab of its panel; the points are reloaded through consts-Destination; the list can be read again after a
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

package com.constsdestination.tilepacks.ui.panel;

import com.constsdestination.tilepacks.FilterManager;
import com.constsdestination.tilepacks.TilePackManager;
import com.constsdestination.tilepacks.data.TilePack;
import com.constsdestination.tilepacks.ui.panel.custom.CustomPackManagerPanel;
import com.constsdestination.tilepacks.ui.panel.header.HeaderPanel;
import com.constsdestination.tilepacks.ui.panel.pack.TilePackPanel;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.ui.PluginPanel;

/**
 * UI panel parent, contains all content for the panel.
 */
public class TilePacksListPanel extends PluginPanel
{

    private final TilePackManager tilePackManager;
    private final Runnable reloadPoints;
    private final FilterManager filterManager;
    private final ChatMessageManager chatMessageManager;
    private final Gson gson;

    private final HeaderPanel headerPanel;
    private final JPanel listContainer = new JPanel();

    /** reloadPoints: puts the tiles of the packs that are on in the world again, after one is turned on or off. */
    public TilePacksListPanel(TilePackManager tilePackManager,
        Runnable reloadPoints,
        FilterManager filterManager,
        ChatMessageManager chatMessageManager,
        Gson gson
    )
    {
        super();
        this.tilePackManager = tilePackManager;
        this.reloadPoints = reloadPoints;
        this.filterManager = filterManager;
        this.chatMessageManager = chatMessageManager;
        this.gson = gson;

        headerPanel = new HeaderPanel(filterManager, this);
        add(headerPanel);

        add(listContainer);
        listContainer.setLayout(new GridLayout(0, 1, 0, 0));

        CustomPackManagerPanel customPackManager = new CustomPackManagerPanel(tilePackManager, reloadPoints, gson, this);

        add(customPackManager);

        createTilePackPanels();
    }

    public void createTilePackPanels()
    {
        listContainer.removeAll();
        String search = headerPanel.getSearchText();
        for (Map.Entry<Integer, TilePack> pack : tilePackManager.getTilePacks().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
            .collect(Collectors.toList()))
        {
            TilePack tilePack = pack.getValue();
            boolean matchesSearch = Strings.isNullOrEmpty(search) || tilePack.packName.toLowerCase().contains(search.toLowerCase());
            boolean matchesFilters = (filterManager.getShowVisible() && tilePack.visible) || (filterManager.getShowInvisible() && !tilePack.visible);

            if (matchesSearch && matchesFilters)
            {
                JPanel tile = new TilePackPanel(tilePackManager, reloadPoints, chatMessageManager,
                    gson, this, tilePack);
                listContainer.add(tile);
            }
        }
        listContainer.revalidate();
        listContainer.repaint();
    }

    /** Shows the packs and filters as they're saved now, as after a profile change. */
    public void reload()
    {
        headerPanel.showFilters();
        createTilePackPanels();
    }
}
