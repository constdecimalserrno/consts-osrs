/*
 * Copyright (c) 2023, Jason O'Neill
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from Tree Despawn Timer,
 *   TreeDespawnTimerPlugin.java, into a tracker consts-EXP feeds its events; no debug levels or chat messages,
 *   no timer thread, since only the pie is drawn, the top-level world view instead of the deprecated client calls)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsexp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.Angle;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;

/**
 * Every tree in the scene and who is chopping it, followed the way Tree Despawn Timer does, so each tree can show how
 * long it has left. A player is chopping the tree they face while playing a woodcutting animation.
 *
 * Only the client thread touches it: game events, and the overlay drawing the timers.
 */
@Singleton
class TreeTimer
{
    // A player who starts chopping this soon after showing up, without moving, was most likely chopping already.
    static final int PLAYER_SPAWNED_TICKS = 8;
    // Lumber Up, the axe special attack, plays an animation of its own; it counts as chopping for this long after.
    private static final int PLAYER_SPEC_TICKS = 8;
    // Chopping animations often start facing the wrong way, so who chops what is checked again this often.
    private static final int ANIMATION_RECHECK_TICKS = 4;
    // Every 15 seconds, trees far away and untouched, or out of time with nobody on them, are forgotten.
    private static final int GARBAGE_COLLECT_TICKS = 25;
    private static final int FAR_AWAY_TILES = 150;
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final Client client;

    // Each tile of each tree, and each tree once.
    private final Map<WorldPoint, TreeTimerState> treeAtLocation = new HashMap<>();
    private final Set<TreeTimerState> trees = new HashSet<>();
    private final Map<Player, TreeTimerState> playerTreeChopping = new HashMap<>();
    // Ticks left in each player's first few after showing up, and where they showed up.
    private final Map<Player, Integer> newlySpawnedPlayers = new HashMap<>();
    private final Map<Player, WorldPoint> playerSpawnLocation = new HashMap<>();
    // Animation changes are handled on the next game tick, with that tick's other changes, as Tree Despawn Timer does.
    private final List<Runnable> deferTickQueue = new ArrayList<>();
    private final Map<Player, Integer> playerRecentlySpeced = new HashMap<>();
    private int nextGarbageCollect = GARBAGE_COLLECT_TICKS;
    // Counts down the ticks after the local player changes plane (at the redwoods: climbs up or down) or logs in.
    private int localPlayerRecentlyClimbedRedwood;
    private int currentPlayerPlane;
    private int nextAnimationRecheck;

    @Inject
    TreeTimer(Client client)
    {
        this.client = client;
    }

    /**
     * At startup: everyone already in view counts as just arrived, so a tree they are already chopping stays hidden.
     * Read on the next game tick.
     */
    void start()
    {
        deferTickQueue.add(() ->
        {
            for (Player player : players())
            {
                onPlayerSpawned(player);
                handlePlayerChopping(player);
            }
        });
    }

    void clear()
    {
        treeAtLocation.clear();
        trees.clear();
        playerTreeChopping.clear();
        newlySpawnedPlayers.clear();
        playerSpawnLocation.clear();
        deferTickQueue.clear();
        playerRecentlySpeced.clear();
    }

    /** The trees to draw, each once. */
    Collection<TreeTimerState> getTrees()
    {
        return Collections.unmodifiableSet(trees);
    }

    // Game events

    void onGameStateChanged(GameState state)
    {
        // A new world or a new login: every tree is seen again from scratch.
        if (state == GameState.HOPPING || state == GameState.LOGGING_IN)
        {
            clear();
            localPlayerRecentlyClimbedRedwood = PLAYER_SPAWNED_TICKS;
        }
    }

    void onGameTick()
    {
        computePlayerPlaneChange();
        if (localPlayerRecentlyClimbedRedwood > 0)
        {
            localPlayerRecentlyClimbedRedwood--;
        }
        List<Runnable> deferred = new ArrayList<>(deferTickQueue);
        deferTickQueue.clear();
        deferred.forEach(Runnable::run);

        trees.forEach(TreeTimerState::tick);
        nextGarbageCollect--;
        if (nextGarbageCollect <= 0)
        {
            nextGarbageCollect = GARBAGE_COLLECT_TICKS;
            collectGarbage();
        }

        nextAnimationRecheck--;
        if (nextAnimationRecheck <= 0)
        {
            nextAnimationRecheck = ANIMATION_RECHECK_TICKS;
            for (Player player : players())
            {
                handlePlayerChopping(player);
            }
        }

        newlySpawnedPlayers.entrySet().removeIf(p ->
        {
            p.setValue(p.getValue() - 1);
            if (p.getValue() <= 0)
            {
                playerSpawnLocation.remove(p.getKey());
            }
            return p.getValue() <= 0;
        });
        playerRecentlySpeced.entrySet().removeIf(p ->
        {
            p.setValue(p.getValue() - 1);
            return p.getValue() <= 0;
        });
    }

