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
import com.constsdestination.tilepacks.ui.panel.pack.actions.CopyToClipboardLabel;
import com.constsdestination.tilepacks.ui.panel.pack.actions.DeleteCustomPackLabel;
import com.constsdestination.tilepacks.ui.panel.pack.actions.EditCustomPackLabel;
import com.constsdestination.tilepacks.ui.panel.pack.actions.TogglePackLabel;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Before;
import org.junit.Test;

public class TilePacksPanelTest extends TilePacksTest
{
    private static final String TILES = "[{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0,\"color\":\"#FFFFFF00\"}]";

    private SavedConfig saved;
    private TilePackManager manager;
    private TilePacksListPanel panel;

    /** The Tile packs tab as consts-Destination builds it, for tests in other packages. */
    public static TilePacksListPanel newPanel(Gson gson, ConfigManager configManager)
    {
        return new TilePacksListPanel(new TilePackManager(gson, configManager), () ->
        {
        }, mock(ChatMessageManager.class), gson);
    }

    @Before
    public void setUp()
    {
        saved = new SavedConfig();
        manager = new TilePackManager(gson, saved.configManager);
        panel = new TilePacksListPanel(manager, () ->
        {
        }, mock(ChatMessageManager.class), gson);
    }

    @Test
    public void thereAreOnlyYourOwnPacksAndNothingToLookUp()
    {
        List<String> labels = labels(panel);
        assertTrue(labels.contains("Your packs"));
        assertTrue(labels.contains(TilePacksListPanel.wrapped(TilePacksListPanel.NO_PACKS)));
        assertTrue(rows().isEmpty());
        assertFalse("no built-in packs", labels.contains("Abyssal Sire"));
        assertNull("no search", find(panel, IconTextField.class));
        assertTrue("the form says where tiles come from",
            labels.contains(TilePacksListPanel.wrapped(CustomPackManagerPanel.TILES_FROM)));
    }

    @Test
    public void everyPackCanBeTurnedOnCopiedEditedAndDeleted()
    {
        manager.addCustomPack("Tithe farm", TILES);
        manager.addCustomPack("Hallowed Sepulchre", TILES);
        panel.createTilePackPanels();

        assertEquals(2, rows().size());
        assertFalse(labels(panel).contains(TilePacksListPanel.wrapped(TilePacksListPanel.NO_PACKS)));
        for (TilePackPanel row : rows())
        {
            assertNotNull(find(row, TogglePackLabel.class));
            assertNotNull(find(row, CopyToClipboardLabel.class));
            assertNotNull(find(row, EditCustomPackLabel.class));
            assertNotNull(find(row, DeleteCustomPackLabel.class));
            // The name, then four controls: nothing to hide it or to open a link
            assertEquals(5, labels(row).size());
        }
        assertEquals("Tithe farm", labels(rows().get(0)).get(0));
    }

    @Test
    public void aPackPastedIntoTheFormIsAddedOnAndTheFormEmpties() throws Exception
    {
        List<FlatTextField> fields = new ArrayList<>();
        collect(panel, FlatTextField.class, fields);
        assertEquals(2, fields.size());
        JButton add = find(panel, JButton.class);
        assertEquals("Add Pack", add.getText());

        SwingUtilities.invokeAndWait(() ->
        {
            fields.get(0).setText("Shared pack");
            fields.get(1).setText(TILES);
            add.doClick();
        });

        assertEquals("Shared pack", manager.getTilePacks().get(10000).packName);
        assertTrue(manager.isPackEnabled(10000));
        assertTrue(labels(panel).contains("Shared pack"));
        assertEquals("", fields.get(0).getText());
        assertEquals("", fields.get(1).getText());
    }

    @Test
    public void aPackIsCheckedBeforeItsSaved()
    {
        assertEquals("Must add a pack name", CustomPackManagerPanel.checkPack(gson, "", TILES));
        assertEquals("Must add tiles", CustomPackManagerPanel.checkPack(gson, "Name", ""));
        assertEquals("Error parsing tiles, check the formatting", CustomPackManagerPanel.checkPack(gson, "Name", "not tiles"));
        assertEquals("Error parsing tiles, check the formatting", CustomPackManagerPanel.checkPack(gson, "Name", "[]"));
        assertNull(CustomPackManagerPanel.checkPack(gson, "Name", TILES));
    }

    @Test
    public void reloadShowsAnotherProfilesPacks()
    {
        saved.put(TilePackManager.CONFIG_GROUP, "tilePacks_pack_10000", "{\"id\":10000,\"packName\":\"Theirs\",\"packTiles\":\"[]\"}");
        manager.reload();
        panel.reload();

        assertTrue(labels(panel).contains("Theirs"));
    }

    private List<TilePackPanel> rows()
    {
        List<TilePackPanel> rows = new ArrayList<>();
        collect(panel, TilePackPanel.class, rows);
        return rows;
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
