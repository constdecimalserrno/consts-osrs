/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: cut down to one plugin's settings in its own sidebar
 * panel, kept up to date while it's open, with a master switch, optional settings tabs and tabs of the plugin's own)
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
package com.constsdestination;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.JTextComponent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigObject;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.UnitFormatterFactory;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.TitleCaseListCellRenderer;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The plugin's settings in a sidebar panel of their own, drawn the way RuneLite's Configuration panel draws them:
 * sections that open and close, and a checkbox, number box, text box, color button or dropdown per setting, with
 * Reset on a right-click of its name. RuneLite's ConfigPanel, cut down to the setting types these plugins use and to
 * one plugin. It stays open beside the game, so it follows the config: a change made anywhere else, a preset or the
 * Configuration panel, shows up here too.
 *
 * <p>A plugin can name one of its boolean settings as its master switch: it's drawn beside the title, like RuneLite's
 * own on/off switch, instead of as a row. Off quiets the plugin without turning it off, so this panel stays put.
 *
 * <p>A plugin can also add tabs of its own (addTab). Then the title and switch sit over a row of tabs: Settings,
 * which the panel opens on, then the plugin's.
 *
 * <p>And it can split its settings into tabs (addSettingsTab), each holding the sections it names, in place of
 * Settings, under a panel of the plugin's own if it gives one. The settings outside any section, and any section no
 * tab names, go on the first; Reset goes at the end of the last. Each keeps its scrollbar showing, so the rows keep one width from one settings tab to the next.
 *
 * <p>With neither kind of tab there are no tabs, only the settings.
 *
 * <p>Each consts-osrs plugin keeps its own copy, since each one is built alone for the Plugin Hub. The copies differ
 * only in their package line.
 */
class ConfigSidePanel extends PluginPanel
{
    private static final Logger log = LoggerFactory.getLogger(ConfigSidePanel.class);

    private static final int SPINNER_FIELD_WIDTH = 6;
    static final String SETTINGS_TAB = "Settings";
    private static final ImageIcon SECTION_EXPAND_ICON;
    private static final ImageIcon SECTION_RETRACT_ICON;
    private static final ImageIcon SWITCH_ON;
    private static final ImageIcon SWITCH_OFF;

    static
    {
        BufferedImage sectionRetractIcon = ImageUtil.loadImageResource(ConfigSidePanel.class, "/util/arrow_right.png");
        sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
        SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
        final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
        SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);

