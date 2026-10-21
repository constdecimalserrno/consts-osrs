package com.constsdestination;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

/**
 * Where you're about to act and where you acted: the tile under your cursor and anything there you can act on in the
 * hover color, the tile you're walking to and whatever you're interacting with in the clicked color. The entity
 * outlines are RuneLite's Interact Highlight by Adam, folded in (InteractTracker, ConstsDestinationOutlineOverlay).
 */
@PluginDescriptor(
    name = "consts-Destination",
    description = "Highlights the tile and anything you hover that you can act on, and keeps what you clicked lit until you're done",
    tags = {"destination", "highlight", "tile", "cursor", "hover", "walk", "click", "outline", "interact", "npc", "object"}
)
public class ConstsDestinationPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ConstsDestinationOverlay overlay;

    @Inject
    private ConstsDestinationOutlineOverlay outlineOverlay;

    @Inject
    private InteractTracker tracker;

    @Inject
    private ConstsDestinationConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ColorPickerManager colorPickerManager;

    @Inject
    private EventBus eventBus;

    // The sidebar button: the plugin's settings in a panel of their own.
    private ConfigSidePanel configPanel;
    private NavigationButton navButton;

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
        overlayManager.add(outlineOverlay);
        clickedTile = null;
        tracker.clear();
        configPanel = new ConfigSidePanel(configManager, colorPickerManager, config, this);
        eventBus.register(configPanel);
        navButton = NavigationButton.builder()
            .tooltip("consts-Destination")
            .icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
            .priority(5)
            .panel(configPanel)
            .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        overlayManager.remove(outlineOverlay);
        clickedTile = null;
        tracker.clear();
        clientToolbar.removeNavigation(navButton);
        eventBus.unregister(configPanel);
        navButton = null;
        configPanel = null;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        tracker.onMenuOptionClicked(event);

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
        if (event.getGameState() == GameState.LOADING)
        {
            tracker.onLoading();
        }
        else if (event.getGameState() != GameState.LOGGED_IN)
        {
            clickedTile = null;
            tracker.clear();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        tracker.onGameTick();
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        tracker.onInteractingChanged(event.getSource(), event.getTarget());
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        tracker.onNpcSpawned(event.getNpc());
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        tracker.onActorDespawned(event.getNpc());
    }

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned event)
    {
        tracker.onPlayerSpawned(event.getPlayer());
    }

    @Subscribe
    public void onPlayerDespawned(PlayerDespawned event)
    {
        tracker.onActorDespawned(event.getPlayer());
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event)
    {
        tracker.onItemDespawned(event.getItem());
    }

    /** Whether outlines are on for this kind of thing. */
    static boolean outlines(ConstsDestinationConfig config, InteractTracker.Kind kind)
    {
        switch (kind)
        {
            case NPC:
                return config.outlineNpcs();
            case PLAYER:
                return config.outlinePlayers();
            case OBJECT:
                return config.outlineObjects();
            case ITEM:
                return config.outlineGroundItems();
            default:
                return false;
        }
    }

    WorldPoint getClickedTile()
    {
        return clickedTile;
    }
}
