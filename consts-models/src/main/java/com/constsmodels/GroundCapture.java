package com.constsmodels;

import java.util.Arrays;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;

/**
 * The ground of an area as the game draws it, ready to close into a block. Each tile is its shape
 * ({@link SceneTileModel}) or its paint ({@link SceneTilePaint}, two triangles over the corner heights), in its colors,
 * laid on the plane's height map so that neighboring tiles share their corners and edges. Where the game draws
 * nothing (no tile, or its hidden color) the surface still covers the ground, in {@link #VOID}, so the block has no
 * holes. Ground off the plane, such as a bridge deck over the ground, is kept apart in {@link #loose}.
 *
 * <p>Coordinates are the scene's local ones (128 to a tile) turned into east, north, up: x is local x, y is local y
 * and z is minus the game's height, which grows downward. Use it on the client thread.
 */
final class GroundCapture
{
    /** The color the game gives a tile, or part of one, it doesn't draw. */
    static final int HIDDEN = 12345678;

    /** Where the game draws nothing: the black around caves and upper floors. */
    static final int VOID = 0x141414;

    private static final int TILE = 128;

    /** A tile's area doubled, as a cross product measures it. */
    private static final long TILE_AREA_2 = 2L * TILE * TILE;

    private static final byte FILL = 0;
    private static final byte PAINT = 1;
    private static final byte SHAPE = 2;
    private static final byte FLAT_SHAPE = 3;

    /** The ground on the plane, edge to edge over the area, its tiles sharing their corners and edges. */
    final Mesh surface;

    /** Ground drawn off the plane, such as bridge decks: kept as the game draws it, not closed. */
    final Mesh loose;

    private final Tile[][] tiles;
    private final int[][][] heights;
    private final int heightOffset;
    private final int plane;
    private final int x0;
    private final int y0;
    private final int w;
    private final int h;
    private final TextureColors textures;

    private final MeshBuilder top = new MeshBuilder("Ground");
    private final MeshBuilder rest = new MeshBuilder("Loose ground");

    // Each surface vertex's place in local units, to find the edges a point splits
    private int[] px = new int[1024];
    private int[] py = new int[1024];

    // Corner vertices, (w + 1) by (h + 1), and the points tile shapes put along tile edges between corners: east-west
    // edges by line and column, north-south edges by line and row
    private final int[] corners;
    private final int[][] rowEdges;
    private final int[][] columnEdges;

    private final Tile[] ground;
    private final byte[] kinds;

    // One tile's triangles while it's put together
    private int[] ta = new int[32];
    private int[] tb = new int[32];
    private int[] tc = new int[32];
    private int[] tcolor = new int[32];
    private int count;
    private int[] shapeVertices = new int[64];