        // RuneLite's switch, redrawn: its image belongs to the Configuration panel, which a Hub plugin can't use.
        BufferedImage on = new BufferedImage(25, 15, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = on.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(220, 138, 0, 89));
        g.fillRoundRect(3, 3, 18, 9, 9, 9);
        g.setColor(new Color(220, 138, 0));
        g.fillOval(12, 2, 11, 11);
        g.dispose();
        SWITCH_ON = new ImageIcon(on);
        SWITCH_OFF = new ImageIcon(ImageUtil.flipImage(ImageUtil.luminanceScale(ImageUtil.grayscaleImage(on), 0.61f), true, false));
    }

    private final ConfigManager configManager;
    private final ColorPickerManager colorPickerManager;
    private final Config config;
    private final Plugin plugin;
    private final ConfigDescriptor descriptor;
    private final String group;
    private final String switchKey;
    private final JToggleButton masterSwitch;
    /** The title and master switch, and the tabs under them once there are any. */
    private final JPanel header;
    /**
     * Every setting in one scrolling list: the whole panel below the title, or the Settings tab. Out of settingsTabs,
     * and empty, once the plugin adds settings tabs.
     */
    private final SettingsTab settings = new SettingsTab(SETTINGS_TAB, null, List.of(),
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    /** Settings alone, or the plugin's settings tabs in its place, in the order they were added. */
    private final List<SettingsTab> settingsTabs = new ArrayList<>(List.of(settings));
    /** The plugin's own tabs, title to contents, in the order they were added. */
    private final Map<String, JComponent> tabs = new LinkedHashMap<>();
    /** Shows the chosen tab's contents; null until the first tab is added. */
    private JPanel tabDisplay;
    private MaterialTabGroup tabGroup;

    private final TitleCaseListCellRenderer listCellRenderer = new TitleCaseListCellRenderer();
    private final Map<String, Boolean> sectionOpen = new HashMap<>();
    /** One per setting on screen: reads the setting back and shows it, if the screen is behind. */
    private final List<Runnable> loaders = new ArrayList<>();
    private final AtomicBoolean reloadQueued = new AtomicBoolean();
    /** True while the loaders write to the widgets, so their listeners don't save what they were just shown. */
    private boolean loading;

    ConfigSidePanel(ConfigManager configManager, ColorPickerManager colorPickerManager, Config config, Plugin plugin)
    {
        this(configManager, colorPickerManager, config, plugin, null);
    }

    /** switchKey: the boolean setting drawn as the master switch beside the title, or null for none. */
    ConfigSidePanel(ConfigManager configManager, ColorPickerManager colorPickerManager, Config config, Plugin plugin,
        String switchKey)
    {
        super(false);

        this.configManager = configManager;
        this.colorPickerManager = colorPickerManager;
        this.config = config;
        this.plugin = plugin;
        this.descriptor = configManager.getConfigDescriptor(config);
        this.group = descriptor.getGroup().value();
        this.switchKey = switchKey;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        PluginDescriptor about = plugin.getClass().getAnnotation(PluginDescriptor.class);
        String name = about != null ? about.name() : group;
        JLabel title = new JLabel(name);
        title.setForeground(Color.WHITE);
        if (about != null && !about.description().isEmpty())
        {
            title.setToolTipText("<html>" + name + ":<br>" + about.description() + "</html>");
        }
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        topPanel.add(title);
        header = new JPanel(new BorderLayout());
        header.add(topPanel, BorderLayout.NORTH);
        add(header, BorderLayout.NORTH);

        ConfigItemDescriptor switchItem = switchKey == null ? null : descriptor.getItems().stream()
            .filter(i -> switchKey.equals(i.getItem().keyName()) && i.getType() == boolean.class)
            .findFirst()
            .orElse(null);
        if (switchItem != null)
        {
            masterSwitch = new JToggleButton(SWITCH_OFF);
            masterSwitch.setSelectedIcon(SWITCH_ON);
            SwingUtil.removeButtonDecorations(masterSwitch);
            masterSwitch.setPreferredSize(new Dimension(25, 0));
            masterSwitch.setName(switchKey);
            masterSwitch.setToolTipText("<html>" + switchItem.getItem().name() + ":<br>" + switchItem.getItem().description() + "</html>");
            masterSwitch.setSelected(Boolean.parseBoolean(configManager.getConfiguration(group, switchKey)));
            masterSwitch.addItemListener(e ->
            {
                if (!loading)
                {
                    configManager.setConfiguration(group, switchKey, "" + masterSwitch.isSelected());
                }
            });
            topPanel.add(masterSwitch, BorderLayout.EAST);
        }
        else
        {
            masterSwitch = null;
        }

        add(settings.view, BorderLayout.CENTER);

        rebuild();
    }

    /**
     * Adds a tab of the plugin's own. The first one puts the panel in tabs under the title: Settings, or the plugin's
     * settings tabs, then the plugin's own tabs in the order they were added. The panel shows the first.
     */
    void addTab(String title, JComponent content)
    {
        tabs.put(title, content);
        showTabs();
    }

    /**
     * Adds a settings tab holding the sections named, after any added before it: the plugin's settings tabs take the
     * place of Settings. The panel shows the first.
     */
    void addSettingsTab(String title, String... sectionKeys)
    {
        addSettingsTab(title, null, sectionKeys);
    }

    /** As addSettingsTab, with a panel of the plugin's own on top of the tab, such as a live table; null for none. */
    void addSettingsTab(String title, JComponent top, String... sectionKeys)
    {
        if (settingsTabs.remove(settings))
        {
            settings.rows.removeAll();
        }
        settingsTabs.add(new SettingsTab(title, top, List.of(sectionKeys),
            ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS));
        showTabs();
        rebuild();
    }

    /** A tab with 6 px of padding a side instead of RuneLite's 10, so four tabs fit on one row. */
    private MaterialTab tab(String title, JComponent view)
    {
        return new MaterialTab(title, tabGroup, view)
        {
            @Override
            public Insets getInsets(Insets insets)
            {
                Insets padded = super.getInsets(insets);
                padded.left -= 4;
                padded.right -= 4;
                return padded;
            }

            @Override
            public Insets getInsets()
            {
                return getInsets(new Insets(0, 0, 0, 0));
            }
        };
    }

    /** Puts the tabs under the title, the settings tabs then the plugin's own, and shows the first. */
    private void showTabs()
    {
        if (tabGroup == null)
        {
            tabDisplay = new JPanel(new BorderLayout());
            tabDisplay.setBackground(ColorScheme.DARK_GRAY_COLOR);
            remove(settings.view);
            add(tabDisplay, BorderLayout.CENTER);
        }
        else
        {
            header.remove(tabGroup);
        }

        // MaterialTabGroup can't put a tab before another, so it's built again with every tab in its place.
        tabGroup = new MaterialTabGroup(tabDisplay);
        tabGroup.setBorder(new EmptyBorder(0, 0, 6, 0));
        // Tighter than RuneLite's 8 px apart, so four tabs fit on one row.
        tabGroup.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 0));
        for (SettingsTab tab : settingsTabs)
        {
            tabGroup.addTab(tab(tab.title, tab.view));
        }
        for (Map.Entry<String, JComponent> tab : tabs.entrySet())
        {
            tabGroup.addTab(tab(tab.getKey(), tab.getValue()));
        }
        header.add(tabGroup, BorderLayout.SOUTH);
        tabGroup.select(tabGroup.getTab(0));

        revalidate();
        repaint();
    }

    /** Opens or closes a section, and remembers it while the plugin runs. */
    private void toggleSection(ConfigSectionDescriptor csd, JButton button, JPanel contents)
    {
        boolean newState = !contents.isVisible();
        contents.setVisible(newState);
        button.setIcon(newState ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
        button.setToolTipText(newState ? "Retract" : "Expand");
        sectionOpen.put(csd.getKey(), newState);
        SwingUtilities.invokeLater(contents::revalidate);
    }

    void rebuild()
    {
        for (SettingsTab tab : settingsTabs)
        {
            tab.rows.removeAll();
            if (tab.top != null)
            {
                tab.rows.add(tab.top);
            }
        }
        loaders.clear();

        final Map<String, JPanel> sectionWidgets = new HashMap<>();
        final Map<ConfigObject, JPanel> topLevelPanels = new TreeMap<>(Comparator
            .comparingInt(ConfigObject::position)
            .thenComparing(ConfigObject::name));

        for (ConfigSectionDescriptor csd : descriptor.getSections())
        {
            ConfigSection cs = csd.getSection();
            final boolean isOpen = sectionOpen.getOrDefault(csd.getKey(), !cs.closedByDefault());

            final JPanel section = new JPanel();
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));

            final JPanel sectionHeader = new JPanel();
            sectionHeader.setLayout(new BorderLayout());
            sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
            // For whatever reason, the header extends out by a single pixel when closed. Adding a single pixel of
            // border on the right only affects the width when closed, fixing the issue.
            sectionHeader.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(0, 0, 3, 1)));
            section.add(sectionHeader, BorderLayout.NORTH);

            final JButton sectionToggle = new JButton(isOpen ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
            sectionToggle.setPreferredSize(new Dimension(18, 0));
            sectionToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
            sectionToggle.setToolTipText(isOpen ? "Retract" : "Expand");
            SwingUtil.removeButtonDecorations(sectionToggle);
            sectionHeader.add(sectionToggle, BorderLayout.WEST);

            String name = cs.name();
            final JLabel sectionName = new JLabel(name);
            sectionName.setForeground(ColorScheme.BRAND_ORANGE);
            sectionName.setFont(FontManager.getRunescapeBoldFont());
            sectionName.setToolTipText("<html>" + name + ":<br>" + cs.description() + "</html>");
            sectionHeader.add(sectionName, BorderLayout.CENTER);

            final JPanel sectionContents = new JPanel();
            sectionContents.setLayout(new DynamicGridLayout(0, 1, 0, 5));
            sectionContents.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
            sectionContents.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)));
            sectionContents.setVisible(isOpen);
            section.add(sectionContents, BorderLayout.SOUTH);

            // Add listeners to each part of the header so that it's easier to toggle them
            final MouseAdapter adapter = new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    toggleSection(csd, sectionToggle, sectionContents);
                }
            };
            sectionToggle.addActionListener(actionEvent -> toggleSection(csd, sectionToggle, sectionContents));
            sectionName.addMouseListener(adapter);
            sectionHeader.addMouseListener(adapter);

            sectionWidgets.put(csd.getKey(), sectionContents);

            topLevelPanels.put(csd, section);
        }

        for (ConfigItemDescriptor cid : descriptor.getItems())
        {
            if (cid.getItem().hidden() || cid.getItem().keyName().equals(switchKey))
            {
                continue;
            }

            JPanel item = new JPanel();
            item.setName(cid.getItem().keyName());
            item.setLayout(new BorderLayout());
            item.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
            String name = cid.getItem().name();
            JLabel configEntryName = new JLabel(name);
            configEntryName.setForeground(Color.WHITE);
            String description = cid.getItem().description();
            if (!"".equals(description))
            {
                configEntryName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
            }
            configEntryName.setComponentPopupMenu(resetMenu(cid));
            item.add(configEntryName, BorderLayout.CENTER);

            if (cid.getType() == boolean.class)
            {
                item.add(createCheckbox(cid), BorderLayout.EAST);
            }
            else if (cid.getType() == int.class)
            {
                item.add(createIntSpinner(cid), BorderLayout.EAST);
            }
            else if (cid.getType() == double.class)
            {
                item.add(createDoubleSpinner(cid), BorderLayout.EAST);
            }
            else if (cid.getType() == String.class)
            {
                item.add(createTextField(cid), BorderLayout.SOUTH);
            }
            else if (cid.getType() == Color.class)
            {
                item.add(createColorPicker(cid), BorderLayout.EAST);
            }
            else if (cid.getType() instanceof Class && ((Class<?>) cid.getType()).isEnum())
            {
                item.add(createComboBox(cid), BorderLayout.EAST);
            }
            else
            {
                // A kind of setting this panel doesn't draw; it's still in RuneLite's Configuration panel.
                continue;
            }

            JPanel section = sectionWidgets.get(cid.getItem().section());
            if (section == null)
            {
                topLevelPanels.put(cid, item);
            }
            else
            {
                section.add(item);
            }
        }

        // Each section on the tab that names it; the settings outside any section, and any section no tab names, on
        // the first.
        topLevelPanels.forEach((setting, row) -> tabOf(setting).rows.add(row));

        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener((e) ->
        {
            final int result = JOptionPane.showOptionDialog(resetButton, "Are you sure you want to reset this plugin's configuration?",
                "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new String[]{"Yes", "No"}, "No");

            if (result == JOptionPane.YES_OPTION)
            {
                configManager.setDefaultConfiguration(config, true);

                // Reset non-config panel keys
                plugin.resetConfiguration();

                rebuild();
            }
        });
        settingsTabs.get(settingsTabs.size() - 1).rows.add(resetButton);

        revalidate();
        repaint();
    }

    private SettingsTab tabOf(ConfigObject setting)
    {
        if (setting instanceof ConfigSectionDescriptor)
        {
            String key = ((ConfigSectionDescriptor) setting).getKey();
            for (SettingsTab tab : settingsTabs)
            {
                if (tab.sections.contains(key))
                {
                    return tab;
                }
            }
        }
        return settingsTabs.get(0);
    }

    /** Shows every setting as it's saved now, leaving the one being typed into alone. */
    void reload()
    {
        loading = true;
        try
        {
            loaders.forEach(Runnable::run);
            if (masterSwitch != null)
            {
                boolean on = Boolean.parseBoolean(configManager.getConfiguration(group, switchKey));
                if (masterSwitch.isSelected() != on)
                {
                    masterSwitch.setSelected(on);
                }
            }
        }
        finally
        {
            loading = false;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // A preset turns a dozen settings at once: one reload after the last of them.
        if (group.equals(event.getGroup()) && reloadQueued.compareAndSet(false, true))
        {
            SwingUtilities.invokeLater(() ->
            {
                reloadQueued.set(false);
                reload();
            });
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        SwingUtilities.invokeLater(this::rebuild);
    }

    private String saved(ConfigItemDescriptor cid)
    {
        return configManager.getConfiguration(group, cid.getItem().keyName());
    }

    private JCheckBox createCheckbox(ConfigItemDescriptor cid)
    {
        JCheckBox checkbox = new JCheckBox();
        checkbox.setSelected(Boolean.parseBoolean(saved(cid)));
        checkbox.addActionListener(ae -> changeConfiguration(checkbox, cid));
        loaders.add(() ->
        {
            boolean value = Boolean.parseBoolean(saved(cid));
            if (checkbox.isSelected() != value)
            {
                checkbox.setSelected(value);
            }
        });
        return checkbox;
    }

    private int intValue(ConfigItemDescriptor cid)
    {
        Integer saved = configManager.getConfiguration(group, cid.getItem().keyName(), int.class);
        int value = saved != null ? saved : 0;
        Range range = cid.getRange();
        int min = range != null ? range.min() : 0;
        int max = range != null ? range.max() : Integer.MAX_VALUE;
        // Config may previously have been out of range
        return Math.max(min, Math.min(max, value));
    }

    private JSpinner createIntSpinner(ConfigItemDescriptor cid)
    {
        Range range = cid.getRange();
        int min = 0, max = Integer.MAX_VALUE;
        if (range != null)
        {
            min = range.min();
            max = range.max();
        }

        SpinnerNumberModel model = new SpinnerNumberModel(intValue(cid), min, max, 1);
        JSpinner spinner = new JSpinner(model);
        units(spinner, cid);
        spinner.addChangeListener(ce -> changeConfiguration(spinner, cid));
        loaders.add(() ->
        {
            int value = intValue(cid);
            if (!Objects.equals(spinner.getValue(), value))
            {
                spinner.setValue(value);
            }
        });
        return spinner;
    }

    private JSpinner createDoubleSpinner(ConfigItemDescriptor cid)
    {
        Double saved = configManager.getConfiguration(group, cid.getItem().keyName(), double.class);
        SpinnerNumberModel model = new SpinnerNumberModel(saved != null ? saved : 0d, 0, Double.MAX_VALUE, 0.1);
        JSpinner spinner = new JSpinner(model);
        units(spinner, cid);
        spinner.addChangeListener(ce -> changeConfiguration(spinner, cid));
        loaders.add(() ->
        {
            Double value = configManager.getConfiguration(group, cid.getItem().keyName(), double.class);
            double shown = value != null ? value : 0d;
            if (!Objects.equals(spinner.getValue(), shown))
            {
                spinner.setValue(shown);
            }
        });
        return spinner;
    }

    private void units(JSpinner spinner, ConfigItemDescriptor cid)
    {
        JFormattedTextField spinnerTextField = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        spinnerTextField.setColumns(SPINNER_FIELD_WIDTH);

        Units units = cid.getUnits();
        if (units != null)
        {
            // The existing DefaultFormatterFactory with a NumberEditorFormatter. Its model is the same SpinnerModel above.
            JFormattedTextField.AbstractFormatterFactory delegate = spinnerTextField.getFormatterFactory();
            spinnerTextField.setFormatterFactory(new UnitFormatterFactory(delegate, units.value()));
        }
    }

    private JTextComponent createTextField(ConfigItemDescriptor cid)
    {
        JTextComponent textField;

        if (cid.getItem().secret())
        {
            textField = new JPasswordField();
        }
        else
        {
            final JTextArea textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textField = textArea;
        }

        textField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        textField.setText(saved(cid));

        textField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                changeConfiguration(textField, cid);
            }
        });
        loaders.add(() ->
        {
            String value = saved(cid);
            if (!textField.isFocusOwner() && !Objects.equals(textField.getText(), value == null ? "" : value))
            {
                textField.setText(value);
            }
        });

        return textField;
    }

    private static String hex(Color color, boolean alphaHidden)
    {
        return "#" + (alphaHidden ? ColorUtil.colorToHexCode(color) : ColorUtil.colorToAlphaHexCode(color)).toUpperCase();
    }

    private ColorJButton createColorPicker(ConfigItemDescriptor cid)
    {
        Color existing = configManager.getConfiguration(group, cid.getItem().keyName(), Color.class);

        ColorJButton colorPickerBtn;

        boolean alphaHidden = cid.getAlpha() == null;

        if (existing == null)
        {
            colorPickerBtn = new ColorJButton("Pick a color", Color.BLACK);
        }
        else
        {
            colorPickerBtn = new ColorJButton(hex(existing, alphaHidden), existing);
        }

        colorPickerBtn.setFocusable(false);
        colorPickerBtn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                RuneliteColorPicker colorPicker = colorPickerManager.create(
                    ConfigSidePanel.this,
                    colorPickerBtn.getColor(),
                    cid.getItem().name(),
                    alphaHidden);
                colorPicker.setLocationRelativeTo(colorPickerBtn);
                colorPicker.setOnColorChange(c ->
                {
                    colorPickerBtn.setColor(c);
                    colorPickerBtn.setText(hex(c, alphaHidden));
                });
                colorPicker.setOnClose(c -> changeConfiguration(colorPicker, cid));
                colorPicker.setVisible(true);
            }
        });
        loaders.add(() ->
        {
            Color value = configManager.getConfiguration(group, cid.getItem().keyName(), Color.class);
            if (value != null && !value.equals(colorPickerBtn.getColor()))
            {
                colorPickerBtn.setColor(value);
                colorPickerBtn.setText(hex(value, alphaHidden));
            }
        });

        return colorPickerBtn;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> enumValue(Class<? extends Enum> type, String saved)
    {
        try
        {
            return saved == null ? null : Enum.valueOf(type, saved);
        }
        catch (IllegalArgumentException ex)
        {
            log.debug("invalid selected item", ex);
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JComboBox<Enum<?>> createComboBox(ConfigItemDescriptor cid)
    {
        Class<? extends Enum> type = (Class<? extends Enum>) cid.getType();

        JComboBox<Enum<?>> box = new JComboBox<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
        // set renderer prior to calling box.getPreferredSize(), since it will invoke the renderer
        // to build components for each combobox element in order to compute the display size of the
        // combobox
        box.setRenderer(listCellRenderer);
        box.setPreferredSize(new Dimension(box.getPreferredSize().width, 22));

        Enum<?> selectedItem = enumValue(type, saved(cid));
        if (selectedItem != null)
        {
            box.setSelectedItem(selectedItem);
            box.setToolTipText(Text.titleCase(selectedItem));
        }
        box.addItemListener(e ->
        {
            if (e.getStateChange() == ItemEvent.SELECTED)
            {
                changeConfiguration(box, cid);
                box.setToolTipText(Text.titleCase((Enum<?>) box.getSelectedItem()));
            }
        });
        loaders.add(() ->
        {
            Enum<?> value = enumValue(type, saved(cid));
            if (value != null && value != box.getSelectedItem())
            {
                box.setSelectedItem(value);
                box.setToolTipText(Text.titleCase(value));
            }
        });

        return box;
    }

    private void changeConfiguration(Component component, ConfigItemDescriptor cid)
    {
        if (loading)
        {
            return;
        }

        final ConfigItem configItem = cid.getItem();

        if (configItem.warning() != null && !configItem.warning().isEmpty())
        {
            final int result = JOptionPane.showOptionDialog(component, configItem.warning(),
                "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new String[]{"Yes", "No"}, "No");

            if (result != JOptionPane.YES_OPTION)
            {
                reload();
                return;
            }
        }

        if (component instanceof JCheckBox)
        {
            JCheckBox checkbox = (JCheckBox) component;
            configManager.setConfiguration(group, configItem.keyName(), "" + checkbox.isSelected());
        }
        else if (component instanceof JSpinner)
        {
            JSpinner spinner = (JSpinner) component;
            configManager.setConfiguration(group, configItem.keyName(), "" + spinner.getValue());
        }
        else if (component instanceof JTextComponent)
        {
            JTextComponent textField = (JTextComponent) component;
            configManager.setConfiguration(group, configItem.keyName(), textField.getText());
        }
        else if (component instanceof RuneliteColorPicker)
        {
            RuneliteColorPicker colorPicker = (RuneliteColorPicker) component;
            configManager.setConfiguration(group, configItem.keyName(), colorPicker.getSelectedColor().getRGB() + "");
        }
        else if (component instanceof JComboBox)
        {
            JComboBox<?> jComboBox = (JComboBox<?>) component;
            configManager.setConfiguration(group, configItem.keyName(), ((Enum<?>) jComboBox.getSelectedItem()).name());
        }
    }

    /** Right-click a setting's name: Reset puts that one setting back to its default. */
    private JPopupMenu resetMenu(ConfigItemDescriptor cid)
    {
        JMenuItem menuItem = new JMenuItem("Reset");
        menuItem.addActionListener(e ->
        {
            // To reset one item we'll just unset it and then apply defaults over the whole group
            configManager.unsetConfiguration(group, cid.getItem().keyName());
            configManager.setDefaultConfiguration(config, false);

            reload();
        });
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem);
        return menu;
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(PANEL_WIDTH + SCROLLBAR_WIDTH, super.getPreferredSize().height);
    }

    /** A panel as wide as the sidebar, however long its rows. */
    private static class FixedWidthPanel extends JPanel
    {
        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
        }
    }

    /**
     * Settings, scrolling, on a tab of their own: the plugin's top panel, if any, then the sections named. scrollbar:
     * when the scrollbar shows.
     */
    private static class SettingsTab
    {
        final String title;
        final JComponent top;
        final List<String> sections;
        /** The settings and sections, one under another. */
        final JPanel rows = new FixedWidthPanel();
        final JScrollPane view;

        SettingsTab(String title, JComponent top, List<String> sections, int scrollbar)
        {
            this.title = title;
            this.top = top;
            this.sections = sections;
            rows.setBorder(new EmptyBorder(8, 10, 10, 10));
            rows.setLayout(new DynamicGridLayout(0, 1, 0, 5));
            rows.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel northPanel = new FixedWidthPanel();
            northPanel.setLayout(new BorderLayout());
            northPanel.add(rows, BorderLayout.NORTH);

            view = new JScrollPane(northPanel, scrollbar, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        }
    }
}
