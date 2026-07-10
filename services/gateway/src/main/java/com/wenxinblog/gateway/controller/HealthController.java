package com.wenxinblog.gateway.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 聚合所有微服务的健康状态
 */
@Slf4j
@RestController
@RequestMapping("/health")
public class HealthController {

    private final WebClient webClient;

    // 微服务健康检查端点
    private static final Map<String, String> SERVICE_HEALTH_ENDPOINTS = Map.of(
        "auth-service", "http://localhost:8001/health",
        "user-service", "http://localhost:8002/health",
        "blog-service", "http://localhost:8003/health",
        "content-service", "http://localhost:8004/health",
        "search-service", "http://localhost:8005/health",
        "recommendation-service", "http://localhost:8006/health",
        "ad-service", "http://localhost:8007/health"
    );

    public HealthController(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 获取网关和所有服务的健康状态
     */
    @GetMapping
    public Mono<HealthResponse> health() {
        log.debug("Health check requested");

        HealthResponse response = new HealthResponse();
        response.setStatus("UP");
        response.setTimestamp(System.currentTimeMillis());

        // 并行检查所有服务
        return Mono.zip(
            checkService("auth-service"),
            checkService("user-service"),
            checkService("blog-service"),
            checkService("content-service"),
            checkService("search-service"),
            checkService("recommendation-service"),
            checkService("ad-service")
        )
        .map(tuple -> {
            Map<String, ServiceHealth> services = new HashMap<>();
            services.put("auth-service", tuple.getT1());
            services.put("user-service", tuple.getT2());
            services.put("blog-service", tuple.getT3());
            services.put("content-service", tuple.getT4());
            services.put("search-service", tuple.getT5());
            services.put("recommendation-service", tuple.getT6());
            services.put("ad-service", tuple.getT7());

            response.setServices(services);

            // 如果有服务宕机，整体状态降级
            boolean anyDown = services.values().stream()
                .anyMatch(s -> "DOWN".equals(s.getStatus()));
            if (anyDown) {
                response.setStatus("DEGRADED");
            }

            return response;
        })
        .onErrorResume(e -> {
            log.error("Health check failed", e);
            response.setStatus("DOWN");
            response.setError(e.getMessage());
            return Mono.just(response);
        });
    }

    /**
     * 网关自身健康检查
     */
    @GetMapping("/gateway")
    public Mono<Map<String, Object>> gatewayHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "gateway");
        return Mono.just(health);
    }

    /**
     * 检查单个服务健康状态
     */
    @GetMapping("/service/{serviceName}")
    public Mono<ServiceHealth> serviceHealth(@PathVariable String serviceName) {
        if (!SERVICE_HEALTH_ENDPOINTS.containsKey(serviceName)) {
            return Mono.just(ServiceHealth.down(serviceName, "Unknown service"));
        }
        return checkService(serviceName);
    }

    /**
     * 调用服务健康检查端点
     */
    private Mono<ServiceHealth> checkService(String serviceName) {
        String healthUrl = SERVICE_HEALTH_ENDPOINTS.get(serviceName);

        return webClient.get()
            .uri(healthUrl)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(3))
            .map(response -> ServiceHealth.up(serviceName))
            .onErrorResume(e -> {
                log.warn("Service {} health check failed: {}", serviceName, e.getMessage());
                return Mono.just(ServiceHealth.down(serviceName, e.getMessage()));
            });
    }

    /**
     * 健康检查响应
     */
    @Data
    public static class HealthResponse {
        private String status;
        private long timestamp;
        private Map<String, ServiceHealth> services;
        private String error;
    }

    /**
     * 服务健康状态
     */
    @Data
    public static class ServiceHealth {
        private String service;
        private String status;
        private long timestamp;
        private String error;

        public static ServiceHealth up(String service) {
            ServiceHealth health = new ServiceHealth();
            health.setService(service);
            health.setStatus("UP");
            health.setTimestamp(System.currentTimeMillis());
            return health;
        }

        public static ServiceHealth down(String service, String error) {
            ServiceHealth health = new ServiceHealth();
            health.setService(service);
            health.setStatus("DOWN");
            health.setTimestamp(System.currentTimeMillis());
            health.setError(error);
            return health;
        }
    }
}
