package com.constsdestination;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(ConstsDestinationConfig.GROUP)
public interface ConstsDestinationConfig extends Config
{
    // Not plain "destination": on the Plugin Hub a group name has to be ours alone.
    String GROUP = "constsdestination";

    @ConfigItem(
        keyName = "highlightColor",
        name = "Highlight Color",
        description = "Color for the tile under your cursor",
        position = 0
    )
    default Color highlightColor()
    {
        return new Color(62, 170, 255);
    }

    @Range(min = 1, max = 6)
    @ConfigItem(
        keyName = "borderWidth",
        name = "Border Width",
        description = "Outline thickness in pixels",
        position = 1
    )
    default int borderWidth()
    {
        return 2;
    }

    @Range(min = 0, max = 255)
    @ConfigItem(
        keyName = "fillOpacity",
        name = "Fill Opacity",
        description = "Fill transparency (0 = outline only, 255 = solid)",
        position = 2
    )
    default int fillOpacity()
    {
        return 30;
    }

    @ConfigItem(
        keyName = "clickedColor",
        name = "Clicked Color",
        description = "Color for the clicked tile",
        position = 3
    )
    default Color clickedColor()
    {
        return new Color(255, 165, 0);
    }
}
