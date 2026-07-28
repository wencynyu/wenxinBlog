package com.wenxinblog.experiment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ClickHouseConfig {
    @Value("${clickhouse.url:http://localhost:8123}")
    private String url;

    @Bean
    public WebClient clickHouseClient() {
        return WebClient.builder().baseUrl(url).build();
    }
}
