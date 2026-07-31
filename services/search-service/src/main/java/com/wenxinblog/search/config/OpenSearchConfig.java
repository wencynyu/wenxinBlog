package com.wenxinblog.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenSearch配置
 */
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.uris}")
    private String uris;

    @Value("${opensearch.username:}")
    private String username;

    @Value("${opensearch.password:}")
    private String password;

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        org.apache.http.HttpHost host = org.apache.http.HttpHost.create(uris);
        org.apache.http.impl.nio.client.HttpAsyncClientBuilder builder =
            org.apache.http.impl.nio.client.HttpAsyncClientBuilder.create();

        if (username != null && !username.isEmpty()) {
            org.apache.http.auth.UsernamePasswordCredentials credentials =
                new org.apache.http.auth.UsernamePasswordCredentials(username, password);
            builder.setDefaultCredentialsProvider(new org.apache.http.impl.client.BasicCredentialsProvider() {{
                setCredentials(org.apache.http.auth.AuthScope.ANY, credentials);
            }});
        }

        return RestClient.builder(host)
            .setHttpClientConfigCallback(httpClientBuilder -> builder)
            .build();
    }

    @Bean
    public OpenSearchTransport openSearchTransport(RestClient restClient) {
        // 注册 JavaTimeModule：BlogDocument 的 published_at/created_at 是 LocalDateTime，
        // opensearch-java 默认 JacksonJsonpMapper 不认 JSR-310，date 字段反序列化会失败。
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }
}
