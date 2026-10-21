package com.constsmodels;

import java.util.Arrays;

/**
 * An area capture put together as one piece, a diorama: the ground closed into a solid block, side walls running
 * down from its edge to a flat bottom, with everything in the area standing on it. It's centered on your tile: x and y
 * count from the middle of it and z from the ground there.
 *
 * <p>Parts come in the scene's local coordinates (128 to a tile) turned into east, north, up, as
 * {@link GroundCapture} gives them, and are moved by the center as they're added.
 */
final class Diorama
{
    /** How far below the lowest ground the bottom is: a tile, so even the lowest point has some ground under it. */
    static final int DEPTH = 128;

    /** The side walls' and the bottom's color: dark earth. */
    static final int EARTH = 0x4A3B2A;

    private final MeshBuilder mesh;
    private final float centerX;
    private final float centerY;
    private final float centerZ;
    private int vertices;

    /** A diorama centered on a point in local coordinates, z up: the middle of your tile, at the ground. */
    Diorama(String name, float centerX, float centerY, float centerZ)
    {
        this.mesh = new MeshBuilder(name);
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
    }

    /** What an area capture is called: "Area at 3222, 3218", from your tile's world coordinates. */
    static String name(int worldX, int worldY)
    {
        return "Area at " + worldX + ", " + worldY;
    }

    /**
     * Adds the ground closed into a block. The surface has to cover the rectangle from (minX, minY) to (maxX, maxY)
     * exactly, its triangles wound up and sharing the vertices along every edge inside it, as
     * {@link GroundCapture#surface} does. Each edge along the rectangle's sides gets a wall down to the bottom, and
     * the bottom closes the walls.
     */
    Diorama addGround(Mesh surface, float minX, float minY, float maxX, float maxY)
    {
        if (surface.isEmpty())
        {
            return this;
        }
        int first = vertices;
        add(surface);

        float bottom = surface.bounds()[2] - DEPTH - centerZ;
        int middle = addVertex((minX + maxX) / 2 - centerX, (minY + maxY) / 2 - centerY, bottom);
        int[] below = new int[surface.vertexCount()];
        Arrays.fill(below, -1);

        for (int f = 0; f < surface.faceCount(); f++)
        {
            for (int edge = 0; edge < 3; edge++)
            {
                int u = edge == 0 ? surface.a[f] : edge == 1 ? surface.b[f] : surface.c[f];
                int v = edge == 0 ? surface.b[f] : edge == 1 ? surface.c[f] : surface.a[f];
                if (!alongSide(surface, u, v, minX, minY, maxX, maxY))
                {
                    continue;
                }
                // The wall under the top edge from u to v, facing out, and the slice of bottom inside it
                int bottomU = under(surface, u, below, bottom);
                int bottomV = under(surface, v, below, bottom);
                mesh.addTriangle(first + v, first + u, bottomU, EARTH);
                mesh.addTriangle(first + v, bottomU, bottomV, EARTH);
                mesh.addTriangle(middle, bottomV, bottomU, EARTH);
            }
        }
        return this;
    }

    /** Whether an edge runs along one of the rectangle's sides, where the ground ends. */
    private static boolean alongSide(Mesh surface, int u, int v, float minX, float minY, float maxX, float maxY)
    {
        float ux = surface.x[u];
        float uy = surface.y[u];
        return ux == surface.x[v] && (ux == minX || ux == maxX) || uy == surface.y[v] && (uy == minY || uy == maxY);
    }

    private int under(Mesh surface, int v, int[] below, float bottom)
    {
        if (below[v] == -1)
        {
            below[v] = addVertex(surface.x[v] - centerX, surface.y[v] - centerY, bottom);
        }
        return below[v];
    }

    /** Adds a part as it is, such as ground off the plane. */
    Diorama add(Mesh part)
    {
        return add(part, 0, 0, 0, 0);
    }

    /**
     * Adds something standing in the area: turned by a game orientation (2048 to a full turn) and moved to its place
     * in local coordinates, z up.
     */
    Diorama add(Mesh part, int orientation, float x, float y, float z)
    {
        mesh.add(part, orientation, x - centerX, y - centerY, z - centerZ);
        vertices += part.vertexCount();
        return this;
    }

    private int addVertex(float x, float y, float z)
    {
        vertices++;
        return mesh.addVertex(x, y, z);
    }

    Mesh build()
    {
        return mesh.build();
    }
}
