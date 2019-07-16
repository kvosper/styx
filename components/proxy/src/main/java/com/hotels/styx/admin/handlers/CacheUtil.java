package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.common.MethodCache;
import com.hotels.styx.common.Pair;

import java.time.Duration;

/**
 * Provides a quick way to server-side cache the output of a handler for a URL.
 */
public final class CacheUtil {
    private CacheUtil() {
    }

    public static WebServiceHandler cached(WebServiceHandler handler, Duration expiration) {
        return MethodCache.<WebServiceHandler>cached()
                .implementation(handler)
                .interfaceType(WebServiceHandler.class)
                .expiration(expiration)
                .keyExtractor(args -> {
                    HttpRequest request = (HttpRequest) args[0];
                    return key(request);
                })
                .build();
    }

    private static Object key(HttpRequest rq) {
        return Pair.pair(rq.path(), rq.queryParams());
    }

}
