package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.ItemLayer;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class SceneCaptureTest
{
    private static final int TILE = 128;
    private static final int SCENE = 104;

    // Ground

    @Test
    public void groundIsEachTilesPaintOrShapeOverTheHeightMap()
    {
        Tile[][] tiles = new Tile[SCENE][SCENE];
        // The top-level view's heights can cover the extended scene, 40 tiles more each way
        int[][][] heights = new int[4][185][185];
        heights[0][50][60] = -100;
        heights[0][51][60] = -110;
        heights[0][51][61] = -120;
        heights[0][50][61] = -130;
        heights[0][52][60] = -140;
        heights[0][52][61] = -150;

        tiles[10][20] = tile(0, paint(100, 200, 300, 400));
        // Its west edge has a point halfway, which the painted tile beside it has to meet
        tiles[11][20] = tile(0, westNotchedShape(11, 20, -110, -140, -150, -120));
        // (12, 20) has no tile: the game draws nothing there

        GroundCapture ground = GroundCapture.capture(tiles, heights, 0, 10, 20, 12, 20, null);
        Mesh surface = ground.surface;

        // Paint: two triangles, one split where the shape's edge point is; the shape's three; the empty tile's two
        assertEquals(8, surface.faceCount());
        assertTrue(ground.loose.isEmpty());

        // Heights grow downward: z is minus the height, read from the extended map
        int sw = vertexAt(surface, 10 * TILE, 20 * TILE);
        assertEquals(100, surface.z[sw], 0f);
        assertEquals(150, surface.z[vertexAt(surface, 12 * TILE, 21 * TILE)], 0f);

        // The painted tile's southwest half takes the average of its southwest, southeast and northwest corners
        assertEquals(1, countColor(surface, JagexColor.hslToRgbAverage(100, 200, 400)));
        // Its northeast half, split in two at the shape's point
        assertEquals(2, countColor(surface, JagexColor.hslToRgbAverage(300, 400, 200)));
        // The shape's hidden face and the empty tile are filled, so the ground has no holes
        assertEquals(3, countColor(surface, GroundCapture.VOID));
        assertEquals(2, countColor(surface, JagexColor.hslToRgbAverage(500, 500, 500)));

        // The halfway point is one vertex, shared by both tiles, at the shape's own height
        int notch = vertexAt(surface, 11 * TILE, 20 * TILE + 64);
        assertEquals(115, surface.z[notch], 0f);
        assertEquals(3 + 2, uses(surface, notch));

        assertClosed(new Diorama("Test", 0, 0, 0)
            .addGround(surface, 10 * TILE, 20 * TILE, 13 * TILE, 21 * TILE)
            .build());
    }

    @Test
    public void aBridgeDeckIsKeptApartFromTheGroundUnderIt()
    {
        Tile[][] tiles = new Tile[SCENE][SCENE];
        int[][][] heights = new int[4][105][105];
        heights[0][5][5] = -10;
        heights[1][5][5] = -300;

        Tile below = tile(0, paint(100, 100, 100, 100));
        Tile deck = tile(1, paint(900, 900, 900, 900));
        when(deck.getBridge()).thenReturn(below);
        tiles[5][5] = deck;

        GroundCapture ground = GroundCapture.capture(tiles, heights, 0, 5, 5, 5, 5, null);
        assertEquals(2, ground.surface.faceCount());
        assertEquals(10, ground.surface.z[vertexAt(ground.surface, 5 * TILE, 5 * TILE)], 0f);
        assertEquals(JagexColor.hslToRgbAverage(100, 100, 100), ground.surface.color[0]);

        // The deck is drawn from the level above's heights, and isn't part of the block
        assertEquals(2, ground.loose.faceCount());
        assertEquals(300, ground.loose.bounds()[5], 0f);
        assertEquals(JagexColor.hslToRgbAverage(900, 900, 900), ground.loose.color[0]);
    }

    @Test
    public void aShapeThatDoesntCoverItsTileLiesFlatInItsLargestColor()
    {
        Tile[][] tiles = new Tile[SCENE][SCENE];
        int[][][] heights = new int[4][105][105];
        SceneTileModel shape = shape(
            new int[]{0, TILE, 0},
            new int[]{0, 0, 0},
            new int[]{0, 0, TILE},
            new int[]{0}, new int[]{1}, new int[]{2},
            new int[]{700}, new int[]{700}, new int[]{700});
        tiles[0][0] = tile(0, shape);

        Mesh surface = GroundCapture.capture(tiles, heights, 0, 0, 0, 0, 0, null).surface;
        assertEquals(2, surface.faceCount());
        assertEquals(2, countColor(surface, JagexColor.hslToRgbAverage(700, 700, 700)));
    }

    // Placing things

    @Test
    public void thingsStandWhereAndHowTheGameDrawsThem()
    {
        Tile[][] tiles = new Tile[SCENE][SCENE];
        for (int x = 9; x <= 11; x++)
        {
            for (int y = 9; y <= 11; y++)
            {
                tiles[x][y] = tile(0, paint(100, 100, 100, 100));
            }
        }
        int[][][] heights = new int[4][105][105];
        for (int[] column : heights[0])
        {
            Arrays.fill(column, -40);
        }

        // A 2 by 2 object, southwest tile (9, 9), on all four of its tiles; its model points east, turned a quarter
        GameObject statue = mock(GameObject.class);
        when(statue.getSceneMinLocation()).thenReturn(new Point(9, 9));
        when(statue.getX()).thenReturn(10 * TILE);
        when(statue.getY()).thenReturn(10 * TILE);
        when(statue.getZ()).thenReturn(-90);
        when(statue.getModelOrientation()).thenReturn(512);
        Model statueModel = pointer(1001);
        when(statue.getRenderable()).thenReturn(statueModel);
        for (Tile tile : new Tile[]{tiles[9][9], tiles[10][9], tiles[9][10], tiles[10][10]})
        {
            when(tile.getGameObjects()).thenReturn(new GameObject[]{statue, null, null, null, null});
        }

        // An actor drawn into the scene for a frame is left to the actor list
        NPC goblin = mock(NPC.class);
        GameObject drawnGoblin = mock(GameObject.class);
        when(drawnGoblin.getSceneMinLocation()).thenReturn(new Point(11, 11));
        when(drawnGoblin.getRenderable()).thenReturn(goblin);
        when(tiles[11][11].getGameObjects()).thenReturn(new GameObject[]{drawnGoblin, null, null, null, null});

        WallObject wall = mock(WallObject.class);
        when(wall.getX()).thenReturn(9 * TILE + 64);
        when(wall.getY()).thenReturn(11 * TILE + 64);
        when(wall.getZ()).thenReturn(-40);
        Model wallModel = pointer(1002);
        when(wall.getRenderable1()).thenReturn(wallModel);
        when(tiles[9][11].getWallObject()).thenReturn(wall);

        DecorativeObject banner = mock(DecorativeObject.class);
        when(banner.getX()).thenReturn(11 * TILE + 64);
        when(banner.getY()).thenReturn(9 * TILE + 64);
        when(banner.getZ()).thenReturn(-40);
        when(banner.getXOffset()).thenReturn(16);
        when(banner.getYOffset()).thenReturn(-8);
        Model bannerModel = pointer(1003);
        when(banner.getRenderable()).thenReturn(bannerModel);
        when(tiles[11][9].getDecorativeObject()).thenReturn(banner);

        GroundObject rug = mock(GroundObject.class);
        when(rug.getX()).thenReturn(11 * TILE + 64);
        when(rug.getY()).thenReturn(10 * TILE + 64);
        when(rug.getZ()).thenReturn(-40);
        Model rugModel = pointer(1004);
        when(rug.getRenderable()).thenReturn(rugModel);
        when(tiles[11][10].getGroundObject()).thenReturn(rug);

        // Items on a table lie at the table's height
        ItemLayer pile = mock(ItemLayer.class);
        when(pile.getX()).thenReturn(10 * TILE + 64);
        when(pile.getY()).thenReturn(11 * TILE + 64);
        when(pile.getZ()).thenReturn(-40);
        when(pile.getHeight()).thenReturn(60);
        TileItem coins = mock(TileItem.class);
        Model coinsModel = pointer(1005);
        when(coins.getModel()).thenReturn(coinsModel);
        when(pile.getBottom()).thenReturn(coins);
        when(tiles[10][11].getItemLayer()).thenReturn(pile);

        Player me = actor(Player.class, 10 * TILE + 64, 10 * TILE + 64, 0, 0);
        NPC guard = actor(NPC.class, 11 * TILE + 64, 11 * TILE + 64, 0, 1024);
        Model guardModel = pointer(1006);
        when(guard.getModel()).thenReturn(guardModel);
        // Upstairs, and out of reach: not in the capture
        NPC upstairs = actor(NPC.class, 10 * TILE + 64, 10 * TILE + 64, 1, 0);
        Model upstairsModel = pointer(1007);
        when(upstairs.getModel()).thenReturn(upstairsModel);
        NPC far = actor(NPC.class, 20 * TILE + 64, 10 * TILE + 64, 0, 0);
        Model farModel = pointer(1008);
        when(far.getModel()).thenReturn(farModel);

        Client client = client(tiles, heights, me, new NPC[]{guard, upstairs, far, goblin});
        Mesh area = SceneCapture.capture(client, 1, null);

        assertEquals("Area at 3200, 3201", area.name);

        // Centered on my tile, at the ground: the statue's middle is a tile southwest of mine, raised 50 over it,
        // and a quarter turn takes its east-pointing tip to the south
        float[] statueTip = tip(area, 1001);
        assertEquals(-64, statueTip[0], 0.01);
        assertEquals(-64 - 100, statueTip[1], 0.01);
        assertEquals(50, statueTip[2], 0.01);
        assertEquals(1, countColor(area, color(1001)));

        assertEquals(-TILE, tip(area, 1002)[0] - 100, 0.01);
        assertEquals(TILE, tip(area, 1002)[1], 0.01);
        assertEquals(0, tip(area, 1002)[2], 0.01);

        // A decoration moves by its offsets
        assertEquals(TILE + 16 + 100, tip(area, 1003)[0], 0.01);
        assertEquals(-TILE - 8, tip(area, 1003)[1], 0.01);

        assertEquals(TILE + 100, tip(area, 1004)[0], 0.01);
        assertEquals(0, tip(area, 1004)[1], 0.01);

        assertEquals(100, tip(area, 1005)[0], 0.01);
        assertEquals(TILE, tip(area, 1005)[1], 0.01);
        assertEquals(60, tip(area, 1005)[2], 0.01);

        // The guard faces north (1024), a half turn: east becomes west
        assertEquals(TILE - 100, tip(area, 1006)[0], 0.01);
        assertEquals(TILE, tip(area, 1006)[1], 0.01);
        assertEquals(0, tip(area, 1006)[2], 0.01);

        assertEquals(0, countColor(area, color(1007)));
        assertEquals(0, countColor(area, color(1008)));
    }

    // The block

    @Test
    public void theAreaIsOneClosedBlock()
    {
        Tile[][] tiles = new Tile[SCENE][SCENE];
        int[][][] heights = new int[4][105][105];
        for (int x = 0; x < 105; x++)
        {
            for (int y = 0; y < 105; y++)
            {
                heights[0][x][y] = -((x * 37 + y * 11) % 90);
            }
        }
        for (int x = 28; x <= 32; x++)
        {
            for (int y = 38; y <= 42; y++)
            {
                tiles[x][y] = tile(0, paint(100 + x, 200 + y, 300, 400));
            }
        }
        // Shapes with points halfway along all four edges: two side by side, one on the area's edge
        tiles[30][40] = tile(0, crossShape(30, 40, heights[0]));
        tiles[30][41] = tile(0, crossShape(30, 41, heights[0]));
        tiles[32][39] = tile(0, crossShape(32, 39, heights[0]));
        tiles[29][40] = tile(0, westNotchedShape(29, 40, heights[0][29][40], heights[0][30][40], heights[0][30][41],
            heights[0][29][41]));
        // A hole, a hidden tile and a tile off the plane
        tiles[31][41] = null;
        tiles[28][38] = tile(0, paint(GroundCapture.HIDDEN, 0, GroundCapture.HIDDEN, 0));
        tiles[31][39] = tile(1, paint(900, 900, 900, 900));

        Player me = actor(Player.class, 30 * TILE + 64, 40 * TILE + 64, 0, 0);
        Mesh area = SceneCapture.capture(client(tiles, heights, me, new NPC[0]), 2, null);

        assertFalse(area.isEmpty());
        assertEquals(2, countColor(area, JagexColor.hslToRgbAverage(900, 900, 900)));
        Mesh block = withoutColor(area, JagexColor.hslToRgbAverage(900, 900, 900));
        assertClosed(block);
        assertTrue(volume(block) > 0);

        // Centered on my tile, at the ground in its middle
        float[] box = block.bounds();
        assertEquals(-2.5f * TILE, box[0], 0f);
        assertEquals(2.5f * TILE, box[3], 0f);
        assertEquals(-2.5f * TILE, box[1], 0f);
        assertEquals(2.5f * TILE, box[4], 0f);
        float centerZ = -heightAtMiddle(heights[0], 30, 40);
        float lowestZ = -Arrays.stream(heights[0]).skip(28).limit(6)
            .flatMapToInt(column -> Arrays.stream(column).skip(38).limit(6)).max().getAsInt();
        assertEquals(lowestZ - Diorama.DEPTH - centerZ, box[2], 0f);
    }

    @Test
    public void aDioramaOfOneTileIsABox()
    {
        MeshBuilder tile = new MeshBuilder("Tile");
        int sw = tile.addVertex(0, 0, 0);
        int se = tile.addVertex(TILE, 0, 0);
        int ne = tile.addVertex(TILE, TILE, 0);
        int nw = tile.addVertex(0, TILE, 0);
        tile.addTriangle(sw, se, nw, 1);
        tile.addTriangle(ne, nw, se, 1);

        Mesh box = new Diorama(Diorama.name(1, 2), 64, 64, 0).addGround(tile.build(), 0, 0, TILE, TILE).build();
        assertEquals("Area at 1, 2", box.name);
        assertClosed(box);
        assertEquals((double) TILE * TILE * Diorama.DEPTH, volume(box), 1);
        assertEquals(-64, box.bounds()[0], 0f);
        assertEquals(-Diorama.DEPTH, box.bounds()[2], 0f);
    }

    @Test
    public void nothingToCaptureIsAnEmptyMesh()
    {
        Client client = mock(Client.class);
        assertTrue(SceneCapture.capture(client, 6, null).isEmpty());
    }

    @Test
    public void aWideAreaIsQuick()
    {
        // Light stand-ins for the client, so the time is the capture's: radius 24, a painted tile and a
        // 200-face object on every tile
        Tile[][] tiles = new Tile[SCENE][SCENE];
        int[][][] heights = new int[4][105][105];
        Model model = fakeModel(200);
        for (int x = 0; x < SCENE; x++)
        {
            for (int y = 0; y < SCENE; y++)
            {
                heights[0][x][y] = -((x * 7 + y * 13) % 64);
                SceneTilePaint paint = fake(SceneTilePaint.class, "getSwColor", 100, "getSeColor", 200,
                    "getNeColor", 300, "getNwColor", 400, "getTexture", -1);
                GameObject object = fake(GameObject.class, "getSceneMinLocation", new Point(x, y),
                    "getX", x * TILE + 64, "getY", y * TILE + 64, "getRenderable", model, "getModelOrientation", 512);
                tiles[x][y] = fake(Tile.class, "getSceneTilePaint", paint,
                    "getGameObjects", new GameObject[]{object, null, null, null, null});
            }
        }
        Player me = actor(Player.class, 52 * TILE + 64, 52 * TILE + 64, 0, 0);
        Client client = client(tiles, heights, me, new NPC[0]);

        SceneCapture.capture(client, 24, null);
        long start = System.nanoTime();
        Mesh area = SceneCapture.capture(client, 24, null);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(49 * 49 * (2 + 200) + 4 * 49 * 3, area.faceCount());
        assertTrue("radius 24 took " + millis + " ms", millis < 1000);
    }

    // Stand-ins

    private static Client client(Tile[][] tiles, int[][][] heights, Player me, NPC[] npcs)
    {
        Scene scene = mock(Scene.class);
        Tile[][][] levels = new Tile[4][][];
        levels[0] = tiles;
        for (int level = 1; level < 4; level++)
        {
            levels[level] = new Tile[SCENE][SCENE];
        }
        when(scene.getTiles()).thenReturn(levels);

        WorldView view = mock(WorldView.class);
        when(view.getScene()).thenReturn(scene);
        when(view.getId()).thenReturn(-1);
        when(view.isTopLevel()).thenReturn(true);
        when(view.getPlane()).thenReturn(0);
        when(view.getSizeX()).thenReturn(SCENE);
        when(view.getSizeY()).thenReturn(SCENE);
        when(view.getTileHeights()).thenReturn(heights);
        when(view.getTileSettings()).thenReturn(new byte[4][SCENE][SCENE]);
        when(view.getTileHeight(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
            interpolate(heights[(int) call.getArgument(2)], call.getArgument(0), call.getArgument(1)));
        doReturn(set(me)).when(view).players();
        doReturn(set(npcs)).when(view).npcs();

        Client client = mock(Client.class);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(client.getWorldView(anyInt())).thenReturn(view);
        when(client.getLocalPlayer()).thenReturn(me);
        return client;
    }

    private static <T extends Actor> T actor(Class<T> type, int x, int y, int plane, int orientation)
    {
        T actor = mock(type);
        when(actor.getLocalLocation()).thenReturn(new LocalPoint(x, y, -1));
        when(actor.getWorldLocation()).thenReturn(new WorldPoint(3200, 3201, plane));
        when(actor.getCurrentOrientation()).thenReturn(orientation);
        return actor;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static <T> IndexedObjectSet<T> set(T... items)
    {
        IndexedObjectSet<T> set = mock(IndexedObjectSet.class);
        when(set.iterator()).thenAnswer(call -> Arrays.asList(items).iterator());
        return set;
    }

    private static Tile tile(int level, SceneTilePaint paint)
    {
        Tile tile = mock(Tile.class);
        when(tile.getRenderLevel()).thenReturn(level);
        when(tile.getSceneTilePaint()).thenReturn(paint);
        return tile;
    }

    private static Tile tile(int level, SceneTileModel shape)
    {
        Tile tile = mock(Tile.class);
        when(tile.getRenderLevel()).thenReturn(level);
        when(tile.getSceneTileModel()).thenReturn(shape);
        return tile;
    }

    private static SceneTilePaint paint(int sw, int se, int ne, int nw)
    {
        SceneTilePaint paint = mock(SceneTilePaint.class);
        when(paint.getSwColor()).thenReturn(sw);
        when(paint.getSeColor()).thenReturn(se);
        when(paint.getNeColor()).thenReturn(ne);
        when(paint.getNwColor()).thenReturn(nw);
        when(paint.getTexture()).thenReturn(-1);
        return paint;
    }

    private static SceneTileModel shape(int[] vx, int[] vy, int[] vz, int[] a, int[] b, int[] c, int[] colorA,
        int[] colorB, int[] colorC)
    {
        SceneTileModel shape = mock(SceneTileModel.class);
        when(shape.getVertexX()).thenReturn(vx);
        when(shape.getVertexY()).thenReturn(vy);
        when(shape.getVertexZ()).thenReturn(vz);
        when(shape.getFaceX()).thenReturn(a);
        when(shape.getFaceY()).thenReturn(b);
        when(shape.getFaceZ()).thenReturn(c);
        when(shape.getTriangleColorA()).thenReturn(colorA);
        when(shape.getTriangleColorB()).thenReturn(colorB);
        when(shape.getTriangleColorC()).thenReturn(colorC);
        return shape;
    }

    /** A tile shape with a point halfway up its west edge: a hidden face in the southwest, then two of HSL 500. */
    private static SceneTileModel westNotchedShape(int sx, int sy, int sw, int se, int ne, int nw)
    {
        int x = sx * TILE;
        int y = sy * TILE;
        return shape(
            new int[]{x, x + TILE, x + TILE, x, x},
            new int[]{sw, se, ne, nw, (sw + nw) / 2},
            new int[]{y, y, y + TILE, y + TILE, y + 64},
            new int[]{0, 4, 4}, new int[]{1, 1, 2}, new int[]{4, 2, 3},
            new int[]{GroundCapture.HIDDEN, 500, 500}, new int[]{0, 500, 500}, new int[]{0, 500, 500});
    }

    /** A tile shape with points halfway along all four edges and in the middle, fanned out from the middle. */
    private static SceneTileModel crossShape(int sx, int sy, int[][] heights)
    {
        int x = sx * TILE;
        int y = sy * TILE;
        int[][] ring = {{0, 0}, {64, 0}, {128, 0}, {128, 64}, {128, 128}, {64, 128}, {0, 128}, {0, 64}};
        int[] vx = new int[9];
        int[] vy = new int[9];
        int[] vz = new int[9];
        for (int i = 0; i < 8; i++)
        {
            vx[i] = x + ring[i][0];
            vz[i] = y + ring[i][1];
            vy[i] = interpolate(heights, vx[i], vz[i]);
        }
        vx[8] = x + 64;
        vz[8] = y + 64;
        vy[8] = interpolate(heights, vx[8], vz[8]);
        int[] a = new int[8];
        int[] b = new int[8];
        int[] c = new int[8];
        int[] colors = new int[8];
        for (int i = 0; i < 8; i++)
        {
            a[i] = 8;
            b[i] = i;
            c[i] = (i + 1) % 8;
            colors[i] = 600 + i;
        }
        return shape(vx, vy, vz, a, b, c, colors, colors, colors);
    }

    /** The game's interpolated height at a local point, as WorldView.getTileHeight gives it. */
    private static int interpolate(int[][] heights, int x, int y)
    {
        int sx = x >> 7;
        int sy = y >> 7;
        int fx = x & 127;
        int fy = y & 127;
        int south = fx * heights[sx + 1][sy] + (TILE - fx) * heights[sx][sy] >> 7;
        int north = heights[sx][sy + 1] * (TILE - fx) + fx * heights[sx + 1][sy + 1] >> 7;
        return (TILE - fy) * south + fy * north >> 7;
    }

    private static int heightAtMiddle(int[][] heights, int sx, int sy)
    {
        return interpolate(heights, sx * TILE + 64, sy * TILE + 64);
    }

    /** A one-triangle model, flat-shaded in an HSL color, pointing east: its tip 100 east of its origin. */
    private static Model pointer(int hsl)
    {
        Model model = mock(Model.class);
        when(model.getVerticesCount()).thenReturn(3);
        when(model.getVerticesX()).thenReturn(new float[]{100, 0, 0});
        when(model.getVerticesY()).thenReturn(new float[]{0, 0, 0});
        when(model.getVerticesZ()).thenReturn(new float[]{0, 10, -10});
        when(model.getFaceCount()).thenReturn(1);
        when(model.getFaceIndices1()).thenReturn(new int[]{0});
        when(model.getFaceIndices2()).thenReturn(new int[]{1});
        when(model.getFaceIndices3()).thenReturn(new int[]{2});
        when(model.getFaceColors1()).thenReturn(new int[]{hsl});
        when(model.getFaceColors2()).thenReturn(new int[]{0});
        when(model.getFaceColors3()).thenReturn(new int[]{-1});
        return model;
    }

    private static int color(int hsl)
    {
        return JagexColor.hslToRgb((short) hsl, JagexColor.BRIGHTNESS_MIN);
    }

    /** Where the tip of a pointer model in an HSL color ended up. */
    private static float[] tip(Mesh mesh, int hsl)
    {
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            if (mesh.color[f] == color(hsl))
            {
                int v = mesh.a[f];
                return new float[]{mesh.x[v], mesh.y[v], mesh.z[v]};
            }
        }
        throw new AssertionError("no model in color " + hsl);
    }

    private static int vertexAt(Mesh mesh, float x, float y)
    {
        int found = -1;
        for (int v = 0; v < mesh.vertexCount(); v++)
        {
            if (mesh.x[v] == x && mesh.y[v] == y)
            {
                assertEquals("one vertex at " + x + ", " + y, -1, found);
                found = v;
            }
        }
        assertNotEquals("a vertex at " + x + ", " + y, -1, found);
        return found;
    }

    private static int uses(Mesh mesh, int v)
    {
        int n = 0;
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            if (mesh.a[f] == v || mesh.b[f] == v || mesh.c[f] == v)
            {
                n++;
            }
        }
        return n;
    }

    private static int countColor(Mesh mesh, int rgb)
    {
        int n = 0;
        for (int color : mesh.color)
        {
            if (color == rgb)
            {
                n++;
            }
        }
        return n;
    }

    private static Mesh withoutColor(Mesh mesh, int rgb)
    {
        MeshBuilder kept = new MeshBuilder(mesh.name);
        for (int v = 0; v < mesh.vertexCount(); v++)
        {
            kept.addVertex(mesh.x[v], mesh.y[v], mesh.z[v]);
        }
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            if (mesh.color[f] != rgb)
            {
                kept.addTriangle(mesh.a[f], mesh.b[f], mesh.c[f], mesh.color[f]);
            }
        }
        return kept.build();
    }

    /** Every edge is shared by exactly two triangles, running opposite ways, so the mesh is closed and wound out. */
    static void assertClosed(Mesh mesh)
    {
        Map<Long, Integer> edges = new HashMap<>();
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            int[] corners = {mesh.a[f], mesh.b[f], mesh.c[f]};
            for (int i = 0; i < 3; i++)
            {
                int u = corners[i];
                int v = corners[(i + 1) % 3];
                assertNotEquals("triangle " + f + " has a repeated corner", u, v);
                edges.merge((long) u << 32 | v, 1, Integer::sum);
            }
        }
        for (Map.Entry<Long, Integer> edge : edges.entrySet())
        {
            long key = edge.getKey();
            long reverse = (key & 0xFFFFFFFFL) << 32 | key >>> 32;
            assertEquals("edge " + (key >>> 32) + "-" + (key & 0xFFFFFFFFL) + " runs one way once", 1,
                (int) edge.getValue());
            assertEquals("edge " + (key >>> 32) + "-" + (key & 0xFFFFFFFFL) + " has a triangle on its other side",
                1, (int) edges.getOrDefault(reverse, 0));
        }
    }

    private static double volume(Mesh mesh)
    {
        double sum = 0;
        for (int f = 0; f < mesh.faceCount(); f++)
        {
            int a = mesh.a[f];
            int b = mesh.b[f];
            int c = mesh.c[f];
            sum += mesh.x[a] * ((double) mesh.y[b] * mesh.z[c] - (double) mesh.z[b] * mesh.y[c])
                - mesh.y[a] * ((double) mesh.x[b] * mesh.z[c] - (double) mesh.z[b] * mesh.x[c])
                + mesh.z[a] * ((double) mesh.x[b] * mesh.y[c] - (double) mesh.y[b] * mesh.x[c]);
        }
        return sum / 6;
    }

    /** A plain stand-in for an interface, faster than a mock: each named method returns its value. */
    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Object... answers)
    {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < answers.length; i += 2)
        {
            values.put((String) answers[i], answers[i + 1]);
        }
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) ->
        {
            Object value = values.get(method.getName());
            if (value != null)
            {
                return value;
            }
            Class<?> returns = method.getReturnType();
            if (returns == int.class)
            {
                return method.getName().equals("getTexture") ? -1 : 0;
            }
            if (returns == boolean.class)
            {
                return false;
            }
            if (returns == byte.class)
            {
                return (byte) 0;
            }
            return null;
        });
    }

    private static Model fakeModel(int faces)
    {
        int vertices = faces + 2;
        float[] vx = new float[vertices];
        float[] vy = new float[vertices];
        float[] vz = new float[vertices];
        for (int v = 0; v < vertices; v++)
        {
            vx[v] = (float) Math.cos(v) * 50;
            vy[v] = -v;
            vz[v] = (float) Math.sin(v) * 50;
        }
        int[] a = new int[faces];
        int[] b = new int[faces];
        int[] c = new int[faces];
        int[] colors = new int[faces];
        int[] flat = new int[faces];
        for (int f = 0; f < faces; f++)
        {
            a[f] = f;
            b[f] = f + 1;
            c[f] = f + 2;
            colors[f] = 1000 + f;
            flat[f] = -1;
        }
        return fake(Model.class, "getVerticesCount", vertices, "getVerticesX", vx, "getVerticesY", vy,
            "getVerticesZ", vz, "getFaceCount", faces, "getFaceIndices1", a, "getFaceIndices2", b,
            "getFaceIndices3", c, "getFaceColors1", colors, "getFaceColors2", colors, "getFaceColors3", flat);
    }
}
