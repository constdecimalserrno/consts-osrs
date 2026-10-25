/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2019, Bram91 <https://github.com/bram91>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's HiScore plugin into
 *   consts-Leaderboard; skills and the total level only, levels stop at 99, a 99 shows in yellow and the lowest
 *   skill in red, a bar naming whose skills are showing and a button for your own, in place of the hiscore tabs;
 *   a Friends dropdown that looks a friend up)
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
package com.constsleaderboard;

import static net.runelite.client.hiscore.HiscoreSkill.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sidebar: a name to look up, a bar naming whose skills are showing with a button beside it for yours, then the skills and the total level the way the HiScore
 * plugin shows them, except that levels stop at 99, a 99 is yellow and the lowest skill is red. Hovering one gives
 * its rank, its experience and what's left to the next level. Under them, your friends, in a dropdown that opens and
 * closes: click one to look them up.
 */
class ConstsLeaderboardPanel extends PluginPanel
{
    private static final Logger log = LoggerFactory.getLogger(ConstsLeaderboardPanel.class);

    /* The maximum allowed username length in RuneScape accounts */
    private static final int MAX_USERNAME_LENGTH = 12;

    /** The game's own yellow, for a level that can't go any higher. */
    static final Color MAX_COLOR = Color.YELLOW;

    /** The game's own red, for the lowest skill: the one furthest from 99. */
    static final Color LOWEST_COLOR = Color.RED;

    /* What the name bar says before the first lookup */
    private static final String NOBODY = "Nobody yet";

    /* Where the panel keeps whether Friends is open: a hidden key, not a setting */
    static final String CONFIG_GROUP = "constsleaderboard";
    static final String FRIENDS_OPEN = "friendsOpen";

    /* A friend's dot: green online, grey offline */
    private static final ImageIcon ONLINE_DOT = dot(ColorScheme.PROGRESS_COMPLETE_COLOR);
    private static final ImageIcon OFFLINE_DOT = dot(ColorScheme.MEDIUM_GRAY_COLOR);

    /** The skills, in the order the game's skills tab shows them. */
    static final List<HiscoreSkill> SKILLS = List.of(
        ATTACK, HITPOINTS, MINING,
        STRENGTH, AGILITY, SMITHING,
        DEFENCE, HERBLORE, FISHING,
        RANGED, THIEVING, COOKING,
        PRAYER, CRAFTING, FIREMAKING,
        MAGIC, FLETCHING, WOODCUTTING,
        RUNECRAFT, SLAYER, FARMING,
        CONSTRUCTION, HUNTER, SAILING
    );

    /** The highest total level there is: every skill at 99. */
    static final int MAX_TOTAL = SKILLS.size() * Levels.MAX;

    private final ConstsLeaderboardPlugin plugin;
    private final HiscoreClient hiscoreClient;
    private final SpriteManager spriteManager;
    private final ConfigManager configManager;

    private final IconTextField searchBar;

    /* Whose skills the panel is showing */
    private final JLabel shownName;

    /* Shows the account you're logged in to */
    private final JButton meButton;

    /* The account you're logged in to, or null when logged out */
    private String myName;

    /* The skills' labels, then the total level's */
    private final Map<HiscoreSkill, JLabel> skillLabels = new LinkedHashMap<>();

    /* A level's color when it isn't a 99 */
    private Color levelColor;

    /* The hiscores the next lookup reads */
    private HiscoreEndpoint endpoint = HiscoreEndpoint.NORMAL;

    /* Used to ignore the button for your own skills while a lookup is loading */
    private boolean loading = false;

    /* The bar that opens and closes Friends, its title with the arrow, how many are online, and the list itself */
    final JPanel friendsHeader;
    private final JLabel friendsTitle;
    final JLabel friendsCount;
    final JPanel friendsList;

    @Inject
    ConstsLeaderboardPanel(ConstsLeaderboardPlugin plugin, HiscoreClient hiscoreClient, SpriteManager spriteManager,
        ConfigManager configManager)
    {
        this.plugin = plugin;
        this.hiscoreClient = hiscoreClient;
        this.spriteManager = spriteManager;
        this.configManager = configManager;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new GridBagLayout());

        // Expand sub items to fit width of panel, align to top of panel
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 0;
        c.insets = new Insets(0, 0, 10, 0);

        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.setMinimumSize(new Dimension(0, 30));
        searchBar.addActionListener(e ->
        {
            endpoint = plugin.getWorldEndpoint();
            lookup();
        });
        searchBar.addClearListener(() ->
        {
            searchBar.setIcon(IconTextField.Icon.SEARCH);
            searchBar.setEditable(true);
            loading = false;
        });