    void onGameObjectSpawned(GameObject gameObject)
    {
        if (!TreeTimerTree.isTree(gameObject) || treeAtLocation.containsKey(gameObject.getWorldLocation()))
        {
            return;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return;
        }

        TreeTimerState tree = TreeTimerState.of(client, worldView, gameObject);
        tree.getPoints().forEach(point -> treeAtLocation.put(point, tree));
        trees.add(tree);
    }

    void onGameObjectDespawned(GameObject gameObject)
    {
        if (!TreeTimerTree.isTree(gameObject))
        {
            return;
        }

        TreeTimerState tree = treeAtLocation.get(gameObject.getWorldLocation());
        if (tree != null)
        {
            deleteTree(tree);
        }
    }

    void onAnimationChanged(Actor actor)
    {
        if (actor instanceof Player)
        {
            Player player = (Player) actor;
            deferTickQueue.add(() -> handlePlayerChopping(player));
        }
    }

    void onPlayerSpawned(Player player)
    {
        if (player == null || player.equals(client.getLocalPlayer()))
        {
            return;
        }
        newlySpawnedPlayers.put(player, PLAYER_SPAWNED_TICKS);
        playerSpawnLocation.put(player, player.getWorldLocation());
    }

    void onPlayerDespawned(Player player)
    {
        TreeTimerState tree = playerTreeChopping.remove(player);
        if (tree == null)
        {
            return;
        }

        boolean redwood = tree.getTree() == TreeTimerTree.REDWOOD;
        if (redwood)
        {
            computePlayerPlaneChange();
        }

        // Out of view, still chopping. At the redwoods a player vanishing is usually one climbing down, unless it was
        // us who just climbed.
        if (!redwood || hasLocalPlayerRecentlyClimbedRedwood())
        {
            tree.playerChoppingUnrendered(player);
        }
        else
        {
            tree.removePlayerChopping(player);
        }
    }

    /** A Woodcutting XP drop: you took a log from the tree you're chopping. */
    void onStatChanged(Skill skill)
    {
        if (skill != Skill.WOODCUTTING)
        {
            return;
        }

        TreeTimerState tree = playerTreeChopping.get(client.getLocalPlayer());
        if (tree != null)
        {
            tree.choppedLog();
        }
    }

    // Who chops what

    void handlePlayerChopping(Player player)
    {
        // A player who has moved since showing up wasn't already chopping.
        if (hasMoved(player))
        {
            newlySpawnedPlayers.remove(player);
            playerSpawnLocation.remove(player);
        }
        boolean isNewPlayer = newlySpawnedPlayers.containsKey(player);
        TreeTimerState previousTree = playerTreeChopping.get(player);
        TreeTimerState newTree = null;
        if (isWoodcutting(player))
        {
            TreeTimerState interactingTree = findClosestFacingTree(player);
            if (interactingTree == null)
            {
                return;
            }
            newTree = interactingTree;
            // A player showed up and started chopping almost at once: they have most likely been at it a while.
            if (isNewPlayer && !interactingTree.hasUnrenderedPlayersChopping()
                && interactingTree.getPlayersChopping().isEmpty())
            {
                if (interactingTree.getTree() == TreeTimerTree.REDWOOD)
                {
                    // At the redwoods, players show up by climbing the ladder too. Only when we just climbed up is
                    // everyone already there likely to have been chopping.
                    if (hasLocalPlayerRecentlyClimbedRedwood())
                    {
                        interactingTree.hide();
                    }
                }
                else
                {
                    interactingTree.hide();
                }
            }
            interactingTree.addPlayerChopping(player);
            playerTreeChopping.put(player, interactingTree);
        }
        if (previousTree != newTree && previousTree != null)
        {
            previousTree.removePlayerChopping(player);
            if (newTree == null)
            {
                playerTreeChopping.remove(player);
            }
        }
    }

