package com.wenxinblog.search.config;

import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 配置标记类。
 *
 * ES 连接由 spring-boot-starter-data-elasticsearch 自动配置
 * （application.yml 的 spring.elasticsearch.uris 指向本地 ES 9.3.8），
 * 此处不手动创建 client，避免与自动配置的 ReactiveElasticsearchClient 冲突。
 *
 * 保留本类作为 search-service ES 相关组件配置的容器：后续如需自定义
 * 连接行为（重试 / 连接池 / 索引模板 / Bean 覆盖等），在此添加 @Bean 或配置即可。
 */
@Configuration
public class ElasticsearchConfig {
}
