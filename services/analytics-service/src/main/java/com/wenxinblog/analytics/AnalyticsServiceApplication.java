package com.wenxinblog.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 行为事件分析服务：消费 Kafka topic user-behavior-events，批量写入 ClickHouse 做持久化事件日志。
 *
 * <p>@EnableKafka 让 @KafkaListener 生效；@EnableScheduling 让 BehaviorEventConsumer 的
 * 定时 flush（每 5 秒）生效，避免低流量时段 buffer 长期不刷盘。
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
