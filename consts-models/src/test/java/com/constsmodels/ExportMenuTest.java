package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.ItemComposition;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.util.Text;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** Which entries get Export Model and where, the Only with Shift rule, pets under the cursor and the tabs. */
public class ExportMenuTest
{
    private Client client;
    private ConstsModelsConfig config;
    private ConstsModelsPlugin plugin;
    private MenuManager menuManager;
    private ItemManager itemManager;
    private FakeMenu menu;
    private ExportMenu exportMenu;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        config = mock(ConstsModelsConfig.class);
        plugin = mock(ConstsModelsPlugin.class);
        menuManager = mock(MenuManager.class);
        itemManager = mock(ItemManager.class);
        menu = new FakeMenu();
        when(client.getMenu()).thenReturn(menu);
        exportMenu = new ExportMenu(plugin, client, config, menuManager, itemManager);
    }

    @Test
    public void anNpcGetsExportModelJustBelowItsExamine()
    {
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.WALK, "Walk here", "", 0);
        game(MenuAction.NPC_FIRST_OPTION, "Talk-to", "<col=ffff00>Bob", 12);
        game(MenuAction.EXAMINE_NPC, "Examine", "<col=ffff00>Bob", 12);

        assertEquals(List.of("Talk-to Bob", "Walk here", "Examine Bob", "Export Model Bob", "Cancel"), shown());
        MenuEntry export = exports().get(0);
        assertEquals(MenuAction.RUNELITE, export.getType());
        assertEquals(12, export.getIdentifier());
        assertEquals("<col=ffff00>Bob", export.getTarget());
        assertEquals(WorldView.TOPLEVEL, export.getWorldViewId());
    }

    @Test
    public void objectsAndGroundItemsKeepTheirIdTileAndWorldView()
    {
        game(MenuAction.CANCEL, "Cancel", "", 0);
        // A tree on a boat, world view 3
        Entry tree = entry(MenuAction.EXAMINE_OBJECT, "Examine", "<col=ffff>Tree", 1276, 50, 51);
        tree.setWorldViewId(3);
        menu.entries.add(tree);
        exportMenu.onMenuEntryAdded(new MenuEntryAdded(tree));
        game(MenuAction.EXAMINE_ITEM_GROUND, "Examine", "<col=ff9040>Bones", 526, 49, 52);

        List<MenuEntry> exports = exports();
        assertEquals(2, exports.size());
        MenuEntry treeExport = exports.get(0);
        assertEquals(1276, treeExport.getIdentifier());
        assertEquals(50, treeExport.getParam0());
        assertEquals(51, treeExport.getParam1());
        assertEquals(3, treeExport.getWorldViewId());
        MenuEntry bonesExport = exports.get(1);
        assertEquals(526, bonesExport.getIdentifier());
        assertEquals(49, bonesExport.getParam0());
        assertEquals(52, bonesExport.getParam1());
    }

    @Test
    public void eachPlayerGetsOneBelowItsOptions()
    {
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.WALK, "Walk here", "", 0);
        // The game adds a player's options lowest first, low-priority ones over 2000
        for (int index : new int[]{7, 8})
        {
            String name = "<col=ffffff>Player" + index + "<col=40ff00>  (level-3)";
            game(MenuAction.PLAYER_EIGHTH_OPTION, "Report", name, index).setDeprioritized(true);
            game(MenuAction.RUNELITE_PLAYER, "Lookup", name, index).setDeprioritized(true);
            // Another plugin's entry in between
            game(MenuAction.RUNELITE, "Something", name, index);
            game(MenuAction.PLAYER_FOURTH_OPTION, "Trade with", name, index).setDeprioritized(true);
            game(MenuAction.PLAYER_THIRD_OPTION, "Follow", name, index).setDeprioritized(true);
        }

        assertEquals(2, exports().size());
        List<String> shown = shown();
        assertEquals("Walk here", shown.get(0));
        for (int index : new int[]{7, 8})
        {
            int export = shown.indexOf("Export Model Player" + index + "  (level-3)");
            assertTrue(shown.toString(), export > 0);
            assertEquals("Report Player" + index + "  (level-3)", shown.get(export - 1));
        }
    }

    @Test
    public void stacksOfOneItemShareOne()
    {
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.EXAMINE_ITEM_GROUND, "Examine", "Bones", 526, 49, 52);
        game(MenuAction.EXAMINE_ITEM_GROUND, "Examine", "Bones", 526, 49, 52);
        game(MenuAction.EXAMINE_ITEM_GROUND, "Examine", "Bones", 526, 50, 52);

        assertEquals(2, exports().size());
    }

    @Test
    public void nothingElseGetsIt()
    {
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.WALK, "Walk here", "", 0);
        game(MenuAction.NPC_FIRST_OPTION, "Talk-to", "Bob", 12);
        game(MenuAction.GAME_OBJECT_FIRST_OPTION, "Open", "Door", 1530, 50, 50);
        game(MenuAction.GROUND_ITEM_THIRD_OPTION, "Take", "Bones", 526, 49, 52);
        game(MenuAction.CC_OP, "Wield", "Bronze sword", 1);
        game(MenuAction.EXAMINE_ITEM, "Examine", "Bronze sword", 10);
        game(MenuAction.RUNELITE, "Mark object", "Door", 1530, 50, 50);

        assertEquals(0, exports().size());
    }

    @Test
    public void itIsNeverTheLeftClick()
    {
        // An object with nothing to do but Examine, and no Walk here above it
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.EXAMINE_OBJECT, "Examine", "Statue", 300, 40, 40);

        assertEquals(List.of("Examine Statue", "Export Model Statue", "Cancel"), shown());
    }

    @Test
    public void itGoesBelowItsExamineEvenAfterAnotherPluginsEntry()
    {
        game(MenuAction.CANCEL, "Cancel", "", 0);
        Entry examine = entry(MenuAction.EXAMINE_OBJECT, "Examine", "Door", 1530, 50, 50);
        menu.entries.add(examine);
        // Another plugin put its entry in first, above the Examine
        menu.createMenuEntry(-1).setOption("Mark object").setTarget("Door");
        exportMenu.onMenuEntryAdded(new MenuEntryAdded(examine));

        assertEquals(List.of("Mark object Door", "Examine Door", "Export Model Door", "Cancel"), shown());
    }

    @Test
    public void aFullMenuGoesWithout()
    {
        menu.limit = 2;
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.EXAMINE_NPC, "Examine", "Bob", 12);

        assertEquals(List.of("Examine Bob", "Cancel"), shown());
    }

    @Test
    public void onlyWithShiftHidesItUntilShiftIsHeld()
    {
        when(config.shiftOnly()).thenReturn(true);
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.EXAMINE_NPC, "Examine", "Bob", 12);
        assertEquals(0, exports().size());

        when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
        game(MenuAction.EXAMINE_NPC, "Examine", "Alice", 13);
        assertEquals(1, exports().size());

        // Off, Shift doesn't matter
        when(config.shiftOnly()).thenReturn(false);
        when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(false);
        game(MenuAction.EXAMINE_NPC, "Examine", "Carol", 14);
        assertEquals(2, exports().size());
    }

    @Test
    public void aPetUnderTheCursorGetsItWhenTheMenuOpens()
    {
        WorldView view = worldView();
        NPC pet = npc(9, "Cat", true, new Rectangle(0, 0, 100, 100));
        NPC goblin = npc(10, "Goblin", false, new Rectangle(0, 0, 100, 100));
        NPC farPet = npc(11, "Dog", true, new Rectangle(300, 300, 50, 50));
        doReturn(new Indexed<NPC>().put(9, pet).put(10, goblin).put(11, farPet)).when(view).npcs();
        when(client.getMouseCanvasPosition()).thenReturn(new Point(50, 50));
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.WALK, "Walk here", "", 0);

        exportMenu.onMenuOpened(new MenuOpened());

        assertEquals(List.of("Walk here", "Export Model Cat", "Cancel"), shown());
        MenuEntry export = exports().get(0);
        assertEquals(9, export.getIdentifier());
        assertEquals(WorldView.TOPLEVEL, export.getWorldViewId());
        assertEquals("<col=ffff00>Cat", export.getTarget());
    }

    @Test
    public void aPetOnABoatIsFoundInItsWorldView()
    {
        WorldView top = worldView();
        WorldView boat = mock(WorldView.class);
        when(boat.getId()).thenReturn(3);
        doReturn(new Indexed<WorldView>().put(3, boat)).when(top).worldViews();
        doReturn(new Indexed<NPC>().put(4, npc(4, "Cat", true, new Rectangle(0, 0, 100, 100)))).when(boat).npcs();
        when(client.getMouseCanvasPosition()).thenReturn(new Point(50, 50));
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.WALK, "Walk here", "", 0);

        exportMenu.onMenuOpened(new MenuOpened());

        assertEquals(3, exports().get(0).getWorldViewId());
    }

    @Test
    public void aPetWithItsOwnExamineIsntDoubled()
    {
        WorldView view = worldView();
        doReturn(new Indexed<NPC>().put(9, npc(9, "Cat", true, new Rectangle(0, 0, 100, 100)))).when(view).npcs();
        when(client.getMouseCanvasPosition()).thenReturn(new Point(50, 50));
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.WALK, "Walk here", "", 0);
        game(MenuAction.EXAMINE_NPC, "Examine", "<col=ffff00>Cat", 9);

        exportMenu.onMenuOpened(new MenuOpened());

        assertEquals(1, exports().size());
    }

    @Test
    public void petsNeedTheGameWorldAndShift()
    {
        WorldView view = worldView();
        doReturn(new Indexed<NPC>().put(9, npc(9, "Cat", true, new Rectangle(0, 0, 100, 100)))).when(view).npcs();
        when(client.getMouseCanvasPosition()).thenReturn(new Point(50, 50));

        // A menu over the interface: no Walk here
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.CC_OP, "Wield", "Bronze sword", 1);
        exportMenu.onMenuOpened(new MenuOpened());
        assertEquals(0, exports().size());

        game(MenuAction.WALK, "Walk here", "", 0);
        when(config.shiftOnly()).thenReturn(true);
        exportMenu.onMenuOpened(new MenuOpened());
        assertEquals(0, exports().size());

        when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
        exportMenu.onMenuOpened(new MenuOpened());
        assertEquals(1, exports().size());
    }

    @Test
    public void aClickOnAPetsEntryExportsThePet()
    {
        WorldView view = worldView();
        NPC pet = npc(9, "Cat", true, new Rectangle(0, 0, 100, 100));
        doReturn(new Indexed<NPC>().put(9, pet)).when(view).npcs();
        when(client.getMouseCanvasPosition()).thenReturn(new Point(50, 50));
        Mesh mesh = CoreTest.triangle(0xFF0000);
        when(plugin.capture(pet, "Pet Cat")).thenReturn(mesh);
        game(MenuAction.CANCEL, "Cancel", "", 0);
        game(MenuAction.WALK, "Walk here", "", 0);
        exportMenu.onMenuOpened(new MenuOpened());

        click(exports().get(0));

        verify(plugin).show(mesh);
    }

    @Test
    public void yourselfIsOnTheEquipmentAndInventoryTabs()
    {
        exportMenu.startUp();

        ArgumentCaptor<WidgetMenuOption> option = ArgumentCaptor.forClass(WidgetMenuOption.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<MenuEntry>> onClick = ArgumentCaptor.forClass(Consumer.class);
        verify(menuManager).addManagedCustomMenu(option.capture(), onClick.capture());
        assertEquals(ExportMenu.EXPORT_MODEL, option.getValue().getMenuOption());
        assertEquals("Yourself", Text.removeTags(option.getValue().getMenuTarget()));

        onClick.getValue().accept(new Entry());
        verify(plugin).captureMe();

        exportMenu.shutDown();
        verify(menuManager).removeManagedCustomMenu(option.getValue());
    }

    @Test
    public void anItemInTheInventoryOrBankGetsExportModelBelowItsExamine()
    {
        game(MenuAction.CANCEL, "Cancel", "", 0);
        Entry wield = entry(MenuAction.CC_OP, "Wield", "<col=ff9040>Bronze sword", 2, 5, 9764864);
        wield.setItemId(1277);
        menu.entries.add(wield);
        exportMenu.onMenuEntryAdded(new MenuEntryAdded(wield));
        Entry examine = entry(MenuAction.CC_OP_LOW_PRIORITY, "Examine", "<col=ff9040>Bronze sword", 10, 5, 9764864);
        examine.setItemId(1277);
        menu.entries.add(examine);
        exportMenu.onMenuEntryAdded(new MenuEntryAdded(examine));
        // Added again, as the game does for the same slot: still one
        exportMenu.onMenuEntryAdded(new MenuEntryAdded(examine));

        assertEquals(List.of("Wield Bronze sword", "Examine Bronze sword", "Export Model Bronze sword", "Cancel"), shown());
        MenuEntry export = exports().get(0);
        assertEquals(MenuAction.RUNELITE, export.getType());
        assertEquals(1277, export.getIdentifier());
        assertEquals(5, export.getParam0());
        assertEquals(9764864, export.getParam1());
        assertTrue(ExportMenu.isItemExamine(examine));
    }

    @Test
    public void aClickExportsTheItemsOwnModelNotedOrNot()
    {
        // A noted bronze sword is the sword
        when(itemManager.canonicalize(1278)).thenReturn(1277);
        ItemComposition sword = mock(ItemComposition.class);
        when(sword.getInventoryModel()).thenReturn(2601);
        when(sword.getColorToReplace()).thenReturn(new short[]{10, 20});
        when(sword.getColorToReplaceWith()).thenReturn(new short[]{11, 21});
        when(client.getItemDefinition(1277)).thenReturn(sword);
        ModelData cached = mock(ModelData.class);
        ModelData copy = mock(ModelData.class);
        when(cached.shallowCopy()).thenReturn(copy);
        when(copy.cloneColors()).thenReturn(copy);
        when(copy.cloneTextures()).thenReturn(copy);
        Model lit = mock(Model.class);
        when(copy.light()).thenReturn(lit);
        when(client.loadModelData(2601)).thenReturn(cached);
        Mesh mesh = CoreTest.triangle(0xFF0000);
        when(plugin.captureModel(lit, "Item Bronze sword")).thenReturn(mesh);

        exportMenu.exportItem(1278, "<col=ff9040>Bronze sword");

        verify(copy).recolor((short) 10, (short) 11);
        verify(copy).recolor((short) 20, (short) 21);
        verify(plugin).show(mesh);
    }

    @Test
    public void anItemWithoutAModelSaysSo()
    {
        when(itemManager.canonicalize(4151)).thenReturn(4151);
        ItemComposition whip = mock(ItemComposition.class);
        when(whip.getInventoryModel()).thenReturn(-1);
        when(client.getItemDefinition(4151)).thenReturn(whip);

        exportMenu.exportItem(4151, "<col=ff9040>Abyssal whip");

        verify(plugin).chat("Couldn't find the model of Abyssal whip.");
    }

    @Test
    public void onlyExaminesAndPlayerOptionsAreAnchors()
    {
        assertEquals(ExportTarget.Kind.NPC, ExportMenu.kindOf(MenuAction.EXAMINE_NPC));
        assertEquals(ExportTarget.Kind.OBJECT, ExportMenu.kindOf(MenuAction.EXAMINE_OBJECT));
        assertEquals(ExportTarget.Kind.ITEM, ExportMenu.kindOf(MenuAction.EXAMINE_ITEM_GROUND));
        assertEquals(ExportTarget.Kind.PLAYER, ExportMenu.kindOf(MenuAction.PLAYER_FIRST_OPTION));
        assertEquals(ExportTarget.Kind.PLAYER, ExportMenu.kindOf(MenuAction.PLAYER_EIGHTH_OPTION));
        assertEquals(ExportTarget.Kind.PLAYER, ExportMenu.kindOf(MenuAction.RUNELITE_PLAYER));
        assertNull(ExportMenu.kindOf(MenuAction.EXAMINE_ITEM));
        assertNull(ExportMenu.kindOf(MenuAction.NPC_FIRST_OPTION));
        assertNull(ExportMenu.kindOf(MenuAction.RUNELITE));
        assertNull(ExportMenu.kindOf(null));
        assertNull(ExportMenu.kindOf(MenuAction.WALK));
    }

    @Test
    public void theFakeMenuPutsEntriesInLikeTheGame()
    {
        // createMenuEntry(i) puts the new entry at i, under the one that was there; -1 is the top
        Entry bottom = game(MenuAction.CANCEL, "Cancel", "", 0);
        MenuEntry top = menu.createMenuEntry(-1);
        MenuEntry under = menu.createMenuEntry(1);
        assertSame(bottom, menu.getMenuEntries()[0]);
        assertSame(under, menu.getMenuEntries()[1]);
        assertSame(top, menu.getMenuEntries()[2]);
    }

    // The game, faked

    /** The game adding an entry to the menu it's building, and RuneLite telling plugins. */
    private Entry game(MenuAction type, String option, String target, int identifier)
    {
        return game(type, option, target, identifier, 0, 0);
    }

    private Entry game(MenuAction type, String option, String target, int identifier, int x, int y)
    {
        Entry entry = entry(type, option, target, identifier, x, y);
        menu.entries.add(entry);
        exportMenu.onMenuEntryAdded(new MenuEntryAdded(entry));
        return entry;
    }

    static Entry entry(MenuAction type, String option, String target, int identifier, int x, int y)
    {
        Entry entry = new Entry();
        entry.setType(type).setOption(option).setTarget(target).setIdentifier(identifier).setParam0(x).setParam1(y);
        return entry;
    }

    /**
     * The menu as the player sees it, top first, once the game has sorted it: entries with an opcode over 1000
     * (Examine, Cancel, RuneLite's own and anything deprioritized) go below the rest, keeping their order.
     */
    private List<String> shown()
    {
        List<MenuEntry> sorted = new ArrayList<>(menu.entries);
        boolean swapped = true;
        while (swapped)
        {
            swapped = false;
            for (int i = 0; i < sorted.size() - 1; i++)
            {
                if (opcode(sorted.get(i)) < 1000 && opcode(sorted.get(i + 1)) > 1000)
                {
                    Collections.swap(sorted, i, i + 1);
                    swapped = true;
                }
            }
        }
        Collections.reverse(sorted);
        return sorted.stream()
            .map(e -> Text.removeTags(e.getOption() + " " + e.getTarget()).trim())
            .collect(Collectors.toList());
    }

    private static int opcode(MenuEntry entry)
    {
        return entry.getType().getId() + (entry.isDeprioritized() ? MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET : 0);
    }

    private List<MenuEntry> exports()
    {
        List<MenuEntry> found = new ArrayList<>();
        for (MenuEntry entry : menu.entries)
        {
            if (ExportMenu.EXPORT_MODEL.equals(entry.getOption()))
            {
                found.add(entry);
            }
        }
        return found;
    }

    static void click(MenuEntry entry)
    {
        entry.onClick().accept(entry);
    }

    private WorldView worldView()
    {
        WorldView view = mock(WorldView.class);
        when(view.getId()).thenReturn(WorldView.TOPLEVEL);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(client.getWorldView(WorldView.TOPLEVEL)).thenReturn(view);
        return view;
    }

    static NPC npc(int index, String name, boolean follower, Rectangle hull)
    {
        NPC npc = mock(NPC.class);
        NPCComposition composition = mock(NPCComposition.class);
        when(composition.isFollower()).thenReturn(follower);
        when(npc.getComposition()).thenReturn(composition);
        when(npc.getIndex()).thenReturn(index);
        when(npc.getName()).thenReturn(name);
        when(npc.getConvexHull()).thenReturn(hull);
        return npc;
    }

    /** The game's menu: Cancel at the bottom (index 0), the left click at the top. */
    static final class FakeMenu implements Menu
    {
        final List<MenuEntry> entries = new ArrayList<>();

        /** How many entries the menu takes, like the game's. */
        int limit = 500;

        @Override
        public MenuEntry createMenuEntry(int idx)
        {
            if (entries.size() >= limit)
            {
                throw new IllegalStateException("menu is full");
            }
            int at = idx < 0 ? entries.size() + idx + 1 : idx;
            if (at < 0 || at > entries.size())
            {
                throw new IllegalArgumentException();
            }
            Entry entry = new Entry();
            entries.add(at, entry);
            return entry;
        }

        @Override
        public MenuEntry[] getMenuEntries()
        {
            return entries.toArray(new MenuEntry[0]);
        }

        @Override
        public void setMenuEntries(MenuEntry[] menuEntries)
        {
            entries.clear();
            Collections.addAll(entries, menuEntries);
        }

        @Override
        public void removeMenuEntry(MenuEntry entry)
        {
            entries.remove(entry);
        }

        @Override
        public int getMenuX()
        {
            return 0;
        }

        @Override
        public int getMenuY()
        {
            return 0;
        }

        @Override
        public int getMenuWidth()
        {
            return 0;
        }

        @Override
        public int getMenuHeight()
        {
            return 0;
        }
    }

    /** A menu entry: what's set on it, and its click. A new one is RuneLite's, as in the game. */
    static final class Entry implements MenuEntry
    {
        private String option = "";
        private String target = "";
        private int identifier;
        private MenuAction type = MenuAction.RUNELITE;
        private int param0;
        private int param1;
        private boolean forceLeftClick;
        private int worldViewId = WorldView.TOPLEVEL;
        private boolean deprioritized;
        private int itemId = -1;
        private Consumer<MenuEntry> onClick;

        @Override
        public String getOption()
        {
            return option;
        }

        @Override
        public MenuEntry setOption(String option)
        {
            this.option = option;
            return this;
        }

        @Override
        public String getTarget()
        {
            return target;
        }

        @Override
        public MenuEntry setTarget(String target)
        {
            this.target = target;
            return this;
        }

        @Override
        public int getIdentifier()
        {
            return identifier;
        }

        @Override
        public MenuEntry setIdentifier(int identifier)
        {
            this.identifier = identifier;
            return this;
        }

        @Override
        public MenuAction getType()
        {
            return type;
        }

        @Override
        public MenuEntry setType(MenuAction type)
        {
            this.type = type;
            return this;
        }

        @Override
        public int getParam0()
        {
            return param0;
        }

        @Override
        public MenuEntry setParam0(int param0)
        {
            this.param0 = param0;
            return this;
        }

        @Override
        public int getParam1()
        {
            return param1;
        }

        @Override
        public MenuEntry setParam1(int param1)
        {
            this.param1 = param1;
            return this;
        }

        @Override
        public boolean isForceLeftClick()
        {
            return forceLeftClick;
        }

        @Override
        public MenuEntry setForceLeftClick(boolean forceLeftClick)
        {
            this.forceLeftClick = forceLeftClick;
            return this;
        }

        @Override
        public int getWorldViewId()
        {
            return worldViewId;
        }

        @Override
        public MenuEntry setWorldViewId(int worldViewId)
        {
            this.worldViewId = worldViewId;
            return this;
        }

        @Override
        public boolean isDeprioritized()
        {
            return deprioritized;
        }

        @Override
        public MenuEntry setDeprioritized(boolean deprioritized)
        {
            this.deprioritized = deprioritized;
            return this;
        }

        @Override
        public MenuEntry onClick(Consumer<MenuEntry> onClick)
        {
            this.onClick = onClick;
            return this;
        }

        @Override
        public Consumer<MenuEntry> onClick()
        {
            return onClick;
        }

        @Override
        public boolean isItemOp()
        {
            return false;
        }

        @Override
        public int getItemOp()
        {
            return -1;
        }

        @Override
        public int getItemId()
        {
            return itemId;
        }

        @Override
        public MenuEntry setItemId(int itemId)
        {
            this.itemId = itemId;
            return this;
        }

        @Override
        public Widget getWidget()
        {
            return null;
        }

        @Override
        public NPC getNpc()
        {
            return null;
        }

        @Override
        public Player getPlayer()
        {
            return null;
        }

        @Override
        public Actor getActor()
        {
            return null;
        }

        @Override
        public Menu getSubMenu()
        {
            return null;
        }

        @Override
        public Menu createSubMenu()
        {
            return null;
        }

        @Override
        public void deleteSubMenu()
        {
        }
    }

    /** A world view's players, NPCs or world views, by index. */
    static final class Indexed<T> implements IndexedObjectSet<T>
    {
        private final Map<Integer, T> byIndex = new LinkedHashMap<>();

        Indexed<T> put(int index, T value)
        {
            byIndex.put(index, value);
            return this;
        }

        @Override
        public T byIndex(int index)
        {
            return byIndex.get(index);
        }

        @Override
        public Iterator<T> iterator()
        {
            return byIndex.values().iterator();
        }
    }
}
