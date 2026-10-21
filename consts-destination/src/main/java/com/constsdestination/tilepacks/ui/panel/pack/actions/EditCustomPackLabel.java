/*
 * Copyright (c) 2024, Trevor <https://github.com/TrevorMDev>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: an edit control for custom packs, made from
 *   DeleteCustomPackLabel: a pencil, drawn here, that opens the pack's name and tiles to change)
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
package com.constsdestination.tilepacks.ui.panel.pack.actions;

import com.constsdestination.tilepacks.TilePackManager;
import com.constsdestination.tilepacks.data.TilePack;
import com.constsdestination.tilepacks.ui.panel.TilePacksListPanel;
import com.constsdestination.tilepacks.ui.panel.custom.CustomPackManagerPanel;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.util.ImageUtil;

/**
 * UI control that handles the editing of custom packs: their name and their tiles, checked as the Add Pack button
 * checks them.
 */
public class EditCustomPackLabel extends JLabel
{
    private static final ImageIcon EDIT_ICON;
    private static final ImageIcon EDIT_ICON_HOVER;

    private final TilePackManager tilePackManager;
    private final Runnable reloadPoints;
    private final Gson gson;
    private final TilePack tilePack;
    private final TilePacksListPanel tilePacksList;

    static
    {
        final BufferedImage editIcon = pencil(new Color(0x85, 0x85, 0x85));
        EDIT_ICON = new ImageIcon(editIcon);
        EDIT_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(editIcon, 0.50f));
    }

    public EditCustomPackLabel(TilePackManager tilePackManager, Runnable reloadPoints, Gson gson, TilePack tilePack,
        TilePacksListPanel tilePacksList)
    {
        super();
        this.tilePackManager = tilePackManager;
        this.reloadPoints = reloadPoints;
        this.gson = gson;
        this.tilePack = tilePack;
        this.tilePacksList = tilePacksList;

        setIcon(EDIT_ICON);
        setToolTipText("Change this pack's name or tiles");
        addMouseListener(new EditCustomPackMouseAdapter(this));
    }

    /** A 20 by 20 pencil in the grey of the other icons, its point to the bottom left. */
    static BufferedImage pencil(Color color)
    {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.rotate(Math.PI / 4, 10, 10);
        g.fill(new Rectangle2D.Double(7.5, 0.5, 5, 3));
        g.fill(new Rectangle2D.Double(7.5, 4.5, 5, 9.5));
        Path2D.Double point = new Path2D.Double();
        point.moveTo(7.5, 15);
        point.lineTo(12.5, 15);
        point.lineTo(10, 19.5);
        point.closePath();
        g.fill(point);
        g.dispose();
        return image;
    }

    /**
     * Opens the pack's name and tiles to change, until they're right or the change is cancelled, then saves them.
     */
    private void edit(Component parent)
    {
        JTextField nameField = new JTextField(tilePack.packName);
        JTextArea tilesArea = new JTextArea(tilePack.packTiles);
        tilesArea.setLineWrap(true);
        JScrollPane tilesScroll = new JScrollPane(tilesArea);
        tilesScroll.setPreferredSize(new Dimension(300, 140));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{new JLabel("Custom Pack Name"), nameField, new JLabel("Custom Pack Tiles"), tilesScroll})
        {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(c);
        }

        while (true)
        {
            int result = JOptionPane.showConfirmDialog(parent, form, "Edit Pack", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION)
            {
                return;
            }
            String problem = CustomPackManagerPanel.checkPack(gson, nameField.getText(), tilesArea.getText());
            if (problem == null)
            {
                break;
            }
            JOptionPane.showMessageDialog(parent, problem);
        }

        tilePackManager.editCustomPack(tilePack, nameField.getText(), tilesArea.getText());
        reloadPoints.run();
        tilePacksList.createTilePackPanels();
    }

    class EditCustomPackMouseAdapter extends MouseAdapter
    {
        private final EditCustomPackLabel editCustomPackLabel;

        EditCustomPackMouseAdapter(EditCustomPackLabel editCustomPackLabel)
        {
            this.editCustomPackLabel = editCustomPackLabel;
        }

        @Override
        public void mousePressed(MouseEvent e)
        {
            if (SwingUtilities.isLeftMouseButton(e))
            {
                edit(editCustomPackLabel);
            }
        }

        @Override
        public void mouseEntered(MouseEvent e)
        {
            setIcon(EDIT_ICON_HOVER);
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            setIcon(EDIT_ICON);
        }
    }
}
