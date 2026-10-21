package com.constsmodels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * The sidebar: its two main buttons, Capture area and Export yourself, with a hint that anything in the game can be
 * exported from its right-click menu; then the folder files are saved to, with a button to open it and one to choose
 * another; then the models captured this session, newest first. A click on one opens it in the viewer again.
 */
class ConstsModelsPanel extends PluginPanel
{
    /** The most captures the list keeps; older ones drop off the end. */
    static final int MAX_RECENT = 20;

    /* The space around everything, and the width left inside it */
    private static final int MARGIN = 10;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - 2 * MARGIN;

    /* Every other row of the list, as consts-World stripes its own */
    private static final Color ODD_ROW = new Color(44, 44, 44);

    /* The padding inside the folder's bar */
    private static final int BAR_PADDING = 8;

    private final ConstsModelsPlugin plugin;
    private final ConstsModelsConfig config;

    private final JLabel folderCaption = new JLabel("Save to");
    private final JLabel folderPath = new JLabel();
    private final ActionCard areaCard;
    private final JLabel hint = new WrappingLabel("Or right-click anything in the game and pick "
        + "<font color='white'>Export Model</font>.", CONTENT_WIDTH);
    private final JLabel recentOpenHint = new JLabel("Click one to open it");
    private final JPanel recentList = new JPanel(new GridLayout(0, 1));
    private final JComponent recentEmpty;

    /* The captures listed, newest first */
    private final List<Mesh> recent = new ArrayList<>();
    private final List<ActionCard> recentRows = new ArrayList<>();

