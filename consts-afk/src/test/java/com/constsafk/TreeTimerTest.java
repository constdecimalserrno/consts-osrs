package com.constsafk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigItem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;

public class TreeTimerTest
{
    private static final int BASE_X = 3136;
    private static final int BASE_Y = 3136;
    // Orientations, as Angle reads them.
    private static final int FACING_WEST = 512;
    private static final int FACING_EAST = 1536;
    private static final int CHOPPING = AnimationID.HUMAN_WOODCUTTING_RUNE_AXE;

    private static final WorldPoint MY_TILE = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint OAK_TILE = new WorldPoint(3201, 3200, 0);
    // East of the oak, so the oak is the only tree next to them.
    private static final WorldPoint THEIR_TILE = new WorldPoint(3202, 3200, 0);

    private Client client;
    private Player me;
    private final List<Player> inView = new ArrayList<>();
    private TreeTimer timer;
    private long now = 1_000_000L;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp()
    {
        client = mock(Client.class);
        WorldView worldView = mock(WorldView.class);
        when(client.getTopLevelWorldView()).thenReturn(worldView);
        when(worldView.getBaseX()).thenReturn(BASE_X);
        when(worldView.getBaseY()).thenReturn(BASE_Y);
        IndexedObjectSet<Player> players = mock(IndexedObjectSet.class);
        when(players.iterator()).thenAnswer(invocation -> inView.iterator());
        doReturn(players).when(worldView).players();

        me = player("Me", MY_TILE, FACING_EAST);
        when(client.getLocalPlayer()).thenReturn(me);
        timer = new TreeTimer(client);
    }

    // The health model

    @Test
    public void choppingAloneShouldOnlyStartTheClockWithYourFirstLog()
    {
        TreeTimerState oak = spawnTree(ObjectID.OAKTREE, OAK_TILE);
        startChopping(me);
        tick(5);

        assertEquals("no log yet, so no health lost", 45, oak.getTimeTicks());
        assertTrue("shown while you chop it", oak.shouldShowTimer());

        timer.onStatChanged(Skill.ATTACK);
        tick(1);
        assertEquals("only Woodcutting XP is a log", 45, oak.getTimeTicks());

        timer.onStatChanged(Skill.WOODCUTTING);
        tick(3);
        assertEquals("then 1 a tick", 42, oak.getTimeTicks());
    }

    @Test
    public void aTreeNobodyChopsShouldClimbBackToFull()
    {
        TreeTimerState oak = spawnTree(ObjectID.OAKTREE, OAK_TILE);
        startChopping(me);
        tick(1);
        timer.onStatChanged(Skill.WOODCUTTING);
        tick(10);
        assertEquals(35, oak.getTimeTicks());

        stopChopping(me);
        tick(1);
        assertEquals("back up 1 a tick", 36, oak.getTimeTicks());
        assertTrue("shown while it's below full", oak.shouldShowTimer());

        tick(20);
        assertEquals("never above full", 45, oak.getTimeTicks());
        assertFalse("full and nobody on it: nothing to show", oak.shouldShowTimer());

        startChopping(me);
        tick(3);
        assertEquals("back at full, it waits for a first log again", 45, oak.getTimeTicks());
    }

    @Test
    public void anotherPlayerChoppingShouldWearItDownWithoutWaitingForALog()
    {
        TreeTimerState oak = spawnTree(ObjectID.OAKTREE, OAK_TILE);
        Player them = player("Them", THEIR_TILE, FACING_WEST);
        inView.add(them);

        startChopping(them);
        tick(5);

        assertEquals("someone else is taken to have had their log", 40, oak.getTimeTicks());
        assertTrue(oak.shouldShowTimer());
        assertTrue(oak.getPlayersChopping().contains(them));
    }

    @Test
    public void aChopperGoingOutOfViewShouldStillCount()
    {
        TreeTimerState oak = spawnTree(ObjectID.OAKTREE, OAK_TILE);
        Player them = player("Them", THEIR_TILE, FACING_WEST);
        startChopping(them);
        tick(1);

        timer.onPlayerDespawned(them);
        tick(4);

        assertTrue(oak.getPlayersChopping().isEmpty());
        assertTrue(oak.hasUnrenderedPlayersChopping());
        assertEquals("still going down", 40, oak.getTimeTicks());
    }

    // Confidence

