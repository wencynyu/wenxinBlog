package com.wenxinblog.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * JWT 认证过滤器（Spring Cloud 2025 命名约定：类名以 GatewayFilterFactory 结尾）。
 *
 * 策略：GET 请求 + 白名单路径（auth/**、health）直接放行；
 * POST/PUT/DELETE 提取 Bearer token → 调 auth-service 验证 → 注入
 * X-User-Id / X-User-Roles / X-User-Email 到下游请求头。
 */
@Slf4j
@Component
public class AuthenticationFilterGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AuthenticationFilterGatewayFilterFactory.Config> {

    private static final Set<String> WHITELIST = Set.of("/api/v1/auth", "/health");

    private final WebClient authServiceClient;

    public AuthenticationFilterGatewayFilterFactory(WebClient.Builder builder) {
        super(Config.class);
        this.authServiceClient = builder.baseUrl("http://localhost:8001").build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            HttpMethod method = exchange.getRequest().getMethod();

            // GET 请求 + 白名单 → 直接放行
            if (method == HttpMethod.GET || isWhitelisted(path)) {
                return chain.filter(exchange);
            }

            // 提取 Bearer token
            String token = extractToken(exchange);
            if (token == null || token.isEmpty()) {
                return unauthorized(exchange, "Missing authorization token");
            }

            // 验证 token → 注入用户信息
            return validateToken(token)
                    .flatMap(userInfo -> {
                        if (userInfo == null || !userInfo.isValid()) {
                            return unauthorized(exchange, "Invalid or expired token");
                        }
                        String userId = userInfo.userId();
                        log.debug("Auth OK: user={} method={} path={}", userId, method, path);
                        return chain.filter(
                                exchange.mutate()
                                        .request(r -> r.header("X-User-Id", userId)
                                                .header("X-User-Roles", String.join(",", userInfo.roles()))
                                                .header("X-User-Email", userInfo.email()))
                                        .build()
                        );
                    })
                    .onErrorResume(e -> {
                        log.error("Auth error for {} {}: {}", method, path, e.getMessage());
                        return unauthorized(exchange, "Authentication service unavailable");
                    });
        };
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(prefix -> path.startsWith(prefix));
    }

    private String extractToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Mono<UserInfo> validateToken(String token) {
        return authServiceClient.get()
                .uri("/api/v1/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(ValidationResponse.class)
                .map(r -> new UserInfo(r.getUserId(), r.getEmail(), r.getRoles()))
                .onErrorReturn(new UserInfo(null, null, List.of()));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\""
                + message + "\"}}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    // --- inner types ---

    public static class Config {
    }

    private record UserInfo(String userId, String email, List<String> roles) {
        boolean isValid() {
            return userId != null && !userId.isEmpty();
        }
    }

    @lombok.Data
    static class ValidationResponse {
        private String userId;
        private String email;
        private List<String> roles;
    }
}
