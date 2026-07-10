package com.wenxinblog.recommendation.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.repository.UserInterestTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEventConsumer {

    private final ObjectMapper objectMapper;
    private final UserInterestTagRepository interestTagRepository;

    @KafkaListener(topics = "user-behavior-events", groupId = "recommendation-service-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventType = node.get("eventType").asText();
            String userId = node.get("userId").asText();

            log.info("Consumed behavior event: userId={}, type={}", userId, eventType);

            // Update interest tags based on behavior
            if (node.has("tags")) {
                for (JsonNode tagNode : node.get("tags")) {
                    String tag = tagNode.asText();
                    double weight = getWeight(eventType);

                    interestTagRepository.findByUserId(userId)
                            .map(UserInterestTag::getTag)
                            .collectList()
                            .filter(existingTags -> !existingTags.contains(tag))
                            .flatMap(ignored -> interestTagRepository.save(
                                    UserInterestTag.builder()
                                            .userId(userId).tag(tag).weight(weight)
                                            .createdAt(LocalDateTime.now()).build()))
                            .subscribe();
                }
            }
        } catch (Exception e) {
            log.error("Failed to process behavior event: {}", e.getMessage(), e);
        }
    }

    private double getWeight(String eventType) {
        return switch (eventType) {
            case "like_post" -> 0.5;
            case "comment_post" -> 0.7;
            case "share_post" -> 0.8;
            case "view_post" -> 0.1;
            default -> 0.2;
        };
    }
}
