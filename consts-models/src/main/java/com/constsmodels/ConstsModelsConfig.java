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
        description = "The file the viewer saves first: OBJ with its colors in a material file, or PLY with a color on every corner",
        position = 1
    )
    default ExportFormat format()
    {
        return ExportFormat.OBJ;
    }

    @ConfigItem(
        keyName = "captureRadius",
        name = "Area radius (tiles)",
        description = "How far Capture area reaches from you, in tiles, each way",
        position = 2
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
        position = 3
    )
    default boolean shiftOnly()
    {
        return false;
    }

    // The same three, written from the sidebar's Settings section

    @ConfigItem(
        keyName = "format",
        name = "",
        description = ""
    )
    void format(ExportFormat format);

    @ConfigItem(
        keyName = "captureRadius",
        name = "",
        description = ""
    )
    void captureRadius(int radius);

    @ConfigItem(
        keyName = "shiftOnly",
        name = "",
        description = ""
    )
    void shiftOnly(boolean shiftOnly);

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

    @ConfigItem(
        keyName = "background",
        name = "",
        description = "",
        hidden = true
    )
    default int background()
    {
        return ViewerCanvas.DEFAULT_BACKGROUND;
    }

    @ConfigItem(
        keyName = "background",
        name = "",
        description = ""
    )
    void background(int rgb);
}
