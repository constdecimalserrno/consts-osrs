/*
 * Copyright (c) 2021, Adam <Adam@sigterm.info>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's Interact Highlight
 *   plugin into consts-Destination; the tracking moved out of the plugin class, the outline colors follow
 *   consts-Destination's settings)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsdestination;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Node;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;

/**
 * What you're hovering and what you're interacting with - NPCs, players, objects and ground items - worked out from
 * the menu the way RuneLite's Interact Highlight plugin by Adam does. consts-Destination outlines what this finds.
 */
@Singleton
class InteractTracker
{
    /** The kinds of thing you can act on, by the menu entry that targets them. */
    enum Kind
    {
        NPC, PLAYER, OBJECT, ITEM
    }

    private final Client client;

    private TileObject interactedObject;
    private TileItem interactedItem;
    private Actor interactedActor;
    private boolean attacked;
    private int clickTick;
    private int gameCycle;

    @Inject
    InteractTracker(Client client)
    {
        this.client = client;
    }

    /** The kind of thing a menu entry acts on, or null for anything else (walk here, interface, items). */
    static Kind kindOf(MenuAction action)
    {
        switch (action)
        {
            case WIDGET_TARGET_ON_GAME_OBJECT:
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case EXAMINE_OBJECT:
                return Kind.OBJECT;
            case WIDGET_TARGET_ON_GROUND_ITEM:
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
            case EXAMINE_ITEM_GROUND:
                return Kind.ITEM;
            case WIDGET_TARGET_ON_NPC:
            case NPC_FIRST_OPTION:
            case NPC_SECOND_OPTION:
            case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION:
            case EXAMINE_NPC:
                return Kind.NPC;
            case WIDGET_TARGET_ON_PLAYER:
            case PLAYER_FIRST_OPTION:
            case PLAYER_SECOND_OPTION:
            case PLAYER_THIRD_OPTION:
            case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION:
            case PLAYER_SIXTH_OPTION:
            case PLAYER_SEVENTH_OPTION:
            case PLAYER_EIGHTH_OPTION:
                return Kind.PLAYER;
            default:
                return null;
        }
    }

    /** The menu entry under the cursor: the hovered row of an open menu, else the left-click option. */
    MenuEntry hoveredEntry()
    {
        MenuEntry[] menuEntries = client.getMenuEntries();
        if (menuEntries == null || menuEntries.length == 0)
        {
            return null;
        }
        if (!client.isMenuOpen())
        {
            return menuEntries[menuEntries.length - 1];
        }

        final int menuX = client.getMenuX();
        final int menuY = client.getMenuY();
        final int menuWidth = client.getMenuWidth();
        final Point mousePosition = client.getMouseCanvasPosition();

        int dy = mousePosition.getY() - menuY;
        dy -= 19; // Height of Choose Option
        if (dy < 0)
        {
            return menuEntries[menuEntries.length - 1];
        }

        int idx = dy / 15; // Height of each menu option
        idx = menuEntries.length - 1 - idx;

        if (mousePosition.getX() > menuX && mousePosition.getX() < menuX + menuWidth
            && idx >= 0 && idx < menuEntries.length)
        {
            return menuEntries[idx];
        }
        return menuEntries[menuEntries.length - 1];
    }

    /** Whether hovering this entry means attacking: an NPC's second option, or casting a combat spell on one. */
    boolean isAttack(MenuAction action)
    {
        return action == MenuAction.NPC_SECOND_OPTION
            || action == MenuAction.WIDGET_TARGET_ON_NPC
                && client.getSelectedWidget() != null
                && WidgetUtil.componentToInterface(client.getSelectedWidget().getId()) == InterfaceID.MAGIC_SPELLBOOK;
    }

    void clear()
    {
        interactedObject = null;
        interactedItem = null;
        interactedActor = null;
        attacked = false;
    }

    void onLoading()
    {
        interactedObject = null;
    }

