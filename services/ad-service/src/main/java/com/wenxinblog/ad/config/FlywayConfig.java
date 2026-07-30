package com.wenxinblog.ad.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式 Flyway 配置。
 *
 * ad-service 运行时用 R2DBC，但 Flyway 基于 JDBC。Spring Boot 4.0.4 在 R2DBC + JDBC 共存时
 * FlywayAutoConfiguration 不自动触发，故显式声明 Flyway bean：用 spring.flyway.* 的 JDBC 连接
 * 自行 migrate（@Bean initMethod=migrate，启动时执行），不依赖 DataSource bean。
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
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }
}
