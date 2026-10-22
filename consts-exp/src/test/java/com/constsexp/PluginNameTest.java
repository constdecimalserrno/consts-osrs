package com.constsexp;

import static org.junit.Assert.assertEquals;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

/** The plugin is consts-EXP: to RuneLite, to the Plugin Hub, and in the config group its settings are saved under. */
public class PluginNameTest
{
    @Test
    public void runeLiteLoadsItAsConstsExp() throws Exception
    {
        Class<?> plugin = Class.forName("com.constsexp.ConstsExpPlugin");
        assertEquals("consts-EXP", plugin.getAnnotation(PluginDescriptor.class).name());
    }

    @Test
    public void thePluginHubListsItAsConstsExp() throws Exception
    {
        Properties hub = new Properties();
        try (Reader in = Files.newBufferedReader(Paths.get("runelite-plugin.properties")))
        {
            hub.load(in);
        }
        assertEquals("consts-EXP", hub.getProperty("displayName"));
        assertEquals("com.constsexp.ConstsExpPlugin", hub.getProperty("plugins"));
    }

    @Test
    public void itsSettingsAreSavedUnderItsOwnName() throws Exception
    {
        Class<?> config = Class.forName("com.constsexp.ConstsExpConfig");
        assertEquals("constsexp", config.getAnnotation(ConfigGroup.class).value());
    }
}
