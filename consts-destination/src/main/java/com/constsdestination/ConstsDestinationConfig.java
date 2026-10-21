package com.constsdestination;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * One idea in two forms: the hover color for what you're about to act on, the clicked color for what you acted on.
 * Tiles show it on the ground, outlines on NPCs, players, objects and ground items. First, what a left-click goes
 * past; last, how tile packs are drawn.
 */
@ConfigGroup(ConstsDestinationConfig.GROUP)
public interface ConstsDestinationConfig extends Config
{
    // Not plain "destination": on the Plugin Hub a group name has to be ours alone.
    String GROUP = "constsdestination";
    String MASTER_KEY = "masterSwitch";

    @ConfigItem(
        keyName = MASTER_KEY,
        name = "Master Switch",
        description = "Off hides every tile and outline consts-Destination draws, tile packs too, and leaves left-clicks as the game has them. The plugin itself stays on, so its sidebar button stays.",
        position = -1
    )
    default boolean masterSwitch()
    {
        return true;
    }

    @ConfigSection(
        name = "Clicks",
        description = "What a left-click goes past. A right-click still has every option",
        position = 0
    )
    String clicksSection = "clicks";

    @ConfigSection(
        name = "Colors",
        description = "The hover color, the clicked color, and the color for a fight",
        position = 1
    )
    String colorsSection = "colors";

    @ConfigSection(
        name = "Tiles",
        description = "The tile under your cursor, and the tile you clicked to walk to",
        position = 2
    )
    String tilesSection = "tiles";

    @ConfigSection(
        name = "Outlines",
        description = "NPCs, players, objects and ground items you hover or interact with",
        position = 3
    )
    String outlinesSection = "outlines";

    @ConfigSection(
        name = "Tile packs",
        description = "How the tiles of the packs you turn on in the Tile packs tab are drawn",
        position = 4
    )
    String tilePacksSection = "tilePacks";

    // Clicks

    @ConfigItem(
        keyName = "groundItemPickup",
        name = "Ground Item Pickup",
        description = "Left-click takes ground items. Off: taking one needs a right-click and it isn't outlined, so a left-click goes to the tree, NPC or tile behind it - for Forestry",
        section = clicksSection,
        position = 0
    )
    default boolean groundItemPickup()
    {
        return true;
    }

    @ConfigItem(
        keyName = "minigameFocus",
        name = "Minigame Focus",
        description = "Loot and campfires need a right-click, so a left-click stays on the minigame. Items you can't trade stay one click: what minigames have you pick up, like eggs, soul fragments and bruma roots, and clue scrolls",
        section = clicksSection,
        position = 1
    )
    default boolean minigameFocus()
    {
        return false;
    }

    // Colors

    @ConfigItem(
        keyName = "highlightColor",
        name = "Hover Color",
        description = "The tile under your cursor, and anything you hover that you can act on",
        section = colorsSection,
        position = 0
    )
    default Color highlightColor()
    {
        return new Color(62, 170, 255);
    }

    @ConfigItem(
        keyName = "clickedColor",
        name = "Clicked Color",
        description = "The tile you clicked to walk to, and what you're interacting with",
        section = colorsSection,
        position = 1
    )
    default Color clickedColor()
    {
        return new Color(255, 165, 0);
    }

    @ConfigItem(
        keyName = "attackColor",
        name = "Attack Color",
        description = "An NPC you're about to attack, or are attacking",
        section = colorsSection,
        position = 2
    )
    default Color attackColor()
    {
        return new Color(255, 70, 50);
    }

    // Tiles

