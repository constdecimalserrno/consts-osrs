/*
 * Copyright (c) 2018, Psikoi <https://github.com/Psikoi>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's World Hopper plugin
 *   (WorldTableRow) into consts-World; a star for the hot list in place of the favourites menu, a hop on one click
 *   in the hot list, no ping column)
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
package com.constsworld;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;

/**
 * One world: its flag and number, its player count, its activity and a star. The star is filled when the world is in
 * the hot list; clicking it adds the world or removes it, and so does the row's right-click menu. A row in the hot
 * list hops on one click, a row in the list of every world on a double-click.
 */
class WorldRow extends JPanel
{
    private static final ImageIcon FLAG_AUS;
    private static final ImageIcon FLAG_UK;
    private static final ImageIcon FLAG_US;
    private static final ImageIcon FLAG_US_EAST;
    private static final ImageIcon FLAG_US_WEST;
    private static final ImageIcon FLAG_GER;
    private static final ImageIcon FLAG_BR;
    private static final ImageIcon FLAG_JP;
    private static final ImageIcon FLAG_SG;
    private static final ImageIcon FLAG_ZA;

    static final ImageIcon STAR_ON;
    static final ImageIcon STAR_OFF;
    private static final ImageIcon STAR_ON_HOVER;
    private static final ImageIcon STAR_OFF_HOVER;

    private static final Color CURRENT_WORLD = new Color(66, 227, 17);
    private static final Color DANGEROUS_WORLD = new Color(251, 62, 62);
    private static final Color BETA_WORLD = new Color(79, 145, 255);
    private static final Color MEMBERS_WORLD = new Color(210, 193, 53);
    private static final Color FREE_WORLD = new Color(200, 200, 200);
    private static final Color SEASONAL_WORLD = new Color(133, 177, 178);
    private static final Color PVP_ARENA_WORLD = new Color(144, 179, 255);
    private static final Color QUEST_SPEEDRUNNING_WORLD = new Color(94, 213, 201);
    private static final Color FRESH_START_WORLD = new Color(255, 211, 83);

    static
    {
        FLAG_AUS = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_aus.png"));
        FLAG_UK = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_uk.png"));
        FLAG_US = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_us.png"));
        FLAG_US_EAST = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_us_east.png"));
        FLAG_US_WEST = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_us_west.png"));
        FLAG_GER = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_ger.png"));
        FLAG_BR = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_br.png"));
        FLAG_JP = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_jp.png"));
        FLAG_SG = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_sg.png"));
        FLAG_ZA = new ImageIcon(ImageUtil.loadImageResource(WorldRow.class, "flag_za.png"));

        BufferedImage starOn = ImageUtil.loadImageResource(WorldRow.class, "star_on.png");
        BufferedImage starOff = ImageUtil.loadImageResource(WorldRow.class, "star_off.png");
        STAR_ON = new ImageIcon(starOn);
        STAR_OFF = new ImageIcon(starOff);
        // Hovering a filled star dims it, since a click takes it off; an empty one lights up
        STAR_ON_HOVER = new ImageIcon(ImageUtil.luminanceOffset(starOn, -80));
        STAR_OFF_HOVER = new ImageIcon(ImageUtil.fillImage(starOff, Color.YELLOW));
    }

    private static final int LOCATION_US_WEST = -73;
    private static final int LOCATION_US_EAST = -42;

    private final World world;
    private final BiConsumer<World, Boolean> onStar;
    private int playerCount;
    private final int worldLocation; // from enum WORLD_LOCATIONS
    private boolean hot;

    private final JMenuItem hotMenuOption = new JMenuItem();

    private JLabel worldField;
    private JLabel playerCountField;
    private JLabel activityField;
    private JLabel starField;

    private Color lastBackground;

