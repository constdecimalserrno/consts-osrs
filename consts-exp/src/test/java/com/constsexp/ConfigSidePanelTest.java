package com.constsexp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import org.junit.Before;
import org.junit.Test;

public class ConfigSidePanelTest
{
    private static final String GROUP = ConstsExpConfig.GROUP;

    private final Map<String, String> saved = new HashMap<>();
    private ConfigManager configManager;
    private ConstsExpConfig config;
    private ConfigDescriptor descriptor;
    private ConfigSidePanel panel;

    @Before
    public void setUp()
    {
        configManager = mock(ConfigManager.class);
        when(configManager.getConfigDescriptor(any())).thenCallRealMethod();
        when(configManager.getConfiguration(eq(GROUP), anyString())).thenAnswer(i -> saved.get(i.getArgument(1)));
        when(configManager.getConfiguration(eq(GROUP), anyString(), eq(int.class))).thenAnswer(i ->
        {
            String value = saved.get(i.getArgument(1));
            return value == null ? null : Integer.valueOf(value);
        });
        config = (ConstsExpConfig) Proxy.newProxyInstance(ConstsExpConfig.class.getClassLoader(),
            new Class<?>[]{ConstsExpConfig.class}, (proxy, method, args) -> null);
        descriptor = configManager.getConfigDescriptor(config);
        panel = new ConfigSidePanel(configManager, mock(ColorPickerManager.class), config, new ConstsExpPlugin());
    }

    @Test
    public void itDrawsEverySettingYouCanSeeAndEverySection()
    {
        List<String> labels = labels(panel);
        assertTrue(labels.contains("consts-EXP"));
        for (ConfigSectionDescriptor section : descriptor.getSections())
        {
            assertTrue(section.name(), labels.contains(section.name()));
        }
        int shown = 0;
        for (ConfigItemDescriptor item : descriptor.getItems())
        {
            String name = item.getItem().name();
            if (item.getItem().hidden())
            {
                assertFalse(name, labels.contains(name));
            }
            else
            {
                assertTrue(name, labels.contains(name));
                shown++;
            }
        }
        assertTrue(shown > 50);
    }

    @Test
    public void aCheckboxSavesWhenYouClickIt()
    {
        ConfigItemDescriptor item = first(boolean.class);
        JCheckBox box = control(item, JCheckBox.class);
        box.doClick();
        verify(configManager).setConfiguration(GROUP, item.getItem().keyName(), "true");
    }

    @Test
    public void aChangeMadeElsewhereShowsUpWithoutBeingSavedBack() throws Exception
    {
        ConfigItemDescriptor flag = first(boolean.class);
        ConfigItemDescriptor number = first(int.class);
        ConfigItemDescriptor preset = first(Preset.class);
        JCheckBox box = control(flag, JCheckBox.class);
        JSpinner spinner = control(number, JSpinner.class);
        JComboBox<?> presets = control(preset, JComboBox.class);
        int min = number.getRange() != null ? number.getRange().min() : 0;

        saved.put(flag.getItem().keyName(), "true");
        saved.put(number.getItem().keyName(), String.valueOf(min + 1));
        saved.put(preset.getItem().keyName(), Preset.MINING.name());
        changedElsewhere(panel, GROUP, flag.getItem().keyName());

        assertTrue(box.isSelected());
        assertEquals(min + 1, spinner.getValue());
        assertEquals(Preset.MINING, presets.getSelectedItem());
        verify(configManager, never()).setConfiguration(anyString(), anyString(), anyString());
    }

    @Test
    public void anotherPluginsChangesAreIgnored() throws Exception
    {
        ConfigItemDescriptor flag = first(boolean.class);
        JCheckBox box = control(flag, JCheckBox.class);
        saved.put(flag.getItem().keyName(), "true");
        changedElsewhere(panel, "someotherplugin", flag.getItem().keyName());
        assertFalse(box.isSelected());
    }

