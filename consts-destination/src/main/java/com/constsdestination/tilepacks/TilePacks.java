/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2021, Adam <Adam@sigterm.info>
 * Copyright (c) 2022, Trevor <https://github.com/TrevorMDev>
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: TilePacksPlugin, no longer a plugin of its own but a
 *   part of consts-Destination: its panel is a tab in consts-Destination's, its settings and saved data are
 *   consts-Destination's, Tile Packs' are brought over once and left as they were, the points are loaded on the
 *   client thread, and a profile change reads its packs again; lombok written out; only your own packs, without
 *   the list's filters)
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
package com.constsdestination.tilepacks;

import com.constsdestination.ConstsDestinationConfig;
import com.constsdestination.tilepacks.ui.overlay.GroundMarkerMinimapOverlay;
import com.constsdestination.tilepacks.ui.overlay.GroundMarkerOverlay;
import com.constsdestination.tilepacks.ui.panel.TilePacksListPanel;
import com.google.gson.Gson;
import javax.inject.Inject;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tile Packs, by TrevorMDev, folded into consts-Destination: your own tile packs, sets of ground markers you make or
 * paste in, to turn on and off, edit and delete. consts-Destination starts and stops it, shows its panel as a tab,
 * and passes it the events it needs; its tiles follow consts-Destination's Master Switch.
 */
public class TilePacks
{
    private static final Logger log = LoggerFactory.getLogger(TilePacks.class);

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ConstsDestinationConfig config;
    @Inject
    private ConfigManager configManager;
    @Inject
    private Gson gson;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ChatMessageManager chatMessageManager;

    private GroundMarkerMinimapOverlay minimapOverlay;
    private GroundMarkerOverlay overlay;
    private TilePackManager tilePackManager;
    private PointManager pointManager;
    private TilePacksListPanel panel;

    public void startUp()
    {
        migrate();

        tilePackManager = new TilePackManager(gson, configManager);
        pointManager = new PointManager(tilePackManager, gson, client);

        overlay = new GroundMarkerOverlay(pointManager, client, config);
        minimapOverlay = new GroundMarkerMinimapOverlay(pointManager, client, config);
        overlayManager.add(overlay);
        overlayManager.add(minimapOverlay);
        panel = new TilePacksListPanel(tilePackManager, this::reloadPoints, chatMessageManager, gson);

        reloadPoints();

        log.debug("Tile Packs started");
    }

    public void shutDown()
    {
        overlayManager.remove(overlay);
        overlayManager.remove(minimapOverlay);
        log.debug("Tile Packs stopped");
    }

    /** The Tile packs tab: your packs and the form to add one, scrolling. */
    public JComponent getPanel()
    {
        return panel.getWrappedPanel();
    }

    /**
     * Brings Tile Packs' saved data over, if it hasn't been yet. If that fails, the packs start with what there is and
     * it's tried again at the next start.
     */
    private void migrate()
    {
        try
        {
            new TilePackMigrations(gson, configManager).migrateToLatest();
        }
        catch (Exception e)
        {
            log.error("Unable to bring Tile Packs' saved data over", e);
            chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage("consts-Destination couldn't bring your Tile Packs packs over; it tries again next time it starts. Please report this issue.")
                .build());
        }
    }

    /** Puts the tiles of the packs that are on in the world again, on the client thread, where they're drawn. */
    private void reloadPoints()
    {
        clientThread.invokeLater(pointManager::loadPoints);
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged profileChanged)
    {
        // Another profile has its own packs, and may not have brought Tile Packs' over yet.
        migrate();
        tilePackManager.reload();
        SwingUtilities.invokeLater(panel::reload);
        reloadPoints();
    }

    @Subscribe
    public void onWorldViewLoaded(WorldViewLoaded event)
    {
        log.debug("Changing world view");
        // map region has just been updated
        pointManager.loadWorldViewPoints(event.getWorldView());
    }
}
