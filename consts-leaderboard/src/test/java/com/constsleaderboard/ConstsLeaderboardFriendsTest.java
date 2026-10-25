package com.constsleaderboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import org.junit.Before;
import org.junit.Test;

public class ConstsLeaderboardFriendsTest
{
    private ConstsLeaderboardPlugin plugin;
    private HiscoreClient hiscoreClient;
    private ConfigManager configManager;

    @Before
    public void setUp()
    {
        plugin = mock(ConstsLeaderboardPlugin.class);
        when(plugin.getWorldEndpoint()).thenReturn(HiscoreEndpoint.NORMAL);
        hiscoreClient = mock(HiscoreClient.class);
        when(hiscoreClient.lookupAsync(any(), any())).thenReturn(new CompletableFuture<>());
        configManager = mock(ConfigManager.class);
    }

    private ConstsLeaderboardPanel panel()
    {
        return new ConstsLeaderboardPanel(plugin, hiscoreClient, mock(SpriteManager.class), configManager);
    }

    private static void click(JComponent c)
    {
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false));
    }

    @Test
    public void friendsStartOpen()
    {
        assertTrue(panel().friendsList.isVisible());
    }

    @Test
    public void closingFriendsHidesThemAndIsRemembered()
    {
        ConstsLeaderboardPanel panel = panel();
        click(panel.friendsHeader);
        assertFalse(panel.friendsList.isVisible());
        verify(configManager).setConfiguration(ConstsLeaderboardPanel.CONFIG_GROUP, ConstsLeaderboardPanel.FRIENDS_OPEN, false);

        when(configManager.getConfiguration(ConstsLeaderboardPanel.CONFIG_GROUP, ConstsLeaderboardPanel.FRIENDS_OPEN))
            .thenReturn("false");
        assertFalse(panel().friendsList.isVisible());
    }

    @Test
    public void eachFriendIsARowAndClickingOneLooksThemUp() throws Exception
    {
        // The search box only takes text on the Swing thread
        SwingUtilities.invokeAndWait(this::rowsLookUp);
    }

    private void rowsLookUp()
    {
        ConstsLeaderboardPanel panel = panel();
        Map<String, Boolean> friends = new LinkedHashMap<>();
        friends.put("Lynx Titan", true);
        friends.put("Bob", false);
        panel.setFriends(friends);

        assertEquals(2, panel.friendsList.getComponentCount());
        JLabel lynx = (JLabel) panel.friendsList.getComponent(0);
        assertEquals("Lynx Titan", lynx.getText());
        assertEquals("1 online", panel.friendsCount.getText());

        click((JLabel) panel.friendsList.getComponent(1));
        verify(hiscoreClient).lookupAsync("Bob", HiscoreEndpoint.NORMAL);
    }

    @Test
    public void loggedOutOrFriendlessSaysSo()
    {
        ConstsLeaderboardPanel panel = panel();
        assertEquals("Log in to see your friends", ((JLabel) panel.friendsList.getComponent(0)).getText());
        panel.setFriends(Map.of());
        assertEquals("No friends yet", ((JLabel) panel.friendsList.getComponent(0)).getText());
        assertEquals("", panel.friendsCount.getText());
    }
}