    private GroundCapture(Tile[][] tiles, int[][][] heights, int plane, int x0, int y0, int x1, int y1,
        TextureColors textures)
    {
        this.tiles = tiles;
        this.heights = heights;
        this.plane = plane;
        this.x0 = x0;
        this.y0 = y0;
        this.w = x1 - x0 + 1;
        this.h = y1 - y0 + 1;
        this.textures = textures;
        // The top-level view's heights may cover the extended scene, centered on its tiles
        this.heightOffset = Math.max(0, (heights[plane].length - tiles.length) / 2);

        corners = new int[(w + 1) * (h + 1)];
        Arrays.fill(corners, -1);
        rowEdges = new int[w * (h + 1)][];
        columnEdges = new int[(w + 1) * h][];
        ground = new Tile[w * h];
        kinds = new byte[w * h];

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                classify(x, y);
            }
        }
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                build(x, y);
            }
        }
        surface = top.build();
        loose = rest.build();
    }

    /**
     * The ground of the tiles from (x0, y0) to (x1, y1), scene coordinates, both ends included.
     *
     * @param tiles the plane's tiles, [x][y]
     * @param heights the tile heights, [level][x][y], as the world view has them
     */
    static GroundCapture capture(Tile[][] tiles, int[][][] heights, int plane, int x0, int y0, int x1, int y1,
        TextureColors textures)
    {
        return new GroundCapture(tiles, heights, plane, x0, y0, x1, y1, textures);
    }

    /** The game's height of a tile corner on a level, at scene coordinates; it grows downward. */
    private int cornerHeight(int level, int cx, int cy)
    {
        int[][] map = heights[level];
        int i = Math.max(0, Math.min(map.length - 1, cx + heightOffset));
        int j = Math.max(0, Math.min(map[i].length - 1, cy + heightOffset));
        return map[i][j];
    }

    // First pass: which tile is the ground on the plane, what it's made of, and the edge points its shape needs
    private void classify(int x, int y)
    {
        int sx = x0 + x;
        int sy = y0 + y;
        Tile tile = tiles[sx][sy];
        Tile onPlane = null;
        if (tile != null)
        {
            // A bridge is two tiles: its deck, drawn a level up, and the ground below it
            Tile below = tile.getBridge();
            if (tile.getRenderLevel() == plane)
            {
                onPlane = tile;
            }
            else
            {
                addLoose(tile, sx, sy);
            }
            if (below != null)
            {
                if (onPlane == null && below.getRenderLevel() == plane)
                {
                    onPlane = below;
                }
                else
                {
                    addLoose(below, sx, sy);
                }
            }
        }

        int i = y * w + x;
        ground[i] = onPlane;
        kinds[i] = FILL;
        if (onPlane == null)
        {
            return;
        }

        SceneTileModel shape = onPlane.getSceneTileModel();
        SceneTilePaint paint = onPlane.getSceneTilePaint();
        if (shape != null)
        {
            if (coversTile(shape, sx, sy))
            {
                kinds[i] = SHAPE;
                registerEdgePoints(shape);
            }
            else
            {
                kinds[i] = FLAT_SHAPE;
            }
        }
        else if (paint != null && paint.getNeColor() != HIDDEN)
        {
            kinds[i] = PAINT;
        }
    }

    // Second pass: the tile's triangles, split where a neighbor's shape put points along an edge they share
    private void build(int x, int y)
    {
        int sx = x0 + x;
        int sy = y0 + y;
        int i = y * w + x;
        count = 0;
        switch (kinds[i])
        {
            case PAINT:
                addPaint(ground[i].getSceneTilePaint(), sx, sy);
                break;
            case SHAPE:
                addShape(ground[i].getSceneTileModel());
                break;
            case FLAT_SHAPE:
                int color = largestFaceColor(ground[i].getSceneTileModel());
                addFlat(sx, sy, color, color);
                break;
            default:
                addFlat(sx, sy, VOID, VOID);
                break;
        }

        splitAt(rowEdges[y * w + x]);
        splitAt(rowEdges[(y + 1) * w + x]);
        splitAt(columnEdges[x * h + y]);
        splitAt(columnEdges[(x + 1) * h + y]);

        for (int t = 0; t < count; t++)
        {
            top.addTriangle(ta[t], tb[t], tc[t], tcolor[t]);
        }
    }

    private void addPaint(SceneTilePaint paint, int sx, int sy)
    {
        int texture = paint.getTexture();
        if (texture != -1 && textures != null)
        {
            int rgb = textures.color(texture);
            addFlat(sx, sy, rgb, rgb);
            return;
        }
        int sw = paint.getSwColor();
        int se = paint.getSeColor();
        int ne = paint.getNeColor();
        int nw = paint.getNwColor();
        addFlat(sx, sy, average(sw, se, nw), average(ne, nw, se));
    }

    /** Two triangles over the tile's corners, split as the game splits its paint: southwest half, northeast half. */
    private void addFlat(int sx, int sy, int southwestColor, int northeastColor)
    {
        int sw = corner(sx, sy);
        int se = corner(sx + 1, sy);
        int ne = corner(sx + 1, sy + 1);
        int nw = corner(sx, sy + 1);
        addScratch(sw, se, nw, southwestColor);
        addScratch(ne, nw, se, northeastColor);
    }

    private void addShape(SceneTileModel shape)
    {
        int[] faceA = shape.getFaceX();
        int[] faceB = shape.getFaceY();
        int[] faceC = shape.getFaceZ();
        int[] vx = shape.getVertexX();
        int[] vy = shape.getVertexY();
        int[] vz = shape.getVertexZ();

        if (shapeVertices.length < vx.length)
        {
            shapeVertices = new int[vx.length];
        }
        Arrays.fill(shapeVertices, 0, vx.length, -1);

        for (int f = 0; f < faceA.length; f++)
        {
            int a = faceA[f];
            int b = faceB[f];
            int c = faceC[f];
            if (cross(vx[a], vz[a], vx[b], vz[b], vx[c], vz[c]) == 0)
            {
                // A sliver with no area: nothing to see, and its edges would only get in the way
                continue;
            }
            addScratch(shapeVertex(vx, vy, vz, a), shapeVertex(vx, vy, vz, b), shapeVertex(vx, vy, vz, c),
                faceColor(shape, f));
        }
    }

    /** The surface vertex for a shape's vertex: a shared corner, a shared edge point, or the tile's own. */
    private int shapeVertex(int[] vx, int[] vy, int[] vz, int v)
    {
        if (shapeVertices[v] != -1)
        {
            return shapeVertices[v];
        }
        int lx = vx[v];
        int ly = vz[v];
        boolean onColumn = lx % TILE == 0;
        boolean onRow = ly % TILE == 0;
        int index = -1;
        if (onColumn && onRow)
        {
            index = corner(lx / TILE, ly / TILE);
        }
        else if (onColumn || onRow)
        {
            index = findEdgePoint(edgeFor(lx, ly), lx, ly);
        }
        if (index == -1)
        {
            index = vertex(lx, ly, vy[v]);
        }
        shapeVertices[v] = index;
        return index;
    }

    /** Whether a shape's arrays hang together: every face's corners and colors are there. */
    private static boolean wellFormed(SceneTileModel shape)
    {
        int[] faceA = shape.getFaceX();
        int[] faceB = shape.getFaceY();
        int[] faceC = shape.getFaceZ();
        int[] vx = shape.getVertexX();
        int[] vy = shape.getVertexY();
        int[] vz = shape.getVertexZ();
        int[] colorA = shape.getTriangleColorA();
        int[] colorB = shape.getTriangleColorB();
        int[] colorC = shape.getTriangleColorC();
        if (faceA == null || faceB == null || faceC == null || vx == null || vy == null || vz == null
            || colorA == null || colorB == null || colorC == null)
        {
            return false;
        }
        int faces = faceA.length;
        int vertices = vx.length;
        if (faceB.length < faces || faceC.length < faces || colorA.length < faces || colorB.length < faces
            || colorC.length < faces || vy.length < vertices || vz.length < vertices)
        {
            return false;
        }
        for (int f = 0; f < faces; f++)
        {
            if (!inside(faceA[f], vertices) || !inside(faceB[f], vertices) || !inside(faceC[f], vertices))
            {
                return false;
            }
        }
        return true;
    }

    /** Whether a shape covers its tile exactly: every corner on the tile, every face wound up, adding to a tile. */
    private static boolean coversTile(SceneTileModel shape, int sx, int sy)
    {
        if (!wellFormed(shape))
        {
            return false;
        }
        int[] faceA = shape.getFaceX();
        int[] faceB = shape.getFaceY();
        int[] faceC = shape.getFaceZ();
        int[] vx = shape.getVertexX();
        int[] vz = shape.getVertexZ();
        int minX = sx * TILE;
        int minY = sy * TILE;
        long total = 0;
        for (int f = 0; f < faceA.length; f++)
        {
            int a = faceA[f];
            int b = faceB[f];
            int c = faceC[f];
            if (!onTile(vx[a], vz[a], minX, minY) || !onTile(vx[b], vz[b], minX, minY)
                || !onTile(vx[c], vz[c], minX, minY))
            {
                return false;
            }
            long area = cross(vx[a], vz[a], vx[b], vz[b], vx[c], vz[c]);
            if (area < 0)
            {
                return false;
            }
            total += area;
        }
        return total == TILE_AREA_2;
    }

    private static boolean inside(int index, int length)
    {
        return index >= 0 && index < length;
    }

    private static boolean onTile(int lx, int ly, int minX, int minY)
    {
        return lx >= minX && lx <= minX + TILE && ly >= minY && ly <= minY + TILE;
    }

    /** Twice a triangle's area seen from above, positive when it winds counterclockwise. */
    private static long cross(int ax, int ay, int bx, int by, int cx, int cy)
    {
        return (long) (bx - ax) * (cy - ay) - (long) (cx - ax) * (by - ay);
    }

    /** Puts a shape's points along its tile's edges where the neighbor sharing the edge will find them. */
    private void registerEdgePoints(SceneTileModel shape)
    {
        int[] faceA = shape.getFaceX();
        int[] faceB = shape.getFaceY();
        int[] faceC = shape.getFaceZ();
        int[] vx = shape.getVertexX();
        int[] vy = shape.getVertexY();
        int[] vz = shape.getVertexZ();
        for (int f = 0; f < faceA.length; f++)
        {
            int a = faceA[f];
            int b = faceB[f];
            int c = faceC[f];
            if (cross(vx[a], vz[a], vx[b], vz[b], vx[c], vz[c]) == 0)
            {
                continue;
            }
            registerEdgePoint(vx[a], vz[a], vy[a]);
            registerEdgePoint(vx[b], vz[b], vy[b]);
            registerEdgePoint(vx[c], vz[c], vy[c]);
        }
    }

    private void registerEdgePoint(int lx, int ly, int height)
    {
        boolean onColumn = lx % TILE == 0;
        boolean onRow = ly % TILE == 0;
        if (onColumn == onRow)
        {
            // A corner, shared anyway, or inside the tile
            return;
        }
        int edge = edgeFor(lx, ly);
        if (findEdgePoint(edge, lx, ly) != -1)
        {
            return;
        }
        int[][] edges = onColumn ? columnEdges : rowEdges;
        int slot = edge >>> 1;
        int index = vertex(lx, ly, height);
        int[] points = edges[slot];
        if (points == null)
        {
            edges[slot] = new int[]{index};
        }
        else
        {
            points = Arrays.copyOf(points, points.length + 1);
            points[points.length - 1] = index;
            edges[slot] = points;
        }
    }

    /** The tile edge a point between corners lies on: its slot, times two, plus one for a north-south edge. */
    private int edgeFor(int lx, int ly)
    {
        if (lx % TILE == 0)
        {
            int line = lx / TILE - x0;
            int row = ly / TILE - y0;
            return (line * h + row) << 1 | 1;
        }
        int line = ly / TILE - y0;
        int column = lx / TILE - x0;
        return (line * w + column) << 1;
    }

    private int findEdgePoint(int edge, int lx, int ly)
    {
        int[] points = ((edge & 1) == 1 ? columnEdges : rowEdges)[edge >>> 1];
        if (points != null)
        {
            for (int p : points)
            {
                if (px[p] == lx && py[p] == ly)
                {
                    return p;
                }
            }
        }
        return -1;
    }

    /** Splits the tile's triangle whose edge runs through each point, so both tiles on the edge meet there. */
    private void splitAt(int[] points)
    {
        if (points == null)
        {
            return;
        }
        for (int p : points)
        {
            for (int t = 0; t < count; t++)
            {
                int a = ta[t];
                int b = tb[t];
                int c = tc[t];
                if (between(p, a, b))
                {
                    tb[t] = p;
                    addScratch(p, b, c, tcolor[t]);
                    break;
                }
                if (between(p, b, c))
                {
                    tc[t] = p;
                    addScratch(a, p, c, tcolor[t]);
                    break;
                }
                if (between(p, c, a))
                {
                    tc[t] = p;
                    addScratch(b, c, p, tcolor[t]);
                    break;
                }
            }
        }
    }

    /** Whether a point lies on the straight edge from u to v, strictly between its ends, along a tile edge. */
    private boolean between(int p, int u, int v)
    {
        if (px[u] == px[v] && px[p] == px[u])
        {
            return py[p] > Math.min(py[u], py[v]) && py[p] < Math.max(py[u], py[v]);
        }
        if (py[u] == py[v] && py[p] == py[u])
        {
            return px[p] > Math.min(px[u], px[v]) && px[p] < Math.max(px[u], px[v]);
        }
        return false;
    }

    private int corner(int cx, int cy)
    {
        int i = (cy - y0) * (w + 1) + (cx - x0);
        if (corners[i] == -1)
        {
            corners[i] = vertex(cx * TILE, cy * TILE, cornerHeight(plane, cx, cy));
        }
        return corners[i];
    }

    private int vertex(int lx, int ly, int height)
    {
        int index = top.addVertex(lx, ly, -height);
        if (index == px.length)
        {
            px = Arrays.copyOf(px, index * 2);
            py = Arrays.copyOf(py, index * 2);
        }
        px[index] = lx;
        py[index] = ly;
        return index;
    }

    private void addScratch(int a, int b, int c, int color)
    {
        if (count == ta.length)
        {
            ta = Arrays.copyOf(ta, count * 2);
            tb = Arrays.copyOf(tb, count * 2);
            tc = Arrays.copyOf(tc, count * 2);
            tcolor = Arrays.copyOf(tcolor, count * 2);
        }
        ta[count] = a;
        tb[count] = b;
        tc[count] = c;
        tcolor[count] = color;
        count++;
    }

    /** A tile drawn off the plane, kept as the game draws it, without its hidden parts. */
    private void addLoose(Tile tile, int sx, int sy)
    {
        SceneTileModel shape = tile.getSceneTileModel();
        if (shape != null)
        {
            if (!wellFormed(shape))
            {
                return;
            }
            int[] faceA = shape.getFaceX();
            int[] faceB = shape.getFaceY();
            int[] faceC = shape.getFaceZ();
            int[] vx = shape.getVertexX();
            int[] vy = shape.getVertexY();
            int[] vz = shape.getVertexZ();
            int[] colorA = shape.getTriangleColorA();
            int first = -1;
            for (int f = 0; f < faceA.length; f++)
            {
                if (colorA[f] == HIDDEN)
                {
                    continue;
                }
                if (first == -1)
                {
                    first = rest.addVertex(vx[0], vz[0], -vy[0]);
                    for (int v = 1; v < vx.length; v++)
                    {
                        rest.addVertex(vx[v], vz[v], -vy[v]);
                    }
                }
                rest.addTriangle(first + faceA[f], first + faceB[f], first + faceC[f], faceColor(shape, f));
            }
            return;
        }

        SceneTilePaint paint = tile.getSceneTilePaint();
        if (paint == null || paint.getNeColor() == HIDDEN)
        {
            return;
        }
        int level = tile.getRenderLevel();
        int lx = sx * TILE;
        int ly = sy * TILE;
        int sw = rest.addVertex(lx, ly, -cornerHeight(level, sx, sy));
        int se = rest.addVertex(lx + TILE, ly, -cornerHeight(level, sx + 1, sy));
        int ne = rest.addVertex(lx + TILE, ly + TILE, -cornerHeight(level, sx + 1, sy + 1));
        int nw = rest.addVertex(lx, ly + TILE, -cornerHeight(level, sx, sy + 1));
        int texture = paint.getTexture();
        if (texture != -1 && textures != null)
        {
            int rgb = textures.color(texture);
            rest.addTriangle(sw, se, nw, rgb);
            rest.addTriangle(ne, nw, se, rgb);
        }
        else
        {
            rest.addTriangle(sw, se, nw, average(paint.getSwColor(), paint.getSeColor(), paint.getNwColor()));
            rest.addTriangle(ne, nw, se, average(paint.getNeColor(), paint.getNwColor(), paint.getSeColor()));
        }
    }

    private int faceColor(SceneTileModel shape, int f)
    {
        int a = shape.getTriangleColorA()[f];
        if (a == HIDDEN)
        {
            return VOID;
        }
        int[] faceTextures = shape.getTriangleTextureId();
        if (faceTextures != null && f < faceTextures.length && faceTextures[f] != -1 && textures != null)
        {
            return textures.color(faceTextures[f]);
        }
        return average(a, shape.getTriangleColorB()[f], shape.getTriangleColorC()[f]);
    }

    /** The color of a shape's largest face the game draws, for a shape that can't be laid on the height map. */
    private int largestFaceColor(SceneTileModel shape)
    {
        if (!wellFormed(shape))
        {
            return VOID;
        }
        int[] faceA = shape.getFaceX();
        int[] faceB = shape.getFaceY();
        int[] faceC = shape.getFaceZ();
        int[] vx = shape.getVertexX();
        int[] vz = shape.getVertexZ();
        int[] colorA = shape.getTriangleColorA();
        int best = -1;
        long bestArea = -1;
        for (int f = 0; f < faceA.length; f++)
        {
            if (colorA[f] == HIDDEN)
            {
                continue;
            }
            int a = faceA[f];
            int b = faceB[f];
            int c = faceC[f];
            long area = Math.abs(cross(vx[a], vz[a], vx[b], vz[b], vx[c], vz[c]));
            if (area > bestArea)
            {
                bestArea = area;
                best = f;
            }
        }
        return best == -1 ? VOID : faceColor(shape, best);
    }

    /** One RGB color for three of the game's HSL corner colors, leaving out a hidden one. */
    private static int average(int a, int b, int c)
    {
        if (b == HIDDEN)
        {
            b = a;
        }
        if (c == HIDDEN)
        {
            c = a;
        }
        return JagexColor.hslToRgbAverage(a, b, c);
    }
}