    @Test
    public void theMasterSwitchSitsBesideTheTitleInsteadOfInTheList()
    {
        saved.put(ConstsExpConfig.MASTER_KEY, "true");
        ConfigSidePanel withSwitch = withMasterSwitch();

        JToggleButton toggle = find(withSwitch, JToggleButton.class, b -> ConstsExpConfig.MASTER_KEY.equals(b.getName()));
        assertNotNull(toggle);
        assertTrue(toggle.isSelected());
        assertNull(find(withSwitch, JPanel.class, p -> ConstsExpConfig.MASTER_KEY.equals(p.getName())));
        assertFalse(labels(withSwitch).contains("Master Switch"));
        // Without one, it's an ordinary row.
        assertTrue(labels(panel).contains("Master Switch"));
    }

    @Test
    public void theMasterSwitchSavesWhenClickedAndFollowsTheConfig()
    {
        saved.put(ConstsExpConfig.MASTER_KEY, "true");
        ConfigSidePanel withSwitch = withMasterSwitch();
        JToggleButton toggle = find(withSwitch, JToggleButton.class, b -> ConstsExpConfig.MASTER_KEY.equals(b.getName()));

        toggle.doClick();
        verify(configManager).setConfiguration(GROUP, ConstsExpConfig.MASTER_KEY, "false");

        saved.put(ConstsExpConfig.MASTER_KEY, "true");
        withSwitch.reload();
        assertTrue(toggle.isSelected());
        verify(configManager, never()).setConfiguration(GROUP, ConstsExpConfig.MASTER_KEY, "true");
    }

    private ConfigSidePanel expPanel()
    {
        return ConstsExpPlugin.sidePanel(configManager, mock(ColorPickerManager.class), config, new ConstsExpPlugin());
    }

    private ConfigSidePanel withMasterSwitch()
    {
        return new ConfigSidePanel(configManager, mock(ColorPickerManager.class), config, new ConstsExpPlugin(),
            ConstsExpConfig.MASTER_KEY);
    }

    @Test
    public void withoutATabOfItsOwnThereAreNoTabs()
    {
        assertNull(find(panel, MaterialTabGroup.class, g -> true));
        assertFalse(labels(panel).contains(ConfigSidePanel.SETTINGS_TAB));
    }

    @Test
    public void theSettingsComeFirstThenItsOwnTabs()
    {
        saved.put(ConstsExpConfig.MASTER_KEY, "true");
        ConfigSidePanel tabbed = withMasterSwitch();
        tabbed.addTab("Packs", new JLabel("pack list"));
        tabbed.addTab("More", new JLabel("more things"));

        MaterialTabGroup tabs = tabs(tabbed);
        assertEquals(ConfigSidePanel.SETTINGS_TAB, tabs.getTab(0).getText());
        assertEquals("Packs", tabs.getTab(1).getText());
        assertEquals("More", tabs.getTab(2).getText());
        assertEquals(1, count(tabbed, MaterialTabGroup.class));

        // The title and the master switch stay on top of the tabs.
        assertTrue(labels(tabbed).contains("consts-EXP"));
        assertNotNull(find(tabbed, JToggleButton.class, b -> ConstsExpConfig.MASTER_KEY.equals(b.getName())));
    }

    @Test
    public void itOpensOnTheSettingsAndItsOwnTabShowsItsOwn()
    {
        ConfigItemDescriptor flag = first(boolean.class);
        panel.addTab("Packs", new JLabel("pack list"));
        MaterialTabGroup tabs = tabs(panel);

        assertFalse(labels(panel).contains("pack list"));
        assertNotNull(control(flag, JCheckBox.class));

        tabs.select(tabs.getTab(1));
        assertTrue(labels(panel).contains("pack list"));
        assertNull(find(panel, JPanel.class, p -> flag.getItem().keyName().equals(p.getName())));

        tabs.select(tabs.getTab(0));
        assertFalse(labels(panel).contains("pack list"));
        assertNotNull(control(flag, JCheckBox.class));
    }

    @Test
    public void withoutSettingsTabsTheSettingsAreOneListAsBefore()
    {
        // One scrolling list under the title, its scrollbar there only when it's needed, with every section, then Reset.
        assertEquals(1, count(panel, JScrollPane.class));
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            find(panel, JScrollPane.class, v -> true).getVerticalScrollBarPolicy());
        List<String> all = sections(panel);
        assertEquals(descriptor.getSections().size(), all.size());
        assertNotNull(find(panel, JButton.class, b -> "Reset".equals(b.getText())));

