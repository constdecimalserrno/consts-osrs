package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.menus.MenuManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** A click on Export Model: finding the target again from the entry, naming it, and capturing it. */
public class ExportMenuLookupTest
{
    private static final int BOAT = 3;

    private Client client;
    private ConstsModelsPlugin plugin;
    private ExportMenuTest.FakeMenu menu;
    private ExportMenu exportMenu;
    private WorldView view;
    private Tile[][][] tiles;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        plugin = mock(ConstsModelsPlugin.class);
        menu = new ExportMenuTest.FakeMenu();
        when(client.getMenu()).thenReturn(menu);
        exportMenu = new ExportMenu(plugin, client, mock(ConstsModelsConfig.class), mock(MenuManager.class));

        view = mock(WorldView.class);
        when(view.getId()).thenReturn(WorldView.TOPLEVEL);
        when(client.getWorldView(WorldView.TOPLEVEL)).thenReturn(view);
        tiles = new Tile[4][104][104];
        Scene scene = mock(Scene.class);
        when(scene.getTiles()).thenReturn(tiles);
        when(view.getScene()).thenReturn(scene);
        when(plugin.capture(any(), any())).thenReturn(CoreTest.triangle(0xFF0000));
    }

    @Test
    public void anNpcIsFoundByItsIndexInItsWorldView()
    {
        WorldView boat = mock(WorldView.class);
        when(client.getWorldView(BOAT)).thenReturn(boat);
        NPC goblin = ExportMenuTest.npc(5, "Goblin", false, null);
        doReturn(new ExportMenuTest.Indexed<NPC>().put(5, goblin)).when(boat).npcs();
        Mesh mesh = CoreTest.triangle(0x00FF00);
        when(plugin.capture(goblin, "NPC Goblin")).thenReturn(mesh);

        ExportMenuTest.click(export(MenuAction.EXAMINE_NPC, "<col=ffff00>Goblin<col=ff00>  (level-2)", 5, 0, 0, BOAT));

        verify(plugin).show(mesh);
    }

    @Test
    public void aFollowerIsAPet()
    {
        NPC cat = ExportMenuTest.npc(9, "Cat", true, null);
        doReturn(new ExportMenuTest.Indexed<NPC>().put(9, cat)).when(view).npcs();

        ExportMenuTest.click(export(MenuAction.EXAMINE_NPC, "<col=ffff00>Cat", 9, 0, 0, WorldView.TOPLEVEL));

        verify(plugin).capture(cat, "Pet Cat");
    }

    @Test
    public void aPlayerIsNamedWithoutTagsSpacesOrLevel()
    {
        Player named = mock(Player.class);
        when(named.getName()).thenReturn("Iron Man");
        Player unnamed = mock(Player.class);
        doReturn(new ExportMenuTest.Indexed<Player>().put(7, named).put(8, unnamed)).when(view).players();

        ExportMenuTest.click(export(MenuAction.PLAYER_THIRD_OPTION, "<col=ffffff>Iron Man<col=40ff00>  (level-3)",
            7, 0, 0, WorldView.TOPLEVEL));
        ExportMenuTest.click(export(MenuAction.PLAYER_THIRD_OPTION, "<img=2><col=ffffff>Zezima<col=40ff00>  (skill-2277)",
            8, 0, 0, WorldView.TOPLEVEL));

        verify(plugin).capture(named, "Player Iron Man");
        verify(plugin).capture(unnamed, "Player Zezima");
    }

    @Test
    public void aWallComesWithBothHalves()
    {
        Renderable first = mock(Renderable.class);
        Renderable second = mock(Renderable.class);
        WallObject wall = mock(WallObject.class);
        when(wall.getId()).thenReturn(1530);
        when(wall.getRenderable1()).thenReturn(first);
        when(wall.getRenderable2()).thenReturn(second);
        when(tile(0, 50, 51).getWallObject()).thenReturn(wall);

        ExportMenuTest.click(export(MenuAction.EXAMINE_OBJECT, "<col=ffff>Wall", 1530, 50, 51, WorldView.TOPLEVEL));

        verify(plugin).capture(first, "Object Wall");
        verify(plugin).capture(second, "Object Wall");
        Mesh shown = shown();
        assertEquals("Object Wall", shown.name);
        assertEquals(2, shown.faceCount());
    }

    @Test
    public void aDecorationsHalvesStayWhereTheGameDrawsThem()
    {
        DecorativeObject decoration = mock(DecorativeObject.class);
        when(decoration.getId()).thenReturn(2000);
        when(decoration.getRenderable()).thenReturn(mock(Renderable.class));
        when(decoration.getRenderable2()).thenReturn(mock(Renderable.class));
        when(decoration.getXOffset()).thenReturn(10);
        when(decoration.getYOffset()).thenReturn(20);
        when(decoration.getXOffset2()).thenReturn(-30);
        when(decoration.getYOffset2()).thenReturn(40);
        when(tile(0, 50, 51).getDecorativeObject()).thenReturn(decoration);

        ExportMenuTest.click(export(MenuAction.EXAMINE_OBJECT, "Banner", 2000, 50, 51, WorldView.TOPLEVEL));

        // Each half's triangle starts at (0, 0, 0): it lands on the half's offset
        Mesh shown = shown();
        assertEquals(10, shown.x[0], 0.01);
        assertEquals(20, shown.y[0], 0.01);
        assertEquals(-30, shown.x[3], 0.01);
        assertEquals(40, shown.y[3], 0.01);
    }

    @Test
    public void anObjectIsFoundByTheIdItHasTurnedInto()
    {
        Renderable lever = mock(Renderable.class);
        GameObject object = mock(GameObject.class);
        when(object.getId()).thenReturn(100);
        when(object.getRenderable()).thenReturn(lever);
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getImpostorIds()).thenReturn(new int[]{200, 201});
        when(client.getObjectDefinition(100)).thenReturn(composition);
        when(tile(0, 50, 51).getGameObjects()).thenReturn(new GameObject[]{null, object, null});

        ExportMenuTest.click(export(MenuAction.EXAMINE_OBJECT, "Lever", 201, 50, 51, WorldView.TOPLEVEL));

        verify(plugin).capture(lever, "Object Lever");
    }

    @Test
    public void anObjectUnderABridgeOrOnAnotherPlaneIsStillFound()
    {
        when(view.getPlane()).thenReturn(1);
        Renderable rug = mock(Renderable.class);
        GroundObject ground = mock(GroundObject.class);
        when(ground.getId()).thenReturn(77);
        when(ground.getRenderable()).thenReturn(rug);
        Tile bridge = mock(Tile.class);
        when(bridge.getGroundObject()).thenReturn(ground);
        when(tile(1, 50, 51).getBridge()).thenReturn(bridge);

        Renderable chest = mock(Renderable.class);
        GameObject object = mock(GameObject.class);
        when(object.getId()).thenReturn(88);
        when(object.getRenderable()).thenReturn(chest);
        when(tile(0, 60, 61).getGameObjects()).thenReturn(new GameObject[]{object});

        ExportMenuTest.click(export(MenuAction.EXAMINE_OBJECT, "Rug", 77, 50, 51, WorldView.TOPLEVEL));
        ExportMenuTest.click(export(MenuAction.EXAMINE_OBJECT, "Chest", 88, 60, 61, WorldView.TOPLEVEL));

        verify(plugin).capture(rug, "Object Rug");
        verify(plugin).capture(chest, "Object Chest");
    }

    @Test
    public void aGroundItemIsNamedByWhatItIs()
    {
        TileItem bones = mock(TileItem.class);
        when(bones.getId()).thenReturn(526);
        TileItem coins = mock(TileItem.class);
        when(coins.getId()).thenReturn(995);
        when(tile(0, 49, 52).getGroundItems()).thenReturn(List.of(bones, coins));
        ItemComposition definition = mock(ItemComposition.class);
        when(definition.getName()).thenReturn("Coins");
        when(client.getItemDefinition(995)).thenReturn(definition);

        // Another plugin has put the stack's size after its name
        ExportMenuTest.click(export(MenuAction.EXAMINE_ITEM_GROUND, "<col=ff9040>Coins (1000)", 995, 49, 52,
            WorldView.TOPLEVEL));

        verify(plugin).capture(coins, "Item Coins");
    }

    @Test
    public void somethingGoneSaysSoInChat()
    {
        doReturn(new ExportMenuTest.Indexed<NPC>()).when(view).npcs();
        tile(0, 50, 51);

        ExportMenuTest.click(export(MenuAction.EXAMINE_NPC, "<col=ffff00>Goblin<col=ff00>  (level-2)", 5, 0, 0,
            WorldView.TOPLEVEL));
        ExportMenuTest.click(export(MenuAction.EXAMINE_OBJECT, "Tree", 1276, 50, 51, WorldView.TOPLEVEL));
        // A world view that's gone, and a tile off the scene
        ExportMenuTest.click(export(MenuAction.EXAMINE_OBJECT, "Tree", 1276, 50, 51, BOAT));
        ExportMenuTest.click(export(MenuAction.EXAMINE_ITEM_GROUND, "Bones", 526, 200, -1, WorldView.TOPLEVEL));

        verify(plugin).chat("Couldn't find Goblin to export.");
        verify(plugin, times(2)).chat("Couldn't find Tree to export.");
        verify(plugin).chat("Couldn't find Bones to export.");
        verify(plugin, never()).show(any());
    }

    @Test
    public void namesLoseTheirTagsAndLevels()
    {
        assertEquals("NPC Goblin", ExportTarget.name("NPC", null, "<col=ffff00>Goblin<col=ff00>  (level-2)"));
        assertEquals("NPC Goblin", ExportTarget.name("NPC", "Goblin", "anything"));
        assertEquals("Object", ExportTarget.name("Object", "", "<col=ffff></col>"));
        assertEquals("", ExportTarget.clean(null));
    }

    /** The game adds an entry, then a click on the Export Model that went in under it. */
    private MenuEntry export(MenuAction type, String target, int identifier, int x, int y, int worldView)
    {
        menu.entries.clear();
        menu.createMenuEntry(0).setType(MenuAction.CANCEL).setOption("Cancel");
        ExportMenuTest.Entry entry = ExportMenuTest.entry(type, "Examine", target, identifier, x, y);
        entry.setWorldViewId(worldView);
        menu.entries.add(entry);
        exportMenu.onMenuEntryAdded(new MenuEntryAdded(entry));
        MenuEntry export = menu.getMenuEntries()[1];
        assertEquals(ExportMenu.EXPORT_MODEL, export.getOption());
        return export;
    }

    private Tile tile(int z, int x, int y)
    {
        Tile tile = mock(Tile.class);
        tiles[z][x][y] = tile;
        return tile;
    }

    private Mesh shown()
    {
        ArgumentCaptor<Mesh> mesh = ArgumentCaptor.forClass(Mesh.class);
        verify(plugin).show(mesh.capture());
        return mesh.getValue();
    }
}
