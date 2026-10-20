package com.constsleaderboard;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.IconID;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.hiscore.HiscoreEndpoint;
import org.junit.Before;
import org.junit.Test;

public class ConstsLeaderboardPluginTest
{
    private ConstsLeaderboardPlugin plugin;
    private Client client;

    @Before
    public void setUp() throws Exception
    {
        plugin = new ConstsLeaderboardPlugin();
        client = mock(Client.class);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));

        Field field = ConstsLeaderboardPlugin.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(plugin, client);
    }

    @Test
    public void lookingUpMyselfReadsMyAccountsOwnHiscores()
    {
        assertEquals(HiscoreEndpoint.NORMAL, plugin.findHiscoreEndpointFromLocalPlayer());

        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(1);
        assertEquals(HiscoreEndpoint.IRONMAN, plugin.findHiscoreEndpointFromLocalPlayer());

        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(2);
        assertEquals(HiscoreEndpoint.ULTIMATE_IRONMAN, plugin.findHiscoreEndpointFromLocalPlayer());

        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(3);
        assertEquals(HiscoreEndpoint.HARDCORE_IRONMAN, plugin.findHiscoreEndpointFromLocalPlayer());
    }

    @Test
    public void aLeaguesWorldReadsTheLeaguesHiscores()
    {
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));
        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(1);
        assertEquals(HiscoreEndpoint.SEASONAL, plugin.getWorldEndpoint());
        assertEquals(HiscoreEndpoint.SEASONAL, plugin.findHiscoreEndpointFromLocalPlayer());
    }

    @Test
    public void aNameInChatPicksHiscoresByItsIcon()
    {
        assertEquals(HiscoreEndpoint.NORMAL, ConstsLeaderboardPlugin.findHiscoreEndpointFromPlayerName("Zezima"));
        assertEquals(HiscoreEndpoint.IRONMAN,
            ConstsLeaderboardPlugin.findHiscoreEndpointFromPlayerName(IconID.IRONMAN + "Zezima"));
        assertEquals(HiscoreEndpoint.HARDCORE_IRONMAN,
            ConstsLeaderboardPlugin.findHiscoreEndpointFromPlayerName(IconID.HARDCORE_IRONMAN + "Zezima"));
        assertEquals(HiscoreEndpoint.SEASONAL,
            ConstsLeaderboardPlugin.findHiscoreEndpointFromPlayerName(IconID.LEAGUE + "Zezima"));
    }
}
