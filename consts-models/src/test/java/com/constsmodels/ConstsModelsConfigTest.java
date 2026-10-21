package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import org.junit.Test;

public class ConstsModelsConfigTest
{
    /**
     * RuneLite hands a plugin its config as a Proxy, defined outside the plugin's package, so every type a setting
     * returns has to be public. A package-private ExportFormat stopped the viewer from opening.
     */
    @Test
    public void everySettingsTypeIsPublic()
    {
        for (Method m : ConstsModelsConfig.class.getMethods())
        {
            Class<?> type = m.getReturnType();
            assertTrue(m.getName() + " returns " + type.getName(), type.isPrimitive() || Modifier.isPublic(type.getModifiers()));
        }
    }

    @Test
    public void theConfigProxyCanHandBackTheFormat()
    {
        ConstsModelsConfig config = (ConstsModelsConfig) Proxy.newProxyInstance(ConstsModelsConfig.class.getClassLoader(),
            new Class<?>[]{ConstsModelsConfig.class}, (proxy, method, args) -> ExportFormat.THREE_MF);
        assertEquals(ExportFormat.THREE_MF, config.format());
    }
}
