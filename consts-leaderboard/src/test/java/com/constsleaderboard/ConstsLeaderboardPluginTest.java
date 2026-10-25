package com.constsleaderboard;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
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

    private static Friend friend(String name, int world)
    {
        Friend f = mock(Friend.class);
        when(f.getName()).thenReturn(name);
        when(f.getWorld()).thenReturn(world);
        return f;
    }

    @Test
    public void friendsListOnlineFirstThenByName()
    {
        Map<String, Boolean> friends = ConstsLeaderboardPlugin.friends(new Friend[]{
            friend("zezima", 0), friend("Bob", 0), friend("Lynx Titan", 302), null, friend(null, 1), friend("alice", 420)});

        Map<String, Boolean> expected = new LinkedHashMap<>();
        expected.put("alice", true);
        expected.put("Lynx Titan", true);
        expected.put("Bob", false);
        expected.put("zezima", false);
        assertEquals(expected, friends);
        assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(friends.keySet()));
    }

    @Test
    public void thePanelHearsYourFriendsWhenTheyChangeAndLosesThemAtLogout() throws Exception
    {
        ConstsLeaderboardPanel panel = mock(ConstsLeaderboardPanel.class);
        Field field = ConstsLeaderboardPlugin.class.getDeclaredField("panel");
        field.setAccessible(true);
        field.set(plugin, panel);

        FriendContainer container = mock(FriendContainer.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getFriendContainer()).thenReturn(container);
        Friend offline = friend("Bob", 0);
        Friend online = friend("Bob", 302);
        when(container.getMembers()).thenReturn(new Friend[]{offline});
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        when(container.getMembers()).thenReturn(new Friend[]{online});
        plugin.onGameTick(new GameTick());
        when(client.getGameState()).thenReturn(GameState.LOADING);
        plugin.onGameTick(new GameTick());

        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        SwingUtilities.invokeAndWait(() -> { });

        InOrder order = inOrder(panel);
        order.verify(panel).setFriends(Map.of("Bob", false));
        order.verify(panel).setFriends(Map.of("Bob", true));
        order.verify(panel).setFriends(null);
        verify(panel, times(3)).setFriends(any());
    }
}
