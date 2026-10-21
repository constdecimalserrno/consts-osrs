package com.constsmodels;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.menus.MenuManager;

/**
 * TICKET 1 (stub): the Export Model option on the right-click menu of anything with a model: players, NPCs, pets,
 * objects (game, wall, decorative and ground objects) and ground items, plus your own player. A click captures the
 * model on the client thread with {@link ConstsModelsPlugin#capture} and opens it with
 * {@link ConstsModelsPlugin#show}. The plugin registers this on the event bus and calls startUp and shutDown.
 */
class ExportMenu
{
    static final String EXPORT_MODEL = "Export Model";

    private final ConstsModelsPlugin plugin;
    private final Client client;
    private final ConstsModelsConfig config;
    private final MenuManager menuManager;

    @Inject
    ExportMenu(ConstsModelsPlugin plugin, Client client, ConstsModelsConfig config, MenuManager menuManager)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.menuManager = menuManager;
    }

    void startUp()
    {
    }

    void shutDown()
    {
    }
}
