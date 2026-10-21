/*
 * Copyright (c) 2023, Trevor <https://github.com/TrevorMDev>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination; the pack check is a
 *   method of its own, which the edit control uses too, and stops at a pack without tiles; the points are reloaded
 *   through consts-Destination; two unused icon fields dropped)
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
package com.constsdestination.tilepacks.ui.panel.custom;

import com.constsdestination.tilepacks.TilePackManager;
import com.constsdestination.tilepacks.data.GroundMarkerPoint;
import com.constsdestination.tilepacks.ui.panel.TilePacksListPanel;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;

/**
 * UI that handles the addition of new tile packs
 */
public class CustomPackManagerPanel extends PluginPanel
{
    private final TilePackManager tilePackManager;
    private final Runnable reloadPoints;
    private final Gson gson;
    private final TilePacksListPanel tilePacksList;

    private final JLabel inputLabel;
    private final FlatTextField nameInput;
    private final JLabel tilesLabel;
    private final FlatTextField tilesInput;
    private final JButton addPackButton;
    private final JPanel discordRow;
    private final JLabel discordShare;
    private final JLabel discordShareButton;

    public CustomPackManagerPanel(TilePackManager tilePackManager, Runnable reloadPoints,
        Gson gson, TilePacksListPanel tilePacksList)
    {
        super();
        this.tilePackManager = tilePackManager;
        this.reloadPoints = reloadPoints;
        this.gson = gson;
        this.tilePacksList = tilePacksList;

        inputLabel = new JLabel("Custom Pack Name");
        add(inputLabel);

        nameInput = new FlatTextField();
        nameInput.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        nameInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameInput.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        nameInput.setMinimumSize(new Dimension(0, 30));
        add(nameInput);

        tilesLabel = new JLabel("Custom Pack Tiles");
        add(tilesLabel);

        tilesInput = new FlatTextField();
        tilesInput.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        tilesInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tilesInput.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        tilesInput.setMinimumSize(new Dimension(0, 30));
        add(tilesInput);

        addPackButton = new JButton();
        addPackButton.setText("Add Pack");
        addPackButton.setHorizontalAlignment(JLabel.CENTER);
        addPackButton.setFocusable(false);
        addPackButton.setPreferredSize((new Dimension(PluginPanel.PANEL_WIDTH - 10, 30)));
        addPackButton.addActionListener(e ->
        {
            String problem = checkPack(gson, nameInput.getText(), tilesInput.getText());
            if (problem != null)
            {
                JOptionPane.showMessageDialog(inputLabel, problem);
                return;
            }
            tilePackManager.addCustomPack(nameInput.getText(), tilesInput.getText());
            reloadPoints.run();
            tilePacksList.createTilePackPanels();
        });
        add(addPackButton);

        discordRow = new JPanel();
        discordRow.setLayout(new BorderLayout());
        discordRow.setBorder(new EmptyBorder(6, 4, 1, 4));
        add(discordRow, BorderLayout.SOUTH);

        discordShare = new JLabel("Got cool tiles? Share in discord!");
        discordRow.add(discordShare, BorderLayout.WEST);

        discordShareButton = new DiscordLinkLabel();
        discordRow.add(discordShareButton, BorderLayout.EAST);
    }

    /** What's wrong with a custom pack's name and tiles, to tell the user, or null if nothing is. */
    public static String checkPack(Gson gson, String name, String tiles)
    {
        if (name.isEmpty())
        {
            return "Must add a pack name";
        }
        if (tiles.isEmpty())
        {
            return "Must add tiles";
        }
        //check the format of the import points. We actually save the string, so this formatting is just to check it is valid.
        List<GroundMarkerPoint> importPoints;
        try
        {
            importPoints = gson.fromJson(tiles, new TypeToken<List<GroundMarkerPoint>>()
            {
            }.getType());
        }
        catch (Exception ex)
        {
            return "Error parsing tiles, check the formatting";
        }
        if (importPoints == null || importPoints.isEmpty())
        {
            return "Error parsing tiles, check the formatting";
        }
        return null;
    }
}
