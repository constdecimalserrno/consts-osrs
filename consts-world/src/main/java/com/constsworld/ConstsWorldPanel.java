/*
 * Copyright (c) 2018, Psikoi <https://github.com/Psikoi>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's World Hopper plugin
 *   (WorldSwitcherPanel) into consts-World; the hot list first, then a search box over the worlds; no ping column,
 *   filters or favourites)
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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.EnumComposition;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * The sidebar. The hot list always comes first, whatever the search or the sort: click a world there to hop to it.
 * Under it, a search box over every world, sorted by the column header you click; double-click a world to hop to it.
 * The star at the end of a row adds the world to the hot list or removes it.
 */
class ConstsWorldPanel extends PluginPanel
{
    private static final Color ODD_ROW = new Color(44, 44, 44);

    /* A run of digits or a run of anything else, for sorting activities */
    private static final Pattern CHUNK = Pattern.compile("\\d+|\\D+");

    static final int WORLD_COLUMN_WIDTH = 60;
    static final int PLAYERS_COLUMN_WIDTH = 40;
    static final int STAR_COLUMN_WIDTH = 22;

    /* What the line under the search box says before anything's typed */
    static final String SEARCH_HINT = "Search activities, worlds or regions";

    private final ConstsWorldPlugin plugin;

    private final JPanel hotContainer = new JPanel(new GridLayout(0, 1));
    private final JLabel hotEmpty = new JLabel();
    private final IconTextField searchBar = new IconTextField();
    private final JLabel searchStatus = new JLabel();
    private final JPanel listContainer = new JPanel(new GridLayout(0, 1));
    private final JLabel listEmpty = new JLabel();

    private WorldTableHeader worldHeader;
    private WorldTableHeader playersHeader;
    private WorldTableHeader activityHeader;

    private WorldOrder orderIndex = WorldOrder.WORLD;
    private boolean ascendingOrder = true;

    // A row for every world, then one for every world in the hot list
    private final ArrayList<WorldRow> rows = new ArrayList<>();
    private final ArrayList<WorldRow> hotRows = new ArrayList<>();

    private final Map<Integer, World> worlds = new HashMap<>();
    private final Map<Integer, Integer> worldLocations = new HashMap<>();
    private final Map<Integer, Integer> playerCounts = new HashMap<>();
    private boolean loaded;

