package com.constsdestination;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Highlights the tile under your cursor, and keeps the tile you walked to lit in another color until your next click.
 */
@PluginDescriptor(
    name = "consts-Destination",
    description = "Highlights the tile under your cursor, and keeps the tile you clicked to walk to lit until your next click",
    tags = {"destination", "highlight", "tile", "cursor", "hover", "walk", "click"}
)
public class ConstsDestinationPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ConstsDestinationOverlay overlay;

    private WorldPoint clickedTile;

    @Provides
    ConstsDestinationConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ConstsDestinationConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        clickedTile = null;
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        clickedTile = null;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        MenuAction action = event.getMenuAction();

        if (action == MenuAction.WALK)
        {
            // Clicked a tile to walk to: keep it lit in the clicked color
            if (client.getSelectedSceneTile() != null)
            {
                clickedTile = client.getSelectedSceneTile().getWorldLocation();
            }
        }
        else
        {
            // Any other click clears the locked tile
            clickedTile = null;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            clickedTile = null;
        }
    }

    WorldPoint getClickedTile()
    {
        return clickedTile;
    }
}
