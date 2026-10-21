package com.constsmodels;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(ConstsModelsConfig.GROUP)
public interface ConstsModelsConfig extends Config
{
    String GROUP = "constsmodels";

    @ConfigItem(
        keyName = "format",
        name = "Save as",
        description = "The file the viewer saves first: STL or 3MF to print, OBJ or PLY for 3D software",
        position = 1
    )
    default ExportFormat format()
    {
        return ExportFormat.STL;
    }

    @ConfigItem(
        keyName = "printSize",
        name = "Print size (mm)",
        description = "The longest side of a print, in millimetres. STL and 3MF files are scaled to it",
        position = 2
    )
    @Range(min = 10, max = 500)
    default int printSizeMm()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "base",
        name = "Add a base",
        description = "Stand prints on a solid base, so a model stays up and an area prints as a block",
        position = 3
    )
    default boolean addBase()
    {
        return true;
    }

    @ConfigItem(
        keyName = "colors",
        name = "Print colors",
        description = "How many filament colors a print starts with, 1 to 9; the viewer can change it and pick each color",
        position = 4
    )
    @Range(min = 1, max = PrintColors.MAX)
    default int maxColors()
    {
        return 4;
    }

    @ConfigItem(
        keyName = "captureRadius",
        name = "Area radius (tiles)",
        description = "How far Capture area reaches from you, in tiles, each way",
        position = 5
    )
    @Range(min = 1, max = 24)
    default int captureRadius()
    {
        return 6;
    }

    @ConfigItem(
        keyName = "shiftOnly",
        name = "Only with Shift",
        description = "Show Export Model on the right-click menu only while Shift is held",
        position = 6
    )
    default boolean shiftOnly()
    {
        return false;
    }

    @ConfigItem(
        keyName = "outputFolder",
        name = "",
        description = "",
        hidden = true
    )
    default String outputFolder()
    {
        return "";
    }

    @ConfigItem(
        keyName = "outputFolder",
        name = "",
        description = ""
    )
    void outputFolder(String folder);
}
