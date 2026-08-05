package com.wenxinblog.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 网关级 RBAC 粗粒度授权：按 (METHOD, path) 映射到所需权限，缺失则 403。
 *
 * <p>在 AuthenticationFilter（order 0）之后执行，读取其注入的 X-User-Permissions。
 * 细粒度（own/any 属主判定）仍在各服务内完成，本过滤器只管"管理/粗权限端点"。
 */
@Component
@Order(20)
public class AuthorizationFilter implements GlobalFilter, Ordered {

    private static final List<Rule> RULES = List.of(
            new Rule(HttpMethod.GET, "/api/v1/analytics/**", "analytics:read"),
            new Rule(HttpMethod.GET, "/api/v1/experiments", "experiment:manage"),
            new Rule(HttpMethod.GET, "/api/v1/experiments/*/results", "experiment:manage"),
            new Rule(HttpMethod.POST, "/api/v1/experiments/**", "experiment:manage"),
            new Rule(HttpMethod.PUT, "/api/v1/experiments/**", "experiment:manage"),
            new Rule(HttpMethod.POST, "/api/v1/campaigns/**", "ad:manage"),
            new Rule(HttpMethod.PUT, "/api/v1/campaigns/**", "ad:manage"),
            new Rule(HttpMethod.DELETE, "/api/v1/campaigns/**", "ad:manage"),
            new Rule(HttpMethod.POST, "/api/v1/recommend/admin/backfill", "recommendation:manage"),
            new Rule(HttpMethod.POST, "/api/v1/posts/*/feature", "post:feature"),
            new Rule(HttpMethod.POST, "/api/v1/comments/*/moderate", "comment:moderate"),
            new Rule(HttpMethod.POST, "/api/v1/admin/users/*/ban", "user:ban"),
            new Rule(HttpMethod.POST, "/api/v1/admin/users/*/unban", "user:ban"),
            new Rule(HttpMethod.POST, "/api/v1/admin/users/*/roles", "user:assign_role"),
            new Rule(HttpMethod.POST, "/api/v1/categories/**", "category:manage"),
            new Rule(HttpMethod.PUT, "/api/v1/categories/**", "category:manage"),
            new Rule(HttpMethod.DELETE, "/api/v1/categories/**", "category:manage"),
            // 角色/权限管理（role:manage）
            new Rule(HttpMethod.GET, "/api/v1/admin/permissions", "role:manage"),
            new Rule(HttpMethod.POST, "/api/v1/admin/permissions", "role:manage"),
            new Rule(HttpMethod.DELETE, "/api/v1/admin/permissions/*", "role:manage"),
            new Rule(HttpMethod.GET, "/api/v1/admin/roles", "role:manage"),
            new Rule(HttpMethod.GET, "/api/v1/admin/roles/*", "role:manage"),
            new Rule(HttpMethod.POST, "/api/v1/admin/roles", "role:manage"),
            new Rule(HttpMethod.DELETE, "/api/v1/admin/roles/*", "role:manage"),
            new Rule(HttpMethod.POST, "/api/v1/admin/roles/*/permissions", "role:manage"),
            new Rule(HttpMethod.DELETE, "/api/v1/admin/roles/*/permissions/*", "role:manage"),
            // 用户管理（role:manage）
            new Rule(HttpMethod.GET, "/api/v1/admin/users", "role:manage"),
            new Rule(HttpMethod.GET, "/api/v1/admin/users/*", "role:manage")
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == null) {
            return chain.filter(exchange);
        }

        String required = RULES.stream()
                .filter(r -> r.method().name().equals(method.name()) && matcher.match(r.pattern(), path))
                .map(Rule::permission)
                .findFirst()
                .orElse(null);

        if (required == null) {
            // 未映射该路径 → 交给下游（公开或服务内细粒度校验）
            return chain.filter(exchange);
        }

        String perms = exchange.getRequest().getHeaders().getFirst("X-User-Permissions");
        boolean allowed = perms != null && Arrays.stream(perms.split(","))
                .map(String::trim)
                .anyMatch(required::equals);
        if (!allowed) {
            return forbidden(exchange, required);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String required) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Missing required permission: "
                + required + "\"}}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return 20;
    }

    private record Rule(HttpMethod method, String pattern, String permission) {
    }
}
