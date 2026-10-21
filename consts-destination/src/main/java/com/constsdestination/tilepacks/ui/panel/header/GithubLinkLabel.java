/*
 * Copyright (c) 2025, Trevor <https://github.com/TrevorMDev>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Destination; links to the tile
 *   packs help in consts-Destination's README)
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
package com.constsdestination.tilepacks.ui.panel.header;

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
 * UI control that navigates to GitHub
 */
public class GithubLinkLabel extends JLabel
{
    private static final ImageIcon HELP_ICON;
    private static final ImageIcon HELP_ICON_HOVER;
    // The help for tile packs in consts-Destination, where the same panel is described.
    private static final String GITHUB_LINK = "https://github.com/constdecimalserrno/consts-osrs/tree/main/consts-destination#tile-packs";

    static
    {
        // Icon is https://www.flaticon.com/free-icon/help-web-button_18436
        // Made by https://www.flaticon.com/authors/freepik
        final BufferedImage helpIcon = ImageUtil.loadImageResource(TilePacks.class, "question_mark_icon.png");
        HELP_ICON = new ImageIcon(helpIcon);
        HELP_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(helpIcon, 0.50f));
    }

    GithubLinkLabel()
    {
        super();

        setIcon(HELP_ICON);
        setToolTipText("Click for help with tile packs");
        addMouseListener(new HelpLinkMouseAdapter());
    }

    class HelpLinkMouseAdapter extends MouseAdapter
    {
        @Override
        public void mousePressed(MouseEvent e)
        {
            if (SwingUtilities.isLeftMouseButton(e))
            {
                LinkBrowser.browse(GITHUB_LINK);
            }
        }

        @Override
        public void mouseEntered(MouseEvent e)
        {
            setIcon(HELP_ICON_HOVER);
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            setIcon(HELP_ICON);
        }
    }
}
