package com.wenxinblog.gateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Redis滑动窗口限流过滤器
 * 支持基于用户/IP/API的限流
 */
@Slf4j
@Component
public class RateLimitFilter extends AbstractGatewayFilterFactory<RateLimitFilter.Config> {

    private static final String RATE_LIMIT_PREFIX = "rate-limit:";
    private static final int DEFAULT_LIMIT = 60;
    private static final int DEFAULT_WINDOW = 60;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> rateLimitScript;

    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;

        // 加载Lua脚本
        try {
            ClassPathResource resource = new ClassPathResource("scripts/rate-limit.lua");
            this.rateLimitScript = RedisScript.of(resource, List.class);
            log.info("Rate limit Lua script loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load rate limit script", e);
            throw new RuntimeException("Failed to initialize rate limit filter", e);
        }
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            // 获取限流key
            String limitKey = buildLimitKey(exchange, config);

            // 执行限流检查
            return checkRateLimit(limitKey, config.getLimit(), config.getWindow())
                .flatMap(allowed -> {
                    if (Boolean.TRUE.equals(allowed)) {
                        log.debug("Request allowed for key: {}", limitKey);
                        return chain.filter(exchange);
                    } else {
                        log.warn("Rate limit exceeded for key: {}, path: {}", limitKey, path);
                        return rateLimitExceeded(exchange);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Rate limit check failed for path: {}", path, e);
                    // 限流检查失败时放行，避免影响正常业务
                    return chain.filter(exchange);
                });
        };
    }

    /**
     * 构建限流key
     * 优先级: 用户 > IP > API
     */
    private String buildLimitKey(ServerWebExchange exchange, Config config) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

        if (userId != null && !userId.isEmpty() && "user".equals(config.getType())) {
            return RATE_LIMIT_PREFIX + "user:" + userId;
        }

        String ip = getClientIp(exchange);
        if ("ip".equals(config.getType()) || userId == null) {
            return RATE_LIMIT_PREFIX + "ip:" + ip;
        }

        return RATE_LIMIT_PREFIX + "api:" + exchange.getRequest().getPath().value();
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        }
        // 处理多个IP的情况，取第一个
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 检查限流
     */
    private Mono<Boolean> checkRateLimit(String key, int limit, int window) {
        long now = System.currentTimeMillis();

        return redisTemplate.execute(
            rateLimitScript,
            Collections.singletonList(key),
            List.of(String.valueOf(limit), String.valueOf(window), String.valueOf(now))
        )
        .next()
        .map(result -> {
            if (result instanceof List) {
                List<?> list = (List<?>) result;
                if (!list.isEmpty() && "1".equals(String.valueOf(list.get(0)))) {
                    return true;
                }
            } else if ("1".equals(String.valueOf(result))) {
                return true;
            }
            return false;
        })
        .defaultIfEmpty(true);
    }

    /**
     * 返回429限流响应
     */
    private Mono<Void> rateLimitExceeded(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", "60");

        String body = "{\"success\":false,\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"请求过于频繁，请稍后再试\"}}";

        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器配置
     */
    @Data
    public static class Config {
        private int limit = DEFAULT_LIMIT;
        private int window = DEFAULT_WINDOW;
        private String type = "user";  // user, ip, api
    }
}
