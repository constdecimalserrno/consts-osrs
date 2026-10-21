/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Lotto <https://github.com/devLotto>
 * Copyright (c) 2019, gregg1494 <https://github.com/gregg1494>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from RuneLite's World Hopper plugin into
 *   consts-World; a hot list kept in config in place of favourites; no quick-hop keys, ::hop command, Hop-to option,
 *   pings or settings)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsworld;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WorldListLoad;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.WorldsFetch;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * consts-World: a world switcher in the sidebar that opens on your hot list. Star the worlds you hop between once, in
 * All worlds, then hop to one with a click. Either tab can be searched by activity, number or region and sorted.
 * RuneLite's World Hopper plugin by Adam, cut down to its panel, with a hot list in place of its favourites.
 */
@PluginDescriptor(
    name = "consts-World",
    description = "A world switcher that opens on your hot list: star the worlds you hop between once, then hop with a click",
    tags = {"world", "worlds", "hopper", "switcher", "hop", "search", "activity", "hot list", "favourites", "skilling"}
)
public class ConstsWorldPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(ConstsWorldPlugin.class);

    static final String CONFIG_GROUP = "constsworld";
    static final String HOT_LIST_KEY = "hotList";

    private static final int REFRESH_THROTTLE = 60_000; // ms

    private static final int DISPLAY_SWITCHER_MAX_ATTEMPTS = 3;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private WorldService worldService;

    private NavigationButton navButton;
    private ConstsWorldPanel panel;

    // The worlds in the hot list, in the order they were added; kept in RuneLite's config
    private HotList hotList = new HotList();

    private net.runelite.api.World quickHopTargetWorld;
    private int displaySwitcherAttempts = 0;

    private int lastWorld;

    private Instant lastFetch;

    @Override
    protected void startUp() throws Exception
    {
        hotList = HotList.parse(configManager.getConfiguration(CONFIG_GROUP, HOT_LIST_KEY));

        panel = new ConstsWorldPanel(this);

        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
        navButton = NavigationButton.builder()
            .tooltip("consts-World")
            .icon(icon)
            .priority(7)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);

        // populate initial world list
        updateList();
    }

    @Override
    protected void shutDown() throws Exception
    {
        clientToolbar.removeNavigation(navButton);
        resetQuickHopper();
    }

    List<Integer> getHotList()
    {
        return hotList.worlds();
    }

    boolean isHot(int world)
    {
        return hotList.contains(world);
    }

    void addToHotList(int world)
    {
        if (hotList.add(world))
        {
            log.debug("Adding world {} to the hot list", world);
            saveHotList();
        }
    }

    void removeFromHotList(int world)
    {
        if (hotList.remove(world))
        {
            log.debug("Removing world {} from the hot list", world);
            saveHotList();
        }
    }

    private void saveHotList()
    {
        if (hotList.isEmpty())
        {
            configManager.unsetConfiguration(CONFIG_GROUP, HOT_LIST_KEY);
        }
        else
        {
            configManager.setConfiguration(CONFIG_GROUP, HOT_LIST_KEY, hotList.format());
        }
    }

    int getCurrentWorld()
    {
        return client.getWorld();
    }

    int getLastWorld()
    {
        return lastWorld;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
        {
            if (lastWorld != client.getWorld())
            {
                int newWorld = client.getWorld();
                int oldWorld = lastWorld;
                SwingUtilities.invokeLater(() -> panel.switchCurrentHighlight(newWorld, oldWorld));
                lastWorld = newWorld;
            }
        }
    }

    @Subscribe
    public void onWorldListLoad(WorldListLoad worldListLoad)
    {
        Map<Integer, Integer> worldData = new HashMap<>();

        for (net.runelite.api.World w : worldListLoad.getWorlds())
        {
            worldData.put(w.getId(), w.getPlayerCount());
        }

        SwingUtilities.invokeLater(() -> panel.updateListData(worldData));
        this.lastFetch = Instant.now(); // This counts as a fetch as it updates populations
    }

    void refresh()
    {
        Instant now = Instant.now();
        if (lastFetch != null && now.toEpochMilli() - lastFetch.toEpochMilli() < REFRESH_THROTTLE)
        {
            log.debug("Throttling world refresh");
            return;
        }

        lastFetch = now;
        worldService.refresh();
    }

    @Subscribe
    public void onWorldsFetch(WorldsFetch worldsFetch)
    {
        updateList();
    }

    /**
     * This method ONLY updates the list's UI, not the actual world list and data it displays.
     */
    private void updateList()
    {
        WorldResult worldResult = worldService.getWorlds();
        if (worldResult != null)
        {
            clientThread.invokeLater(() ->
            {
                if (client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
                {
                    return false;
                }

                var locationEnum = client.getEnum(EnumID.WORLD_LOCATIONS);
                SwingUtilities.invokeLater(() -> panel.populate(worldResult.getWorlds(), locationEnum));
                return true;
            });
        }
    }

    void hopTo(World world)
    {
        // A world in the hot list that the world list doesn't have can't be hopped to
        if (world.getAddress() == null)
        {
            return;
        }

        // this is called from the panel, on edt
        clientThread.invoke(() -> hop(world));
    }

    private void hop(World world)
    {
        assert client.isClientThread();

        final net.runelite.api.World rsWorld = client.createWorld();
        rsWorld.setActivity(world.getActivity());
        rsWorld.setAddress(world.getAddress());
        rsWorld.setId(world.getId());
        rsWorld.setPlayerCount(world.getPlayers());
        rsWorld.setLocation(world.getRegion().getId());
        rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

        if (client.getGameState() == GameState.LOGIN_SCREEN)
        {
            // on the login screen we can just change the world by ourselves
            client.changeWorld(rsWorld);
            return;
        }

        String chatMessage = new ChatMessageBuilder()
            .append(ChatColorType.NORMAL)
            .append("Quick-hopping to World ")
            .append(ChatColorType.HIGHLIGHT)
            .append(Integer.toString(world.getId()))
            .append(ChatColorType.NORMAL)
            .append("..")
            .build();

        chatMessageManager
            .queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(chatMessage)
                .build());

        quickHopTargetWorld = rsWorld;
        displaySwitcherAttempts = 0;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (quickHopTargetWorld == null)
        {
            return;
        }

        if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
        {
            client.openWorldHopper();

            if (++displaySwitcherAttempts >= DISPLAY_SWITCHER_MAX_ATTEMPTS)
            {
                String chatMessage = new ChatMessageBuilder()
                    .append(ChatColorType.NORMAL)
                    .append("Failed to quick-hop after ")
                    .append(ChatColorType.HIGHLIGHT)
                    .append(Integer.toString(displaySwitcherAttempts))
                    .append(ChatColorType.NORMAL)
                    .append(" attempts.")
                    .build();

                chatMessageManager
                    .queue(QueuedMessage.builder()
                        .type(ChatMessageType.CONSOLE)
                        .runeLiteFormattedMessage(chatMessage)
                        .build());

                resetQuickHopper();
            }
        }
        else
        {
            client.hopToWorld(quickHopTargetWorld);
            resetQuickHopper();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }

        if (event.getMessage().equals("Please finish what you're doing before using the World Switcher."))
        {
            resetQuickHopper();
        }
    }

    private void resetQuickHopper()
    {
        displaySwitcherAttempts = 0;
        quickHopTargetWorld = null;
    }
}
