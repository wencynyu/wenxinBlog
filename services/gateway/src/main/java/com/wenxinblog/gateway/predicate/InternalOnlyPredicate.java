package com.wenxinblog.gateway.predicate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.function.Predicate;

/**
 * 内部路由谓词
 * 只允许来自内部网络的请求访问
 */
@Slf4j
@Component
public class InternalOnlyPredicate extends AbstractRoutePredicateFactory<InternalOnlyPredicate.Config> {

    // 内网IP段
    private static final String[] INTERNAL_PREFIXES = {
        "10.",           // 10.0.0.0/8
        "192.168.",      // 192.168.0.0/16
        "172.16.",       // 172.16.0.0/12 开始
        "172.17.",
        "172.18.",
        "172.19.",
        "172.20.",
        "172.21.",
        "172.22.",
        "172.23.",
        "172.24.",
        "172.25.",
        "172.26.",
        "172.27.",
        "172.28.",
        "172.29.",
        "172.30.",
        "172.31.",
        "127.",          // localhost
        "localhost",
        "0:0:0:0:0:0:0:1",  // IPv6 localhost
        "::1"
    };

    public InternalOnlyPredicate() {
        super(Config.class);
    }

    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return exchange -> {
            String clientIp = getClientIp(exchange);

            boolean isInternal = isInternalIp(clientIp);

            if (!isInternal) {
                log.warn("Blocked external access to internal route from IP: {}, path: {}",
                    clientIp, exchange.getRequest().getPath());
            }

            return isInternal;
        };
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
                : "";
        }
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 检查是否为内网IP
     */
    private boolean isInternalIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        for (String prefix : INTERNAL_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 配置类
     */
    public static class Config {
        // 可配置参数（预留）
    }
}
