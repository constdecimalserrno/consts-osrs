package com.constsdestination.tilepacks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.constsdestination.tilepacks.ui.panel.TilePacksListPanel;
import com.constsdestination.tilepacks.ui.panel.custom.CustomPackManagerPanel;
import com.constsdestination.tilepacks.ui.panel.pack.TilePackPanel;
import com.constsdestination.tilepacks.ui.panel.pack.actions.DeleteCustomPackLabel;
import com.constsdestination.tilepacks.ui.panel.pack.actions.EditCustomPackLabel;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Before;
import org.junit.Test;

public class TilePacksPanelTest extends TilePacksTest
{
    private SavedConfig saved;
    private TilePackManager manager;
    private FilterManager filters;
    private TilePacksListPanel panel;

    /** The Tile packs tab as consts-Destination builds it, for tests in other packages. */
    public static TilePacksListPanel newPanel(Gson gson, ConfigManager configManager)
    {
        return new TilePacksListPanel(new TilePackManager(gson, configManager), () ->
        {
        }, new FilterManager(configManager), mock(ChatMessageManager.class), gson);
    }

    @Before
    public void setUp()
    {
        saved = new SavedConfig();
        manager = new TilePackManager(gson, saved.configManager);
        filters = new FilterManager(saved.configManager);
        panel = new TilePacksListPanel(manager, () ->
        {
        }, filters, mock(ChatMessageManager.class), gson);
    }

    @Test
    public void everyPackHasARow()
    {
        assertEquals(manager.getTilePacks().size(), rows().size());
        assertTrue(labels(panel).contains("Abyssal Sire"));
    }

    @Test
    public void theSearchNarrowsTheList() throws Exception
    {
        search("zulrah");

        List<TilePackPanel> rows = rows();
        assertFalse(rows.isEmpty());
        for (TilePackPanel row : rows)
        {
            assertTrue(labels(row).stream().anyMatch(l -> l != null && l.toLowerCase().contains("zulrah")));
        }
    }

    @Test
    public void aHiddenPackShowsOnlyWithTheHiddenFilter()
    {
        manager.getTilePacks().get(1).visible = false;
        manager.updatePack(manager.getTilePacks().get(1));
        panel.createTilePackPanels();
        assertFalse(labels(panel).contains("Abyssal Sire"));

        filters.setShowInvisible(true);
        panel.createTilePackPanels();
        assertTrue(labels(panel).contains("Abyssal Sire"));

        filters.setShowVisible(false);
        panel.createTilePackPanels();
        assertEquals(1, rows().size());
    }

    @Test
    public void aCustomPackCanBeEditedAndDeletedAndABuiltInOneCant()
    {
        manager.addCustomPack("My pack", "[{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0}]");
        panel.createTilePackPanels();

        TilePackPanel custom = row("My pack");
        assertNotNull(find(custom, EditCustomPackLabel.class));
        assertNotNull(find(custom, DeleteCustomPackLabel.class));
        TilePackPanel builtIn = row("Abyssal Sire");
        assertNull(find(builtIn, EditCustomPackLabel.class));
        assertNull(find(builtIn, DeleteCustomPackLabel.class));
    }

    @Test
    public void aCustomPackIsCheckedBeforeItsSaved()
    {
        String tiles = "[{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0,\"color\":\"#FFFFFF00\"}]";
        assertEquals("Must add a pack name", CustomPackManagerPanel.checkPack(gson, "", tiles));
        assertEquals("Must add tiles", CustomPackManagerPanel.checkPack(gson, "Name", ""));
        assertEquals("Error parsing tiles, check the formatting", CustomPackManagerPanel.checkPack(gson, "Name", "not tiles"));
        assertEquals("Error parsing tiles, check the formatting", CustomPackManagerPanel.checkPack(gson, "Name", "[]"));
        assertNull(CustomPackManagerPanel.checkPack(gson, "Name", tiles));
    }

    @Test
    public void reloadShowsAnotherProfilesPacks()
    {
        saved.put(TilePackManager.CONFIG_GROUP, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"Theirs\",\"packTiles\":\"[]\",\"visible\":true}");
        manager.reload();
        filters.reload();
        panel.reload();

        assertTrue(labels(panel).contains("Theirs"));
    }

    private void search(String text) throws Exception
    {
        // The search box, like any Swing box, is typed into on Swing's thread.
        SwingUtilities.invokeAndWait(() -> find(panel, IconTextField.class).setText(text));
        panel.createTilePackPanels();
    }

    private List<TilePackPanel> rows()
    {
        List<TilePackPanel> rows = new ArrayList<>();
        collect(panel, TilePackPanel.class, rows);
        return rows;
    }

    private TilePackPanel row(String name)
    {
        return rows().stream().filter(r -> labels(r).contains(name)).findFirst()
            .orElseThrow(() -> new AssertionError("no row for " + name));
    }

    private static <T> T find(Container root, Class<T> type)
    {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found.isEmpty() ? null : found.get(0);
    }

    private static <T> void collect(Container root, Class<T> type, List<T> found)
    {
        for (Component c : root.getComponents())
        {
            if (type.isInstance(c))
            {
                found.add(type.cast(c));
            }
            if (c instanceof Container)
            {
                collect((Container) c, type, found);
            }
        }
    }

    private static List<String> labels(Container root)
    {
        List<String> out = new ArrayList<>();
        List<JLabel> labels = new ArrayList<>();
        collect(root, JLabel.class, labels);
        for (JLabel label : labels)
        {
            out.add(label.getText());
        }
        return out;
    }
}
