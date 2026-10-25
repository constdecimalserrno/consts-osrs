package com.constsexp;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.Test;

/**
 * RuneLite hands a plugin its config as a {@link Proxy} outside the plugin's package, so every setting's type must be
 * public, or reading it throws IllegalAccessError on every call (the tick pendulum's Rhythm did, 2026-10-25).
 */
public class ConfigProxyTest
{
    @Test
    public void everySettingShouldReadThroughAProxy() throws Exception
    {
        ConstsExpConfig config = (ConstsExpConfig) Proxy.newProxyInstance(ConstsExpConfig.class.getClassLoader(),
            new Class<?>[]{ConstsExpConfig.class}, (proxy, method, args) -> sample(method.getReturnType()));
        for (Method method : ConstsExpConfig.class.getMethods())
        {
            if (method.getParameterCount() == 0 && method.getReturnType() != void.class)
            {
                method.invoke(config);
            }
        }
    }

    private static Object sample(Class<?> type)
    {
        if (type.isEnum())
        {
            return type.getEnumConstants()[0];
        }
        if (type == boolean.class)
        {
            return false;
        }
        if (type == int.class)
        {
            return 0;
        }
        if (type == double.class)
        {
            return 0d;
        }
        if (type == long.class)
        {
            return 0L;
        }
        return null;
    }
}