    /** The tree the player faces, or the only tree next to them, since a player starting to chop can face away. */
    TreeTimerState findClosestFacingTree(Player player)
    {
        WorldPoint actorLocation = player.getWorldLocation();
        if (actorLocation == null)
        {
            return null;
        }

        boolean[] hasNeighbor = new boolean[DIRECTIONS.length];
        int neighborTrees = 0;
        for (int i = 0; i < DIRECTIONS.length; i++)
        {
            hasNeighbor[i] = treeAtLocation.containsKey(neighborPoint(actorLocation, DIRECTIONS[i]));
            neighborTrees += hasNeighbor[i] ? 1 : 0;
        }
        if (neighborTrees == 1)
        {
            for (int i = 0; i < DIRECTIONS.length; i++)
            {
                if (hasNeighbor[i])
                {
                    return treeAtLocation.get(neighborPoint(actorLocation, DIRECTIONS[i]));
                }
            }
        }

        Direction direction = new Angle(player.getOrientation()).getNearestDirection();
        WorldPoint facingPoint = neighborPoint(actorLocation, direction);
        if (treeAtLocation.containsKey(facingPoint))
        {
            return treeAtLocation.get(facingPoint);
        }

        // Since June 2026 a player chopping faces the tree's south-west corner, so a tree east of someone facing south
        // or north of someone facing west may be the one.
        if (direction == Direction.SOUTH)
        {
            return treeAtLocation.get(neighborPoint(actorLocation, Direction.EAST));
        }
        if (direction == Direction.WEST)
        {
            return treeAtLocation.get(neighborPoint(actorLocation, Direction.NORTH));
        }
        return null;
    }

    private static WorldPoint neighborPoint(WorldPoint point, Direction direction)
    {
        switch (direction)
        {
            case NORTH:
                return point.dy(1);
            case SOUTH:
                return point.dy(-1);
            case EAST:
                return point.dx(1);
            case WEST:
                return point.dx(-1);
            default:
                throw new IllegalStateException();
        }
    }

    // Chopping with any axe, 1-handed or 2-handed, or the Lumber Up special.
    private boolean isWoodcutting(Player player)
    {
        if (playerRecentlySpeced.containsKey(player))
        {
            return true;
        }
        switch (player.getAnimation())
        {
            // 1H axes
            case AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE:
            case AnimationID.HUMAN_WOODCUTTING_IRON_AXE:
            case AnimationID.HUMAN_WOODCUTTING_STEEL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_BLACK_AXE:
            case AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE:
            case AnimationID.HUMAN_WOODCUTTING_RUNE_AXE:
            case AnimationID.HUMAN_WOODCUTTING_GILDED_AXE:
            case AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE:
            case AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE:
            case AnimationID.HUMAN_WOODCUTTING_3A_AXE:
            case AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE:
            // 2H axes
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_3A:
                return true;
            // Lumber Up, the special attack
            case AnimationID.DRAGON_SMALLAXE_ANIM:
            case AnimationID.DRAGON_SMALLAXE_SWOOSH:
                playerRecentlySpeced.put(player, PLAYER_SPEC_TICKS);
                return true;
            default:
                return false;
        }
    }

    private boolean hasMoved(Player player)
    {
        WorldPoint spawnLocation = playerSpawnLocation.get(player);
        return spawnLocation != null && !spawnLocation.equals(player.getWorldLocation());
    }

    // Trees coming and going

    private void collectGarbage()
    {
        Player localPlayer = client.getLocalPlayer();
        WorldPoint here = localPlayer != null ? localPlayer.getWorldLocation() : null;
        List<TreeTimerState> toDelete = new ArrayList<>();
        for (TreeTimerState tree : trees)
        {
            // Trees far away on our plane that show nothing, and trees out of time with nobody chopping.
            boolean isFarAway = here != null && tree.getWorldPoint().getPlane() == here.getPlane()
                && tree.getWorldPoint().distanceTo(here) > FAR_AWAY_TILES;
            if ((isFarAway && !tree.shouldShowTimer())
                || (tree.getTimeTicks() <= 0 && tree.getPlayersChopping().isEmpty()))
            {
                toDelete.add(tree);
            }
        }
        toDelete.forEach(this::deleteTree);
    }

    private void deleteTree(TreeTimerState tree)
    {
        tree.getPlayersChopping().forEach(playerTreeChopping::remove);
        tree.getPoints().forEach(treeAtLocation::remove);
        trees.remove(tree);
    }

    private void computePlayerPlaneChange()
    {
        Player localPlayer = client.getLocalPlayer();
        WorldPoint location = localPlayer != null ? localPlayer.getWorldLocation() : null;
        if (location == null)
        {
            return;
        }

        int newPlane = location.getPlane();
        if (newPlane != currentPlayerPlane)
        {
            currentPlayerPlane = newPlane;
            localPlayerRecentlyClimbedRedwood = PLAYER_SPAWNED_TICKS;
        }
    }

    private boolean hasLocalPlayerRecentlyClimbedRedwood()
    {
        return localPlayerRecentlyClimbedRedwood > 0;
    }

    private Iterable<? extends Player> players()
    {
        WorldView worldView = client.getTopLevelWorldView();
        return worldView != null ? worldView.players() : Collections.emptyList();
    }
}