    void onNpcSpawned(NPC npc)
    {
        final Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null && npc == localPlayer.getInteracting())
        {
            interactedActor = npc;
        }
    }

    void onPlayerSpawned(Player otherPlayer)
    {
        final Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null && otherPlayer == localPlayer.getInteracting())
        {
            interactedActor = otherPlayer;
        }
    }

    void onActorDespawned(Actor actor)
    {
        if (actor == interactedActor)
        {
            interactedActor = null;
        }
    }

    void onItemDespawned(TileItem item)
    {
        if (item == interactedItem)
        {
            interactedObject = null;
            interactedItem = null;
        }
    }

    void onGameTick()
    {
        if (client.getTickCount() > clickTick && client.getLocalDestinationLocation() == null && interactedActor == null)
        {
            // when the destination is reached, clear the interacting object
            interactedObject = null;
            interactedItem = null;
        }
    }

    void onInteractingChanged(Actor source, Actor target)
    {
        if (source == client.getLocalPlayer() && client.getTickCount() > clickTick)
        {
            interactedObject = null;
            interactedItem = null;
            interactedActor = target;
            attacked = target != null && target.getCombatLevel() > 0;
        }
    }

    void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
    {
        MenuAction action = menuOptionClicked.getMenuAction();
        Kind kind = kindOf(action);
        if (kind != null && action != MenuAction.EXAMINE_OBJECT && action != MenuAction.EXAMINE_ITEM_GROUND
            && action != MenuAction.EXAMINE_NPC)
        {
            int worldId = menuOptionClicked.getMenuEntry().getWorldViewId();
            int x = menuOptionClicked.getParam0();
            int y = menuOptionClicked.getParam1();
            int id = menuOptionClicked.getId();
            switch (kind)
            {
                case OBJECT:
                    interactedObject = findTileObject(worldId, x, y, id);
                    interactedItem = null;
                    interactedActor = null;
                    break;
                case NPC:
                    interactedObject = null;
                    interactedItem = null;
                    interactedActor = menuOptionClicked.getMenuEntry().getNpc();
                    attacked = isAttack(action);
                    break;
                case PLAYER:
                    interactedObject = null;
                    interactedItem = null;
                    interactedActor = menuOptionClicked.getMenuEntry().getPlayer();
                    attacked = false;
                    break;
                case ITEM:
                    ItemLayer layer = findItemLayer(worldId, x, y);
                    interactedObject = layer;
                    interactedItem = findItem(layer, id);
                    interactedActor = null;
                    break;
            }
            clickTick = client.getTickCount();
            gameCycle = client.getGameCycle();
        }
        else if (action == MenuAction.WIDGET_TARGET_ON_WIDGET || action == MenuAction.WALK
            || menuOptionClicked.isItemOp())
        {
            // Any menu click which clears an interaction
            interactedObject = null;
            interactedActor = null;
        }
    }

    TileObject findTileObject(int worldId, int x, int y, int id)
    {
        Tile tile = sceneTile(worldId, x, y);
        if (tile != null)
        {
            for (GameObject gameObject : tile.getGameObjects())
            {
                if (gameObject != null && objIdEquals(id, gameObject.getId()))
                {
                    return gameObject;
                }
            }

            WallObject wallObject = tile.getWallObject();
            if (wallObject != null && objIdEquals(id, wallObject.getId()))
            {
                return wallObject;
            }

            DecorativeObject decorativeObject = tile.getDecorativeObject();
            if (decorativeObject != null && objIdEquals(id, decorativeObject.getId()))
            {
                return decorativeObject;
            }

            GroundObject groundObject = tile.getGroundObject();
            if (groundObject != null && objIdEquals(id, groundObject.getId()))
            {
                return groundObject;
            }
        }
        return null;
    }

    private boolean objIdEquals(int id1 /* base id or multiloc id */, int id2 /* base id */)
    {
        if (id1 == id2)
        {
            return true;
        }

        var obj = client.getObjectDefinition(id2);
        obj = obj.getImpostorIds() == null ? obj : obj.getImpostor();
        return obj != null && obj.getId() == id1;
    }

    ItemLayer findItemLayer(int worldId, int x, int y)
    {
        Tile tile = sceneTile(worldId, x, y);
        return tile != null ? tile.getItemLayer() : null;
    }

    private Tile sceneTile(int worldId, int x, int y)
    {
        WorldView wv = client.getWorldView(worldId);
        if (wv == null)
        {
            return null;
        }
        int offset = worldId == WorldView.TOPLEVEL ? (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2 : 0;
        Scene scene = wv.getScene();
        Tile[][][] tiles = scene.getExtendedTiles();
        return tiles[wv.getPlane()][x + offset][y + offset];
    }

    static TileItem findItem(ItemLayer layer, int id)
    {
        if (layer == null)
        {
            return null;
        }

        Node current = layer.getTop();
        while (current instanceof TileItem)
        {
            final TileItem item = (TileItem) current;
            current = current.getNext();

            if (item.getId() == id)
            {
                return item;
            }
        }
        return null;
    }

    TileObject getInteractedObject()
    {
        return interactedObject;
    }

    TileItem getInteractedItem()
    {
        return interactedItem;
    }

    Actor getInteractedActor()
    {
        return interactedActor;
    }

    boolean isAttacked()
    {
        return attacked;
    }

    int getGameCycle()
    {
        return gameCycle;
    }
}
