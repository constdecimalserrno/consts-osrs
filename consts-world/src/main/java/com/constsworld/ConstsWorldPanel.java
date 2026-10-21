/*
 * Copyright (c) 2018, Psikoi <https://github.com/Psikoi>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's World Hopper plugin
 *   (WorldSwitcherPanel) into consts-World; a search box over two tabs, the hot list and every world; no ping
 *   column, filters or favourites)
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
import java.awt.Component;
import java.awt.Container;
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
import javax.swing.JTextField;
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
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * The sidebar: a search box, then two tabs, the hot list and every world. The search box searches the tab that's
 * showing. The panel always opens on the hot list: click a world there to hop to it. In all worlds, double-click a
 * world to hop to it. The star at the end of a row adds the world to the hot list or removes it, and the column
 * headers sort whichever tab is showing.
 */
class ConstsWorldPanel extends PluginPanel
{
    private static final Color ODD_ROW = new Color(44, 44, 44);

    /* A run of digits or a run of anything else, for sorting activities */
    private static final Pattern CHUNK = Pattern.compile("\\d+|\\D+");

    static final int WORLD_COLUMN_WIDTH = 60;
    static final int PLAYERS_COLUMN_WIDTH = 40;
    static final int STAR_COLUMN_WIDTH = 22;

    private final ConstsWorldPlugin plugin;

    private final IconTextField searchBar = new IconTextField();
    private final MaterialTabGroup tabGroup = new MaterialTabGroup();
    private final MaterialTab hotTab;
    private final MaterialTab allTab;
    private final JPanel listContainer = new JPanel(new GridLayout(0, 1));
    private final JLabel listEmpty = new JLabel();
    private final JScrollPane scrollPane;

    private WorldTableHeader worldHeader;
    private WorldTableHeader playersHeader;
    private WorldTableHeader activityHeader;

    private WorldOrder orderIndex = WorldOrder.WORLD;
    private boolean ascendingOrder = true;

    private Tab tab = Tab.HOT_LIST;

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
        search.setBorder(new EmptyBorder(10, 5, 4, 5));
        search.add(searchBar, BorderLayout.CENTER);
        top.add(search);

        hotTab = new MaterialTab("", tabGroup, null);
        hotTab.setOnSelectEvent(() -> show(Tab.HOT_LIST));
        allTab = new MaterialTab("", tabGroup, null);
        allTab.setOnSelectEvent(() -> show(Tab.ALL_WORLDS));
        tabGroup.setBorder(new EmptyBorder(0, 0, 6, 0));
        tabGroup.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabGroup.addTab(hotTab);
        tabGroup.addTab(allTab);
        top.add(tabGroup);

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

        scrollPane = new JScrollPane(listWrapper);
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
        tabGroup.select(hotTab);
    }

    @Override
    public void onActivate()
    {
        // Opening the panel always shows the hot list
        tabGroup.select(hotTab);
        plugin.refresh();
    }

    /** Shows a tab, with an empty search box that searches it. */
    private boolean show(Tab tab)
    {
        this.tab = tab;
        setPlaceholder(searchBar, tab == Tab.HOT_LIST ? "Search your hot list" : "Search all worlds");
        searchBar.setText("");
        updateList();
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
        return true;
    }

    /** The grey text in the empty search box, through RuneLite's look and feel; nothing happens without it. */
    private static void setPlaceholder(Container container, String text)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof JTextField)
            {
                ((JTextField) child).putClientProperty("JTextField.placeholderText", text);
                child.repaint();
            }
            else if (child instanceof Container)
            {
                setPlaceholder((Container) child, text);
            }
        }
    }

    Tab getTab()
    {
        return tab;
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
     * Sorts the tab that's showing, shows the worlds in it that match the search and counts them in the tab's name.
     */
    void updateList()
    {
        String query = searchBar.getText();
        List<WorldRow> list = tab == Tab.HOT_LIST ? hotRows : rows;
        list.sort(Comparator.comparing(WorldRow::getWorld, comparator(orderIndex, ascendingOrder, this::playerCount)));

        listContainer.removeAll();

        int shown = 0;
        for (WorldRow row : list)
        {
            if (!WorldSearch.matches(row.getWorld(), query))
            {
                continue;
            }
            row.setBackground(shown++ % 2 == 0 ? ODD_ROW : ColorScheme.DARK_GRAY_COLOR);
            listContainer.add(row);
        }

        boolean searching = query != null && !query.isBlank();
        if (shown == 0)
        {
            listEmpty.setText("<html><body style='width: 170px'>" + emptyMessage(tab, loaded, searching)
                + "</body></html>");
            listContainer.add(listEmpty);
        }

        hotTab.setText(tabTitle("Hot list", tab == Tab.HOT_LIST ? shown : hotRows.size()));
        allTab.setText(tabTitle("All worlds", tab == Tab.ALL_WORLDS ? shown : rows.size()));

        listContainer.revalidate();
        listContainer.repaint();
    }

    /** A tab's name and how many worlds it shows. */
    static String tabTitle(String name, int count)
    {
        return name + " (" + count + ")";
    }

    /** What a tab says when it has no worlds to show. */
    static String emptyMessage(Tab tab, boolean loaded, boolean searching)
    {
        if (tab == Tab.HOT_LIST)
        {
            return searching
                ? "Nothing in your hot list matches. Try All worlds."
                : "Your hot list is empty. Star worlds in All worlds to add them here, then click one here to hop.";
        }
        if (!loaded)
        {
            return "Loading worlds...";
        }
        return "No world matches. Try an activity like Tempoross, a world like 330 or a region like UK.";
    }

    /** The order of a tab, by the column clicked. */
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
     * Rebuilds the hot list's rows. A world the world list doesn't have (or not yet) still gets a row, so it can be
     * removed.
     */
    void rebuildHotList()
    {
        hotRows.clear();

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
            hotRows.add(buildRow(world, true));
        }
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

    /** A star clicked, on either tab: the world goes into the hot list or comes out of it. */
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
        updateList();
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

    /** The two tabs: the hot list, which the panel opens on, and every world. */
    enum Tab
    {
        HOT_LIST,
        ALL_WORLDS
    }
}