        // A tab of its own puts that same list on Settings, first.
        panel.addTab("Packs", new JLabel("pack list"));
        MaterialTabGroup tabs = tabs(panel);
        assertEquals(2, count(panel, MaterialTab.class));
        assertEquals(ConfigSidePanel.SETTINGS_TAB, tabs.getTab(0).getText());
        assertEquals(all, sections(tabs.getTab(0).getContent()));
    }

    @Test
    public void settingsTabsHoldOnlyTheirOwnSectionsAndItOpensOnTheFirst()
    {
        ConfigSidePanel tabbed = withMasterSwitch();
        tabbed.addSettingsTab("One", ConstsExpConfig.healthSection, ConstsExpConfig.prayerSection);
        tabbed.addSettingsTab("Two", ConstsExpConfig.soundsSection);
        tabbed.addTab("Packs", new JLabel("pack list"));

        // The settings tabs in place of Settings, then its own.
        MaterialTabGroup tabs = tabs(tabbed);
        assertEquals(3, count(tabbed, MaterialTab.class));
        assertEquals("One", tabs.getTab(0).getText());
        assertEquals("Two", tabs.getTab(1).getText());
        assertEquals("Packs", tabs.getTab(2).getText());

        // Two holds Sounds alone, then Reset, at the end of the last settings tab.
        assertEquals(List.of(sectionName(ConstsExpConfig.soundsSection)), sections(tabs.getTab(1).getContent()));
        assertNotNull(find(tabs.getTab(1).getContent(), JButton.class, b -> "Reset".equals(b.getText())));
        assertNull(find(tabs.getTab(0).getContent(), JButton.class, b -> "Reset".equals(b.getText())));

        // One holds Preset on top, then Health, Prayer and every section no tab names.
        assertEquals("Preset", labels(tabs.getTab(0).getContent()).get(0));
        List<String> one = sections(tabs.getTab(0).getContent());
        assertTrue(one.contains(sectionName(ConstsExpConfig.healthSection)));
        assertTrue(one.contains(sectionName(ConstsExpConfig.prayerSection)));
        assertFalse(one.contains(sectionName(ConstsExpConfig.soundsSection)));
        assertEquals(descriptor.getSections().size() - 1, one.size());

        // It opens on One.
        assertTrue(sections(tabbed).contains(sectionName(ConstsExpConfig.healthSection)));
        assertFalse(sections(tabbed).contains(sectionName(ConstsExpConfig.soundsSection)));
    }

    @Test
    public void settingsTabsGiveTheirRowsTheSameWidthWhetherTheyScrollOrNot()
    {
        ConfigSidePanel tabbed = withMasterSwitch();
        // One holds everything but Sounds, too long for the panel; Two holds Sounds alone, which fits.
        tabbed.addSettingsTab("One", ConstsExpConfig.healthSection);
        tabbed.addSettingsTab("Two", ConstsExpConfig.soundsSection);
        MaterialTabGroup tabs = tabs(tabbed);

        JViewport one = laidOut(tabbed, tabs, 0);
        int oneRow = find(one, JPanel.class, p -> ConstsExpConfig.PRESET_KEY.equals(p.getName())).getWidth();
        JViewport two = laidOut(tabbed, tabs, 1);
        int twoRow = find(two, JPanel.class, p -> ConstsExpConfig.SOUND_VOLUME_KEY.equals(p.getName())).getWidth();

        assertTrue(one.getView().getPreferredSize().height > one.getHeight());
        assertTrue(two.getView().getPreferredSize().height <= two.getHeight());
        assertEquals(one.getWidth(), two.getWidth());
        assertTrue(oneRow > 0);
        assertEquals(oneRow, twoRow);
    }

    @Test
    public void aChangeShowsUpOnTheTabThatHoldsItAndARebuildKeepsEachTabsOwn() throws Exception
    {
        ConfigSidePanel tabbed = withMasterSwitch();
        tabbed.addSettingsTab("One", ConstsExpConfig.healthSection);
        tabbed.addSettingsTab("Two", ConstsExpConfig.soundsSection);
        MaterialTabGroup tabs = tabs(tabbed);
        ConfigItemDescriptor volume = item(ConstsExpConfig.SOUND_VOLUME_KEY);

        // Changed elsewhere while One shows: Two's control follows.
        saved.put(ConstsExpConfig.SOUND_VOLUME_KEY, "42");
        changedElsewhere(tabbed, GROUP, ConstsExpConfig.SOUND_VOLUME_KEY);
        assertEquals(42, control(tabs.getTab(1).getContent(), volume, JSpinner.class).getValue());

        // A rebuild, as a profile change makes, fills each tab again with its own, and stays on the one showing.
        tabs.select(tabs.getTab(1));
        tabbed.rebuild();
        assertEquals(List.of(sectionName(ConstsExpConfig.soundsSection)), sections(tabs.getTab(1).getContent()));
        assertTrue(sections(tabs.getTab(0).getContent()).contains(sectionName(ConstsExpConfig.healthSection)));
        assertFalse(sections(tabs.getTab(0).getContent()).contains(sectionName(ConstsExpConfig.soundsSection)));
        assertEquals(42, control(tabbed, volume, JSpinner.class).getValue());
    }

    @Test
    public void constsExpsPanelOpensOnAlertsThenSkillsTickAndXpAndGeneral()
    {
        ConfigSidePanel exp = expPanel();

        MaterialTabGroup tabs = tabs(exp);
        assertEquals(4, count(exp, MaterialTab.class));
        assertEquals("Alerts", tabs.getTab(0).getText());
        assertEquals("Skills", tabs.getTab(1).getText());
        assertEquals("Tick & XP", tabs.getTab(2).getText());
        assertEquals("General", tabs.getTab(3).getText());

        assertEquals("Preset", labels(tabs.getTab(0).getContent()).get(0));
        assertEquals(List.of("Health", "Prayer", "Aggro", "Potion", "Cannon", "Idle", "Logout", "Inventory Full",
            "Random Event"), sections(tabs.getTab(0).getContent()));
        assertEquals(List.of("Woodcutting", "Sailing: Salvage", "Sailing: Cargo Hold", "Runecraft"),
            sections(tabs.getTab(1).getContent()));
        assertEquals(List.of("Tick Pendulum"), sections(tabs.getTab(2).getContent()));
        assertEquals(List.of("Timing", "Sounds", "Screen Flash"), sections(tabs.getTab(3).getContent()));

        assertTrue(labels(exp).contains("Preset"));
        assertFalse(sections(exp).contains("Timing"));
    }

    @Test
    public void constsExpsAlertsAndSkillsStartFoldedAndTickAndGeneralOpen()
    {
        MaterialTabGroup tabs = tabs(expPanel());
        List<String> alerts = rows(tabs.getTab(0).getContent(), true);
        List<String> skills = rows(tabs.getTab(1).getContent(), true);
        List<String> tick = rows(tabs.getTab(2).getContent(), true);
        List<String> general = rows(tabs.getTab(3).getContent(), true);
        assertTrue(alerts.size() > 1);
        assertFalse(skills.isEmpty());
        assertEquals(List.of("tickPendulum", "pendulumRhythm", "yourPing"), tick);
        assertFalse(general.isEmpty());

        // Every alert's and skill's setting sits under a folded header, Preset aside; every Tick & XP and General
        // setting shows.
        assertEquals(List.of(ConstsExpConfig.PRESET_KEY), rows(tabs.getTab(0).getContent(), false));
        assertEquals(List.of(), rows(tabs.getTab(1).getContent(), false));
        assertEquals(tick, rows(tabs.getTab(2).getContent(), false));
        assertEquals(general, rows(tabs.getTab(3).getContent(), false));
    }

    @Test
    public void colorsAndChoicesGetTheirOwnControls()
    {
        assertNotNull(control(first(Color.class), Component.class));
        assertNotNull(control(first(Preset.class), JComboBox.class));
    }

    private ConfigItemDescriptor first(Class<?> type)
    {
        return descriptor.getItems().stream()
            .filter(i -> i.getType() == type && !i.getItem().hidden())
            .findFirst()
            .orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " setting"));
    }

    private ConfigItemDescriptor item(String key)
    {
        return descriptor.getItems().stream()
            .filter(i -> i.getItem().keyName().equals(key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no setting " + key));
    }

    private String sectionName(String key)
    {
        return descriptor.getSections().stream()
            .filter(s -> s.getKey().equals(key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no section " + key))
            .name();
    }

    /** A tab's viewport, once the panel shows that tab, laid out at the sidebar's width and 400 px tall. */
    private static JViewport laidOut(ConfigSidePanel panel, MaterialTabGroup tabs, int tab)
    {
        tabs.select(tabs.getTab(tab));
        layOut(panel, PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH, 400);
        return ((JScrollPane) tabs.getTab(tab).getContent()).getViewport();
    }

    /** Lays root out at a size, as a window would, all but what's folded away. */
    private static void layOut(Container root, int width, int height)
    {
        root.setSize(width, height);
        root.doLayout();
        for (Component c : all(root, true))
        {
            if (c instanceof Container)
            {
                ((Container) c).doLayout();
            }
        }
    }

    /** A setting changed somewhere else, as the event bus tells the panel, and the panel caught up. */
    private static void changedElsewhere(ConfigSidePanel panel, String group, String key) throws Exception
    {
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(group);
        changed.setKey(key);
        panel.onConfigChanged(changed);
        SwingUtilities.invokeAndWait(() ->
        {
        });
    }

    private static MaterialTabGroup tabs(Container root)
    {
        MaterialTabGroup tabs = find(root, MaterialTabGroup.class, g -> true);
        assertNotNull("no tabs", tabs);
        return tabs;
    }

    private <T> T control(ConfigItemDescriptor item, Class<T> type)
    {
        return control(panel, item, type);
    }

    /** The control on a setting's row, the row named for its key: several settings share a name. */
    private static <T> T control(Container root, ConfigItemDescriptor item, Class<T> type)
    {
        JPanel row = find(root, JPanel.class, p -> item.getItem().keyName().equals(p.getName()));
        for (Component c : row.getComponents())
        {
            if (!(c instanceof JLabel) && type.isInstance(c))
            {
                return type.cast(c);
            }
        }
        throw new AssertionError("no " + type.getSimpleName() + " for " + item.getItem().name());
    }

    /** Every component under root, depth first; with shownOnly, none that is hidden or inside something hidden. */
    private static List<Component> all(Container root, boolean shownOnly)
    {
        List<Component> out = new ArrayList<>();
        for (Component c : root.getComponents())
        {
            if (shownOnly && !c.isVisible())
            {
                continue;
            }
            out.add(c);
            if (c instanceof Container)
            {
                out.addAll(all((Container) c, shownOnly));
            }
        }
        return out;
    }

    private static <T> T find(Container root, Class<T> type, Predicate<T> test)
    {
        return all(root, false).stream().filter(type::isInstance).map(type::cast).filter(test).findFirst().orElse(null);
    }

    private static int count(Container root, Class<?> type)
    {
        return (int) all(root, false).stream().filter(type::isInstance).count();
    }

    private static List<String> labels(Container root)
    {
        return all(root, false).stream()
            .filter(JLabel.class::isInstance)
            .map(c -> ((JLabel) c).getText())
            .collect(Collectors.toList());
    }

    /** The section headers under root, top to bottom: the names drawn in orange, as a setting's never is. */
    private static List<String> sections(Container root)
    {
        return all(root, false).stream()
            .filter(c -> c instanceof JLabel && ColorScheme.BRAND_ORANGE.equals(c.getForeground()))
            .map(c -> ((JLabel) c).getText())
            .collect(Collectors.toList());
    }

    /** The settings' rows under root, by key, top to bottom: every one, or only those outside a folded section. */
    private static List<String> rows(Container root, boolean foldedToo)
    {
        return all(root, !foldedToo).stream()
            .filter(c -> c instanceof JPanel && c.getName() != null)
            .map(Component::getName)
            .collect(Collectors.toList());
    }
}