    @Test
    public void aTreeAlreadyBeingChoppedWhenYouArriveShouldShowNoTime()
    {
        TreeTimerState oak = spawnTree(ObjectID.OAKTREE, OAK_TILE);
        Player them = player("Them", THEIR_TILE, FACING_WEST);
        when(them.getAnimation()).thenReturn(CHOPPING);
        inView.add(them);

        // They come into view already chopping.
        timer.onPlayerSpawned(them);
        timer.onAnimationChanged(them);
        tick(1);

        assertTrue(oak.isHidden());
        assertFalse("its time can't be known", oak.shouldShowTimer());

        startChopping(me);
        timer.onStatChanged(Skill.WOODCUTTING);
        tick(10);
        assertFalse("chopping it yourself doesn't make the guess any better", oak.shouldShowTimer());
    }

    @Test
    public void aPlayerWhoWalkedUpBeforeChoppingShouldNotHideTheTree()
    {
        TreeTimerState oak = spawnTree(ObjectID.OAKTREE, OAK_TILE);
        Player them = player("Them", new WorldPoint(3206, 3200, 0), FACING_WEST);
        inView.add(them);

        timer.onPlayerSpawned(them);
        tick(2);
        when(them.getWorldLocation()).thenReturn(THEIR_TILE);
        startChopping(them);
        tick(1);

        assertFalse("they walked up to it, so they started it here", oak.isHidden());
        assertTrue(oak.shouldShowTimer());
    }

    @Test
    public void aNewcomerShouldNotHideATreeAlreadyBeingTimed()
    {
        TreeTimerState oak = spawnTree(ObjectID.OAKTREE, OAK_TILE);
        startChopping(me);
        tick(1);

        Player them = player("Them", THEIR_TILE, FACING_WEST);
        when(them.getAnimation()).thenReturn(CHOPPING);
        timer.onPlayerSpawned(them);
        timer.onAnimationChanged(them);
        tick(1);

        assertFalse("we were already on it, so its time is known", oak.isHidden());
    }

    @Test
    public void everyoneInViewAtStartupShouldCountAsAlreadyThere()
    {
        TreeTimerState oak = spawnTree(ObjectID.OAKTREE, OAK_TILE);
        Player them = player("Them", THEIR_TILE, FACING_WEST);
        when(them.getAnimation()).thenReturn(CHOPPING);
        inView.add(them);

        timer.start();
        tick(1);

        assertFalse(oak.shouldShowTimer());
    }

    // Which tree a player chops

    @Test
    public void aPlayerBetweenTwoTreesShouldChopTheOneTheyFace()
    {
        TreeTimerState west = spawnTree(ObjectID.OAKTREE, new WorldPoint(3199, 3200, 0));
        TreeTimerState east = spawnTree(ObjectID.OAKTREE, OAK_TILE);

        assertSame(east, timer.findClosestFacingTree(me));

        when(me.getOrientation()).thenReturn(FACING_WEST);
        assertSame(west, timer.findClosestFacingTree(me));
    }

    @Test
    public void aBigTreeShouldCoverEveryTileItStandsOn()
    {
        GameObject object = treeObject(ObjectID.MAGICTREE, OAK_TILE, 2, 2);
        timer.onGameObjectSpawned(object);
        TreeTimerState magic = onlyTree();

        assertEquals(4, magic.getPoints().size());
        assertTrue(magic.getPoints().contains(new WorldPoint(3202, 3201, 0)));
        assertEquals("the timer sits in the middle of the 2x2", new Point(64, 64), magic.getCenterOffset());

        Player them = player("Them", new WorldPoint(3203, 3201, 0), FACING_WEST);
        assertSame("next to any of its tiles is next to it", magic, timer.findClosestFacingTree(them));
    }

    // Trees coming and going

    @Test
    public void aFallenTreeShouldBeForgotten()
    {
        GameObject object = treeObject(ObjectID.OAKTREE, OAK_TILE, 1, 1);
        timer.onGameObjectSpawned(object);
        timer.onGameObjectSpawned(object);
        assertEquals("seen twice is still one tree", 1, timer.getTrees().size());

        timer.onGameObjectDespawned(object);

        assertTrue(timer.getTrees().isEmpty());
        assertNull(timer.findClosestFacingTree(me));
    }

    @Test
    public void hoppingShouldForgetEveryTree()
    {
        spawnTree(ObjectID.OAKTREE, OAK_TILE);

        timer.onGameStateChanged(GameState.LOADING);
        assertEquals("a loading screen keeps them", 1, timer.getTrees().size());

        timer.onGameStateChanged(GameState.HOPPING);
        assertTrue(timer.getTrees().isEmpty());
    }