    @ConfigItem(
        keyName = "showHoverTile",
        name = "Hover Tile",
        description = "Highlight the tile under your cursor (not while an outlined NPC, player, object or item is under it)",
        section = tilesSection,
        position = 0
    )
    default boolean showHoverTile()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showClickedTile",
        name = "Clicked Tile",
        description = "Keep the tile you clicked to walk to lit until your next click",
        section = tilesSection,
        position = 1
    )
    default boolean showClickedTile()
    {
        return true;
    }

    @Range(min = 1, max = 6)
    @ConfigItem(
        keyName = "borderWidth",
        name = "Border Width",
        description = "Tile outline thickness in pixels",
        section = tilesSection,
        position = 2
    )
    default int borderWidth()
    {
        return 2;
    }

    @Range(min = 0, max = 255)
    @ConfigItem(
        keyName = "fillOpacity",
        name = "Fill Opacity",
        description = "Tile fill: 0 is outline only, 255 is solid",
        section = tilesSection,
        position = 3
    )
    default int fillOpacity()
    {
        return 30;
    }

    // Outlines (from RuneLite's Interact Highlight by Adam)

    @ConfigItem(
        keyName = "outlineOnHover",
        name = "On Hover",
        description = "Outline what you hover, in the hover color",
        section = outlinesSection,
        position = 0
    )
    default boolean outlineOnHover()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineOnInteract",
        name = "On Interact",
        description = "Outline what you clicked, in the clicked color, while you're interacting with it",
        section = outlinesSection,
        position = 1
    )
    default boolean outlineOnInteract()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineNpcs",
        name = "NPCs",
        description = "Outline NPCs",
        section = outlinesSection,
        position = 2
    )
    default boolean outlineNpcs()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlinePlayers",
        name = "Players",
        description = "Outline other players",
        section = outlinesSection,
        position = 3
    )
    default boolean outlinePlayers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineObjects",
        name = "Objects",
        description = "Outline objects: trees, rocks, doors, banks and the rest",
        section = outlinesSection,
        position = 4
    )
    default boolean outlineObjects()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineGroundItems",
        name = "Ground Items",
        description = "Outline items on the ground",
        section = outlinesSection,
        position = 5
    )
    default boolean outlineGroundItems()
    {
        return true;
    }

    @Range(min = 1, max = 10)
    @ConfigItem(
        keyName = "outlineWidth",
        name = "Width",
        description = "Outline thickness in pixels",
        section = outlinesSection,
        position = 6
    )
    default int outlineWidth()
    {
        return 4;
    }

    @Range(min = 0, max = 4)
    @ConfigItem(
        keyName = "outlineFeather",
        name = "Feather",
        description = "How much of the outline fades out, 0 to 4",
        section = outlinesSection,
        position = 7
    )
    default int outlineFeather()
    {
        return 4;
    }

    // Tile packs. These are Tile Packs' own settings, from its TilePacksConfig.java: Copyright (c) 2022, TrevorMDev,
    // BSD 2-Clause License, reproduced in NOTICE. Changed: the keys start with "tilePacks"; its Hide on toolbar is left
    // out, since tile packs have no sidebar button of their own; Fill Opacity is kept within 0 to 255.

    @ConfigItem(
        keyName = "tilePacksShowLabels",
        name = "Show Labels",
        description = "When checked, labels will render as defined in each pack",
        section = tilePacksSection,
        position = 0
    )
    default boolean tilePacksShowLabels()
    {
        return true;
    }

    @ConfigItem(
        keyName = "tilePacksOverrideColorActive",
        name = "Override Color Active",
        description = "When checked, all tiles will render as the selected color",
        section = tilePacksSection,
        position = 1
    )
    default boolean tilePacksOverrideColorActive()
    {
        return false;
    }

    @Alpha
    @ConfigItem(
        keyName = "tilePacksOverrideColor",
        name = "Override Color",
        description = "If Use Override Color is checked, all tiles will be this color.",
        section = tilePacksSection,
        position = 2
    )
    default Color tilePacksOverrideColor()
    {
        return Color.YELLOW;
    }

    @ConfigItem(
        keyName = "tilePacksDrawOnMinimap",
        name = "Draw Tiles on Minimap",
        description = "Configures whether tile packs tiles should be drawn on minimap",
        section = tilePacksSection,
        position = 3
    )
    default boolean tilePacksDrawOnMinimap()
    {
        return false;
    }

    @ConfigItem(
        keyName = "tilePacksBorderWidth",
        name = "Border Width",
        description = "Width of the marked tile border",
        section = tilePacksSection,
        position = 4
    )
    default double tilePacksBorderWidth()
    {
        return 2;
    }

    @Range(min = 0, max = 255)
    @ConfigItem(
        keyName = "tilePacksFillOpacity",
        name = "Fill Opacity",
        description = "Opacity of the tile fill color",
        section = tilePacksSection,
        position = 5
    )
    default int tilePacksFillOpacity()
    {
        return 50;
    }
}
