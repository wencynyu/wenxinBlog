package com.wenxinblog.gateway.exception;

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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void testHandlesResponseStatusException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");

        Mono<Void> result = handler.handle(exchange, ex);

        StepVerifier.create(result)
            .verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void testHandlesTimeoutException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        java.util.concurrent.TimeoutException ex = new java.util.concurrent.TimeoutException("Request timed out");

        Mono<Void> result = handler.handle(exchange, ex);

        StepVerifier.create(result)
            .verifyComplete();

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
    }

    @Test
    void testHandlesGenericException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("Internal server error");

        Mono<Void> result = handler.handle(exchange, ex);

        StepVerifier.create(result)
            .verifyComplete();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
    }

    @Test
    void testReturnsCorrectContentType() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid input");

        handler.handle(exchange, ex).block();

        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void testHandlesConnectException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        java.net.ConnectException ex = new java.net.ConnectException("Connection refused");

        Mono<Void> result = handler.handle(exchange, ex);

        StepVerifier.create(result)
            .verifyComplete();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
    }
}
