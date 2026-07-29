package com.wenxinblog.experiment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.url:jdbc:ch://localhost:8123/default}")
    private String url;

    @Bean
    public DataSource clickHouseDataSource() {
        return DataSourceBuilder.create()
                .url(url)
                .driverClassName("com.clickhouse.jdbc.ClickHouseDriver")
                .build();
    }

    @Bean
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}
