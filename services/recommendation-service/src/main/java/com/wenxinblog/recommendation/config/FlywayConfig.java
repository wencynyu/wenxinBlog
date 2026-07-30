package com.wenxinblog.recommendation.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式 Flyway 配置（recommendation 与 blog/content/ad 共享 blog_db，故用独立 history table 隔离）。
 * baselineVersion=1：dev 表已手动建 → baseline 跳过 V1；新环境 schema 空 → 直接跑 V1 建表。
 */
@Configuration
public class FlywayConfig {
    @Bean(initMethod = "migrate")
    public Flyway flyway(@Value("${spring.flyway.url}") String url,
                         @Value("${spring.flyway.user}") String user,
                         @Value("${spring.flyway.password}") String password) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .table("flyway_schema_history_recommendation")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }
}
