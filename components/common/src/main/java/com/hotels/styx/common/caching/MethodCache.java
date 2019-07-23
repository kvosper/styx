package com.hotels.styx.common.caching;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hotels.styx.api.Clock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class MethodCache implements InvocationHandler {
    private final Object object;
    private final Method method;
    private final Function<Object[], Object> keyExtractor;
    private final Cache<Object, Object> cache;

    MethodCache(Object implementation,
                Method method,
                Function<Object[], Object> keyExtractor,
                Duration expiration,
                Clock clock) {
        this.object = requireNonNull(implementation);
        this.keyExtractor = requireNonNull(keyExtractor);
        this.method = requireNonNull(method);

        CacheBuilder<Object, Object> cb = CacheBuilder.newBuilder();

        if (expiration != null) {
            cb = cb.expireAfterAccess(expiration.toMillis(), MILLISECONDS);
        }

        if (clock != null) {
            cb = cb.ticker(new Ticker() {
                @Override
                public long read() {
                    return MILLISECONDS.toNanos(clock.tickMillis());
                }
            });
        }

        this.cache = cb.build();
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
