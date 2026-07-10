package com.wenxinblog.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * JWT认证过滤器
 * 验证JWT Token并将用户信息添加到请求头
 */
@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    // 白名单路径（不需要认证）
    private static final Set<String> WHITELIST_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/oauth",
        "/health"
    );

    private final WebClient authServiceWebClient;

    public AuthenticationFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.authServiceWebClient = webClientBuilder
            .baseUrl("http://localhost:8001")
            .build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            // 检查是否在白名单中
            if (isWhitelisted(path)) {
                log.debug("Path {} is in whitelist, skipping authentication", path);
                return chain.filter(exchange);
            }

            // 提取Token
            String token = extractToken(exchange);
            if (token == null || token.isEmpty()) {
                log.warn("No token found for path: {}", path);
                return unauthorized(exchange, "Missing authorization token");
            }

            // 验证Token
            return validateToken(token)
                .flatMap(userInfo -> {
                    if (userInfo == null || !userInfo.isValid()) {
                        log.warn("Invalid token for path: {}", path);
                        return unauthorized(exchange, "Invalid or expired token");
                    }

                    // 添加用户信息到请求头
                    exchange.getRequest().mutate()
                        .header("X-User-Id", userInfo.getUserId())
                        .header("X-User-Roles", String.join(",", userInfo.getRoles()))
                        .header("X-User-Email", userInfo.getEmail())
                        .build();

                    log.debug("User {} authenticated successfully for path: {}", userInfo.getUserId(), path);
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    log.error("Authentication error for path: {}", path, e);
                    return unauthorized(exchange, "Authentication service unavailable");
                });
        };
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        return WHITELIST_PATHS.stream()
            .anyMatch(whitelist -> path.equals(whitelist) || path.startsWith(whitelist + "/"));
    }

    /**
     * 从请求头提取Token
     */
    private String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * 调用auth-service验证Token
     */
    private Mono<UserInfo> validateToken(String token) {
        return authServiceWebClient.get()
            .uri("/api/v1/auth/validate")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .retrieve()
            .bodyToMono(ValidationResponse.class)
            .map(response -> new UserInfo(
                response.getUserId(),
                response.getEmail(),
                response.getRoles()
            ))
            .onErrorReturn(new UserInfo(null, null, List.of()));
    }

    /**
     * 返回401未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
            "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"%s\"}}",
            message
        );

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器配置类
     */
    public static class Config {
        // 可配置的参数
    }

    /**
     * 用户信息
     */
    private static class UserInfo {
        private final String userId;
        private final String email;
        private final List<String> roles;

        public UserInfo(String userId, String email, List<String> roles) {
            this.userId = userId;
            this.email = email;
            this.roles = roles;
        }

        public String getUserId() {
            return userId;
        }

        public String getEmail() {
            return email;
        }

        public List<String> getRoles() {
            return roles;
        }

        public boolean isValid() {
            return userId != null && !userId.isEmpty();
        }
    }

    /**
     * Token验证响应
     */
    static class ValidationResponse {
        private String userId;
        private String email;
        private List<String> roles;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