    ConstsModelsPanel(ConstsModelsPlugin plugin, ConstsModelsConfig config)
    {
        this.plugin = plugin;
        this.config = config;

        setBorder(new EmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;

        // The two captures, the panel's main buttons, and the other way to capture
        areaCard = new ActionCard(icon("area_icon.png"), FontManager.getRunescapeBoldFont(), 9, plugin::captureArea);
        areaCard.setTitle("Capture area");
        areaCard.setToolTipText("Everything around you, ground included, as one model to view or print");
        addRow(areaCard, c, 0);

        ActionCard meCard = new ActionCard(icon("me_icon.png"), FontManager.getRunescapeBoldFont(), 9,
            plugin::captureMe);
        meCard.setTitle("Export yourself");
        meCard.setDetail("Your player, as you look now");
        meCard.setToolTipText("Your own player, gear and all, as one model to view or print");
        addRow(meCard, c, 6);

        hint.setFont(FontManager.getRunescapeSmallFont());
        hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        hint.setBorder(new EmptyBorder(0, 2, 0, 2));
        addRow(hint, c, 8);

        // Where files go: the folder in a bar, then a button to open it and one to choose another
        addRow(buildFolderBar(), c, 16);
        addRow(buildFolderButtons(), c, 4);

        // The captures so far, under a title that says what a click on one does
        JLabel recentTitle = new JLabel("This session");
        recentTitle.setFont(FontManager.getRunescapeBoldFont());
        recentTitle.setForeground(Color.WHITE);
        recentOpenHint.setFont(FontManager.getRunescapeSmallFont());
        recentOpenHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        JPanel recentHeader = new JPanel(new BorderLayout());
        recentHeader.setOpaque(false);
        recentHeader.setBorder(new EmptyBorder(0, 2, 0, 2));
        recentHeader.add(recentTitle, BorderLayout.WEST);
        recentHeader.add(recentOpenHint, BorderLayout.EAST);
        addRow(recentHeader, c, 16);

        recentEmpty = buildEmptyNote();
        recentList.setOpaque(false);
        addRow(recentList, c, 6);

        refresh();
        showRecent();
    }

    /** Adds the next row of the panel, the given gap under the row before it. */
    private void addRow(JComponent component, GridBagConstraints c, int gap)
    {
        c.insets = new Insets(gap, 0, 0, 0);
        add(component, c);
        c.gridy++;
    }

    @Override
    public void onActivate()
    {
        super.onActivate();
        refresh();
    }

    /** Reads the folder and the area's size again, which may have changed in the settings since. */
    void refresh()
    {
        File folder = plugin.outputFolder();
        String full = folder == null ? "" : folder.getAbsolutePath();
        FontMetrics metrics = folderPath.getFontMetrics(folderPath.getFont());
        int width = CONTENT_WIDTH - 2 * BAR_PADDING - folderCaption.getPreferredSize().width - 6;
        folderPath.setText(shortPath(full, System.getProperty("user.home"), width, metrics::stringWidth));
        folderPath.setToolTipText(full);
        areaCard.setDetail(areaText(config.captureRadius()));
    }

    /** A capture to list, newest first. Call it on the Swing thread. */
    void addRecent(Mesh mesh)
    {
        recent.add(0, mesh);
        recentRows.add(0, buildRow(mesh));
        while (recent.size() > MAX_RECENT)
        {
            recent.remove(recent.size() - 1);
            recentRows.remove(recentRows.size() - 1);
        }
        showRecent();
    }

    /** The captures listed, newest first. */
    List<Mesh> recent()
    {
        return List.copyOf(recent);
    }

    /** The rows that list them, in the same order. */
    List<ActionCard> recentRows()
    {
        return List.copyOf(recentRows);
    }

    ActionCard areaCard()
    {
        return areaCard;
    }

    JLabel folderLabel()
    {
        return folderPath;
    }

    private void showRecent()
    {
        // Until the first capture the list's own note says how to capture, so the hint would say it twice
        hint.setVisible(!recentRows.isEmpty());
        recentOpenHint.setVisible(!recentRows.isEmpty());
        recentList.removeAll();
        if (recentRows.isEmpty())
        {
            recentList.add(recentEmpty);
        }
        // Striped like the lists in RuneLite's own sidebar
        for (int i = 0; i < recentRows.size(); i++)
        {
            ActionCard row = recentRows.get(i);
            Color stripe = i % 2 == 0 ? ODD_ROW : ColorScheme.DARK_GRAY_COLOR;
            row.setColors(stripe, stripe.brighter());
            recentList.add(row);
        }
        recentList.revalidate();
        recentList.repaint();
    }

    private ActionCard buildRow(Mesh mesh)
    {
        ActionCard row = new ActionCard(null, FontManager.getRunescapeSmallFont(), 5, () -> plugin.reopen(mesh));
        String name = displayName(mesh.name);
        row.setTitle(name);
        row.setDetail(trianglesText(mesh.faceCount()));
        row.setToolTipText("Open " + name + " in the viewer again");
        return row;
    }

    private JPanel buildFolderBar()
    {
        folderCaption.setFont(FontManager.getRunescapeSmallFont());
        folderCaption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        folderPath.setFont(FontManager.getRunescapeSmallFont());
        folderPath.setForeground(Color.WHITE);

        JPanel bar = new JPanel(new BorderLayout(6, 0))
        {
            @Override
            public Dimension getPreferredSize()
            {
                return new Dimension(0, 26);
            }
        };
        bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bar.setBorder(new EmptyBorder(0, BAR_PADDING, 0, BAR_PADDING));
        bar.add(folderCaption, BorderLayout.WEST);
        bar.add(folderPath, BorderLayout.CENTER);
        return bar;
    }

    private JPanel buildFolderButtons()
    {
        JButton open = new JButton("Open folder");
        open.setToolTipText("Show the folder your models are saved in");
        open.addActionListener(e -> plugin.openFolder());

        JButton choose = new JButton("Choose folder");
        choose.setToolTipText("Pick another folder to save your models in");
        choose.addActionListener(e -> chooseFolder());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
        buttons.setOpaque(false);
        for (JButton button : new JButton[]{open, choose})
        {
            button.setFont(FontManager.getRunescapeSmallFont());
            button.setFocusPainted(false);
            button.setPreferredSize(new Dimension(0, 26));
            buttons.add(button);
        }
        return buttons;
    }

    /** What the list shows before the first capture. */
    private JComponent buildEmptyNote()
    {
        JLabel note = new WrappingLabel("<font color='white'>Nothing captured yet</font><br>Right-click anything in "
            + "the game and pick <font color='white'>Export Model</font>, or use a button above. Each capture shows up "
            + "here to open again.", CONTENT_WIDTH);
        note.setFont(FontManager.getRunescapeSmallFont());
        note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        note.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createDashedBorder(ColorScheme.MEDIUM_GRAY_COLOR, 4, 3),
            new EmptyBorder(8, 10, 8, 10)));
        return note;
    }

    private void chooseFolder()
    {
        JFileChooser chooser = new JFileChooser(nearestExisting(plugin.outputFolder()));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Choose where to save models");
        if (chooser.showDialog(this, "Save here") == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null)
        {
            plugin.setOutputFolder(chooser.getSelectedFile());
            refresh();
        }
    }

    /** The folder, or the nearest folder above it that exists, for the chooser to start in. */
    private static File nearestExisting(File folder)
    {
        File f = folder;
        while (f != null && !f.isDirectory())
        {
            f = f.getParentFile();
        }
        return f;
    }

    private ImageIcon icon(String name)
    {
        return new ImageIcon(ImageUtil.loadImageResource(ConstsModelsPanel.class, name));
    }

    /** What Capture area covers: the square of tiles the radius reaches, you in the middle. */
    static String areaText(int radius)
    {
        int side = 2 * radius + 1;
        return side + " \u00d7 " + side + " tiles around you";
    }

    /** A capture's size, in the triangles a printer or 3D program sees. */
    static String trianglesText(int count)
    {
        return QuantityFormatter.formatNumber(count) + (count == 1 ? " triangle" : " triangles");
    }

    /** A capture's name without the game's color tags. */
    static String displayName(String name)
    {
        String plain = name == null ? "" : Text.removeTags(name).trim();
        return plain.isEmpty() ? "Model" : plain;
    }

    /**
     * A folder's path short enough to fit: the home folder becomes ~, then, while it's too wide, the folders after the
     * first make way for dots, from the front: ~/.../3D prints/models. When even that's too wide the first goes too,
     * and a last folder too wide on its own keeps its end.
     */
    static String shortPath(String path, String home, int maxWidth, ToIntFunction<String> width)
    {
        String shown = path;
        if (home != null && !home.isEmpty() && path.startsWith(home)
            && (path.length() == home.length() || isSeparator(path.charAt(home.length()))))
        {
            shown = "~" + path.substring(home.length());
        }
        if (width.applyAsInt(shown) <= maxWidth)
        {
            return shown;
        }

        // Where each folder after the first begins: a separator, not counting one the path starts with
        List<Integer> cuts = new ArrayList<>();
        for (int i = 1; i < shown.length(); i++)
        {
            if (isSeparator(shown.charAt(i)) && !isSeparator(shown.charAt(i - 1)))
            {
                cuts.add(i);
            }
        }

        // Keep the first folder and as many of the last as fit, dots between
        if (cuts.size() >= 2)
        {
            String head = shown.substring(0, cuts.get(0) + 1) + "...";
            for (int k = 1; k < cuts.size(); k++)
            {
                String fit = head + shown.substring(cuts.get(k));
                if (width.applyAsInt(fit) <= maxWidth)
                {
                    return fit;
                }
            }
        }

        // Then without the first folder
        for (int cut : cuts)
        {
            String fit = "..." + shown.substring(cut);
            if (width.applyAsInt(fit) <= maxWidth)
            {
                return fit;
            }
        }

        // Even the last folder is too wide: keep as much of its end as fits
        for (int i = 1; i < shown.length(); i++)
        {
            String fit = "..." + shown.substring(i);
            if (width.applyAsInt(fit) <= maxWidth)
            {
                return fit;
            }
        }
        return "...";
    }

    private static boolean isSeparator(char ch)
    {
        return ch == '/' || ch == '\\';
    }

    /** A label whose text wraps to the width it's given, as many lines as that takes; a plain label won't wrap. */
    private static class WrappingLabel extends JLabel
    {
        private final int wrapWidth;

        WrappingLabel(String html, int wrapWidth)
        {
            super("<html>" + html + "</html>");
            this.wrapWidth = wrapWidth;
        }

        @Override
        public Dimension getPreferredSize()
        {
            View view = (View) getClientProperty(BasicHTML.propertyKey);
            if (view == null)
            {
                return super.getPreferredSize();
            }
            Insets insets = getInsets();
            view.setSize(wrapWidth - insets.left - insets.right, 0);
            int height = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
            return new Dimension(wrapWidth, height + insets.top + insets.bottom);
        }
    }
}
