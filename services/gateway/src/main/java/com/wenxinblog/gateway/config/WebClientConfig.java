package com.wenxinblog.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * WebClient 配置
 * 配置用于调用其他微服务的WebClient
 */
@Configuration
public class WebClientConfig {

    private static final int MAX_MEMORY_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int CONNECTION_TIMEOUT = 5000; // 5秒
    private static final int RESPONSE_TIMEOUT = 30000; // 30秒

    @Bean
    public WebClient.Builder webClientBuilder() {
        // 配置连接池
        ConnectionProvider connectionProvider = ConnectionProvider.builder("gateway-pool")
            .maxConnections(500)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofMinutes(5))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(120))
            .build();

        // 配置HttpClient
        HttpClient httpClient = HttpClient.create(connectionProvider)
            .responseTimeout(Duration.ofMillis(RESPONSE_TIMEOUT))
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECTION_TIMEOUT);

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(MAX_MEMORY_SIZE)
            );
    }

    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }
}
