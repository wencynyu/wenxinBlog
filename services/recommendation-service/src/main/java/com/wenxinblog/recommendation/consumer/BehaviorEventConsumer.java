package com.wenxinblog.recommendation.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.repository.UserInterestTagRepository;
import com.wenxinblog.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 消费用户行为事件（user-behavior-events），更新用户兴趣标签，并刷新用户画像向量
 * （→ user_embeddings），使个性化推荐流随行为实时演进。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEventConsumer {

    private final ObjectMapper objectMapper;
    private final UserInterestTagRepository interestTagRepository;
    private final RecommendationService recommendationService;

    @KafkaListener(topics = "user-behavior-events", groupId = "recommendation-service-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventType = node.get("eventType").asText();
            String userId = node.get("userId").asText();
            log.info("Consumed behavior event: userId={}, type={}", userId, eventType);

            if (!node.has("tags")) {
                return;
            }
            double weight = getWeight(eventType);
            List<String> tags = new ArrayList<>();
            node.get("tags").forEach(t -> tags.add(t.asText()));
            if (tags.isEmpty()) {
                return;
            }

            // 取现有标签 → 只存新增的 → 刷新用户向量（行为→画像闭环）
            interestTagRepository.findByUserId(userId)
                    .map(UserInterestTag::getTag)
                    .collectList()
                    .flatMap(existing -> {
                        List<UserInterestTag> toSave = tags.stream()
                                .filter(t -> !existing.contains(t))
                                .map(t -> UserInterestTag.builder()
                                        .userId(userId).tag(t).weight(weight)
                                        .createdAt(LocalDateTime.now()).build())
                                .toList();
                        return Flux.fromIterable(toSave).flatMap(interestTagRepository::save).then();
                    })
                    .then(recommendationService.refreshUserVector(userId))
                    .doOnError(e -> log.warn("behavior→vector refresh failed for {}: {}", userId, e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
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
