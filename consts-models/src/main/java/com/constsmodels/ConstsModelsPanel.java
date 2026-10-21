package com.constsmodels;

import net.runelite.client.ui.PluginPanel;

/**
 * TICKET 5 (stub): the sidebar. The folder files are saved to (choose it, open it), Capture area, Export yourself,
 * and the models captured this session, to open again in the viewer.
 */
class ConstsModelsPanel extends PluginPanel
{
    private final ConstsModelsPlugin plugin;
    private final ConstsModelsConfig config;

    ConstsModelsPanel(ConstsModelsPlugin plugin, ConstsModelsConfig config)
    {
        this.plugin = plugin;
        this.config = config;
    }

    /** A capture to list, newest first. Call it on the Swing thread. */
    void addRecent(Mesh mesh)
    {
    }
}
