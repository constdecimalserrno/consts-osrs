package com.constsmodels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;

/**
 * The sidebar: its two main buttons, Capture area and Export yourself, with a hint that anything in the game can be
 * exported from its right-click menu; then the folder files are saved to, with a button to copy its path and one to
 * choose another for the session; then the captures, newest first, on two tabs: this session's, and every one kept (see
 * {@link ModelLibrary}). A click on one opens it in the viewer again; its trash can deletes it, at once. Last, the
 * settings, in a section closed to start.
 */
class ConstsModelsPanel extends PluginPanel
{
    /** How big a capture's picture is in its row. */
    static final int PICTURE = 32;

    /* The Settings section's arrow, closed and open, as the other plugins' sidebars draw theirs */
    private static final ImageIcon SECTION_EXPAND_ICON;
    private static final ImageIcon SECTION_RETRACT_ICON;

    static
    {
        BufferedImage arrow = ImageUtil.luminanceOffset(
            ImageUtil.loadImageResource(ConstsModelsPanel.class, "/util/arrow_right.png"), -121);
        SECTION_EXPAND_ICON = new ImageIcon(arrow);
        SECTION_RETRACT_ICON = new ImageIcon(ImageUtil.rotateImage(arrow, Math.PI / 2));
    }

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
    private final MaterialTabGroup tabs = new MaterialTabGroup();
    private final MaterialTab sessionTab;
    private final MaterialTab allTab;
    private final JPanel list = new JPanel(new GridLayout(0, 1));
    private final JComponent sessionEmpty;
    private final JComponent allEmpty;

    /* The captures, newest first: this session's, and every one kept, this session's included */
    private final List<ModelLibrary.Capture> session = new ArrayList<>();
    private final List<ModelLibrary.Capture> all = new ArrayList<>();
    private final List<ActionCard> rows = new ArrayList<>();
    private boolean showingAll;

    /* The settings, in their section at the bottom */
    private final JPanel settings = new JPanel(new DynamicGridLayout(0, 1, 0, 5));
    private final JButton settingsToggle = new JButton(SECTION_EXPAND_ICON);
    private final JComboBox<ExportFormat> format = new JComboBox<>(ExportFormat.values());
    private final JSpinner radius = new JSpinner(radiusModel());
    private final JCheckBox shiftOnly = new JCheckBox();
    /** True while refresh shows the settings as saved, so the controls don't save them again. */
    private boolean loading;

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
        areaCard.setToolTipText("Everything around you, ground included, as one model to view or save");
        addRow(areaCard, c, 0);

        ActionCard meCard = new ActionCard(icon("me_icon.png"), FontManager.getRunescapeBoldFont(), 9,
            plugin::captureMe);
        meCard.setTitle("Export yourself");
        meCard.setDetail("Your player, as you look now");
        meCard.setToolTipText("Your own player, gear and all, as one model to view or save");
        addRow(meCard, c, 6);

        hint.setFont(FontManager.getRunescapeSmallFont());
        hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        hint.setBorder(new EmptyBorder(0, 2, 0, 2));
        addRow(hint, c, 8);

        // Where files go: the folder in a bar, then a button to copy its path and one to choose another
        addRow(buildFolderBar(), c, 16);
        addRow(buildFolderButtons(), c, 4);

        // The captures, on two tabs: this session's, and every one kept
        sessionTab = new MaterialTab("", tabs, null);
        sessionTab.setOnSelectEvent(() -> showTab(false));
        allTab = new MaterialTab("", tabs, null);
        allTab.setOnSelectEvent(() -> showTab(true));
        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        // Side by side, half the width each: the group's own layout puts the second tab on a line it then cuts off
        tabs.setLayout(new GridLayout(1, 2));
        tabs.addTab(sessionTab);
        tabs.addTab(allTab);
        addRow(tabs, c, 14);

        sessionEmpty = emptyNote("<font color='white'>Nothing captured yet</font><br>Right-click anything in the game "
            + "and pick <font color='white'>Export Model</font>, or use a button above. Each capture shows up here to "
            + "open again.");
        allEmpty = emptyNote("<font color='white'>Nothing kept yet</font><br>Every capture is kept here, session after "
            + "session, until you delete it with its <font color='white'>trash can</font>.");
        list.setOpaque(false);
        addRow(list, c, 6);

