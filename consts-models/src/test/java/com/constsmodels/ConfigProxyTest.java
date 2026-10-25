package com.constsmodels;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.Test;

/**
 * RuneLite hands a plugin its config as a {@link Proxy} outside the plugin's package, so every setting's type must be
 * public, or reading it throws IllegalAccessError on every call.
 */
public class ConfigProxyTest
{
    @Test
    public void everySettingShouldReadThroughAProxy() throws Exception
    {
        ConstsModelsConfig config = (ConstsModelsConfig) Proxy.newProxyInstance(ConstsModelsConfig.class.getClassLoader(),
            new Class<?>[]{ConstsModelsConfig.class}, (proxy, method, args) -> sample(method.getReturnType()));
        for (Method method : ConstsModelsConfig.class.getMethods())
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
