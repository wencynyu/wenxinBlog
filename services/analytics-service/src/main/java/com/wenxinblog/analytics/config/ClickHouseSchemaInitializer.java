package com.wenxinblog.analytics.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ClickHouse 表结构初始化：服务启动时 CREATE TABLE IF NOT EXISTS（幂等）。
 * 背景：behavior_events 是 2026-07 手工建的表，仓库里没有 DDL 机制——新表从 ad_events
 * 起改为代码建表，保证新环境（本地/容器）零手工步骤。建表失败只告警不阻断启动：
 * 消费端 flush 失败会保持不 ack、由 Kafka 重投递，修复表后重启即自愈。
 */
@Slf4j
@Component
public class ClickHouseSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate clickHouse;

    public ClickHouseSchemaInitializer(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouse) {
        this.clickHouse = clickHouse;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            clickHouse.execute("""
                    CREATE TABLE IF NOT EXISTS ad_events (
                        event_type     String,
                        campaign_id    UInt64,
                        creative_id    UInt64,
                        user_id        String,
                        position_type  String DEFAULT '',
                        timestamp      DateTime DEFAULT now()
                    ) ENGINE = MergeTree
                    PARTITION BY toDate(timestamp)
                    ORDER BY (campaign_id, event_type, timestamp)
                    TTL toDateTime(toDate(timestamp) + INTERVAL 90 DAY)
                    SETTINGS index_granularity = 8192
                    """);
            log.info("ClickHouse ad_events table ready");
        } catch (Exception e) {
            log.error("Failed to create ad_events table in ClickHouse: {}", e.getMessage());
        }
    }
}
