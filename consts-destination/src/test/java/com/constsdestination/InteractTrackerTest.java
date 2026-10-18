package com.constsdestination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Before;
import org.junit.Test;

public class InteractTrackerTest
{
    private Client client;
    private Player me;
    private InteractTracker tracker;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        me = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(me);
        when(client.getTickCount()).thenReturn(10);
        tracker = new InteractTracker(client);
    }

    @Test
    public void menuEntriesShouldSortByWhatTheyActOn()
    {
        assertEquals(InteractTracker.Kind.NPC, InteractTracker.kindOf(MenuAction.NPC_FIRST_OPTION));
        assertEquals(InteractTracker.Kind.PLAYER, InteractTracker.kindOf(MenuAction.PLAYER_EIGHTH_OPTION));
        assertEquals(InteractTracker.Kind.OBJECT, InteractTracker.kindOf(MenuAction.GAME_OBJECT_THIRD_OPTION));
        assertEquals(InteractTracker.Kind.ITEM, InteractTracker.kindOf(MenuAction.GROUND_ITEM_FIRST_OPTION));
        assertNull("walking acts on nothing", InteractTracker.kindOf(MenuAction.WALK));
        assertNull("an inventory item is not in the world", InteractTracker.kindOf(MenuAction.CC_OP));
    }

    @Test
    public void clickingAnNpcShouldTargetIt()
    {
        NPC banker = mock(NPC.class);

        tracker.onMenuOptionClicked(click(MenuAction.NPC_FIRST_OPTION, banker));

        assertSame(banker, tracker.getInteractedActor());
        assertFalse(tracker.isAttacked());
    }

    @Test
    public void anNpcsSecondOptionShouldBeAnAttack()
    {
        NPC goblin = mock(NPC.class);

        tracker.onMenuOptionClicked(click(MenuAction.NPC_SECOND_OPTION, goblin));

        assertSame(goblin, tracker.getInteractedActor());
        assertTrue(tracker.isAttacked());
    }

    @Test
    public void walkingShouldClearTheTarget()
    {
        tracker.onMenuOptionClicked(click(MenuAction.NPC_FIRST_OPTION, mock(NPC.class)));
        tracker.onMenuOptionClicked(click(MenuAction.WALK, null));

        assertNull(tracker.getInteractedActor());
    }

    @Test
    public void yourNewTargetShouldBeFollowedButNotSomeoneElses()
    {
        NPC cow = mock(NPC.class);
        when(cow.getCombatLevel()).thenReturn(2);

        tracker.onInteractingChanged(mock(Player.class), cow);
        assertNull("another player's target isn't yours", tracker.getInteractedActor());

        tracker.onInteractingChanged(me, cow);
        assertSame(cow, tracker.getInteractedActor());
        assertTrue("something with a combat level is a fight", tracker.isAttacked());
    }

    @Test
    public void aDespawnedTargetShouldBeLetGo()
    {
        NPC cow = mock(NPC.class);
        tracker.onMenuOptionClicked(click(MenuAction.NPC_FIRST_OPTION, cow));

        tracker.onActorDespawned(cow);

        assertNull(tracker.getInteractedActor());
    }

    @Test
    public void anItemShouldBeFoundInItsPile()
    {
        TileItem bones = mock(TileItem.class);
        TileItem coins = mock(TileItem.class);
        when(bones.getId()).thenReturn(526);
        when(coins.getId()).thenReturn(995);
        when(bones.getNext()).thenReturn(coins);
        ItemLayer pile = mock(ItemLayer.class);
        when(pile.getTop()).thenReturn(bones);

        assertSame(coins, InteractTracker.findItem(pile, 995));
        assertNull(InteractTracker.findItem(pile, 1));
        assertNull(InteractTracker.findItem(null, 995));
    }

    private static MenuOptionClicked click(MenuAction action, NPC npc)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(action);
        when(entry.getNpc()).thenReturn(npc);
        return new MenuOptionClicked(entry);
    }
}
