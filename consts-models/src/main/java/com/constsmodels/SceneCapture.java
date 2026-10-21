package com.constsmodels;

import net.runelite.api.Client;

/**
 * TICKET 4 (stub): Capture area. Everything around the player, out to a radius in tiles, as one mesh: the ground
 * (tile shapes, heights and colors), objects, walls, decorations, ground items, NPCs and players, each where and how
 * the game draws it. Use it on the client thread.
 */
final class SceneCapture
{
    private SceneCapture()
    {
    }

    /** The area around the local player as a mesh; an empty mesh when there's nothing to capture. */
    static Mesh capture(Client client, int radius, TextureColors textures)
    {
        return new MeshBuilder("Area").build();
    }
}
