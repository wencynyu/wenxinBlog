package com.wenxinblog.analytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ClickHouse 配置。用 HTTP API（:8123）而非 JDBC——
 * 避免 clickhouse-jdbc 驱动在 Java 25 / Spring Boot 4 上的兼容性问题。
 * ClickHouse HTTP API 原生支持 INSERT FORMAT JSONEachRow 和 SELECT FORMAT JSON。
 */
@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.url:http://localhost:8123}")
    private String url;

    @Bean
    public WebClient clickHouseClient() {
        return WebClient.builder().baseUrl(url).build();
    }
}
