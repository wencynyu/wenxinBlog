package com.wenxinblog.gateway.predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class InternalOnlyPredicateTest {

    private InternalOnlyPredicate predicate;

    @BeforeEach
    void setUp() {
        predicate = new InternalOnlyPredicate();
    }

    @Test
    void testAllowsInternalIp_10Prefix() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }

    @Test
    void testAllowsInternalIp_192_168Prefix() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .remoteAddress(new InetSocketAddress("192.168.1.100", 12345))
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }

    @Test
    void testAllowsInternalIp_172_16Prefix() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .remoteAddress(new InetSocketAddress("172.16.0.1", 12345))
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }

    @Test
    void testAllowsLocalhost_Ip() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }

    @Test
    void testAllowsLocalhost_String() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .header("X-Forwarded-For", "localhost")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }

    @Test
    void testBlocksExternalIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .remoteAddress(new InetSocketAddress("8.8.8.8", 12345))
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertFalse(result);
    }

    @Test
    void testUsesXForwardedForHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .header("X-Forwarded-For", "10.0.0.5")
            .remoteAddress(new InetSocketAddress("8.8.8.8", 12345))
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }

    @Test
    void testUsesXRealIpHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .header("X-Real-IP", "192.168.1.50")
            .remoteAddress(new InetSocketAddress("8.8.8.8", 12345))
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }

    @Test
    void testHandlesMultipleIpsInXForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .header("X-Forwarded-For", "10.0.0.1, 8.8.8.8")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }

    @Test
    void testAllowsIpv6Localhost() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/ads")
            .header("X-Forwarded-For", "::1")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        boolean result = predicate.apply(new InternalOnlyPredicate.Config()).test(exchange);

        assertTrue(result);
    }
}
