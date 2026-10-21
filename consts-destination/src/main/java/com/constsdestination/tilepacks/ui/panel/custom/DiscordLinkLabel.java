/*
 * Copyright (c) 2022, TrevorMDev
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination)
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
package com.constsdestination.tilepacks.ui.panel.custom;

import com.constsdestination.tilepacks.TilePacks;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * UI Label that links to the Tile Packs discord
 */
public class DiscordLinkLabel extends JLabel
{
    private static final ImageIcon DISCORD_ICON;
    private static final ImageIcon DISCORD_ICON_HOVER;

    static
    {
        final BufferedImage discordIcon = ImageUtil.loadImageResource(TilePacks.class, "discord.png");
        DISCORD_ICON = new ImageIcon(discordIcon);
        DISCORD_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(discordIcon, 0.50f));
    }

    DiscordLinkLabel()
    {
        super();
        setIcon(DISCORD_ICON);
        setToolTipText("Click to join the Tile Packs discord");
        addMouseListener(new HelpLinkMouseAdapter());
    }


    class HelpLinkMouseAdapter extends MouseAdapter
    {
        @Override
        public void mousePressed(MouseEvent e)
        {
            if (SwingUtilities.isLeftMouseButton(e))
            {
                LinkBrowser.browse("https://discord.gg/4EQRWxY3Wb");
            }
        }

        @Override
        public void mouseEntered(MouseEvent e)
        {
            setIcon(DISCORD_ICON_HOVER);
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            setIcon(DISCORD_ICON);
        }
    }
}
