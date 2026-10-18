package com.constsdestination;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * One idea in two forms: the hover color for what you're about to act on, the clicked color for what you acted on.
 * Tiles show it on the ground, outlines on NPCs, players, objects and ground items.
 */
@ConfigGroup(ConstsDestinationConfig.GROUP)
public interface ConstsDestinationConfig extends Config
{
    // Not plain "destination": on the Plugin Hub a group name has to be ours alone.
    String GROUP = "constsdestination";

    @ConfigSection(
        name = "Colors",
        description = "The hover color, the clicked color, and the color for a fight",
        position = 0
    )
    String colorsSection = "colors";

    @ConfigSection(
        name = "Tiles",
        description = "The tile under your cursor, and the tile you clicked to walk to",
        position = 1
    )
    String tilesSection = "tiles";

    @ConfigSection(
        name = "Outlines",
        description = "NPCs, players, objects and ground items you hover or interact with",
        position = 2
    )
    String outlinesSection = "outlines";

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
}
