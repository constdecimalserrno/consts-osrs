package com.constsdestination;

import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * What a left-click goes past: every ground item with Ground Item Pickup off, loot and campfires with Minigame Focus
 * on. Their options sink below Walk here, the way RuneLite's Ground Items plugin sinks the items you hide, so a
 * left-click goes to what's behind them - a tree, an NPC, the tile - and the hover outline, which follows the
 * left-click, goes with it. A right-click still has every option.
 */
@Singleton
class ClickThrough
{
    private final ConstsDestinationConfig config;
    private final ItemManager itemManager;

    @Inject
    ClickThrough(ConstsDestinationConfig config, ItemManager itemManager)
    {
        this.config = config;
        this.itemManager = itemManager;
    }

    void onMenuEntryAdded(MenuEntry entry)
    {
        if (config.masterSwitch() && goesPast(entry))
        {
            entry.setDeprioritized(true);
        }
    }

    /** Whether a left-click goes past what this menu entry acts on. */
    boolean goesPast(MenuEntry entry)
    {
        MenuAction type = entry.getType();
        if (type == null)
        {
            return false;
        }
        switch (type)
        {
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
                return !config.groundItemPickup() || config.minigameFocus() && isLoot(entry.getIdentifier());
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
                return config.minigameFocus() && isCampfire(entry.getTarget());
            default:
                // Examine sits at the bottom already, and using an item or a spell on something is a choice you made
                return false;
        }
    }

    /** Loot is anything you could trade. What a minigame has you pick up can't be traded, so it stays one click. */
    private boolean isLoot(int itemId)
    {
        return itemManager.getItemComposition(itemManager.canonicalize(itemId)).isTradeable();
    }

    /** A fire to sit by, by the name in the menu: a lit fire, or any campfire, Forestry's Forester's Campfire too. */
    static boolean isCampfire(String target)
    {
        if (target == null)
        {
            return false;
        }
        String name = Text.removeTags(target).toLowerCase(Locale.ROOT);
        return name.equals("fire") || name.contains("campfire");
    }
}
