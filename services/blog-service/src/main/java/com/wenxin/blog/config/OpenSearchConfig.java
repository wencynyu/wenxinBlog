package com.wenxin.blog.config;

import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenSearch 配置 — blog-service 直接写索引（不走 Kafka）。
 */
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.uris:http://localhost:9200}")
    private String uris;

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        org.apache.http.HttpHost host = org.apache.http.HttpHost.create(uris);
        return RestClient.builder(host).build();
    }

    @Bean
    public OpenSearchTransport openSearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }
}
