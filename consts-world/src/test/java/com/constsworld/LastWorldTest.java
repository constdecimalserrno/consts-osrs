package com.constsworld;

import static com.constsworld.WorldSearchTest.world;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.WorldService;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Before;
import org.junit.Test;

public class LastWorldTest
{
    private static final World TEMPOROSS = world(341, "Tempoross", WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS);
    private static final World BETA = world(450, "Beta world", WorldRegion.UNITED_KINGDOM, WorldType.BETA_WORLD);

    private ConstsWorldPlugin plugin;
    private Client client;
    private ConfigManager configManager;
    private WorldService worldService;
    private WorldResult worlds;

    @Before
    public void setUp() throws Exception
    {
        plugin = new ConstsWorldPlugin();
        client = mock(Client.class);
        configManager = mock(ConfigManager.class);
        worldService = mock(WorldService.class);
        worlds = new WorldResult();
        worlds.setWorlds(List.of(TEMPOROSS, BETA));
        when(client.createWorld()).thenReturn(mock(net.runelite.api.World.class));
        set("client", client);
        set("configManager", configManager);
        set("worldService", worldService);
        set("panel", mock(ConstsWorldPanel.class));
        set("returnPending", true);
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = ConstsWorldPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    @Test
    public void theWorldYouLogInToIsSaved() throws Exception
    {
        when(client.getWorld()).thenReturn(341);
        GameStateChanged loggedIn = new GameStateChanged();
        loggedIn.setGameState(GameState.LOGGED_IN);
        plugin.onGameStateChanged(loggedIn);

        // Hopped, then logged in again on the same world
        when(client.getWorld()).thenReturn(302);
        plugin.onGameStateChanged(loggedIn);
        plugin.onGameStateChanged(loggedIn);
        SwingUtilities.invokeAndWait(() -> { });

        verify(configManager).setConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.LAST_WORLD_KEY, 341);
        verify(configManager).setConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.LAST_WORLD_KEY, 302);
        verify(configManager, times(2)).setConfiguration(anyString(), anyString(), any(Object.class));
    }

    @Test
    public void theLoginScreenGoesBackToTheLastWorldOnce()
    {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(client.getWorld()).thenReturn(302);
        when(configManager.getConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.LAST_WORLD_KEY, Integer.class))
            .thenReturn(341);
        when(worldService.getWorlds()).thenReturn(worlds);

        plugin.returnToLastWorld();
        plugin.returnToLastWorld();

        verify(client, times(1)).changeWorld(any());
        verify(client.createWorld()).setId(341);
    }

    @Test
    public void itWaitsForTheWorldListBeforeGoingBack()
    {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(client.getWorld()).thenReturn(302);
        when(configManager.getConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.LAST_WORLD_KEY, Integer.class))
            .thenReturn(341);

        plugin.returnToLastWorld();
        verify(client, never()).changeWorld(any());

        when(worldService.getWorlds()).thenReturn(worlds);
        plugin.returnToLastWorld();
        verify(client).changeWorld(any());
    }

    @Test
    public void onceLoggedInItLeavesTheWorldAlone()
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(worldService.getWorlds()).thenReturn(worlds);
        when(configManager.getConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.LAST_WORLD_KEY, Integer.class))
            .thenReturn(341);

        plugin.returnToLastWorld();
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        plugin.returnToLastWorld();

        verify(client, never()).changeWorld(any());
    }

    @Test
    public void onlyARealWorldThatIsntTheCurrentOneIsReturnedTo()
    {
        assertSame(TEMPOROSS, ConstsWorldPlugin.worldToReturnTo(341, 302, worlds));
        assertNull(ConstsWorldPlugin.worldToReturnTo(0, 302, worlds));
        assertNull(ConstsWorldPlugin.worldToReturnTo(341, 341, worlds));
        assertNull(ConstsWorldPlugin.worldToReturnTo(399, 302, worlds));
        assertNull(ConstsWorldPlugin.worldToReturnTo(450, 302, worlds));
    }
}
