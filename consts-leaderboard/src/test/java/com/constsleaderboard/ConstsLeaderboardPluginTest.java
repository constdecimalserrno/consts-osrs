package com.constsleaderboard;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.EnumSet;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IconID;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.hiscore.HiscoreEndpoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

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
    public void thePanelHearsYourNameOnceAndLosesItAtLogout() throws Exception
    {
        ConstsLeaderboardPanel panel = mock(ConstsLeaderboardPanel.class);
        Field field = ConstsLeaderboardPlugin.class.getDeclaredField("panel");
        field.setAccessible(true);
        field.set(plugin, panel);

        Player me = mock(Player.class);
        when(me.getName()).thenReturn("Zezima");
        when(client.getLocalPlayer()).thenReturn(me);
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());

        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        SwingUtilities.invokeAndWait(() -> { });

        InOrder order = inOrder(panel);
        order.verify(panel).setMyName("Zezima");
        order.verify(panel).setMyName(null);
        verify(panel, times(2)).setMyName(any());
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
