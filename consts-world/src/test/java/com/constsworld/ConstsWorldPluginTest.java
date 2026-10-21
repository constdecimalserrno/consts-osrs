package com.constsworld;

import static com.constsworld.WorldSearchTest.world;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class ConstsWorldPluginTest
{
    private ConstsWorldPlugin plugin;
    private ConfigManager configManager;
    private ClientThread clientThread;

    @Before
    public void setUp() throws Exception
    {
        plugin = new ConstsWorldPlugin();
        configManager = mock(ConfigManager.class);
        clientThread = mock(ClientThread.class);
        set("configManager", configManager);
        set("clientThread", clientThread);
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = ConstsWorldPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    @Test
    public void theHotListIsSavedEveryTimeItChanges()
    {
        plugin.addToHotList(330);
        plugin.addToHotList(302);
        plugin.addToHotList(330);
        plugin.removeFromHotList(330);
        plugin.removeFromHotList(302);
        plugin.removeFromHotList(302);

        InOrder order = inOrder(configManager);
        order.verify(configManager).setConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.HOT_LIST_KEY, "330");
        order.verify(configManager).setConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.HOT_LIST_KEY, "330,302");
        order.verify(configManager).setConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.HOT_LIST_KEY, "302");
        order.verify(configManager).unsetConfiguration(ConstsWorldPlugin.CONFIG_GROUP, ConstsWorldPlugin.HOT_LIST_KEY);
        order.verifyNoMoreInteractions();
    }

    @Test
    public void theHotListAnswersWhatsInIt()
    {
        assertEquals(List.of(), plugin.getHotList());
        plugin.addToHotList(416);
        plugin.addToHotList(330);
        assertEquals(List.of(416, 330), plugin.getHotList());
        assertTrue(plugin.isHot(330));
        assertFalse(plugin.isHot(302));
    }

    @Test
    public void aWorldTheWorldListDoesntHaveIsNeverHoppedTo()
    {
        World gone = World.builder().id(330).types(EnumSet.noneOf(WorldType.class)).activity("Not in the world list")
            .players(-1).build();
        plugin.hopTo(gone);
        verifyNoInteractions(clientThread);

        plugin.hopTo(world(330, "Tempoross", WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS));
        verify(clientThread).invoke(any(Runnable.class));
        verify(configManager, never()).setConfiguration(anyString(), anyString(), anyString());
    }
}
