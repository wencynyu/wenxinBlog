package com.wenxinblog.ad.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

/** 广告事件 Kafka 发布器（ad-events topic，value 为 JSON，fire-and-forget）。
 *  IMPRESSION（决策计费时）/ CLICK / CONVERSION 统一经此处发布，payload schema 只在这一个地方定义；
 *  消费方是 analytics-service（落 ClickHouse 出报表）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.ad-events:ad-events}")
    private String adEventsTopic;

    public void publish(String eventType, Long campaignId, Long creativeId, String userId,
                        String positionType, LocalDateTime createdAt) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", eventType,
                    "campaignId", campaignId,
                    "creativeId", creativeId,
                    "userId", userId != null ? userId : "",
                    "positionType", positionType != null ? positionType : "",
                    "timestamp", createdAt.toString()
            ));
            kafka.send(adEventsTopic, userId, payload).whenComplete((result, ex) -> {
                if (ex != null) log.warn("Failed to publish ad event to Kafka: {}", ex.getMessage());
            });
        } catch (Exception e) {
            log.warn("Failed to publish ad event to Kafka: {}", e.getMessage());
        }
    }
}
