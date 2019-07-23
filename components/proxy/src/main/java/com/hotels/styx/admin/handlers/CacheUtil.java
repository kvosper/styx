package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.common.Pair;
import com.hotels.styx.common.caching.MethodCaching;


/**
 * Provides a quick way to server-side cache the output of a handler for a URL.
 */
public final class CacheUtil {
    public static final MethodCaching<WebServiceHandler> WEB_SERVICE_HANDLER_CACHING =
            new MethodCaching.Builder<WebServiceHandler>()
                    .interfaceType(WebServiceHandler.class)
                    .keyExtractor(args -> {
                        HttpRequest rq = (HttpRequest) args[0];
                        return key(rq);
                    }).build();

    private CacheUtil() {
    }

    private static Object key(HttpRequest rq) {
        return Pair.pair(rq.path(), rq.queryParams());
    }

}
