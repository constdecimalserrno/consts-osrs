package com.constsafk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import org.junit.Before;
import org.junit.Test;

public class ConfigSidePanelTest
{
    private static final String GROUP = ConstsAfkConfig.GROUP;

    private final Map<String, String> saved = new HashMap<>();
    private ConfigManager configManager;
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
        ConstsAfkConfig config = (ConstsAfkConfig) Proxy.newProxyInstance(ConstsAfkConfig.class.getClassLoader(),
            new Class<?>[]{ConstsAfkConfig.class}, (proxy, method, args) -> null);
        descriptor = configManager.getConfigDescriptor(config);
        panel = new ConfigSidePanel(configManager, mock(ColorPickerManager.class), config, new ConstsAfkPlugin());
    }

    @Test
    public void itDrawsEverySettingYouCanSeeAndEverySection()
    {
        List<String> labels = labels(panel);
        assertTrue(labels.contains("consts-AFK"));
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
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(GROUP);
        changed.setKey(flag.getItem().keyName());
        panel.onConfigChanged(changed);
        SwingUtilities.invokeAndWait(() ->
        {
        });

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
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup("someotherplugin");
        changed.setKey(flag.getItem().keyName());
        panel.onConfigChanged(changed);
        SwingUtilities.invokeAndWait(() ->
        {
        });
        assertFalse(box.isSelected());
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

    /** The control on a setting's row, the row named for its key: several settings share a name. */
    private <T> T control(ConfigItemDescriptor item, Class<T> type)
    {
        JPanel row = find(panel, JPanel.class, p -> item.getItem().keyName().equals(p.getName()));
        for (Component c : row.getComponents())
        {
            if (!(c instanceof JLabel) && type.isInstance(c))
            {
                return type.cast(c);
            }
        }
        throw new AssertionError("no " + type.getSimpleName() + " for " + item.getItem().name());
    }

    private static <T> T find(Container root, Class<T> type, java.util.function.Predicate<T> test)
    {
        for (Component c : root.getComponents())
        {
            if (type.isInstance(c) && test.test(type.cast(c)))
            {
                return type.cast(c);
            }
            if (c instanceof Container)
            {
                T found = find((Container) c, type, test);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<String> labels(Container root)
    {
        List<String> out = new ArrayList<>();
        for (Component c : root.getComponents())
        {
            if (c instanceof JLabel)
            {
                out.add(((JLabel) c).getText());
            }
            if (c instanceof Container)
            {
                out.addAll(labels((Container) c));
            }
        }
        return out;
    }
}
