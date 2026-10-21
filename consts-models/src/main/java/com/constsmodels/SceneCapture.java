package com.constsmodels;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Capture area: everything around the player, out to a radius in tiles, as one {@link Diorama}. That's the ground
 * (tile shapes, heights and colors, from {@link GroundCapture}), then objects, walls, decorations, ground objects,
 * ground items, NPCs and players, each where and how the game draws it, on the player's plane of the top-level world
 * view. Use it on the client thread.
 *
 * <p>Places and turns follow RuneLite's own: a game object stands at its x and y (the middle of its footprint) and its
 * z, turned by its model orientation, once, from its southwest tile; walls and ground objects at their x, y and z;
 * decorations moved by their offsets; ground items at the pile's height above the tile; actors at their location and
 * the ground under their footprint, turned by their current orientation. Heights grow downward in the game, so a
 * height h is z = -h here.
 */
final class SceneCapture
{
    private static final int TILE = Perspective.LOCAL_TILE_SIZE;

    private SceneCapture()
    {
    }

    /** The area around the local player as a mesh; an empty mesh when there's nothing to capture. */
    static Mesh capture(Client client, int radius, TextureColors textures)
    {
        WorldView view = client.getTopLevelWorldView();
        Player me = client.getLocalPlayer();
        Scene scene = view == null ? null : view.getScene();
        LocalPoint here = me == null ? null : me.getLocalLocation();
        WorldPoint world = me == null ? null : me.getWorldLocation();
        if (scene == null || here == null || world == null)
        {
            return new MeshBuilder("Area").build();
        }

        int plane = view.getPlane();
        Tile[][] tiles = scene.getTiles()[plane];
        int[][][] heights = view.getTileHeights();
        int sizeX = tiles.length;
        int sizeY = tiles[0].length;
        int sx = here.getSceneX();
        int sy = here.getSceneY();
        if (sx < 0 || sy < 0 || sx >= sizeX || sy >= sizeY || heights == null)
        {
            return new MeshBuilder("Area").build();
        }
        int x0 = Math.max(0, sx - radius);
        int y0 = Math.max(0, sy - radius);
        int x1 = Math.min(sizeX - 1, sx + radius);
        int y1 = Math.min(sizeY - 1, sy + radius);

        LocalPoint middle = new LocalPoint(sx * TILE + TILE / 2, sy * TILE + TILE / 2, view);
        int groundHere = Perspective.getTileHeight(client, middle, plane);
        Diorama diorama = new Diorama(Diorama.name(world.getX(), world.getY()), middle.getX(), middle.getY(),
            -groundHere);

        GroundCapture ground = GroundCapture.capture(tiles, heights, plane, x0, y0, x1, y1, textures);
        diorama.addGround(ground.surface, x0 * TILE, y0 * TILE, (x1 + 1) * TILE, (y1 + 1) * TILE);
        diorama.add(ground.loose);

        for (int x = x0; x <= x1; x++)
        {
            for (int y = y0; y <= y1; y++)
            {
                Tile tile = tiles[x][y];
                if (tile != null)
                {
                    addObjects(diorama, tile, x, y, textures);
                    Tile below = tile.getBridge();
                    if (below != null)
                    {
                        addObjects(diorama, below, x, y, textures);
                    }
                }
            }
        }

        for (Actor actor : view.players())
        {
            addActor(diorama, client, actor, plane, x0, y0, x1, y1, textures);
        }
        for (Actor actor : view.npcs())
        {
            addActor(diorama, client, actor, plane, x0, y0, x1, y1, textures);
        }
        return diorama.build();
    }

    /** What stands on a tile: its game objects (from their southwest tile), wall, decoration, ground object and items. */
    private static void addObjects(Diorama diorama, Tile tile, int x, int y, TextureColors textures)
    {
        GameObject[] objects = tile.getGameObjects();
        if (objects != null)
        {
            for (GameObject object : objects)
            {
                if (object == null)
                {
                    continue;
                }
                Point min = object.getSceneMinLocation();
                if (min == null || min.getX() != x || min.getY() != y)
                {
                    continue;
                }
                Renderable renderable = object.getRenderable();
                // Actors, projectiles and spot animations sit in the scene only for a frame; actors come in below
                if (renderable instanceof Actor || renderable instanceof Projectile
                    || renderable instanceof GraphicsObject)
                {
                    continue;
                }
                place(diorama, renderable, object.getModelOrientation(), object.getX(), object.getY(), object.getZ(),
                    textures);
            }
        }

        WallObject wall = tile.getWallObject();
        if (wall != null)
        {
            place(diorama, wall.getRenderable1(), 0, wall.getX(), wall.getY(), wall.getZ(), textures);
            place(diorama, wall.getRenderable2(), 0, wall.getX(), wall.getY(), wall.getZ(), textures);
        }

        DecorativeObject decoration = tile.getDecorativeObject();
        if (decoration != null)
        {
            place(diorama, decoration.getRenderable(), 0, decoration.getX() + decoration.getXOffset(),
                decoration.getY() + decoration.getYOffset(), decoration.getZ(), textures);
            place(diorama, decoration.getRenderable2(), 0, decoration.getX() + decoration.getXOffset2(),
                decoration.getY() + decoration.getYOffset2(), decoration.getZ(), textures);
        }

        GroundObject groundObject = tile.getGroundObject();
        if (groundObject != null)
        {
            place(diorama, groundObject.getRenderable(), 0, groundObject.getX(), groundObject.getY(),
                groundObject.getZ(), textures);
        }

        ItemLayer items = tile.getItemLayer();
        if (items != null)
        {
            // The pile's height lifts it onto whatever it lies on, such as a table
            int z = items.getZ() - items.getHeight();
            place(diorama, items.getBottom(), 0, items.getX(), items.getY(), z, textures);
            place(diorama, items.getMiddle(), 0, items.getX(), items.getY(), z, textures);
            place(diorama, items.getTop(), 0, items.getX(), items.getY(), z, textures);
        }
    }

    private static void addActor(Diorama diorama, Client client, Actor actor, int plane, int x0, int y0, int x1,
        int y1, TextureColors textures)
    {
        if (actor == null)
        {
            return;
        }
        LocalPoint where = actor.getLocalLocation();
        WorldPoint world = actor.getWorldLocation();
        if (where == null || world == null || world.getPlane() != plane)
        {
            return;
        }
        int sx = where.getSceneX();
        int sy = where.getSceneY();
        if (sx < x0 || sx > x1 || sy < y0 || sy > y1)
        {
            return;
        }
        int ground = Perspective.getFootprintTileHeight(client, where, plane, actor.getFootprintSize());
        place(diorama, actor, actor.getCurrentOrientation(), where.getX(), where.getY(), ground, textures);
    }

    /** A renderable's model at its place: x and y local, height the game's (down), turned by an orientation. */
    private static void place(Diorama diorama, Renderable renderable, int orientation, int x, int y, int height,
        TextureColors textures)
    {
        Model model = ModelCapture.modelOf(renderable);
        if (model == null)
        {
            return;
        }
        Mesh mesh = ModelCapture.capture(model, "", textures);
        if (mesh.isEmpty())
        {
            return;
        }
        diorama.add(mesh, orientation, x, y, -(height - renderable.getAnimationHeightOffset()));
    }
}
