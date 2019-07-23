package com.hotels.styx.common.caching;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hotels.styx.api.HttpRequest.post;
import static com.hotels.styx.api.HttpResponse.response;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MethodCacheTest {
    private final HttpRequest requestFoo = post("http://example.org/foo").build();
    private final HttpRequest requestBar = post("http://example.org/bar").build();
    private AtomicInteger tick;
    private MethodCaching<WebServiceHandler> caching;

    @BeforeMethod
    public void setUp() {
        tick = new AtomicInteger();
        caching = new MethodCaching.Builder<WebServiceHandler>()
                .interfaceType(WebServiceHandler.class)
                .keyExtractor(args -> {
                    HttpRequest rq = (HttpRequest) args[0];
                    return rq.path();
                })
                .clock(tick::get)
                .build();
    }

    @Test
    public void cachesResultsOfCalls() {
        AtomicInteger calls = new AtomicInteger();

        WebServiceHandler hand = (request, context) -> {
            calls.incrementAndGet();
            return Eventual.of(response()
                    .body(request.path(), UTF_8)
                    .build());
        };

        WebServiceHandler cached = caching.cacheMethod(hand);

        for (int i = 0; i < 10; i++) {
            assertThat(call(cached, requestFoo).bodyAs(UTF_8), is("/foo"));
        }
        assertThat(calls.get(), is(1));

        for (int i = 0; i < 10; i++) {
            assertThat(call(cached, requestBar).bodyAs(UTF_8), is("/bar"));
        }
        assertThat(calls.get(), is(2));
    }

    @Test
    public void cacheExpiresAfterLackOfAccess() {
        AtomicInteger calls = new AtomicInteger();

        WebServiceHandler hand = (request, context) -> {
            calls.incrementAndGet();
            return Eventual.of(response()
                    .body(request.path(), UTF_8)
                    .build());
        };

        WebServiceHandler cached = caching.cacheMethod(hand, Duration.ofSeconds(5));

        for (int i = 0; i < 3; i++) {
            assertThat(call(cached, requestFoo).bodyAs(UTF_8), is("/foo"));
        }
        assertThat(calls.get(), is(1));

        tick.set(5001);

        for (int i = 0; i < 3; i++) {
            assertThat(call(cached, requestFoo).bodyAs(UTF_8), is("/foo"));
        }

        assertThat(calls.get(), is(2));
    }

    private static HttpResponse call(WebServiceHandler handler, HttpRequest request) {
        return Mono.from(handler.handle(request, new HttpInterceptorContext())).block();
    }
}