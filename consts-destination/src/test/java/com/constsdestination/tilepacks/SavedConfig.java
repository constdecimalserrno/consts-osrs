package com.constsdestination.tilepacks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigManager;
import org.mockito.invocation.InvocationOnMock;

/** A mocked ConfigManager that keeps what's saved, by group and key, the way RuneLite saves it: as text. */
public class SavedConfig
{
    public final Map<String, String> values = new HashMap<>();
    public final ConfigManager configManager = mock(ConfigManager.class);

    public SavedConfig()
    {
        when(configManager.getConfiguration(anyString(), anyString()))
            .thenAnswer(i -> values.get(key(i)));
        when(configManager.getConfigurationKeys(anyString()))
            .thenAnswer(i -> values.keySet().stream()
                .filter(k -> k.startsWith(i.getArgument(0, String.class)))
                .collect(Collectors.toList()));
        doAnswer(i ->
        {
            values.put(key(i), i.getArgument(2, String.class));
            return null;
        }).when(configManager).setConfiguration(anyString(), anyString(), anyString());
        doAnswer(i ->
        {
            values.put(key(i), String.valueOf((Object) i.getArgument(2)));
            return null;
        }).when(configManager).setConfiguration(anyString(), anyString(), (Object) any());
        doAnswer(i ->
        {
            values.remove(key(i));
            return null;
        }).when(configManager).unsetConfiguration(anyString(), anyString());
    }

    public String get(String group, String key)
    {
        return values.get(group + "." + key);
    }

    public void put(String group, String key, String value)
    {
        values.put(group + "." + key, value);
    }

    private static String key(InvocationOnMock i)
    {
        return i.getArgument(0, String.class) + "." + i.getArgument(1, String.class);
    }
}
