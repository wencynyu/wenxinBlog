package com.wenxinblog.experiment.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 消费 user-behavior-events（group=experiment-service），为带 experimentId 的事件累加指标计数。
 *
 * <p>事件须含 experimentId、variant、eventType：
 * <ul>
 *   <li>impression → metrics:{expId}:{variant}:impressions</li>
 *   <li>view_post → metrics:{expId}:{variant}:clicks</li>
 *   <li>like_post / comment_post → metrics:{expId}:{variant}:engagements</li>
 * </ul>
 * 计数采用 Redis INCR，fire-and-forget；与 recommendation-service 组互不干扰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsEventConsumer {

    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redis;

    @KafkaListener(topics = "user-behavior-events", groupId = "experiment-service")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            JsonNode expIdNode = node.path("experimentId");
            if (expIdNode.isMissingNode() || expIdNode.isNull()) {
                return;
            }
            String experimentId = expIdNode.asText();
            String variant = node.path("variant").asText();
            String eventType = node.path("eventType").asText();
            String key = metricKey(experimentId, variant, eventType);
            if (key == null) {
                return;
            }
            log.info("metrics counter: exp={}, variant={}, type={}", experimentId, variant, eventType);
            redis.opsForValue().increment(key).subscribe();
        } catch (Exception e) {
            log.error("Failed to process behavior event for metrics: {}", e.getMessage(), e);
        }
    }

    private String metricKey(String experimentId, String variant, String eventType) {
        return switch (eventType) {
            case "impression" -> "metrics:" + experimentId + ":" + variant + ":impressions";
            case "view_post" -> "metrics:" + experimentId + ":" + variant + ":clicks";
            case "like_post", "comment_post" -> "metrics:" + experimentId + ":" + variant + ":engagements";
            default -> null;
        };
    }
}
