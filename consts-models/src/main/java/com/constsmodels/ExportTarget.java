package com.constsmodels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.client.util.Text;

/**
 * What an Export Model entry points at, looked up again when it's clicked: a player or an NPC by its index in the
 * entry's world view, or an object or a ground item by its id on the scene tile at the entry's x and y (param0 and
 * param1). A wall or a decoration can be in two halves, and both come along, each where the game draws it. Use it on
 * the client thread.
 */
final class ExportTarget
{
    /** The kinds of thing on the menu that have a model, and what a capture of each is called. */
    enum Kind
    {
        PLAYER("Player"),
        NPC("NPC"),
        OBJECT("Object"),
        ITEM("Item");

        final String prefix;

        Kind(String prefix)
        {
            this.prefix = prefix;
        }
    }

    /** What a capture of a follower is called. */
    static final String PET = "Pet";

    /** What the game puts after a player's or an NPC's name on the menu: "(level-3)", or "(skill-1250)". */
    private static final Pattern LEVEL = Pattern.compile("\\s*\\((level|skill)-\\d+\\)\\s*$");

    /** One piece of the target, and how far east and north (in game units) the game draws it from its tile. */
    static final class Part
    {
        final Renderable renderable;
        final int dx;
        final int dy;

        Part(Renderable renderable, int dx, int dy)
        {
            this.renderable = renderable;
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** What the capture is called: "NPC Goblin". */
    final String name;

    /** One part, or two for a wall or a decoration in halves. */
    final List<Part> parts;

    private ExportTarget(String name, List<Part> parts)
    {
        this.name = name;
        this.parts = parts;
    }

    /** What the entry points at now; null when it's gone. */
    static ExportTarget find(Client client, Kind kind, MenuEntry entry)
    {
        WorldView view = client.getWorldView(entry.getWorldViewId());
        if (view == null)
        {
            return null;
        }
        switch (kind)
        {
            case PLAYER:
                return player(view, entry);
            case NPC:
                return npc(view, entry);
            case OBJECT:
                return object(client, view, entry);
            case ITEM:
                return item(client, view, entry);
            default:
                return null;
        }
    }

    private static ExportTarget player(WorldView view, MenuEntry entry)
    {
        IndexedObjectSet<? extends Player> players = view.players();
        Player player = players == null ? null : players.byIndex(entry.getIdentifier());
        if (player == null)
        {
            return null;
        }
        return whole(name(Kind.PLAYER.prefix, player.getName(), entry.getTarget()), player);
    }

    private static ExportTarget npc(WorldView view, MenuEntry entry)
    {
        IndexedObjectSet<? extends NPC> npcs = view.npcs();
        NPC npc = npcs == null ? null : npcs.byIndex(entry.getIdentifier());
        if (npc == null)
        {
            return null;
        }
        String prefix = isPet(npc) ? PET : Kind.NPC.prefix;
        return whole(name(prefix, npc.getName(), entry.getTarget()), npc);
    }

    static boolean isPet(NPC npc)
    {
        NPCComposition composition = npc.getComposition();
        return composition != null && composition.isFollower();
    }

    private static ExportTarget object(Client client, WorldView view, MenuEntry entry)
    {
        for (Tile tile : tiles(view, entry.getParam0(), entry.getParam1()))
        {
            List<Part> parts = objectParts(client, tile, entry.getIdentifier());
            if (!parts.isEmpty())
            {
                return new ExportTarget(name(Kind.OBJECT.prefix, null, entry.getTarget()), parts);
            }
        }
        return null;
    }

    /** The object with this id on the tile, in its halves; none when it isn't there or has nothing to draw. */
    private static List<Part> objectParts(Client client, Tile tile, int id)
    {
        List<Part> parts = new ArrayList<>();

        WallObject wall = tile.getWallObject();
        if (matches(client, wall, id))
        {
            add(parts, wall.getRenderable1(), 0, 0);
            add(parts, wall.getRenderable2(), 0, 0);
            if (!parts.isEmpty())
            {
                return parts;
            }
        }

        DecorativeObject decoration = tile.getDecorativeObject();
        if (matches(client, decoration, id))
        {
            add(parts, decoration.getRenderable(), decoration.getXOffset(), decoration.getYOffset());
            add(parts, decoration.getRenderable2(), decoration.getXOffset2(), decoration.getYOffset2());
            if (!parts.isEmpty())
            {
                return parts;
            }
        }

        GroundObject ground = tile.getGroundObject();
        if (matches(client, ground, id))
        {
            add(parts, ground.getRenderable(), 0, 0);
            if (!parts.isEmpty())
            {
                return parts;
            }
        }

        GameObject[] objects = tile.getGameObjects();
        if (objects != null)
        {
            for (GameObject object : objects)
            {
                if (matches(client, object, id) && object.getRenderable() != null)
                {
                    add(parts, object.getRenderable(), 0, 0);
                    return parts;
                }
            }
        }
        return parts;
    }

    /**
     * Whether the object is the one with this id. Examine gives the id an object has turned into rather than its own,
     * so the ids it can turn into count too.
     */
    private static boolean matches(Client client, TileObject object, int id)
    {
        if (object == null)
        {
            return false;
        }
        if (object.getId() == id)
        {
            return true;
        }
        ObjectComposition composition = client.getObjectDefinition(object.getId());
        int[] impostors = composition == null ? null : composition.getImpostorIds();
        if (impostors != null)
        {
            for (int impostor : impostors)
            {
                if (impostor == id)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static ExportTarget item(Client client, WorldView view, MenuEntry entry)
    {
        int id = entry.getIdentifier();
        for (Tile tile : tiles(view, entry.getParam0(), entry.getParam1()))
        {
            List<TileItem> items = tile.getGroundItems();
            if (items == null)
            {
                continue;
            }
            for (TileItem item : items)
            {
                if (item != null && item.getId() == id)
                {
                    ItemComposition composition = client.getItemDefinition(id);
                    String itemName = composition == null ? null : composition.getName();
                    return whole(name(Kind.ITEM.prefix, itemName, entry.getTarget()), item);
                }
            }
        }
        return null;
    }

    /**
     * The tiles at a scene x and y to look on: the one on the plane in view first, and what's under it if it's a
     * bridge, then the same on the other planes.
     */
    private static List<Tile> tiles(WorldView view, int x, int y)
    {
        List<Tile> found = new ArrayList<>();
        Scene scene = view.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        if (tiles == null)
        {
            return found;
        }
        int plane = view.getPlane();
        for (int i = -1; i < tiles.length; i++)
        {
            int z = i < 0 ? plane : i;
            if (i == plane || z < 0 || z >= tiles.length)
            {
                continue;
            }
            Tile[][] level = tiles[z];
            if (level == null || x < 0 || x >= level.length || level[x] == null || y < 0 || y >= level[x].length)
            {
                continue;
            }
            Tile tile = level[x][y];
            if (tile == null)
            {
                continue;
            }
            found.add(tile);
            if (tile.getBridge() != null)
            {
                found.add(tile.getBridge());
            }
        }
        return found;
    }

    private static void add(List<Part> parts, Renderable renderable, int dx, int dy)
    {
        if (renderable != null)
        {
            parts.add(new Part(renderable, dx, dy));
        }
    }

    private static ExportTarget whole(String name, Renderable renderable)
    {
        return new ExportTarget(name, Collections.singletonList(new Part(renderable, 0, 0)));
    }

    /** "NPC Goblin": the kind, then the name without its tags or level, from the game or else from the menu. */
    static String name(String prefix, String name, String target)
    {
        String plain = clean(name);
        if (plain.isEmpty())
        {
            plain = clean(target);
        }
        return plain.isEmpty() ? prefix : prefix + " " + plain;
    }

    /** Text from the game or the menu as a player reads it: no tags, plain spaces, no level after a name. */
    static String clean(String text)
    {
        if (text == null)
        {
            return "";
        }
        String plain = Text.removeTags(text).replace(' ', ' ');
        return LEVEL.matcher(plain).replaceAll("").trim();
    }
}