    @Test
    public void treesShouldBeKnownByObjectAndRegion()
    {
        assertEquals(TreeTimerTree.YEW, TreeTimerTree.forObject(ObjectID.YEWTREE));
        assertEquals(TreeTimerTree.YEW, TreeTimerTree.forObject(ObjectID.TREE_YEW_DEFAULT01));
        assertEquals(TreeTimerTree.REDWOOD, TreeTimerTree.forObject(ObjectID.REDWOODTREE_R));
        assertEquals(TreeTimerTree.ROSEWOOD, TreeTimerTree.forObject(ObjectID.ROSEWOOD_TREE));
        assertNull("a rock isn't a tree", TreeTimerTree.forObject(ObjectID.GATHERING_EVENT_RISING_ROOTS));

        assertTrue(TreeTimerTree.isTree(treeObject(ObjectID.MAPLETREE, OAK_TILE, 1, 1)));
        assertFalse("Miscellania's maples never fall",
            TreeTimerTree.isTree(treeObject(ObjectID.MAPLETREE, new WorldPoint(2500, 3850, 0), 1, 1)));
    }

    @Test
    public void treeTimesShouldBeTheWikisInGameTicks()
    {
        assertEquals("27 s", 45, TreeTimerTree.OAK.getMaxTicks());
        assertEquals("1:54", 190, TreeTimerTree.YEW.getMaxTicks());
        assertEquals("3:54", 390, TreeTimerTree.MAGIC.getMaxTicks());
        assertEquals("4:24", 440, TreeTimerTree.REDWOOD.getMaxTicks());
    }

    // Display

    @Test
    public void theTimerShouldReadAsTheDisplaySettingAsks()
    {
        TreeTimerState oak = tree(TreeTimerTree.OAK, OAK_TILE);
        TreeTimerState redwood = tree(TreeTimerTree.REDWOOD, OAK_TILE);

        assertNull("the pie has no text", ConstsAfkTreeTimerOverlay.timerText(TreeTimerDisplay.PIE, oak, 0));
        assertEquals("45", ConstsAfkTreeTimerOverlay.timerText(TreeTimerDisplay.TICKS, oak, 0));
        assertEquals("0:27", ConstsAfkTreeTimerOverlay.timerText(TreeTimerDisplay.SECONDS, oak, 0));
        assertEquals("4:24", ConstsAfkTreeTimerOverlay.timerText(TreeTimerDisplay.SECONDS, redwood, 0));
        assertNull("an unreadable setting is the pie", ConstsAfkTreeTimerOverlay.timerText(null, oak, 0));
    }

    @Test
    public void secondsShouldCountDownBetweenTicks()
    {
        TreeTimerState oak = tree(TreeTimerTree.OAK, OAK_TILE);
        assertEquals("nobody on it: nothing moves", 27, oak.getTimeSeconds(300));

        oak.addPlayerChopping(player("Them", THEIR_TILE, FACING_WEST));
        assertEquals(27, oak.getTimeSeconds(0));
        assertEquals("half a tick in", 26, oak.getTimeSeconds(300));
    }

    @Test
    public void theTimerColorShouldFollowTheTimeLeft()
    {
        TreeTimerState oak = tree(TreeTimerTree.OAK, OAK_TILE);
        oak.addPlayerChopping(player("Them", THEIR_TILE, FACING_WEST));

        assertEquals(TreeTimerState.COLOR_FULL, oak.getTimerColor());
        tickTree(oak, 10);
        assertEquals("35 of 45", TreeTimerState.COLOR_HIGH, oak.getTimerColor());
        tickTree(oak, 18);
        assertEquals("17 of 45", TreeTimerState.COLOR_MEDIUM, oak.getTimerColor());
        tickTree(oak, 11);
        assertEquals("6 of 45", TreeTimerState.COLOR_LOW, oak.getTimerColor());
    }

    @Test
    public void aPopularTreeShouldNeedTenPlayersBesidesYou()
    {
        TreeTimerState oak = tree(TreeTimerTree.OAK, OAK_TILE);
        oak.addPlayerChopping(me);
        addChoppers(oak, 9);
        assertFalse("ten with you is nine besides you", oak.isPopular());

        addChoppers(oak, 1);
        assertTrue(oak.isPopular());

        TreeTimerState guildYew = tree(TreeTimerTree.YEW, new WorldPoint(1590, 3480, 0));
        addChoppers(guildYew, 15);
        assertFalse("the Woodcutting Guild has no popularity boost", guildYew.isPopular());
    }

