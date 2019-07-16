package com.hotels.styx.common;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hotels.styx.api.Clock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A general-purpose cache that can be applied to any interface.
 */
public class MethodCache implements InvocationHandler {
    private final Object object;
    private final Method method;
    private final Function<Object[], Object> keyExtractor;
    private final Cache<Object, Object> cache;

    private MethodCache(Builder<?> builder) {
        this.object = requireNonNull(builder.implementation);
        this.method = builder.method();
        this.keyExtractor = requireNonNull(builder.keyExtractor);

        CacheBuilder<Object, Object> cb = CacheBuilder.newBuilder();

        if (builder.expiration != null) {
            cb = cb.expireAfterAccess(builder.expiration.toMillis(), MILLISECONDS);
        }

        if (builder.clock != null) {
            cb = cb.ticker(new Ticker() {
                @Override
                public long read() {
                    return MILLISECONDS.toNanos(builder.clock.tickMillis());
                }
            });
        }

        cache = cb.build();
    }

    public static <T> Builder<T> cached() {
        return new Builder<>();
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

    public static class Builder<T> {
        private Class<T> interfaceType;
        private T implementation;
        private Method method;
        private Function<Object[], Object> keyExtractor;
        private Duration expiration;
        private Clock clock;

        public Builder<T> interfaceType(Class<T> interfaceType) {
            this.interfaceType = requireNonNull(interfaceType);
            return this;
        }

        public Builder<T> implementation(T implementation) {
            this.implementation = requireNonNull(implementation);
            return this;
        }

        public Builder<T> method(Method method) {
            this.method = requireNonNull(method);
            return this;
        }

        public Builder<T> keyExtractor(Function<Object[], Object> keyExtractor) {
            this.keyExtractor = requireNonNull(keyExtractor);
            return this;
        }

        public Builder<T> expiration(Duration expiration) {
            this.expiration = requireNonNull(expiration);
            return this;
        }

        @VisibleForTesting
        Builder<T> clock(Clock clock) {
            this.clock = requireNonNull(clock);
            return this;
        }

        public T build() {
            ClassLoader classLoader = interfaceType.getClassLoader();
            Class<?>[] interfaces = {interfaceType};
            InvocationHandler handler = new MethodCache(this);
            return (T) Proxy.newProxyInstance(classLoader, interfaces, handler);
        }

        private Method method() {
            if (this.method != null) {
                return method;
            }

            Method[] declaredMethods = interfaceType.getDeclaredMethods();

            if (declaredMethods.length > 1) {
                throw new IllegalArgumentException("Interface must be effectively functional");
            }

            return declaredMethods[0];
        }
    }
}
