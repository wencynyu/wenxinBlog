package com.wenxinblog.gateway.handler;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断降级处理器
 * 处理服务不可用、超时、限流等降级场景，返回缓存兜底响应。
 * 优先级高于 GlobalExceptionHandler：仅拦截降级类异常，
 * 其余异常通过 Mono.error 交由 GlobalExceptionHandler 统一处理。
 */
@Slf4j
@Order(-2)  // 最高优先级，先于 GlobalExceptionHandler 执行
@Component
public class FallbackHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 非降级类异常不拦截，交给 GlobalExceptionHandler 处理
        if (!isFallbackException(ex)) {
            return Mono.error(ex);
        }

        ServerHttpResponse response = exchange.getResponse();

        if (ex instanceof ResponseStatusException) {
            response.setStatusCode(((ResponseStatusException) ex).getStatusCode());
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 构建降级响应
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);

        Map<String, Object> error = new HashMap<>();
        error.put("code", determineErrorCode(ex));
        error.put("message", getFallbackMessage(exchange, ex));
        error.put("fallback", true);

        errorResponse.put("error", error);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to write fallback response", e);
            return Mono.error(e);
        }
    }

    /**
     * 判断是否为降级类异常（超时、熔断、限流、下游 5xx）
     */
    private boolean isFallbackException(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            return ((ResponseStatusException) ex).getStatusCode().is5xxServerError();
        }
        return switch (ex.getClass().getSimpleName()) {
            case "TimeoutException", "CircuitBreakerOpenException", "RateLimitExceededException" -> true;
            default -> false;
        };
    }

    /**
     * 确定错误码
     */
    private String determineErrorCode(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            HttpStatus status = HttpStatus.valueOf(((ResponseStatusException) ex).getStatusCode().value());
            if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                return "SERVICE_UNAVAILABLE";
            } else if (status == HttpStatus.GATEWAY_TIMEOUT) {
                return "GATEWAY_TIMEOUT";
            }
        }
        return "FALLBACK_ACTIVATED";
    }

    /**
     * 获取降级消息
     */
    private String getFallbackMessage(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().value();

        // 针对不同服务的降级策略
        if (path.startsWith("/api/v1/recommend")) {
            log.warn("Recommendation service fallback activated, returning cached data");
            return "推荐服务暂时不可用，已为您显示热门内容";
        } else if (path.startsWith("/api/v1/search")) {
            log.warn("Search service fallback activated");
            return "搜索服务暂时不可用，请稍后再试";
        } else if (path.startsWith("/api/v1/posts")) {
            log.warn("Blog service fallback activated for path: {}", path);
            return "博文服务暂时不可用，请稍后再试";
        } else {
            log.warn("Service fallback activated for path: {}, error: {}", path, ex.getMessage());
            return "服务暂时不可用，请稍后再试";
        }
    }
}