        // The settings, closed to start: the same as the plugin's page in the Configuration panel
        addRow(buildSettings(), c, 16);

        refresh();
        tabs.select(sessionTab);
        showList();
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

    /** Reads the folder and the settings again, either of which may have changed since. */
    void refresh()
    {
        Filepath folder = plugin.outputFolder();
        String full = folder == null ? "" : folder.toString();
        FontMetrics metrics = folderPath.getFontMetrics(folderPath.getFont());
        int width = CONTENT_WIDTH - 2 * BAR_PADDING - folderCaption.getPreferredSize().width - 6;
        folderPath.setText(shortPath(full, System.getProperty("user.home"), width, metrics::stringWidth));
        folderPath.setToolTipText(full);
        areaCard.setDetail(areaText(config.captureRadius()));

        loading = true;
        format.setSelectedItem(config.format());
        radius.setValue(config.captureRadius());
        shiftOnly.setSelected(config.shiftOnly());
        loading = false;
    }

    /** A capture just made and kept: first on both tabs. Call it on the Swing thread. */
    void added(ModelLibrary.Capture capture)
    {
        session.add(0, capture);
        all.removeIf(c -> c.id.equals(capture.id));
        all.add(0, capture);
        showList();
    }

    /** Every capture kept, as read from the library, newest first. Call it on the Swing thread. */
    void setLibrary(List<ModelLibrary.Capture> kept)
    {
        List<ModelLibrary.Capture> merged = new ArrayList<>(kept);
        for (ModelLibrary.Capture mine : session)
        {
            if (merged.stream().noneMatch(c -> c.id.equals(mine.id)))
            {
                merged.add(mine);
            }
        }
        merged.sort((a, b) -> Long.compare(b.time, a.time));
        all.clear();
        all.addAll(merged);
        showList();
    }

    /** A capture deleted: off both tabs. Call it on the Swing thread. */
    void removed(ModelLibrary.Capture capture)
    {
        session.removeIf(c -> c.id.equals(capture.id));
        all.removeIf(c -> c.id.equals(capture.id));
        showList();
    }

    /** This session's captures, newest first. */
    List<ModelLibrary.Capture> session()
    {
        return List.copyOf(session);
    }

    /** Every capture kept, newest first. */
    List<ModelLibrary.Capture> all()
    {
        return List.copyOf(all);
    }

    /** The rows on the tab showing, in order. */
    List<ActionCard> rows()
    {
        return List.copyOf(rows);
    }

    /** Shows one tab or the other, as a click on it does. */
    void selectTab(boolean everyOne)
    {
        tabs.select(everyOne ? allTab : sessionTab);
    }

    MaterialTab sessionTab()
    {
        return sessionTab;
    }

    MaterialTab allTab()
    {
        return allTab;
    }

    ActionCard areaCard()
    {
        return areaCard;
    }

    JLabel folderLabel()
    {
        return folderPath;
    }

    boolean settingsOpen()
    {
        return settings.isVisible();
    }

    JComboBox<ExportFormat> formatBox()
    {
        return format;
    }

    JSpinner radiusSpinner()
    {
        return radius;
    }

    JCheckBox shiftBox()
    {
        return shiftOnly;
    }

    private boolean showTab(boolean everyOne)
    {
        showingAll = everyOne;
        showList();
        return true;
    }

    private void showList()
    {
        sessionTab.setText(tabTitle("Session", session.size()));
        allTab.setText(tabTitle("All models", all.size()));
        // Until the first capture the list's own note says how to capture, so the hint would say it twice
        hint.setVisible(!session.isEmpty());

        List<ModelLibrary.Capture> shown = showingAll ? all : session;
        long now = System.currentTimeMillis();
        rows.clear();
        list.removeAll();
        if (shown.isEmpty())
        {
            list.add(showingAll ? allEmpty : sessionEmpty);
        }
        // Striped like the lists in RuneLite's own sidebar
        for (int i = 0; i < shown.size(); i++)
        {
            ActionCard row = buildRow(shown.get(i), now);
            Color stripe = i % 2 == 0 ? ODD_ROW : ColorScheme.DARK_GRAY_COLOR;
            row.setColors(stripe, stripe.brighter());
            rows.add(row);
            list.add(row);
        }
        list.revalidate();
        list.repaint();
    }

