package com.osrs;

import com.constsafk.ConstsAfkPlugin;
import com.constsdestination.ConstsDestinationPlugin;
import com.constsleaderboard.ConstsLeaderboardPlugin;
import com.constsworld.ConstsWorldPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PluginLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(
            ConstsAfkPlugin.class,
            ConstsDestinationPlugin.class,
            ConstsLeaderboardPlugin.class,
            ConstsWorldPlugin.class
        );
        RuneLite.main(args);
    }
}
