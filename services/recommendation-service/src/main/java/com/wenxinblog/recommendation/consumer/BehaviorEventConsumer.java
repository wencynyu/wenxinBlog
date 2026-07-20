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
 * 消费用户行为事件（user-behavior-events）：
 *  - 把行为涉及的标签存入 user_interest_tags（/interests 展示用）；
 *  - 用交互过的帖子向量 EMA 更新 user_embeddings（item-CF lite，主画像信号）。
 * 使个性化推荐流随用户行为实时演进。
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
            String postId = node.has("postId") && !node.get("postId").isNull() ? node.get("postId").asText() : null;
            double weight = getWeight(eventType);
            log.info("Consumed behavior event: userId={}, postId={}, type={}", userId, postId, eventType);

            Mono<Void> tagPart = node.has("tags") ? saveNewTags(userId, node.get("tags"), weight) : Mono.empty();
            // 向量更新：有 postId → EMA 帖子向量（item-CF）；无 → 回退标签聚合
            Mono<Void> vectorPart = postId != null
                    ? recommendationService.updateUserVectorWithPost(userId, postId, weight)
                    : recommendationService.refreshUserVector(userId).then();

            tagPart.then(vectorPart)
                    .doOnError(e -> log.warn("behavior→vector refresh failed for {}: {}", userId, e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
        } catch (Exception e) {
            log.error("Failed to process behavior event: {}", e.getMessage(), e);
        }
    }

    /** 只存该用户尚不存在的标签（避免重复）。 */
    private Mono<Void> saveNewTags(String userId, JsonNode tagsNode, double weight) {
        List<String> tags = new ArrayList<>();
        tagsNode.forEach(t -> tags.add(t.asText()));
        if (tags.isEmpty()) {
            return Mono.empty();
        }
        return interestTagRepository.findByUserId(userId)
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
                });
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
