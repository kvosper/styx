package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static com.hotels.styx.admin.handlers.CacheUtil.WEB_SERVICE_HANDLER_CACHING;
import static com.hotels.styx.api.HttpRequest.post;
import static com.hotels.styx.api.HttpResponse.response;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CacheUtilTest {
    private AtomicInteger calls;
    private WebServiceHandler hand;

    @BeforeMethod
    public void setUp() {
        this.calls = new AtomicInteger();
        this.hand = (request, context) -> {
            calls.incrementAndGet();
            return Eventual.of(response()
                    .body(request.url().toString(), UTF_8)
                    .build());
        };
    }

    @Test
    public void cachesMatchingUrls() {
        HttpRequest request = post("http://example.org/foo?a=1").build();

        WebServiceHandler cached = WEB_SERVICE_HANDLER_CACHING
                .cacheMethod(hand);

        assertThat(call(cached, request).bodyAs(UTF_8), is("http://example.org/foo?a=1"));
        assertThat(call(cached, request).bodyAs(UTF_8), is("http://example.org/foo?a=1"));

        assertThat(calls.get(), is(1));
    }

    @Test
    public void doesNotCacheNonMatchingPaths() {
        HttpRequest requestFoo = post("http://example.org/foo?a=1").build();
        HttpRequest requestBar = post("http://example.org/bar?a=1").build();

        WebServiceHandler cached = WEB_SERVICE_HANDLER_CACHING
                .cacheMethod(hand);

        assertThat(call(cached, requestFoo).bodyAs(UTF_8), is("http://example.org/foo?a=1"));
        assertThat(call(cached, requestBar).bodyAs(UTF_8), is("http://example.org/bar?a=1"));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void doesNotCacheNonMatchingQueryParams() {
        HttpRequest requestFoo = post("http://example.org/foo?a=1").build();
        HttpRequest requestBar = post("http://example.org/foo?a=2").build();

        WebServiceHandler cached = WEB_SERVICE_HANDLER_CACHING
                .cacheMethod(hand);

        assertThat(call(cached, requestFoo).bodyAs(UTF_8), is("http://example.org/foo?a=1"));
        assertThat(call(cached, requestBar).bodyAs(UTF_8), is("http://example.org/foo?a=2"));

        assertThat(calls.get(), is(2));
    }

    private static HttpResponse call(WebServiceHandler handler, HttpRequest request) {
        return Mono.from(handler.handle(request, new HttpInterceptorContext())).block();
    }
}