    WorldRow(World world, boolean current, boolean hot, int playerCount, int worldLocation, boolean oneClick,
        Consumer<World> onSelect, BiConsumer<World, Boolean> onStar)
    {
        this.world = world;
        this.onStar = onStar;
        this.playerCount = playerCount;
        this.worldLocation = worldLocation;

        // A click count of 1 in the hot list, 2 in the list of every world
        final int hopClicks = oneClick ? 1 : 2;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(2, 0, 2, 0));
        setToolTipText((oneClick ? "Click" : "Double-click") + " to hop to world " + world.getId());
        if (oneClick)
        {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent mouseEvent)
            {
                if (SwingUtilities.isLeftMouseButton(mouseEvent) && mouseEvent.getClickCount() == hopClicks)
                {
                    if (onSelect != null)
                    {
                        onSelect.accept(world);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {
                if (SwingUtilities.isLeftMouseButton(mouseEvent) && mouseEvent.getClickCount() == hopClicks)
                {
                    setBackground(getBackground().brighter());
                }
            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {
                if (SwingUtilities.isLeftMouseButton(mouseEvent) && mouseEvent.getClickCount() == hopClicks)
                {
                    setBackground(getBackground().darker());
                }
            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {
                WorldRow.this.lastBackground = getBackground();
                setBackground(getBackground().brighter());
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {
                setBackground(lastBackground);
            }
        });

        final JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
        popupMenu.add(hotMenuOption);

        setComponentPopupMenu(popupMenu);

        JPanel leftSide = new JPanel(new BorderLayout());
        leftSide.setOpaque(false);

        JPanel worldField = buildWorldField();
        worldField.setPreferredSize(new Dimension(ConstsWorldPanel.WORLD_COLUMN_WIDTH, 0));
        worldField.setOpaque(false);

        JPanel playersField = buildPlayersField();
        playersField.setPreferredSize(new Dimension(ConstsWorldPanel.PLAYERS_COLUMN_WIDTH, 0));
        playersField.setOpaque(false);

        JPanel activityField = buildActivityField();
        activityField.setBorder(new EmptyBorder(5, 5, 5, 5));
        activityField.setOpaque(false);

        buildStarField();
        starField.setPreferredSize(new Dimension(ConstsWorldPanel.STAR_COLUMN_WIDTH, 0));

        setHot(hot);
        recolour(current);

        leftSide.add(worldField, BorderLayout.WEST);
        leftSide.add(playersField, BorderLayout.CENTER);

        add(leftSide, BorderLayout.WEST);
        add(activityField, BorderLayout.CENTER);
        add(starField, BorderLayout.EAST);
    }

    World getWorld()
    {
        return world;
    }

    int getPlayerCount()
    {
        return playerCount;
    }

    /** The star, the menu option and what they do, for a world in the hot list or out of it. */
    void setHot(boolean hot)
    {
        this.hot = hot;

        String action = hotAction(world.getId(), hot);
        hotMenuOption.setText(action);
        starField.setToolTipText(action);
        starField.setIcon(hot ? STAR_ON : STAR_OFF);

        for (ActionListener listener : hotMenuOption.getActionListeners())
        {
            hotMenuOption.removeActionListener(listener);
        }

        hotMenuOption.addActionListener(e -> onStar.accept(world, !hot));
    }

    static String hotAction(int world, boolean hot)
    {
        return hot
            ? "Remove world " + world + " from the hot list"
            : "Add world " + world + " to the hot list";
    }

    void updatePlayerCount(int playerCount)
    {
        this.playerCount = playerCount;
        playerCountField.setText(playerCountString(playerCount));
    }

    private static String playerCountString(int playerCount)
    {
        return playerCount < 0 ? "OFF" : Integer.toString(playerCount);
    }

    public void recolour(boolean current)
    {
        playerCountField.setForeground(current ? CURRENT_WORLD : Color.WHITE);

        if (current)
        {
            activityField.setForeground(CURRENT_WORLD);
            worldField.setForeground(CURRENT_WORLD);
            return;
        }

        EnumSet<WorldType> types = world.getTypes();
        if (world.getAddress() == null)
        {
            // A world in the hot list that the world list doesn't have (yet)
            activityField.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }
        else if (types.contains(WorldType.PVP)
            || types.contains(WorldType.HIGH_RISK)
            || types.contains(WorldType.DEADMAN))
        {
            activityField.setForeground(DANGEROUS_WORLD);
        }
        else if (types.contains(WorldType.SEASONAL) || types.contains(WorldType.TOURNAMENT))
        {
            activityField.setForeground(SEASONAL_WORLD);
        }
        else if (types.contains(WorldType.NOSAVE_MODE) || types.contains(WorldType.BETA_WORLD)
            || types.contains(WorldType.LEGACY_ONLY)) // sometimes used for arbitrarily restricted worlds
        {
            activityField.setForeground(BETA_WORLD);
        }
        else if (types.contains(WorldType.PVP_ARENA))
        {
            activityField.setForeground(PVP_ARENA_WORLD);
        }
        else if (types.contains(WorldType.QUEST_SPEEDRUNNING))
        {
            activityField.setForeground(QUEST_SPEEDRUNNING_WORLD);
        }
        else if (types.contains(WorldType.FRESH_START_WORLD))
        {
            activityField.setForeground(FRESH_START_WORLD);
        }
        else
        {
            activityField.setForeground(Color.WHITE);
        }

        worldField.setForeground(types.contains(WorldType.MEMBERS) ? MEMBERS_WORLD : FREE_WORLD);
    }

    /**
     * Builds the players list field (containing the amount of players logged in that world).
     */
    private JPanel buildPlayersField()
    {
        JPanel column = new JPanel(new BorderLayout());
        column.setBorder(new EmptyBorder(0, 5, 0, 5));

        // Nothing for a world in the hot list that the world list doesn't have (yet)
        playerCountField = new JLabel(world.getAddress() == null ? "" : playerCountString(playerCount));
        playerCountField.setFont(FontManager.getRunescapeSmallFont());

        column.add(playerCountField, BorderLayout.WEST);

        return column;
    }

    /**
     * Builds the activity list field (containing that world's activity/theme).
     */
    private JPanel buildActivityField()
    {
        JPanel column = new JPanel(new BorderLayout());
        column.setBorder(new EmptyBorder(0, 5, 0, 5));

        String activity = world.getActivity();
        activityField = new JLabel(activity);
        activityField.setFont(FontManager.getRunescapeSmallFont());
        if (activity != null && activity.length() > 16)
        {
            // The whole activity, since the column cuts a long one short
            setToolTipText("<html>" + activity + "<br>" + getToolTipText() + "</html>");
        }

        column.add(activityField, BorderLayout.WEST);

        return column;
    }

    /**
     * Builds the star at the end of the row, which adds the world to the hot list or removes it.
     */
    private void buildStarField()
    {
        starField = new JLabel();
        starField.setHorizontalAlignment(SwingConstants.CENTER);
        starField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        starField.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {
                if (SwingUtilities.isLeftMouseButton(mouseEvent))
                {
                    onStar.accept(world, !hot);
                }
            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {
                starField.setIcon(hot ? STAR_ON_HOVER : STAR_OFF_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {
                starField.setIcon(hot ? STAR_ON : STAR_OFF);
            }
        });
    }

    /**
     * Builds the world list field (containing the country's flag and the world index).
     */
    private JPanel buildWorldField()
    {
        JPanel column = new JPanel(new BorderLayout(7, 0));
        column.setBorder(new EmptyBorder(0, 5, 0, 5));

        worldField = new JLabel(world.getId() + "");

        ImageIcon flagIcon = getFlag(world.getRegion(), worldLocation);
        if (flagIcon != null)
        {
            JLabel flag = new JLabel(flagIcon);
            column.add(flag, BorderLayout.WEST);
        }
        column.add(worldField, BorderLayout.CENTER);

        return column;
    }

    private static ImageIcon getFlag(WorldRegion region, int worldLocation)
    {
        if (region == null)
        {
            return null;
        }

        switch (region)
        {
            case UNITED_STATES_OF_AMERICA:
                switch (worldLocation)
                {
                    case LOCATION_US_WEST:
                        return FLAG_US_WEST;
                    case LOCATION_US_EAST:
                        return FLAG_US_EAST;
                    default:
                        return FLAG_US;
                }
            case UNITED_KINGDOM:
                return FLAG_UK;
            case AUSTRALIA:
                return FLAG_AUS;
            case GERMANY:
                return FLAG_GER;
            case BRAZIL:
                return FLAG_BR;
            case JAPAN:
                return FLAG_JP;
            case SINGAPORE:
                return FLAG_SG;
            case SOUTH_AFRICA:
                return FLAG_ZA;
            default:
                return null;
        }
    }
}
