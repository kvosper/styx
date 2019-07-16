package com.hotels.styx.admin.handlers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A general-purpose cache that can be applied to any interface.
 */
public class MethodCache implements InvocationHandler {
    private final Object object;
    private final Method method;
    private final Function<Object[], Object> keyExtractor;
    private final Cache<Object, Object> cache;

    public MethodCache(
            Object object,
            Method method,
            Duration expiration,
            Function<Object[], Object> keyExtractor
    ) {

        this.object = object;
        this.method = method;
        this.keyExtractor = keyExtractor;
        cache = CacheBuilder.newBuilder()
                .expireAfterAccess(expiration.toMillis(), MILLISECONDS)
                .build();
    }

    // maybe use builder pattern?
    public static <T> T cached(Class<T> interfaceType, T implementation, Duration expiration, Function<Object[], Object> keyExtractor) {
        Method[] declaredMethods = interfaceType.getDeclaredMethods();

        if (declaredMethods.length > 1) {
            throw new IllegalArgumentException("Interface must be effectively functional");
        }

        return cached(interfaceType, implementation, expiration, keyExtractor, declaredMethods[0]);
    }

    public static <T> T cached(Class<T> interfaceType, T implementation, Duration expiration, Function<Object[], Object> keyExtractor, Method method) {
        ClassLoader classLoader = interfaceType.getClassLoader();
        Class<?>[] interfaces = {interfaceType};
        InvocationHandler handler = new MethodCache(implementation, method, expiration, keyExtractor);
        return (T) Proxy.newProxyInstance(classLoader, interfaces, handler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!method.equals(this.method)) {
            return method.invoke(object, args);
        }

        Object key = keyExtractor.apply(args);

        return getFromOrAddToCache(key, () -> method.invoke(object, args));
    }

    private Object getFromOrAddToCache(Object key, Invoker generator) throws ExecutionException {
        return cache.get(key, generator::invoke);
    }

    private interface Invoker {
        Object invoke() throws Exception;
    }
}