    private ActionCard buildRow(ModelLibrary.Capture capture, long now)
    {
        ActionCard row = new ActionCard(picture(capture.picture), FontManager.getRunescapeSmallFont(), 5,
            () -> plugin.open(capture));
        String name = displayName(capture.name);
        row.setTitle(name);
        row.setDetail(whenText(capture.time, now) + "  \u00b7  " + trianglesText(capture.faces));
        row.setToolTipText("Open " + name + " in the viewer again");

        // A label, not a button, so it stays as small as its icon
        JLabel delete = new JLabel(new TrashIcon(ColorScheme.LIGHT_GRAY_COLOR), JLabel.CENTER);
        delete.setToolTipText("Delete " + name);
        delete.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        delete.setPreferredSize(new Dimension(TrashIcon.SIZE + 6, 18));
        delete.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                delete.setIcon(new TrashIcon(ColorScheme.PROGRESS_ERROR_COLOR));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                delete.setIcon(new TrashIcon(ColorScheme.LIGHT_GRAY_COLOR));
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e) && delete.contains(e.getPoint()))
                {
                    delete(capture);
                }
            }
        });
        row.setTrailing(delete);
        return row;
    }

    /** Deletes the capture for good, without asking: its trash can was clicked. */
    void delete(ModelLibrary.Capture capture)
    {
        plugin.delete(capture);
    }

    /** A capture's picture at its size in the row, drawn from the bigger one on sharp screens. */
    private static ImageIcon picture(BufferedImage picture)
    {
        if (picture == null)
        {
            BufferedImage blank = new BufferedImage(PICTURE, PICTURE, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics g = blank.getGraphics();
            g.setColor(ColorScheme.DARKER_GRAY_COLOR);
            g.fillRect(0, 0, PICTURE, PICTURE);
            g.dispose();
            return new ImageIcon(blank);
        }
        return new ImageIcon(new BaseMultiResolutionImage(ImageUtil.resizeImage(picture, PICTURE, PICTURE), picture));
    }

    /** "This session (3)". */
    static String tabTitle(String name, int count)
    {
        return name + " (" + count + ")";
    }

    /** When a capture was made: its time today, its day this year, or its day and year before that. */
    static String whenText(long time, long now)
    {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(time);
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        String pattern = then.get(Calendar.YEAR) != today.get(Calendar.YEAR) ? "d MMM yyyy"
            : then.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR) ? "d MMM" : "HH:mm";
        return new SimpleDateFormat(pattern).format(new Date(time));
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
        JButton copy = new JButton("Copy path");
        copy.setToolTipText("Copy the folder's path, to paste in Finder or File Explorer");
        copy.addActionListener(e -> plugin.copyPath());

        JButton choose = new JButton("Choose folder");
        choose.setToolTipText("Pick another folder to save your models in, until RuneLite closes");
        choose.addActionListener(e -> chooseFolder());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
        buttons.setOpaque(false);
        for (JButton button : new JButton[]{copy, choose})
        {
            button.setFont(FontManager.getRunescapeSmallFont());
            button.setFocusPainted(false);
            // Narrow sides, or half the sidebar cuts "Choose folder" short
            button.setMargin(new Insets(2, 4, 2, 4));
            button.setPreferredSize(new Dimension(0, 26));
            buttons.add(button);
        }
        return buttons;
    }

    /**
     * The Settings section: a header that opens and closes it, then a row per setting, named and described as in the
     * Configuration panel. A change is saved at once; one made in the Configuration panel shows here on refresh.
     */
    private JComponent buildSettings()
    {
        JLabel name = new JLabel("Settings");
        name.setForeground(ColorScheme.BRAND_ORANGE);
        name.setFont(FontManager.getRunescapeBoldFont());

        settingsToggle.setToolTipText("Expand");
        settingsToggle.setPreferredSize(new Dimension(18, 0));
        settingsToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
        SwingUtil.removeButtonDecorations(settingsToggle);
        settingsToggle.addActionListener(e -> toggleSettings());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(0, 0, 3, 1)));
        header.add(settingsToggle, BorderLayout.WEST);
        header.add(name, BorderLayout.CENTER);
        MouseAdapter click = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                toggleSettings();
            }
        };
        header.addMouseListener(click);
        name.addMouseListener(click);

        format.setPreferredSize(new Dimension(format.getPreferredSize().width, 22));
        format.addItemListener(e ->
        {
            if (!loading && e.getStateChange() == ItemEvent.SELECTED)
            {
                config.format((ExportFormat) e.getItem());
            }
        });
        ((JSpinner.DefaultEditor) radius.getEditor()).getTextField().setColumns(6);
        radius.addChangeListener(e ->
        {
            if (!loading)
            {
                config.captureRadius((Integer) radius.getValue());
            }
        });
        shiftOnly.addItemListener(e ->
        {
            if (!loading)
            {
                config.shiftOnly(shiftOnly.isSelected());
            }
        });

        settings.setOpaque(false);
        settings.setBorder(new EmptyBorder(6, 0, 0, 0));
        settings.add(settingRow("format", format));
        settings.add(settingRow("captureRadius", radius));
        settings.add(settingRow("shiftOnly", shiftOnly));
        settings.setVisible(false);

        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);
        section.add(header, BorderLayout.NORTH);
        section.add(settings, BorderLayout.CENTER);
        return section;
    }

    /** Opens or closes the Settings section, as a click on its header does. */
    void toggleSettings()
    {
        boolean open = !settings.isVisible();
        settings.setVisible(open);
        settingsToggle.setIcon(open ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
        settingsToggle.setToolTipText(open ? "Retract" : "Expand");
        revalidate();
    }

    /** A setting's row: its name, its description on hover, and its control. */
    private static JPanel settingRow(String key, JComponent control)
    {
        ConfigItem item = setting(key).getAnnotation(ConfigItem.class);
        JLabel name = new JLabel(item.name());
        name.setForeground(Color.WHITE);
        name.setToolTipText("<html>" + item.name() + ":<br>" + item.description() + "</html>");
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(name, BorderLayout.CENTER);
        row.add(control, BorderLayout.EAST);
        return row;
    }

    /** The area radius's numbers, from the setting's own range. */
    private static SpinnerNumberModel radiusModel()
    {
        Range range = setting("captureRadius").getAnnotation(Range.class);
        return new SpinnerNumberModel(range.min(), range.min(), range.max(), 1);
    }

    /** A setting's getter, which carries its name, description and range. */
    static Method setting(String key)
    {
        try
        {
            return ConstsModelsConfig.class.getMethod(key);
        }
        catch (NoSuchMethodException e)
        {
            throw new IllegalArgumentException("no setting " + key, e);
        }
    }

    /** What a tab shows while it has no captures. */
    private JComponent emptyNote(String html)
    {
        JLabel note = new WrappingLabel(html, CONTENT_WIDTH);
        note.setFont(FontManager.getRunescapeSmallFont());
        note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        note.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createDashedBorder(ColorScheme.MEDIUM_GRAY_COLOR, 4, 3),
            new EmptyBorder(8, 10, 8, 10)));
        return note;
    }

    /** Asks for a folder to save in, through RuneLite's own chooser, which hands it over for the session. */
    private void chooseFolder()
    {
        Filepath.Chooser chooser = new Filepath.Chooser()
            .setAcceptsDirectories()
            .setDialogTitle("Choose where to save models");
        Filepath current = plugin.outputFolder();
        if (current != null)
        {
            chooser.setCurrentDirectory(current);
        }
        List<Filepath> picked = chooser.showDialog(this);
        if (picked != null && !picked.isEmpty())
        {
            plugin.setOutputFolder(picked.get(0));
            refresh();
        }
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