    ConstsWorldPanel(ConstsWorldPlugin plugin)
    {
        super(false);
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel top = new JPanel(new DynamicGridLayout(0, 1));
        top.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel hotTitle = new JLabel("Hot list");
        hotTitle.setFont(FontManager.getRunescapeBoldFont());
        hotTitle.setForeground(Color.WHITE);
        hotTitle.setBorder(new EmptyBorder(8, 5, 4, 5));
        top.add(hotTitle);

        // Lined up with the rows under it, which leave room for the scroll bar
        hotContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        hotContainer.setBorder(new EmptyBorder(0, 0, 0, SCROLLBAR_WIDTH));
        hotEmpty.setText("<html><body style='width: 150px'>Star a world below to add it here, then click it to hop."
            + "</body></html>");
        hotEmpty.setIcon(WorldRow.STAR_OFF);
        hotEmpty.setIconTextGap(8);
        hotEmpty.setFont(FontManager.getRunescapeSmallFont());
        hotEmpty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        hotEmpty.setBorder(new EmptyBorder(4, 5, 4, 5));
        top.add(hotContainer);

        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(0, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                updateList();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                updateList();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                updateList();
            }
        });
        JPanel search = new JPanel(new BorderLayout());
        search.setBackground(ColorScheme.DARK_GRAY_COLOR);
        search.setBorder(new EmptyBorder(12, 5, 0, 5));
        search.add(searchBar, BorderLayout.CENTER);
        top.add(search);

        searchStatus.setFont(FontManager.getRunescapeSmallFont());
        searchStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchStatus.setBorder(new EmptyBorder(4, 5, 6, 5));
        top.add(searchStatus);

        listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listEmpty.setFont(FontManager.getRunescapeSmallFont());
        listEmpty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        listEmpty.setBorder(new EmptyBorder(8, 5, 8, 5));

        // As wide as the sidebar, so a long activity is cut short instead of pushing the stars out of sight
        JPanel listWrapper = new JPanel(new BorderLayout())
        {
            @Override
            public Dimension getPreferredSize()
            {
                return new Dimension(PANEL_WIDTH, super.getPreferredSize().height);
            }
        };
        listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listWrapper.add(listContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setColumnHeaderView(buildHeader());
        JPanel corner = new JPanel();
        corner.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        scrollPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(SCROLLBAR_WIDTH, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        rebuildHotList();
        updateList();
    }

    @Override
    public void onActivate()
    {
        plugin.refresh();
    }

    void switchCurrentHighlight(int newWorld, int lastWorld)
    {
        for (List<WorldRow> list : List.of(rows, hotRows))
        {
            for (WorldRow row : list)
            {
                if (row.getWorld().getId() == newWorld)
                {
                    row.recolour(true);
                }
                else if (row.getWorld().getId() == lastWorld)
                {
                    row.recolour(false);
                }
            }
        }
    }

    void updateListData(Map<Integer, Integer> worldData)
    {
        playerCounts.putAll(worldData);
        for (List<WorldRow> list : List.of(rows, hotRows))
        {
            for (WorldRow row : list)
            {
                Integer playerCount = worldData.get(row.getWorld().getId());
                if (playerCount != null)
                {
                    row.updatePlayerCount(playerCount);
                }
            }
        }

        // If the list is being ordered by player count, then it has to be re-painted
        // to properly display the new data
        if (orderIndex == WorldOrder.PLAYERS)
        {
            updateList();
        }
    }

    /**
     * Sorts the list of every world and shows the ones that match the search. The hot list stays as it is.
     */
    void updateList()
    {
        String query = searchBar.getText();
        rows.sort(Comparator.comparing(WorldRow::getWorld, comparator(orderIndex, ascendingOrder, this::playerCount)));

        listContainer.removeAll();

        int shown = 0;
        for (WorldRow row : rows)
        {
            if (!WorldSearch.matches(row.getWorld(), query))
            {
                continue;
            }
            row.setBackground(shown++ % 2 == 0 ? ODD_ROW : ColorScheme.DARK_GRAY_COLOR);
            listContainer.add(row);
        }

        if (shown == 0)
        {
            listEmpty.setText(loaded
                ? "<html><body style='width: 170px'>Try an activity like Tempoross, a world like 330 or a region"
                    + " like UK.</body></html>"
                : "Loading worlds...");
            listContainer.add(listEmpty);
        }
        searchStatus.setText(status(query, shown, loaded));

        listContainer.revalidate();
        listContainer.repaint();
    }

    /** The line under the search box. */
    static String status(String query, int shown, boolean loaded)
    {
        if (!loaded || query == null || query.isBlank())
        {
            return SEARCH_HINT;
        }
        if (shown == 0)
        {
            return "No worlds match";
        }
        return shown == 1 ? "1 world matches" : shown + " worlds match";
    }

    /** The order of the list of every world, by the column clicked. */
    static Comparator<World> comparator(WorldOrder order, boolean ascending, ToIntFunction<World> players)
    {
        Comparator<World> byWorld = Comparator.comparingInt(World::getId);
        switch (order)
        {
            case PLAYERS:
                return direction(Comparator.comparingInt(players), ascending).thenComparing(byWorld);
            case ACTIVITY:
                // Leave worlds without an activity at the bottom, whichever way the list is sorted
                return Comparator.comparing(ConstsWorldPanel::activity,
                    Comparator.nullsLast(direction(ConstsWorldPanel::compareActivities, ascending))).thenComparing(byWorld);
            default:
                return direction(byWorld, ascending);
        }
    }

    private static <T> Comparator<T> direction(Comparator<T> comparator, boolean ascending)
    {
        return ascending ? comparator : comparator.reversed();
    }

    /** Activities in alphabetical order, except that numbers go by their value: 500 skill total before 1250. */
    static int compareActivities(String a, String b)
    {
        Matcher ma = CHUNK.matcher(a);
        Matcher mb = CHUNK.matcher(b);
        while (ma.find() && mb.find())
        {
            String ca = ma.group();
            String cb = mb.group();
            int c;
            if (Character.isDigit(ca.charAt(0)) && Character.isDigit(cb.charAt(0)))
            {
                c = new BigInteger(ca).compareTo(new BigInteger(cb));
            }
            else
            {
                c = ca.compareToIgnoreCase(cb);
            }
            if (c != 0)
            {
                return c;
            }
        }
        return Integer.compare(a.length(), b.length());
    }

    private static String activity(World world)
    {
        String activity = world.getActivity();
        return activity == null || activity.equals("-") ? null : activity;
    }

    private int playerCount(World world)
    {
        return playerCounts.getOrDefault(world.getId(), world.getPlayers());
    }

    /**
     * Rebuilds the hot list's rows, in the order the worlds were added. A world the world list doesn't have (or not
     * yet) still gets a row, so it can be removed.
     */
    void rebuildHotList()
    {
        hotRows.clear();
        hotContainer.removeAll();

        for (int id : plugin.getHotList())
        {
            World world = worlds.get(id);
            if (world == null)
            {
                world = World.builder()
                    .id(id)
                    .types(EnumSet.noneOf(WorldType.class))
                    .activity(loaded ? "Not in the world list" : "Loading...")
                    .players(-1)
                    .build();
            }

            WorldRow row = buildRow(world, true);
            row.setBackground(hotRows.size() % 2 == 0 ? ODD_ROW : ColorScheme.DARK_GRAY_COLOR);
            hotRows.add(row);
            hotContainer.add(row);
        }

        if (hotRows.isEmpty())
        {
            hotContainer.add(hotEmpty);
        }

        hotContainer.revalidate();
        hotContainer.repaint();
    }

    void populate(List<World> worldList, EnumComposition locations)
    {
        worlds.clear();
        worldLocations.clear();
        playerCounts.clear();
        rows.clear();

        for (World world : worldList)
        {
            worlds.put(world.getId(), world);
            if (locations != null)
            {
                worldLocations.put(world.getId(), locations.getIntValue(world.getId()));
            }
            rows.add(buildRow(world, false));
        }
        loaded = true;

        rebuildHotList();
        updateList();
    }

    private void orderBy(WorldOrder order)
    {
        worldHeader.highlight(false, ascendingOrder);
        playersHeader.highlight(false, ascendingOrder);
        activityHeader.highlight(false, ascendingOrder);

        switch (order)
        {
            case WORLD:
                worldHeader.highlight(true, ascendingOrder);
                break;
            case PLAYERS:
                playersHeader.highlight(true, ascendingOrder);
                break;
            case ACTIVITY:
                activityHeader.highlight(true, ascendingOrder);
                break;
        }

        orderIndex = order;
        updateList();
    }

    /**
     * Builds the entire table header.
     */
    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout());
        JPanel leftSide = new JPanel(new BorderLayout());

        worldHeader = buildColumnHeader("World", WorldOrder.WORLD);
        worldHeader.setPreferredSize(new Dimension(WORLD_COLUMN_WIDTH, 0));

        playersHeader = buildColumnHeader("#", WorldOrder.PLAYERS);
        playersHeader.setPreferredSize(new Dimension(PLAYERS_COLUMN_WIDTH, 0));

        activityHeader = buildColumnHeader("Activity", WorldOrder.ACTIVITY);

        // Above the stars, nothing to sort by
        JPanel starHeader = new JPanel();
        starHeader.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        starHeader.setPreferredSize(new Dimension(STAR_COLUMN_WIDTH, 0));

        leftSide.add(worldHeader, BorderLayout.WEST);
        leftSide.add(playersHeader, BorderLayout.CENTER);

        header.add(leftSide, BorderLayout.WEST);
        header.add(activityHeader, BorderLayout.CENTER);
        header.add(starHeader, BorderLayout.EAST);

        return header;
    }

    private WorldTableHeader buildColumnHeader(String title, WorldOrder order)
    {
        WorldTableHeader columnHeader = new WorldTableHeader(title, orderIndex == order, ascendingOrder, plugin::refresh);
        columnHeader.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {
                if (SwingUtilities.isRightMouseButton(mouseEvent))
                {
                    return;
                }
                ascendingOrder = orderIndex != order || !ascendingOrder;
                orderBy(order);
            }
        });
        return columnHeader;
    }

    /**
     * Builds a table row, that displays the world's information.
     */
    private WorldRow buildRow(World world, boolean inHotList)
    {
        return new WorldRow(world,
            world.getId() == plugin.getCurrentWorld() && plugin.getLastWorld() != 0,
            plugin.isHot(world.getId()),
            playerCount(world),
            worldLocations.getOrDefault(world.getId(), -1),
            inHotList,
            plugin::hopTo,
            this::star);
    }

    /** A star clicked, in either list: the world goes into the hot list or comes out of it. */
    private void star(World world, boolean add)
    {
        if (add)
        {
            plugin.addToHotList(world.getId());
        }
        else
        {
            plugin.removeFromHotList(world.getId());
        }

        for (WorldRow row : rows)
        {
            if (row.getWorld().getId() == world.getId())
            {
                row.setHot(add);
            }
        }
        rebuildHotList();
    }

    /**
     * Enumerates the multiple ordering options for the world list.
     */
    enum WorldOrder
    {
        WORLD,
        PLAYERS,
        ACTIVITY
    }
}
