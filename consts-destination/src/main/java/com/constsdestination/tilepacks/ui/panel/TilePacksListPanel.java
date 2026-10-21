/*
 * Copyright (c) 2022, Trevor <https://github.com/TrevorMDev>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination, where it's the Tile
 *   packs tab of its panel; the points are reloaded through consts-Destination; the list can be read again after a
 *   profile change; only your own packs, without the search and filters, and a note while there are none)
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

import com.constsdestination.tilepacks.TilePackManager;
import com.constsdestination.tilepacks.data.TilePack;
import com.constsdestination.tilepacks.ui.panel.custom.CustomPackManagerPanel;
import com.constsdestination.tilepacks.ui.panel.pack.TilePackPanel;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * UI panel parent, contains all content for the panel: your packs, in the order they were made, then the form to add
 * one.
 */
public class TilePacksListPanel extends PluginPanel
{
    /** What the list says while there are no packs. */
    public static final String NO_PACKS = "No packs yet. Add one below, from tiles you marked or a pack someone shared.";

    private final TilePackManager tilePackManager;
    private final Runnable reloadPoints;
    private final ChatMessageManager chatMessageManager;
    private final Gson gson;

    private final JPanel listContainer = new JPanel();
    private final JLabel noPacks = new JLabel(wrapped(NO_PACKS));

    /** reloadPoints: puts the tiles of the packs that are on in the world again, after one is turned on or off. */
    public TilePacksListPanel(TilePackManager tilePackManager,
        Runnable reloadPoints,
        ChatMessageManager chatMessageManager,
        Gson gson
    )
    {
        super();
        this.tilePackManager = tilePackManager;
        this.reloadPoints = reloadPoints;
        this.chatMessageManager = chatMessageManager;
        this.gson = gson;

        JLabel title = new JLabel("Your packs");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(2, 2, 2, 0));
        add(title);

        noPacks.setFont(FontManager.getRunescapeSmallFont());
        noPacks.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        noPacks.setBorder(new EmptyBorder(2, 2, 8, 2));

        add(listContainer);
        listContainer.setLayout(new GridLayout(0, 1, 0, 0));

        CustomPackManagerPanel customPackManager = new CustomPackManagerPanel(tilePackManager, reloadPoints, gson, this);

        add(customPackManager);

        createTilePackPanels();
    }

    public void createTilePackPanels()
    {
        listContainer.removeAll();
        if (tilePackManager.getTilePacks().isEmpty())
        {
            listContainer.add(noPacks);
        }
        for (TilePack tilePack : tilePackManager.getTilePacks().values())
        {
            JPanel tile = new TilePackPanel(tilePackManager, reloadPoints, chatMessageManager,
                gson, this, tilePack);
            listContainer.add(tile);
        }
        listContainer.revalidate();
        listContainer.repaint();
    }

    /** Text for a label that wraps within the panel's width, as a note under a list or form does. */
    public static String wrapped(String text)
    {
        return "<html><body style='width: 150px'>" + text + "</body></html>";
    }

    /** Shows the packs as they're saved now, as after a profile change. */
    public void reload()
    {
        createTilePackPanels();
    }
}
