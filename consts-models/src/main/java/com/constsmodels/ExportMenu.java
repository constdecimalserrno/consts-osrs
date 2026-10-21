package com.constsmodels;

import java.awt.Shape;
import javax.inject.Inject;
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
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import org.slf4j.Logger;
import net.runelite.client.util.Text;
import org.slf4j.LoggerFactory;

/**
 * The Export Model option on the right-click menu of anything with a model: players, NPCs, pets, objects (game, wall,
 * decorative and ground objects), ground items, items in the inventory, bank, equipment or anywhere else an item has an
 * Examine (its own model, without dropping it), and your own player on the equipment and inventory tabs. Each
 * target gets one, low in the menu: just below its Examine, or below a player's options, so it's never the left
 * click. A pet has no entries of its own, so a pet under the cursor gets one when the menu opens. With Only with Shift
 * on, it's there only while Shift is held. A click looks the target up again with {@link ExportTarget}, then, on the
 * client thread, captures it with {@link ConstsModelsPlugin#capture} and opens it with {@link ConstsModelsPlugin#show}.
 * The plugin registers this on the event bus and calls startUp and shutDown.
 */
class ExportMenu
{
    private static final Logger log = LoggerFactory.getLogger(ExportMenu.class);

    static final String EXPORT_MODEL = "Export Model";

    /** How the game colors an NPC's name on the menu. */
    private static final String NPC_COLOR = "<col=ffff00>";

    private final ConstsModelsPlugin plugin;
    private final Client client;
    private final ConstsModelsConfig config;
    private final MenuManager menuManager;
    private final ItemManager itemManager;

    /** Your own player: the equipment and inventory tabs, in the fixed, resizable and bottom-line layouts. */
    private final WidgetMenuOption yourself = new WidgetMenuOption(EXPORT_MODEL, "Yourself",
        InterfaceID.Toplevel.STONE3, InterfaceID.Toplevel.STONE4,
        InterfaceID.ToplevelOsrsStretch.STONE3, InterfaceID.ToplevelOsrsStretch.STONE4,
        InterfaceID.ToplevelPreEoc.STONE3, InterfaceID.ToplevelPreEoc.STONE4);

