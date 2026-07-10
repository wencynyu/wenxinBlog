package com.wenxinblog.gateway.filter;

import tools.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 访问日志过滤器
 * 将请求/响应日志发送到Kafka
 */
@Slf4j
@Component
public class AccessLogFilter extends AbstractGatewayFilterFactory<AccessLogFilter.Config> {

    private static final String ACCESS_LOG_TOPIC = "wenxinblog.access-log";
    private static final String START_TIME_ATTR = "accessLogStartTime";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AccessLogFilter(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        super(Config.class);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 记录开始时间
            exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                try {
                    sendAccessLog(exchange);
                } catch (Exception e) {
                    log.error("Failed to send access log", e);
                }
            }));
        };
    }

    /**
     * 发送访问日志到Kafka
     */
    private void sendAccessLog(ServerWebExchange exchange) {
        Long startTime = exchange.getAttribute(START_TIME_ATTR);
        if (startTime == null) {
            startTime = System.currentTimeMillis();
        }

        long responseTime = System.currentTimeMillis() - startTime;

        AccessLogEvent logEvent = buildAccessLog(exchange, responseTime);

        try {
            String json = objectMapper.writeValueAsString(logEvent);
            kafkaTemplate.send(ACCESS_LOG_TOPIC, logEvent.getTraceId(), json);
            log.debug("Access log sent: traceId={}, path={}, status={}",
                logEvent.getTraceId(), logEvent.getPath(), logEvent.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to serialize access log", e);
        }
    }

    /**
     * 构建访问日志事件
     */
    private AccessLogEvent buildAccessLog(ServerWebExchange exchange, long responseTime) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        AccessLogEvent event = new AccessLogEvent();
        event.setTraceId(getTraceId(exchange));
        event.setRequestId(UUID.randomUUID().toString());
        event.setTimestamp(Instant.now().toString());
        event.setMethod(request.getMethod().name());
        event.setPath(request.getPath().value());
        event.setQuery(request.getQueryParams().toSingleValueMap());
        event.setUserId(request.getHeaders().getFirst("X-User-Id"));
        event.setClientIp(getClientIp(exchange));
        event.setUserAgent(request.getHeaders().getFirst("User-Agent"));
        event.setService(getServiceName(request.getPath().value()));
        event.setStatusCode(response.getStatusCode() != null ? response.getStatusCode().value() : 0);
        event.setResponseTime(responseTime);
        event.setResponseSize(getResponseSize(exchange));

        // 根据状态码决定日志级别
        int status = event.getStatusCode();
        if (status >= 500) {
            event.setLevel("ERROR");
        } else if (status >= 400) {
            event.setLevel("WARN");
        } else if (shouldSample()) {
            event.setLevel("INFO");
        } else {
            event.setLevel("DEBUG");
        }

        return event;
    }

    /**
     * 获取或生成TraceId
     */
    private String getTraceId(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = exchange.getRequest().getHeaders().getFirst("X-B3-TraceId");
        }
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }

    /**
     * 获取客户端IP
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
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 从路径提取服务名
     */
    private String getServiceName(String path) {
        if (path.startsWith("/api/v1/auth")) return "auth-service";
        if (path.startsWith("/api/v1/users")) return "user-service";
        if (path.startsWith("/api/v1/posts") || path.startsWith("/api/v1/comments") || path.startsWith("/api/v1/tags")) return "blog-service";
        if (path.startsWith("/api/v1/content")) return "content-service";
        if (path.startsWith("/api/v1/search")) return "search-service";
        if (path.startsWith("/api/v1/recommend")) return "recommendation-service";
        if (path.startsWith("/api/v1/ads") || path.startsWith("/internal/ads")) return "ad-service";
        return "unknown";
    }

    /**
     * 获取响应大小
     */
    private long getResponseSize(ServerWebExchange exchange) {
        Long size = exchange.getAttribute("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR");
        return size != null ? size : 0;
    }

    /**
     * 日志采样（10%采样率）
     */
    private boolean shouldSample() {
        return Math.random() < 0.1;
    }

    /**
     * 过滤器配置
     */
    @Data
    public static class Config {
        private boolean enabled = true;
        private double sampleRate = 0.1;
    }

    /**
     * 访问日志事件
     */
    @Data
    public static class AccessLogEvent {
        private String traceId;
        private String requestId;
        private String timestamp;
        private String method;
        private String path;
        private Map<String, String> query;
        private String userId;
        private String clientIp;
        private String userAgent;
        private String service;
        private int statusCode;
        private long responseTime;
        private long responseSize;
        private String level;
    }
}
