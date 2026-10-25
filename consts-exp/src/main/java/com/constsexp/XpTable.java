package com.constsexp;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Tick & XP tab's live table: one row per skill trained since the login, newest first (its icon, XP/h, XP gained
 * and the time since its first drop, and a reset), then Reset all. It follows the clock once a second while it shows.
 */
final class XpTable extends JPanel
{
    private static final int ICON_WIDTH = 20;

    private final XpRates rates;
    private final SkillIconManager skillIconManager;
    private final JPanel rows = new JPanel();
    private final JButton resetAll = new JButton("Reset all");
    private final Timer timer = new Timer(1_000, e -> refresh(System.currentTimeMillis()));
    // The skills the rows are for, so they're built again only when a skill joins.
    private List<Skill> shown;
    private final List<JLabel[]> cells = new ArrayList<>();

    XpTable(XpRates rates, SkillIconManager skillIconManager)
    {
        this.rates = rates;
        this.skillIconManager = skillIconManager;
        setLayout(new BorderLayout(0, 5));
        setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);
        add(rows, BorderLayout.CENTER);
        resetAll.setToolTipText("Set every skill's XP/h back to zero, for after a break");
        resetAll.addActionListener(e ->
        {
            rates.resetAll();
            refresh(System.currentTimeMillis());
        });
        add(resetAll, BorderLayout.SOUTH);
        refresh(System.currentTimeMillis());
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        refresh(System.currentTimeMillis());
        timer.start();
    }

    @Override
    public void removeNotify()
    {
        timer.stop();
        super.removeNotify();
    }

    void refresh(long now)
    {
        List<Skill> skills = rates.skills();
        if (!skills.equals(shown))
        {
            build(skills);
        }
        for (int i = 0; i < skills.size(); i++)
        {
            Skill skill = skills.get(i);
            JLabel[] row = cells.get(i);
            row[0].setText(XpRates.format(rates.perHour(skill, now)));
            row[1].setText(XpRates.format(rates.gained(skill)));
            row[2].setText(ConstsExpStatusOverlay.formatClock(rates.elapsed(skill, now)));
        }
    }

    private void build(List<Skill> skills)
    {
        shown = skills;
        cells.clear();
        rows.removeAll();
        resetAll.setEnabled(!skills.isEmpty());
        if (skills.isEmpty())
        {
            JLabel hint = new JLabel("Gain XP and each skill's rate shows here.");
            hint.setFont(FontManager.getRunescapeSmallFont());
            hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            rows.add(hint);
        }
        else
        {
            rows.add(row(transparent(), cell("XP/h", ColorScheme.LIGHT_GRAY_COLOR),
                cell("XP", ColorScheme.LIGHT_GRAY_COLOR), cell("Time", ColorScheme.LIGHT_GRAY_COLOR), null));
            for (Skill skill : skills)
            {
                JLabel icon = new JLabel(new ImageIcon(skillIconManager.getSkillImage(skill, true)));
                icon.setToolTipText(skill.getName());
                JLabel[] row = {cell("", Color.WHITE), cell("", Color.WHITE), cell("", Color.WHITE)};
                cells.add(row);
                JButton reset = resetButton();
                reset.setToolTipText("Set " + skill.getName() + "'s XP/h back to zero");
                reset.addActionListener(e ->
                {
                    rates.reset(skill);
                    refresh(System.currentTimeMillis());
                });
                rows.add(row(icon, row[0], row[1], row[2], reset));
            }
        }
        revalidate();
        repaint();
    }

    /** The icon, three columns, and the reset (or room for it, in the header). */
    private static JPanel row(JComponent icon, JLabel rate, JLabel xp, JLabel time, JButton reset)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(1, 0, 1, 0));
        icon.setPreferredSize(new Dimension(ICON_WIDTH, 18));
        row.add(icon, BorderLayout.WEST);
        JPanel columns = new JPanel(new GridLayout(1, 3, 4, 0));
        columns.setOpaque(false);
        columns.add(rate);
        columns.add(xp);
        columns.add(time);
        row.add(columns, BorderLayout.CENTER);
        JComponent east = reset;
        if (east == null)
        {
            // As wide as a reset, so the header's columns sit over the rows'.
            east = transparent();
            east.setPreferredSize(resetButton().getPreferredSize());
        }
        row.add(east, BorderLayout.EAST);
        return row;
    }

    private static JButton resetButton()
    {
        JButton reset = new JButton("Reset");
        reset.setFont(FontManager.getRunescapeSmallFont());
        reset.setMargin(new Insets(0, 3, 0, 3));
        reset.setFocusable(false);
        return reset;
    }

    private static JPanel transparent()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel cell(String text, Color color)
    {
        JLabel label = new JLabel(text, SwingConstants.RIGHT);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(color);
        return label;
    }
}
