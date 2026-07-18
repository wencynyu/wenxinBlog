package com.wenxinblog.recommendation.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 连接配置 + collection/维度常量。维度 1024 与 embedding 服务的 dimensions 对齐。
 */
@Configuration
@EnableConfigurationProperties(MilvusConfig.MilvusProps.class)
public class MilvusConfig {

    public static final String BLOG_COLLECTION = "blog_embeddings";
    public static final String USER_COLLECTION = "user_embeddings";
    public static final int DIM = 1024;
    public static final String VECTOR_FIELD = "embedding";

    @Bean
    public MilvusServiceClient milvusServiceClient(MilvusProps props) {
        String host = (props.host() == null || props.host().isBlank()) ? "localhost" : props.host();
        int port = props.port() > 0 ? props.port() : 19530;
        ConnectParam connect = ConnectParam.newBuilder().withHost(host).withPort(port).build();
        return new MilvusServiceClient(connect);
    }

    @ConfigurationProperties(prefix = "milvus")
    public record MilvusProps(String host, int port) {}
}
