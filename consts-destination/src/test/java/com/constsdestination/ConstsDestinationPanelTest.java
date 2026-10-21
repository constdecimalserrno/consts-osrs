package com.constsdestination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.constsdestination.tilepacks.SavedConfig;
import com.constsdestination.tilepacks.TilePacksPanelTest;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

public class ConstsDestinationPanelTest
{
    private ConfigSidePanel panel;
    private MaterialTabGroup tabs;

    @Before
    public void setUp()
    {
        SavedConfig saved = new SavedConfig();
        when(saved.configManager.getConfigDescriptor(any())).thenCallRealMethod();
        saved.put(ConstsDestinationConfig.GROUP, ConstsDestinationConfig.MASTER_KEY, "true");
        ConstsDestinationConfig config = (ConstsDestinationConfig) Proxy.newProxyInstance(
            ConstsDestinationConfig.class.getClassLoader(), new Class<?>[]{ConstsDestinationConfig.class},
            (proxy, method, args) -> null);

        panel = ConstsDestinationPlugin.sidePanel(saved.configManager, mock(ColorPickerManager.class), config,
            new ConstsDestinationPlugin(), TilePacksPanelTest.newPanel(RuneLiteAPI.GSON, saved.configManager).getWrappedPanel());
        tabs = find(panel, MaterialTabGroup.class);
    }

    @Test
    public void theTitleAndMasterSwitchSitOverTheTilePacksAndTheSettingsTabs()
    {
        assertTrue(labels(panel).contains("consts-Destination"));
        JToggleButton masterSwitch = find(panel, JToggleButton.class);
        assertNotNull(masterSwitch);
        assertTrue(masterSwitch.isSelected());

        assertNotNull(tabs);
        assertEquals("Tile packs", tabs.getTab(0).getText());
        assertEquals("Settings", tabs.getTab(1).getText());
    }

    @Test
    public void itOpensOnTheTilePacks()
    {
        assertTrue(labels(panel).contains("Abyssal Sire"));
        assertFalse(labels(panel).contains("Hover Color"));
    }

    @Test
    public void theSettingsTabHasTheTilePacksSettingsToo()
    {
        tabs.select(tabs.getTab(1));

        List<String> labels = labels(panel);
        assertFalse(labels.contains("Abyssal Sire"));
        assertTrue(labels.contains("Hover Color"));
        assertTrue(labels.contains("Tile packs"));
        assertTrue(labels.contains("Show Labels"));
        assertTrue(labels.contains("Draw Tiles on Minimap"));
    }

    @Test
    public void theClickTicksComeFirst()
    {
        tabs.select(tabs.getTab(1));

        List<String> labels = labels(panel);
        assertTrue(labels.contains("Ground Item Pickup"));
        assertTrue(labels.contains("Minigame Focus"));
        assertTrue(labels.indexOf("Clicks") >= 0 && labels.indexOf("Clicks") < labels.indexOf("Colors"));
    }

    private static <T> T find(Container root, Class<T> type)
    {
        for (Component c : root.getComponents())
        {
            if (type.isInstance(c))
            {
                return type.cast(c);
            }
            if (c instanceof Container)
            {
                T found = find((Container) c, type);
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
