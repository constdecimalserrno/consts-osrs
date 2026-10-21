package com.constsdestination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.events.ConfigChanged;
import org.junit.Before;
import org.junit.Test;

public class ConstsDestinationPluginTest
{
    private static final WorldPoint TILE = new WorldPoint(3222, 3218, 0);

    private ConstsDestinationPlugin plugin;
    private Client client;

    @Before
    public void setUp() throws Exception
    {
        plugin = new ConstsDestinationPlugin();
        client = mock(Client.class);
        Tile tile = mock(Tile.class);
        when(tile.getWorldLocation()).thenReturn(TILE);
        when(client.getSelectedSceneTile()).thenReturn(tile);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

        Field field = ConstsDestinationPlugin.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(plugin, client);
        Field tracker = ConstsDestinationPlugin.class.getDeclaredField("tracker");
        tracker.setAccessible(true);
        tracker.set(plugin, new InteractTracker(client));
    }

    @Test
    public void walkingToATileShouldKeepItLit()
    {
        plugin.onMenuOptionClicked(click(MenuAction.WALK));

        assertEquals(TILE, plugin.getClickedTile());
    }

    @Test
    public void anyOtherClickShouldClearIt()
    {
        plugin.onMenuOptionClicked(click(MenuAction.WALK));
        plugin.onMenuOptionClicked(click(MenuAction.NPC_FIRST_OPTION));

        assertNull(plugin.getClickedTile());
    }

    @Test
    public void theMasterSwitchShouldClearIt()
    {
        plugin.onMenuOptionClicked(click(MenuAction.WALK));
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(ConstsDestinationConfig.GROUP);
        changed.setKey(ConstsDestinationConfig.MASTER_KEY);
        changed.setNewValue("false");
        plugin.onConfigChanged(changed);

        assertNull(plugin.getClickedTile());
    }

    @Test
    public void loggingOutShouldClearIt()
    {
        plugin.onMenuOptionClicked(click(MenuAction.WALK));
        GameStateChanged loggedOut = new GameStateChanged();
        loggedOut.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(loggedOut);

        assertNull(plugin.getClickedTile());
    }

    @Test
    public void clicksWhileLoggedOutShouldBeIgnored()
    {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

        plugin.onMenuOptionClicked(click(MenuAction.WALK));

        assertNull(plugin.getClickedTile());
    }

    private static MenuOptionClicked click(MenuAction action)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(action);
        return new MenuOptionClicked(entry);
    }
}