        add(searchBar, c);
        c.gridy++;

        // Whose skills these are, in the bold font, and beside it a button that shows yours
        shownName = new JLabel();
        shownName.setFont(FontManager.getRunescapeBoldFont());
        shownName.setOpaque(true);
        shownName.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        shownName.setBorder(new EmptyBorder(0, 8, 0, 8));

        meButton = new JButton(new ImageIcon(ImageUtil.loadImageResource(getClass(), "me_icon.png")));
        meButton.setFocusPainted(false);
        meButton.setPreferredSize(new Dimension(30, 30));
        meButton.addActionListener(e -> showMe());

        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        header.add(shownName, BorderLayout.CENTER);
        header.add(meButton, BorderLayout.EAST);

        showName(NOBODY, ColorScheme.LIGHT_GRAY_COLOR);
        setMyName(null);

        add(header, c);
        c.gridy++;

        // Panel that holds skill icons
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new GridLayout(8, 3));
        statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statsPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

        for (HiscoreSkill skill : SKILLS)
        {
            statsPanel.add(makeSkillPanel(skill));
        }

        add(statsPanel, c);
        c.gridy++;

        JPanel totalPanel = new JPanel();
        totalPanel.setLayout(new GridLayout(1, 1));
        totalPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        totalPanel.add(makeSkillPanel(OVERALL));

        add(totalPanel, c);
        c.gridy++;

        // Friends: a bar like the name bar that opens and closes the list under it
        friendsList = new JPanel(new GridLayout(0, 1));
        friendsList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        friendsList.setBorder(new EmptyBorder(0, 0, 4, 0));

        friendsTitle = new JLabel("Friends");
        friendsTitle.setFont(FontManager.getRunescapeBoldFont());
        friendsTitle.setForeground(Color.WHITE);
        friendsTitle.setIconTextGap(6);

        friendsCount = new JLabel("", SwingConstants.RIGHT);
        friendsCount.setFont(FontManager.getRunescapeSmallFont());
        friendsCount.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        friendsHeader = new JPanel(new BorderLayout());
        friendsHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        friendsHeader.setBorder(new EmptyBorder(0, 8, 0, 8));
        friendsHeader.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        friendsHeader.add(friendsTitle, BorderLayout.WEST);
        friendsHeader.add(friendsCount, BorderLayout.EAST);
        friendsHeader.addMouseListener(clickable(friendsHeader, () -> setFriendsOpen(!friendsList.isVisible())));

        c.insets = new Insets(0, 0, 0, 0);
        add(friendsHeader, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        add(friendsList, c);
        c.gridy++;

        setFriends(null);
        showFriendsOpen(!"false".equals(configManager.getConfiguration(CONFIG_GROUP, FRIENDS_OPEN)));
    }

    /** Your friends, name to online, in the order to list them; null when logged out. */
    void setFriends(Map<String, Boolean> friends)
    {
        friendsList.removeAll();
        if (friends == null || friends.isEmpty())
        {
            JLabel none = new JLabel(friends == null ? "Log in to see your friends" : "No friends yet");
            none.setFont(FontManager.getRunescapeSmallFont());
            none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            none.setBorder(new EmptyBorder(4, 8, 4, 8));
            friendsList.add(none);
            friendsCount.setText("");
        }
        else
        {
            for (Map.Entry<String, Boolean> friend : friends.entrySet())
            {
                friendsList.add(friendRow(friend.getKey(), friend.getValue()));
            }
            friendsCount.setText(friends.values().stream().filter(online -> online).count() + " online");
        }
        friendsList.revalidate();
        friendsList.repaint();
    }

    /* A friend's dot and name; clicking it looks them up, the way the search box does */
    private JLabel friendRow(String name, boolean online)
    {
        JLabel row = new JLabel(name, online ? ONLINE_DOT : OFFLINE_DOT, SwingConstants.LEFT);
        row.setFont(FontManager.getRunescapeFont());
        row.setForeground(online ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
        row.setIconTextGap(6);
        row.setOpaque(true);
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(2, 8, 2, 8));
        row.setToolTipText("Look up " + name);
        row.addMouseListener(clickable(row, () -> lookup(name, plugin.getWorldEndpoint())));
        return row;
    }

    /* Clicks run the action; hovering shows a hand and lightens the background like RuneLite's own lists */
    private static MouseAdapter clickable(JComponent c, Runnable onPress)
    {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                onPress.run();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                c.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                c.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        };
    }

    private void setFriendsOpen(boolean open)
    {
        showFriendsOpen(open);
        configManager.setConfiguration(CONFIG_GROUP, FRIENDS_OPEN, open);
    }

    private void showFriendsOpen(boolean open)
    {
        friendsList.setVisible(open);
        friendsTitle.setIcon(arrow(open));
        revalidate();
    }

    /* The arrow on the Friends bar: down when open, right when closed */
    private static ImageIcon arrow(boolean open)
    {
        return icon(ColorScheme.LIGHT_GRAY_COLOR, g -> g.fillPolygon(open ? new int[]{0, 8, 4} : new int[]{2, 2, 7},
            open ? new int[]{2, 2, 7} : new int[]{0, 8, 4}, 3));
    }

    private static ImageIcon dot(Color color)
    {
        return icon(color, g -> g.fillOval(1, 1, 6, 6));
    }

    /* An 8x8 icon drawn in one color */
    private static ImageIcon icon(Color color, Consumer<Graphics2D> draw)
    {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        draw.accept(g);
        g.dispose();
        return new ImageIcon(img);
    }

    @Override
    public void onActivate()
    {
        super.onActivate();
        searchBar.requestFocusInWindow();
    }

    /* Builds a JPanel displaying a skill's icon and its level */
    private JPanel makeSkillPanel(HiscoreSkill skill)
    {
        JLabel label = new JLabel();
        label.setToolTipText(skill.getName());
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setText(pad("--"));
        levelColor = label.getForeground();

        spriteManager.getSpriteAsync(skill.getSpriteId(), 0, (sprite) ->
            SwingUtilities.invokeLater(() ->
            {
                // Icons are all 25x25 or smaller, so they're fit into a 25x25 canvas to give them a consistent size for
                // better alignment. Further, they are then scaled down to 20x20 to not be overly large in the panel.
                final BufferedImage scaledSprite = ImageUtil.resizeImage(ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20);
                label.setIcon(new ImageIcon(scaledSprite));
            }));

        label.setIconTextGap(skill == OVERALL ? 10 : 4);

        JPanel skillPanel = new JPanel();
        skillPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        skillPanel.setBorder(new EmptyBorder(2, 0, 2, 0));
        skillLabels.put(skill, label);
        skillPanel.add(label);

        return skillPanel;
    }

    void lookup(String username, HiscoreEndpoint endpoint)
    {
        searchBar.setText(username);
        this.endpoint = endpoint;
        lookup();
    }

    /** The account you're logged in to, for the button that shows it; null when logged out greys the button out. */
    void setMyName(String name)
    {
        myName = name == null ? null : sanitize(name);
        meButton.setEnabled(myName != null);
        meButton.setToolTipText(meTooltip(myName, shownName.getText()));
    }

    private void showName(String text, Color color)
    {
        shownName.setText(text);
        shownName.setForeground(color);
        meButton.setToolTipText(meTooltip(myName, text));
    }

    /** The button's tooltip: your name, and whose skills are showing. */
    static String meTooltip(String myName, String shown)
    {
        String you = myName == null ? "log in to show yourself" : "<b>" + myName + "</b>";
        return "<html>You: " + you + "<br>Showing: <b>" + shown + "</b></html>";
    }

    private void showMe()
    {
        if (loading || myName == null)
        {
            return;
        }

        lookup(myName, plugin.getLocalHiscoreEndpoint());
    }

    private void lookup()
    {
        final String lookup = sanitize(searchBar.getText());

        if (lookup.isEmpty())
        {
            return;
        }

        /* RuneScape usernames can't be longer than 12 characters long */
        if (lookup.length() > MAX_USERNAME_LENGTH)
        {
            searchBar.setIcon(IconTextField.Icon.ERROR);
            loading = false;
            return;
        }

        repaint();

        searchBar.setEditable(false);
        searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);
        loading = true;
        showName(lookup, ColorScheme.LIGHT_GRAY_COLOR);

        for (Map.Entry<HiscoreSkill, JLabel> entry : skillLabels.entrySet())
        {
            JLabel label = entry.getValue();
            label.setText(pad("--"));
            label.setForeground(levelColor);
            label.setToolTipText(entry.getKey().getName());
        }

        hiscoreClient.lookupAsync(lookup, endpoint).whenCompleteAsync((result, ex) ->
            SwingUtilities.invokeLater(() ->
            {
                if (!sanitize(searchBar.getText()).equals(lookup))
                {
                    // search has changed in the meantime
                    return;
                }

                if (result == null || ex != null)
                {
                    if (ex != null)
                    {
                        log.warn("Error fetching Hiscore data " + ex.getMessage());
                    }

                    searchBar.setIcon(IconTextField.Icon.ERROR);
                    searchBar.setEditable(true);
                    loading = false;
                    showName(lookup + " not found", ColorScheme.LIGHT_GRAY_COLOR);
                    return;
                }

                //successful player search
                searchBar.setIcon(IconTextField.Icon.SEARCH);
                searchBar.setEditable(true);
                loading = false;
                showName(result.getPlayer(), Color.WHITE);

                applyHiscoreResult(result);
            }));
    }

    private void applyHiscoreResult(HiscoreResult result)
    {
        assert SwingUtilities.isEventDispatchThread();
        repaint();

        int lowest = lowestLevel(result);

        for (Map.Entry<HiscoreSkill, JLabel> entry : skillLabels.entrySet())
        {
            HiscoreSkill skill = entry.getKey();
            JLabel label = entry.getValue();
            Skill s = result.getSkill(skill);

            int level = shownLevel(skill, s);
            if (level > 0)
            {
                label.setText(pad(Integer.toString(level)));
            }
            label.setForeground(isMax(skill, level) ? MAX_COLOR
                : isLowest(skill, level, lowest) ? LOWEST_COLOR
                : levelColor);
            label.setToolTipText(detailsHtml(skill, s));
        }
    }

    /**
     * The lowest level among the ranked skills, or -1 when that's 99: once every skill is 99, none is the lowest.
     * A skill that isn't ranked has no level, so it doesn't count.
     */
    static int lowestLevel(HiscoreResult result)
    {
        int lowest = Levels.MAX;
        for (HiscoreSkill skill : SKILLS)
        {
            int level = shownLevel(skill, result.getSkill(skill));
            if (level > 0)
            {
                lowest = Math.min(lowest, level);
            }
        }
        return lowest < Levels.MAX ? lowest : -1;
    }

    /** A skill at the lowest level; skills tied for it are all the lowest. */
    static boolean isLowest(HiscoreSkill skill, int level, int lowest)
    {
        return skill != OVERALL && level > 0 && level == lowest;
    }

    /** The level to show for a skill, never past 99, or the total level; -1 when there's none. */
    static int shownLevel(HiscoreSkill skill, Skill s)
    {
        if (s == null)
        {
            return -1;
        }
        return skill == OVERALL ? s.getLevel() : Levels.level(s.getExperience());
    }

    /** A 99, or the highest total level. */
    static boolean isMax(HiscoreSkill skill, int level)
    {
        return level >= (skill == OVERALL ? MAX_TOTAL : Levels.MAX);
    }

    /*
        Builds a html string to display on tooltip (when hovering a skill).
     */
    static String detailsHtml(HiscoreSkill skill, Skill s)
    {
        String openingTags = "<html><body style = 'padding: 5px;color:#989898'>";
        String closingTags = "</body></html>";

        int rank = s == null ? -1 : s.getRank();
        long experience = s == null ? -1L : s.getExperience();

        String rankStr = (rank == -1) ? "Unranked" : QuantityFormatter.formatNumber(rank);
        String exp = (experience == -1L) ? "Unranked" : QuantityFormatter.formatNumber(experience);

        String content = "";

        if (skill == OVERALL)
        {
            content += "<p><span style = 'color:white'>" + skill.getName() + "</span></p>";
            content += "<p><span style = 'color:white'>Rank:</span> " + rankStr + "</p>";
            content += "<p><span style = 'color:white'>Experience:</span> " + exp + "</p>";
            return openingTags + content + closingTags;
        }

        String remainingXp = (experience == -1L) ? "Unranked" : QuantityFormatter.formatNumber(Levels.remaining(experience));

        content += "<p><span style = 'color:white'>Skill:</span> " + skill.getName() + "</p>";
        content += "<p><span style = 'color:white'>Rank:</span> " + rankStr + "</p>";
        content += "<p><span style = 'color:white'>Experience:</span> " + exp + "</p>";
        content += "<p><span style = 'color:white'>Remaining XP:</span> " + remainingXp + "</p>";

        // Add a html progress bar to the hover information
        if (experience >= 0)
        {
            // had to wrap the bar with an empty div, if i added the margin directly to the bar, it would mess up
            content += "<div style = 'margin-top:3px'>"
                + "<div style = 'background: #070707; border: 1px solid #070707; height: 6px; width: 100%;'>"
                + "<div style = 'height: 6px; width: " + Levels.progress(experience) + "%; background: #dc8a00;'>"
                + "</div>"
                + "</div>"
                + "</div>";
        }

        return openingTags + content + closingTags;
    }

    private static String sanitize(String lookup)
    {
        return lookup.replace(' ', ' ');
    }

    private static String pad(String str)
    {
        // Left pad label text to keep labels aligned
        return String.format("%2s", str);
    }
}
