package com.wenxinblog.gateway.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class FallbackHandlerTest {

    private FallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FallbackHandler();
    }

    @Test
    void testReturnsFallbackMessageForRecommendationService() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/recommend/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("Service unavailable");

        Mono<Void> result = handler.handle(exchange, ex);

        StepVerifier.create(result)
            .verifyComplete();

        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void testReturnsFallbackMessageForSearchService() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/search?q=test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("Service unavailable");

        handler.handle(exchange, ex).block();

        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void testReturnsFallbackMessageForBlogService() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts/123").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("Service unavailable");

        handler.handle(exchange, ex).block();

        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void testReturnsGenericFallbackMessageForOtherPaths() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/unknown").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("Service unavailable");

        handler.handle(exchange, ex).block();

        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void testHandlesResponseStatusException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");

        handler.handle(exchange, ex).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }
}
