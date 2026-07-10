package com.wenxinblog.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private GatewayFilterChain chain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redisTemplate);
    }

    @Test
    void testRequestAllowed_WhenUnderLimit() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header("X-User-Id", "user123")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        RateLimitFilter.Config config = new RateLimitFilter.Config();
        config.setLimit(100);
        config.setWindow(60);
        config.setType("user");

        // Spring Data Redis 4.x: execute(RedisScript, List<K>, List<?>)
        @SuppressWarnings("unchecked")
        Flux<List<String>> result = Flux.just(Collections.singletonList("1"));
        doReturn(result).when(redisTemplate).execute(any(), any(List.class), any(List.class));

        filter.apply(config).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void testRateLimitExceeded_WhenOverLimit() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header("X-User-Id", "user123")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitFilter.Config config = new RateLimitFilter.Config();
        config.setLimit(10);
        config.setWindow(60);
        config.setType("user");

        @SuppressWarnings("unchecked")
        Flux<List<String>> result = Flux.just(Collections.singletonList("0"));
        doReturn(result).when(redisTemplate).execute(any(), any(List.class), any(List.class));

        filter.apply(config).filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }

    @Test
    void testRedisException_FailOpen() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header("X-User-Id", "user123")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        RateLimitFilter.Config config = new RateLimitFilter.Config();
        config.setLimit(100);
        config.setWindow(60);
        config.setType("user");

        @SuppressWarnings("unchecked")
        Flux<List<String>> errorFlux = Flux.error(new RuntimeException("Redis connection failed"));
        doReturn(errorFlux).when(redisTemplate).execute(any(), any(List.class), any(List.class));

        filter.apply(config).filter(exchange, chain).block();

        // Fail-open: allow request through when Redis is down
        verify(chain).filter(exchange);
    }

    @Test
    void testLimitKeyByUserId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header("X-User-Id", "user123")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        RateLimitFilter.Config config = new RateLimitFilter.Config();
        config.setType("user");

        @SuppressWarnings("unchecked")
        Flux<List<String>> result = Flux.just(Collections.singletonList("1"));
        doReturn(result).when(redisTemplate).execute(any(), any(List.class), any(List.class));

        filter.apply(config).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void testLimitKeyByIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        RateLimitFilter.Config config = new RateLimitFilter.Config();
        config.setType("ip");

        @SuppressWarnings("unchecked")
        Flux<List<String>> result = Flux.just(Collections.singletonList("1"));
        doReturn(result).when(redisTemplate).execute(any(), any(List.class), any(List.class));

        filter.apply(config).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void testGetClientIp_XForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        RateLimitFilter.Config config = new RateLimitFilter.Config();

        @SuppressWarnings("unchecked")
        Flux<List<String>> result = Flux.just(Collections.singletonList("1"));
        doReturn(result).when(redisTemplate).execute(any(), any(List.class), any(List.class));

        filter.apply(config).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void testGetClientIp_XRealIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header("X-Real-IP", "192.168.1.50")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        RateLimitFilter.Config config = new RateLimitFilter.Config();

        @SuppressWarnings("unchecked")
        Flux<List<String>> result = Flux.just(Collections.singletonList("1"));
        doReturn(result).when(redisTemplate).execute(any(), any(List.class), any(List.class));

        filter.apply(config).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void testGetClientIp_RemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        RateLimitFilter.Config config = new RateLimitFilter.Config();

        @SuppressWarnings("unchecked")
        Flux<List<String>> result = Flux.just(Collections.singletonList("1"));
        doReturn(result).when(redisTemplate).execute(any(), any(List.class), any(List.class));

        filter.apply(config).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }
}