    @Inject
    ExportMenu(ConstsModelsPlugin plugin, Client client, ConstsModelsConfig config, MenuManager menuManager,
        ItemManager itemManager)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.menuManager = menuManager;
        this.itemManager = itemManager;
    }

    void startUp()
    {
        menuManager.addManagedCustomMenu(yourself, entry -> plugin.captureMe());
    }

    void shutDown()
    {
        menuManager.removeManagedCustomMenu(yourself);
    }

    /** Whether Export Model is on the menu now: always, or while Shift is held with Only with Shift on. */
    boolean showing()
    {
        return !config.shiftOnly() || client.isKeyPressed(KeyCode.KC_SHIFT);
    }

    /**
     * The kind of target an entry is Export Model's anchor for: an NPC's, object's or ground item's Examine, or any of
     * a player's options (players have no Examine). Null for any other entry.
     */
    static ExportTarget.Kind kindOf(MenuAction type)
    {
        if (type == null)
        {
            return null;
        }
        switch (type)
        {
            case EXAMINE_NPC:
                return ExportTarget.Kind.NPC;
            case EXAMINE_OBJECT:
                return ExportTarget.Kind.OBJECT;
            case EXAMINE_ITEM_GROUND:
                return ExportTarget.Kind.ITEM;
            case PLAYER_FIRST_OPTION:
            case PLAYER_SECOND_OPTION:
            case PLAYER_THIRD_OPTION:
            case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION:
            case PLAYER_SIXTH_OPTION:
            case PLAYER_SEVENTH_OPTION:
            case PLAYER_EIGHTH_OPTION:
            case RUNELITE_PLAYER:
                return ExportTarget.Kind.PLAYER;
            default:
                return null;
        }
    }

    /**
     * As the game builds the menu, Export Model goes in just below each Examine, and below the first option the game
     * adds for each player, which is the lowest of that player's. The game sorts its low-priority entries (Examine,
     * RuneLite's own) below the rest, but keeps their order, so it stays there and never becomes the left click.
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        MenuEntry added = event.getMenuEntry();
        if (isItemExamine(added))
        {
            if (showing())
            {
                Menu menu = client.getMenu();
                MenuEntry[] entries = menu.getMenuEntries();
                if (!hasItemExport(entries, added))
                {
                    addItem(menu, indexOf(entries, added), added);
                }
            }
            return;
        }
        ExportTarget.Kind kind = kindOf(added.getType());
        if (kind == null || !showing())
        {
            return;
        }

        Menu menu = client.getMenu();
        MenuEntry[] entries = menu.getMenuEntries();
        int at = indexOf(entries, added);
        if ((kind == ExportTarget.Kind.PLAYER && !firstForPlayer(entries, at, added))
            || (kind == ExportTarget.Kind.ITEM && hasExport(entries, added)))
        {
            return;
        }
        add(menu, at, kind, added.getTarget(), added.getIdentifier(), added.getParam0(), added.getParam1(),
            added.getWorldViewId());
    }

    /**
     * When the menu opens over the game world, Export Model for each pet under the cursor, just above Cancel. Pets
     * have no entries of their own; one that does (your own) has its Examine, so it has Export Model already.
     */
    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        if (!showing())
        {
            return;
        }
        Menu menu = client.getMenu();
        MenuEntry[] entries = menu.getMenuEntries();
        Point mouse = client.getMouseCanvasPosition();
        WorldView top = client.getTopLevelWorldView();
        // A menu over the game world always has Walk here; one over the interface doesn't
        if (mouse == null || top == null || indexOf(entries, MenuAction.WALK) < 0)
        {
            return;
        }

        int bottom = indexOf(entries, MenuAction.CANCEL) + 1;
        addPets(menu, entries, top, mouse, bottom);
        IndexedObjectSet<? extends WorldView> inside = top.worldViews();
        if (inside != null)
        {
            for (WorldView view : inside)
            {
                if (view != null)
                {
                    addPets(menu, entries, view, mouse, bottom);
                }
            }
        }
    }

    private void addPets(Menu menu, MenuEntry[] entries, WorldView view, Point mouse, int bottom)
    {
        IndexedObjectSet<? extends NPC> npcs = view.npcs();
        if (npcs == null)
        {
            return;
        }
        for (NPC npc : npcs)
        {
            if (npc == null || !ExportTarget.isPet(npc) || hasExamine(entries, npc.getIndex(), view.getId()))
            {
                continue;
            }
            Shape hull = npc.getConvexHull();
            if (hull != null && hull.contains(mouse.getX(), mouse.getY()))
            {
                String name = npc.getName() == null ? "" : npc.getName();
                add(menu, bottom, ExportTarget.Kind.NPC, NPC_COLOR + name, npc.getIndex(), 0, 0, view.getId());
            }
        }
    }

    /** Puts an Export Model for this target in at an index: just below the entry that was there. */
    private void add(Menu menu, int index, ExportTarget.Kind kind, String target, int identifier, int x, int y,
        int worldView)
    {
        MenuEntry entry;
        try
        {
            entry = menu.createMenuEntry(index);
        }
        catch (IllegalStateException e)
        {
            // The menu is full, as it can be over a crowd
            return;
        }
        entry.setOption(EXPORT_MODEL)
            .setTarget(target)
            .setType(MenuAction.RUNELITE)
            .setIdentifier(identifier)
            .setParam0(x)
            .setParam1(y)
            .setWorldViewId(worldView)
            .onClick(clicked -> export(kind, clicked));
    }

    /**
     * Whether an entry is the Examine of an item in an interface (the inventory, the bank, the equipment tab, a shop):
     * those carry the item's id. A ground item's Examine is an anchor of its own, for the item where it lies.
     */
    static boolean isItemExamine(MenuEntry entry)
    {
        return entry.getItemId() > 0 && entry.getType() != MenuAction.EXAMINE_ITEM_GROUND
            && "Examine".equals(Text.removeTags(entry.getOption()));
    }

    /** Puts an Export Model for an item in an interface just below its Examine. */
    private void addItem(Menu menu, int index, MenuEntry examine)
    {
        MenuEntry entry;
        try
        {
            entry = menu.createMenuEntry(index);
        }
        catch (IllegalStateException e)
        {
            return;
        }
        int itemId = examine.getItemId();
        String target = examine.getTarget();
        entry.setOption(EXPORT_MODEL)
            .setTarget(target)
            .setType(MenuAction.RUNELITE)
            .setIdentifier(itemId)
            .setParam0(examine.getParam0())
            .setParam1(examine.getParam1())
            .onClick(clicked -> exportItem(itemId, target));
    }

    /**
     * A click on an item's Export Model, on the client thread: its model as the game draws it on the ground or in the
     * inventory, from the game's cache, so it needn't be dropped first. A noted item or a bank placeholder is the
     * item itself.
     */
    void exportItem(int itemId, String target)
    {
        String what = ExportTarget.clean(target);
        if (what.isEmpty())
        {
            what = "that item";
        }
        try
        {
            ItemComposition item = client.getItemDefinition(itemManager.canonicalize(itemId));
            ModelData data = item == null ? null : client.loadModelData(item.getInventoryModel());
            if (data == null)
            {
                plugin.chat("Couldn't find the model of " + what + ".");
                return;
            }
            plugin.show(plugin.captureModel(itemModel(item, data), ExportTarget.Kind.ITEM.prefix + " " + what));
        }
        catch (RuntimeException e)
        {
            log.warn("Couldn't export item {}", itemId, e);
            plugin.chat("Couldn't export " + what + ": " + e.getMessage());
        }
    }

    /**
     * An item's model with its own recolors and retextures, lit as the game lights it; the cached model is untouched.
     * Its textures are copied only when the item changes some, since a model without any can't copy them: the game
     * throws.
     */
    static Model itemModel(ItemComposition item, ModelData data)
    {
        ModelData copy = data.shallowCopy().cloneColors();
        short[] from = item.getColorToReplace();
        short[] to = item.getColorToReplaceWith();
        if (from != null && to != null)
        {
            for (int i = 0; i < Math.min(from.length, to.length); i++)
            {
                copy.recolor(from[i], to[i]);
            }
        }
        from = item.getTextureToReplace();
        to = item.getTextureToReplaceWith();
        if (from != null && to != null && Math.min(from.length, to.length) > 0)
        {
            try
            {
                copy = copy.cloneTextures();
                for (int i = 0; i < Math.min(from.length, to.length); i++)
                {
                    copy.retexture(from[i], to[i]);
                }
            }
            catch (NullPointerException e)
            {
                // The model has no textures for the item to change
            }
        }
        return copy.light();
    }

    /** A click, on the client thread: finds what the entry points at, captures it and opens the capture. */
    void export(ExportTarget.Kind kind, MenuEntry entry)
    {
        String what = ExportTarget.clean(entry.getTarget());
        if (what.isEmpty())
        {
            what = "that";
        }
        try
        {
            ExportTarget target = ExportTarget.find(client, kind, entry);
            if (target == null)
            {
                log.debug("Nothing to export for {} {} at {},{} in world view {}", kind, entry.getIdentifier(),
                    entry.getParam0(), entry.getParam1(), entry.getWorldViewId());
                plugin.chat("Couldn't find " + what + " to export.");
                return;
            }
            plugin.show(capture(target));
        }
        catch (RuntimeException e)
        {
            log.warn("Couldn't export {}", what, e);
            plugin.chat("Couldn't export " + what + ": " + e.getMessage());
        }
    }

    /** The target as one mesh: its one part, or its halves put together where the game draws them. */
    private Mesh capture(ExportTarget target)
    {
        if (target.parts.size() == 1)
        {
            return plugin.capture(target.parts.get(0).renderable, target.name);
        }
        MeshBuilder mesh = new MeshBuilder(target.name);
        for (ExportTarget.Part part : target.parts)
        {
            mesh.add(plugin.capture(part.renderable, target.name), 0, part.dx, part.dy, 0);
        }
        return mesh.build();
    }

    /**
     * Whether this is the first option the game has added for this player. It adds a player's options one after
     * another, so a later one has an earlier one just below it, past any entries other plugins have put in between.
     */
    private static boolean firstForPlayer(MenuEntry[] entries, int at, MenuEntry added)
    {
        for (int i = at - 1; i >= 0; i--)
        {
            MenuEntry below = entries[i];
            if (below.getType() == MenuAction.RUNELITE)
            {
                continue;
            }
            return kindOf(below.getType()) != ExportTarget.Kind.PLAYER
                || below.getIdentifier() != added.getIdentifier()
                || below.getWorldViewId() != added.getWorldViewId();
        }
        return true;
    }

    /** Whether this item in this slot has its Export Model already. */
    private static boolean hasItemExport(MenuEntry[] entries, MenuEntry examine)
    {
        for (MenuEntry entry : entries)
        {
            if (entry.getType() == MenuAction.RUNELITE && EXPORT_MODEL.equals(entry.getOption())
                && entry.getIdentifier() == examine.getItemId()
                && entry.getParam0() == examine.getParam0()
                && entry.getParam1() == examine.getParam1())
            {
                return true;
            }
        }
        return false;
    }

    /** Whether there's an Export Model for the same thing in the same place already: stacks of an item share one. */
    private static boolean hasExport(MenuEntry[] entries, MenuEntry added)
    {
        for (MenuEntry entry : entries)
        {
            if (entry.getType() == MenuAction.RUNELITE && EXPORT_MODEL.equals(entry.getOption())
                && entry.getIdentifier() == added.getIdentifier()
                && entry.getParam0() == added.getParam0()
                && entry.getParam1() == added.getParam1()
                && entry.getWorldViewId() == added.getWorldViewId())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExamine(MenuEntry[] entries, int npcIndex, int worldView)
    {
        for (MenuEntry entry : entries)
        {
            if (entry.getType() == MenuAction.EXAMINE_NPC && entry.getIdentifier() == npcIndex
                && entry.getWorldViewId() == worldView)
            {
                return true;
            }
        }
        return false;
    }

    /** Where the entry is on the menu; the top when it isn't found, as the game has only just added it. */
    private static int indexOf(MenuEntry[] entries, MenuEntry entry)
    {
        for (int i = entries.length - 1; i >= 0; i--)
        {
            if (entries[i] == entry)
            {
                return i;
            }
        }
        return Math.max(entries.length - 1, 0);
    }

    /** Where the lowest entry of this type is on the menu; -1 when there's none. */
    private static int indexOf(MenuEntry[] entries, MenuAction type)
    {
        for (int i = 0; i < entries.length; i++)
        {
            if (entries[i].getType() == type)
            {
                return i;
            }
        }
        return -1;
    }
}
