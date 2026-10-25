package com.constsmodels;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(ConstsModelsConfig.GROUP)
public interface ConstsModelsConfig extends Config
{
    String GROUP = "constsmodels";

    // Named here once, for the Configuration panel and the sidebar's Settings section alike
    String FORMAT_NAME = "Save as";
    String FORMAT_DESCRIPTION = "The file the viewer saves first: OBJ with its colors in a material file, or PLY with a color on every corner";
    String RADIUS_NAME = "Area radius (tiles)";
    String RADIUS_DESCRIPTION = "How far Capture area reaches from you, in tiles, each way";
    int RADIUS_MIN = 1;
    int RADIUS_MAX = 24;
    String SHIFT_NAME = "Only with Shift";
    String SHIFT_DESCRIPTION = "Show Export Model on the right-click menu only while Shift is held";

    @ConfigItem(
        keyName = "format",
        name = FORMAT_NAME,
        description = FORMAT_DESCRIPTION,
        position = 1
    )
    default ExportFormat format()
    {
        return ExportFormat.OBJ;
    }

    @ConfigItem(
        keyName = "captureRadius",
        name = RADIUS_NAME,
        description = RADIUS_DESCRIPTION,
        position = 2
    )
    @Range(min = RADIUS_MIN, max = RADIUS_MAX)
    default int captureRadius()
    {
        return 6;
    }

    @ConfigItem(
        keyName = "shiftOnly",
        name = SHIFT_NAME,
        description = SHIFT_DESCRIPTION,
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