    @Test
    public void theOverlayShouldDrawNothingWhileTheTreeTimerIsOff()
    {
        ConstsAfkConfig config = mock(ConstsAfkConfig.class);
        TreeTimer trees = mock(TreeTimer.class);
        ConstsAfkTreeTimerOverlay overlay = new ConstsAfkTreeTimerOverlay(client, config, trees);

        overlay.render(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics());
        verify(trees, never()).getTrees();

        when(config.showTreeTimer()).thenReturn(true);
        when(trees.getTrees()).thenReturn(Collections.emptyList());
        overlay.render(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics());
        verify(trees).getTrees();
        verify(config, never()).preset();
    }

    // Settings

    @Test
    public void theTreeTimerSectionShouldHoldEnabledDisplayAndPopularTreeIndicator() throws Exception
    {
        ConstsAfkConfig defaults = mock(ConstsAfkConfig.class, Answers.CALLS_REAL_METHODS);
        assertTrue("on by default", defaults.showTreeTimer());
        assertEquals(TreeTimerDisplay.PIE, defaults.treeTimerDisplay());
        assertTrue(defaults.treeTimerPopularIndicator());

        String[] names = {"Enabled", "Display", "Popular Tree Indicator"};
        String[] methods = {"showTreeTimer", "treeTimerDisplay", "treeTimerPopularIndicator"};
        for (int i = 0; i < methods.length; i++)
        {
            ConfigItem item = ConstsAfkConfig.class.getMethod(methods[i]).getAnnotation(ConfigItem.class);
            assertEquals(names[i], item.name());
            assertEquals(ConstsAfkConfig.treeTimerSection, item.section());
            assertEquals(i, item.position());
            assertFalse("not an alert, so not part of Custom",
                ConstsAfkPlugin.CUSTOM_TOGGLE_KEYS.contains(item.keyName()));
        }
        assertEquals("Pie, Ticks, Seconds", TreeTimerDisplay.PIE + ", " + TreeTimerDisplay.TICKS + ", "
            + TreeTimerDisplay.SECONDS);

        for (Method method : ConstsAfkConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null && item.section().equals(ConstsAfkConfig.treeTimerSection))
            {
                assertTrue(method.getName(), item.name().equals("Enabled") || item.name().equals("Display")
                    || item.name().equals("Popular Tree Indicator"));
            }
        }
    }

    private TreeTimerState spawnTree(int objectId, WorldPoint at)
    {
        GameObject object = treeObject(objectId, at, 1, 1);
        timer.onGameObjectSpawned(object);
        for (TreeTimerState tree : timer.getTrees())
        {
            if (tree.getWorldPoint().equals(at))
            {
                return tree;
            }
        }
        throw new AssertionError("no tree at " + at);
    }

    private TreeTimerState onlyTree()
    {
        assertEquals(1, timer.getTrees().size());
        return timer.getTrees().iterator().next();
    }

    private TreeTimerState tree(TreeTimerTree kind, WorldPoint at)
    {
        return new TreeTimerState(client, kind, at, new Point(0, 0), Collections.singletonList(at));
    }

    private static GameObject treeObject(int id, WorldPoint at, int sizeX, int sizeY)
    {
        GameObject object = mock(GameObject.class);
        when(object.getId()).thenReturn(id);
        when(object.getWorldLocation()).thenReturn(at);
        when(object.getPlane()).thenReturn(at.getPlane());
        when(object.sizeX()).thenReturn(sizeX);
        when(object.sizeY()).thenReturn(sizeY);
        when(object.getSceneMinLocation()).thenReturn(new Point(at.getX() - BASE_X, at.getY() - BASE_Y));
        when(object.getSceneMaxLocation())
            .thenReturn(new Point(at.getX() - BASE_X + sizeX - 1, at.getY() - BASE_Y + sizeY - 1));
        return object;
    }

    private static Player player(String name, WorldPoint at, int orientation)
    {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getWorldLocation()).thenReturn(at);
        when(player.getOrientation()).thenReturn(orientation);
        when(player.getAnimation()).thenReturn(-1);
        return player;
    }

    private void addChoppers(TreeTimerState tree, int count)
    {
        for (int i = 0; i < count; i++)
        {
            tree.addPlayerChopping(player("Chopper " + tree.getPlayersChopping().size(), THEIR_TILE, FACING_WEST));
        }
    }

    /** The animation is read on the next tick, as in game. */
    private void startChopping(Player player)
    {
        when(player.getAnimation()).thenReturn(CHOPPING);
        timer.onAnimationChanged(player);
    }

    private void stopChopping(Player player)
    {
        when(player.getAnimation()).thenReturn(-1);
        timer.onAnimationChanged(player);
    }

    private void tick(int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            now += 600L;
            timer.onGameTick(now);
        }
    }

    private static void tickTree(TreeTimerState tree, int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            tree.tick();
        }
    }
}
