package com.constsdestination;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

public class ClickThroughTest
{
    private static final int NOTED_LOGS = 1512;
    private static final int FORESTERS_CAMPFIRE = 49927;
    private static final String CAMPFIRE = "<col=ffff>Forester's Campfire";

    private static final MenuAction[] GROUND_ITEM_OPTIONS = {
        MenuAction.GROUND_ITEM_FIRST_OPTION, MenuAction.GROUND_ITEM_SECOND_OPTION, MenuAction.GROUND_ITEM_THIRD_OPTION,
        MenuAction.GROUND_ITEM_FOURTH_OPTION, MenuAction.GROUND_ITEM_FIFTH_OPTION,
    };

    private ConstsDestinationConfig config;
    private ItemManager itemManager;
    private ClickThrough clickThrough;

    @Before
    public void setUp()
    {
        config = mock(ConstsDestinationConfig.class);
        when(config.masterSwitch()).thenReturn(true);
        when(config.groundItemPickup()).thenReturn(true);
        itemManager = mock(ItemManager.class);
        item(ItemID.LOGS, true);
        item(ItemID.BARBASSAULT_EGG_01, false);
        when(itemManager.canonicalize(NOTED_LOGS)).thenReturn(ItemID.LOGS);
        clickThrough = new ClickThrough(config, itemManager);
    }

    @Test
    public void leftClicksAreTheGamesUntilATickChangesThem()
    {
        MenuEntry take = take(ItemID.LOGS);
        MenuEntry tend = entry(MenuAction.GAME_OBJECT_FIRST_OPTION, FORESTERS_CAMPFIRE, CAMPFIRE);

        clickThrough.onMenuEntryAdded(take);
        clickThrough.onMenuEntryAdded(tend);

        verify(take, never()).setDeprioritized(anyBoolean());
        verify(tend, never()).setDeprioritized(anyBoolean());
    }

    @Test
    public void groundItemPickupOffSinksEveryGroundItemOption()
    {
        when(config.groundItemPickup()).thenReturn(false);

        for (MenuAction option : GROUND_ITEM_OPTIONS)
        {
            MenuEntry entry = entry(option, ItemID.LOGS, "<col=ff9040>Logs");
            clickThrough.onMenuEntryAdded(entry);
            verify(entry).setDeprioritized(true);
        }
        assertTrue("off means every ground item, a minigame's too", clickThrough.goesPast(take(ItemID.BARBASSAULT_EGG_01)));
    }

    @Test
    public void groundItemPickupOffLeavesTheRestAlone()
    {
        when(config.groundItemPickup()).thenReturn(false);

        assertFalse("examine is at the bottom already",
            clickThrough.goesPast(entry(MenuAction.EXAMINE_ITEM_GROUND, ItemID.LOGS, "<col=ff9040>Logs")));
        assertFalse("using a tinderbox on logs is a choice you made",
            clickThrough.goesPast(entry(MenuAction.WIDGET_TARGET_ON_GROUND_ITEM, ItemID.LOGS, "<col=ff9040>Logs")));
        assertFalse("the tree behind them", clickThrough.goesPast(entry(MenuAction.GAME_OBJECT_FIRST_OPTION,
            ObjectID.OAKTREE, "<col=ffff>Oak tree")));
        assertFalse(clickThrough.goesPast(entry(MenuAction.NPC_FIRST_OPTION, 1, "<col=ffff00>Banker")));
        assertFalse(clickThrough.goesPast(entry(MenuAction.WALK, 0, "")));
        assertFalse("campfires are Minigame Focus's",
            clickThrough.goesPast(entry(MenuAction.GAME_OBJECT_FIRST_OPTION, FORESTERS_CAMPFIRE, CAMPFIRE)));
    }

    @Test
    public void minigameFocusSinksLootAndKeepsWhatTheMinigameHasYouPickUp()
    {
        when(config.minigameFocus()).thenReturn(true);

        assertTrue("logs can be traded, so they're loot", clickThrough.goesPast(take(ItemID.LOGS)));
        assertTrue("noted logs are logs", clickThrough.goesPast(take(NOTED_LOGS)));
        assertFalse("a Barbarian Assault egg can't be traded", clickThrough.goesPast(take(ItemID.BARBASSAULT_EGG_01)));
    }

    @Test
    public void minigameFocusSinksCampfires()
    {
        when(config.minigameFocus()).thenReturn(true);

        for (MenuAction option : new MenuAction[]{MenuAction.GAME_OBJECT_FIRST_OPTION,
            MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION})
        {
            // Tend-to, Check and Rest
            MenuEntry entry = entry(option, FORESTERS_CAMPFIRE, CAMPFIRE);
            clickThrough.onMenuEntryAdded(entry);
            verify(entry).setDeprioritized(true);
        }
        assertTrue("cooking on a fire", clickThrough.goesPast(entry(MenuAction.GAME_OBJECT_FIRST_OPTION,
            ObjectID.FIRE, "<col=ffff>Fire")));
        assertFalse("using logs on it is a choice you made", clickThrough.goesPast(entry(
            MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, FORESTERS_CAMPFIRE, CAMPFIRE)));
        assertFalse("the tree", clickThrough.goesPast(entry(MenuAction.GAME_OBJECT_FIRST_OPTION,
            ObjectID.OAKTREE, "<col=ffff>Oak tree")));
    }

    @Test
    public void theMasterSwitchGivesTheClicksBack()
    {
        when(config.masterSwitch()).thenReturn(false);
        when(config.groundItemPickup()).thenReturn(false);
        when(config.minigameFocus()).thenReturn(true);
        MenuEntry take = take(ItemID.LOGS);
        MenuEntry tend = entry(MenuAction.GAME_OBJECT_FIRST_OPTION, FORESTERS_CAMPFIRE, CAMPFIRE);

        clickThrough.onMenuEntryAdded(take);
        clickThrough.onMenuEntryAdded(tend);

        verify(take, never()).setDeprioritized(anyBoolean());
        verify(tend, never()).setDeprioritized(anyBoolean());
    }

    @Test
    public void campfiresGoByTheirName()
    {
        assertTrue(ClickThrough.isCampfire(CAMPFIRE));
        assertTrue(ClickThrough.isCampfire("Campfire"));
        assertTrue(ClickThrough.isCampfire("<col=ffff>Fire"));
        assertFalse("Wintertodt's fire is the minigame", ClickThrough.isCampfire("<col=ffff>Burning brazier"));
        assertFalse(ClickThrough.isCampfire("<col=ffff>Fire altar"));
        assertFalse(ClickThrough.isCampfire(null));
    }

    private void item(int id, boolean tradeable)
    {
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.isTradeable()).thenReturn(tradeable);
        when(itemManager.canonicalize(id)).thenReturn(id);
        when(itemManager.getItemComposition(id)).thenReturn(composition);
    }

    private static MenuEntry take(int itemId)
    {
        return entry(MenuAction.GROUND_ITEM_THIRD_OPTION, itemId, "<col=ff9040>Item");
    }

    private static MenuEntry entry(MenuAction type, int identifier, String target)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(type);
        when(entry.getIdentifier()).thenReturn(identifier);
        when(entry.getTarget()).thenReturn(target);
        return entry;
    }
}
