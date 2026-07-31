package com.wenxinblog.gateway.exception;

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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理所有异常，返回标准错误格式
 */
@Slf4j
@Order(-1)  // 优先级低于 FallbackHandler：降级类异常由 FallbackHandler 优先处理
@Component
public class GlobalExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 确定HTTP状态码
        HttpStatus status = determineStatus(ex);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 构建错误响应
        Map<String, Object> errorResponse = buildErrorResponse(ex, status);

        // 记录错误日志
        logError(exchange, ex, status);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to write error response", e);
            return Mono.error(e);
        }
    }

    /**
     * 确定HTTP状态码
     */
    private HttpStatus determineStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            return HttpStatus.valueOf(((ResponseStatusException) ex).getStatusCode().value());
        }

        // 根据异常类型返回不同状态码
        String exceptionType = ex.getClass().getSimpleName();
        return switch (exceptionType) {
            case "TimeoutException" -> HttpStatus.GATEWAY_TIMEOUT;
            case "CircuitBreakerOpenException" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "RateLimitExceededException" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 构建错误响应
     */
    private Map<String, Object> buildErrorResponse(Throwable ex, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);

        Map<String, Object> error = new HashMap<>();
        error.put("code", getErrorCode(ex));
        error.put("message", getErrorMessage(ex));
        error.put("status", status.value());

        // 开发环境返回详细错误信息
        if (isDevelopment()) {
            error.put("exception", ex.getClass().getSimpleName());
            if (ex.getMessage() != null) {
                error.put("detail", ex.getMessage());
            }
        }

        response.put("error", error);
        return response;
    }

    /**
     * 获取错误码
     */
    private String getErrorCode(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            return ((ResponseStatusException) ex).getReason();
        }

        return switch (ex.getClass().getSimpleName()) {
            case "TimeoutException" -> "GATEWAY_TIMEOUT";
            case "CircuitBreakerOpenException" -> "SERVICE_UNAVAILABLE";
            case "RateLimitExceededException" -> "RATE_LIMIT_EXCEEDED";
            case "ConnectException" -> "SERVICE_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }

    /**
     * 获取错误消息
     */
    private String getErrorMessage(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            String reason = ((ResponseStatusException) ex).getReason();
            return reason != null ? reason : "请求处理失败";
        }

        return switch (ex.getClass().getSimpleName()) {
            case "TimeoutException" -> "请求超时，请稍后重试";
            case "CircuitBreakerOpenException" -> "服务暂时不可用，请稍后重试";
            case "RateLimitExceededException" -> "请求过于频繁，请稍后再试";
            case "ConnectException" -> "服务连接失败，请稍后重试";
            default -> isDevelopment() ? ex.getMessage() : "服务器内部错误";
        };
    }

    /**
     * 记录错误日志
     */
    private void logError(ServerWebExchange exchange, Throwable ex, HttpStatus status) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        // 根据状态码决定日志级别
        if (status.is5xxServerError()) {
            log.error("Server error: {} {} - Status: {} - Error: {}", method, path, status.value(), ex.getMessage(), ex);
        } else if (status.is4xxClientError()) {
            log.warn("Client error: {} {} - Status: {} - Error: {}", method, path, status.value(), ex.getMessage());
        } else {
            log.error("Unexpected error: {} {} - Status: {}", method, path, status.value(), ex);
        }
    }

    /**
     * 判断是否为开发环境
     */
    private boolean isDevelopment() {
        String env = System.getProperty("spring.profiles.active");
        return env == null || "dev".equals(env) || "development".equals(env);
    }
}
