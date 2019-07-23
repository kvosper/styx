package com.hotels.styx.common.caching;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Clock;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A reusable factory for applying caching to instances of a given interface.
 *
 * If the interface has only one method, this method will have caching applied.
 * If the interface has multiple methods, the method to cache must be explicitly specified.
 *
 * @param <T> interface type
 */
public class MethodCaching<T> {
    private final Class<T> interfaceType;
    private final Method method;
    private final Function<Object[], Object> keyExtractor;
    private final Clock clock;

    private MethodCaching(Builder<T> builder) {
        this.interfaceType = requireNonNull(builder.interfaceType);
        this.keyExtractor = requireNonNull(builder.keyExtractor);
        this.clock = builder.clock;

        if (builder.method != null) {
            this.method = builder.method;
        } else {
            Method[] declaredMethods = interfaceType.getDeclaredMethods();

            if (declaredMethods.length > 1) {
                throw new IllegalArgumentException("Interface must be effectively functional");
            }

            this.method = declaredMethods[0];
        }
    }

    public T cacheMethod(T original) {
        return cacheMethod(original, null);
    }

    public T cacheMethod(T original, Duration expiration) {
        MethodCache mc = new MethodCache(original, method, keyExtractor, expiration, clock);

        ClassLoader classLoader = interfaceType.getClassLoader();
        Class<?>[] interfaces = {interfaceType};
        return (T) Proxy.newProxyInstance(classLoader, interfaces, mc);
    }

    /**
     * Builder for MethodCaching.
     *
     * @param <T> interface type
     */
    public static final class Builder<T> {
        private Class<T> interfaceType;
        private Method method;
        private Function<Object[], Object> keyExtractor;
        private Clock clock;

        public MethodCaching.Builder<T> interfaceType(Class<T> interfaceType) {
            this.interfaceType = requireNonNull(interfaceType);
            return this;
        }

        public MethodCaching.Builder<T> method(Method method) {
            this.method = requireNonNull(method);
            return this;
        }

        public MethodCaching.Builder<T> keyExtractor(Function<Object[], Object> keyExtractor) {
            this.keyExtractor = requireNonNull(keyExtractor);
            return this;
        }

        @VisibleForTesting
        MethodCaching.Builder<T> clock(Clock clock) {
            this.clock = requireNonNull(clock);
            return this;
        }

        public MethodCaching<T> build() {
            return new MethodCaching<>(this);
        }
    }
}
