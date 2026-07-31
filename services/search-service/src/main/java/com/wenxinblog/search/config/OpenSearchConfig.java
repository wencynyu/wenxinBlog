package com.wenxinblog.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端配置（从 OpenSearch 迁移；类名保留避免改动扫描）。
 * ES 关闭了 security，故无需认证配置。
 */
@Configuration
public class OpenSearchConfig {

    @Value("${elasticsearch.uris}")
    private String uris;

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        return RestClient.builder(HttpHost.create(uris)).build();
    }

    @Bean
    public RestClientTransport transport(RestClient restClient) {
        // JavaTimeModule：BlogDocument 的 published_at/created_at 是 LocalDateTime，
        // elasticsearch-java 默认 JacksonJsonpMapper 不认 JSR-310，date 字段反序列化会失败。
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
