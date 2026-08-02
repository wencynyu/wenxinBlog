package com.wenxinblog.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationFilterTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private AuthenticationFilterGatewayFilterFactory filter;

    @BeforeEach
    void setUp() {
        doReturn(webClientBuilder).when(webClientBuilder).baseUrl(anyString());
        doReturn(webClient).when(webClientBuilder).build();
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when((WebClient.RequestHeadersSpec) requestHeadersSpec).header(anyString(), any());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        filter = new AuthenticationFilterGatewayFilterFactory(webClientBuilder);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void testWhitelistedPath_SkipsAuthentication() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/health").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);


        filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

        // Whitelisted paths should pass through to chain
        verify(chain).filter(any());
    }

    @Test
    void testWhitelistedPathWithSlash_SkipsAuthentication() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login/verify").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);


        filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void testWhitelistedPaths_AllPathsCovered() {
        List<String> whitelistedPaths = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/oauth",
            "/health"
        );

        for (String path : whitelistedPaths) {
            MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);


            filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

            verify(chain, atLeastOnce()).filter(any());
            // Reset for next iteration
            reset(chain); when(chain.filter(any())).thenReturn(Mono.empty());
        }
    }

    @Test
    void testMissingToken_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void testInvalidTokenFormat_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/posts")
            .header(HttpHeaders.AUTHORIZATION, "InvalidFormat token123")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void testEmptyToken_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/posts")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void testValidToken_PassesThroughToChain() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);


        // Mock auth-service returning a valid user
        AuthenticationFilterGatewayFilterFactory.ValidationResponse response = new AuthenticationFilterGatewayFilterFactory.ValidationResponse();
        response.setUserId("user123");
        response.setEmail("test@example.com");
        response.setRoles(List.of("USER"));
        response.setPermissions(List.of("post:create"));
        doReturn(Mono.just(response)).when(responseSpec).bodyToMono(AuthenticationFilterGatewayFilterFactory.ValidationResponse.class);

        filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

        // Valid token should pass through to chain, response status set by downstream
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        // 注入真实身份头 + RBAC 权限头
        assertEquals("user123", captor.getValue().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("post:create", captor.getValue().getRequest().getHeaders().getFirst("X-User-Permissions"));
    }

    @Test
    void testAuthServiceUnavailable_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/posts")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // Mock auth-service returning error
        when(responseSpec.bodyToMono(AuthenticationFilterGatewayFilterFactory.ValidationResponse.class))
            .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void testInvalidToken_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/posts")
            .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // Mock auth-service returning invalid user (null userId)
        AuthenticationFilterGatewayFilterFactory.ValidationResponse response = new AuthenticationFilterGatewayFilterFactory.ValidationResponse();
        response.setUserId(null);
        when(responseSpec.bodyToMono(AuthenticationFilterGatewayFilterFactory.ValidationResponse.class))
            .thenReturn(Mono.just(response));

        filter.apply(new AuthenticationFilterGatewayFilterFactory.Config()).filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
