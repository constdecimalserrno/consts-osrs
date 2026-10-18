package com.constsafk;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar guide: a Preset dropdown that is the plugin's own (picking one applies it), and the training guide for
 * whichever preset is on.
 */
class ConstsAfkGuidePanel extends PluginPanel
{
    private static final int TEXT_WIDTH = PANEL_WIDTH - 30;

    private final ConstsAfkPlugin plugin;
    private final JComboBox<Preset> presetBox = new JComboBox<>(Preset.values());
    private final JLabel where = body();
    private final JLabel alerts = body();
    private final JLabel tune = body();
    private boolean showing;

    ConstsAfkGuidePanel(ConstsAfkPlugin plugin)
    {
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("consts-AFK guide");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        addLeft(content, title);
        content.add(Box.createVerticalStrut(8));

        JLabel presetLabel = new JLabel("Preset");
        presetLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        addLeft(content, presetLabel);
        content.add(Box.createVerticalStrut(4));
        presetBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        presetBox.setToolTipText("Picking a preset here applies it, like the dropdown at the top of the config");
        presetBox.addActionListener(e ->
        {
            Preset picked = (Preset) presetBox.getSelectedItem();
            if (!showing && picked != null)
            {
                plugin.applyPreset(picked);
            }
        });
        content.add(presetBox);

        section(content, "Where", where);
        section(content, "What the alerts mean", alerts);
        section(content, "Tune", tune);

        add(content, BorderLayout.NORTH);
    }

    /** Shows the preset that is on, without applying it again. */
    void showPreset(Preset preset)
    {
        Preset shown = preset != null ? preset : Preset.CUSTOM;
        showing = true;
        try
        {
            presetBox.setSelectedItem(shown);
        }
        finally
        {
            showing = false;
        }

        PresetGuide guide = PresetGuide.of(shown);
        where.setText(wrap(guide.getWhere()));
        alerts.setText(wrap(guide.getAlerts()));
        tune.setText(wrap(guide.getTune()));
    }

    private static void section(JPanel content, String heading, JLabel text)
    {
        content.add(Box.createVerticalStrut(12));
        JLabel label = new JLabel(heading);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.BRAND_ORANGE);
        addLeft(content, label);
        content.add(Box.createVerticalStrut(3));
        addLeft(content, text);
    }

    private static void addLeft(JPanel content, JLabel label)
    {
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(label);
    }

    private static JLabel body()
    {
        JLabel label = new JLabel();
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setBorder(BorderFactory.createEmptyBorder());
        return label;
    }

    /** JLabel wraps only HTML, and only at a set width. */
    private static String wrap(String text)
    {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><body style='width: " + TEXT_WIDTH + "px'>" + escaped + "</body></html>";
    }
}
