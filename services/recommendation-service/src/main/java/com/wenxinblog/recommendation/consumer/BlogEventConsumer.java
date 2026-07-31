package com.wenxinblog.recommendation.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenxinblog.recommendation.client.EmbeddingClient;
import com.wenxinblog.recommendation.service.MilvusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 消费 wenxinblog.blog.events：博文创建/更新 → embedding → upsert Milvus；删除 → 移除。
 * 与 search-service（group=search-service）互不干扰（本服务 group=recommendation-service）。
 * 非发布态不进推荐库（已存在则移除）。consume 返回 Mono<Void>，Spring Kafka 等待完成后再提交 offset；
 * Milvus/embedding 失败向上抛错走默认重试（至少一次语义），不静默丢事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogEventConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MilvusService milvusService;
    private final EmbeddingClient embeddingClient;

    @KafkaListener(topics = "wenxinblog.blog.events", groupId = "recommendation-service")
    public Mono<Void> consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();
            JsonNode data = root.path("data");
            String postId = data.path("id").asText();
            log.info("consumed blog event: type={}, postId={}", eventType, postId);
            if (postId.isEmpty()) {
                return Mono.empty();
            }
            return switch (eventType) {
                case "CREATE", "UPDATE" -> handleUpsert(data, postId);
                case "DELETE" -> milvusService.removePost(postId)
                        .doOnError(e -> log.warn("Milvus removePost failed {}: {}", postId, e.getMessage()));
                default -> {
                    log.debug("ignore blog event type: {}", eventType);
                    yield Mono.empty();
                }
            };
        } catch (Exception e) {
            log.warn("Failed to handle blog event: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    private Mono<Void> handleUpsert(JsonNode data, String postId) {
        String status = data.path("status").asText();
        if (!"published".equalsIgnoreCase(status)) {
            return milvusService.removePost(postId);
        }
        String title = data.path("title").asText("");
        String summary = data.path("summary").asText("");
        String authorId = data.path("authorId").asText("");
        String text = (title + " " + summary).trim();
        if (text.isEmpty()) {
            JsonNode content = data.path("content");
            String c = content.isTextual() ? content.asText("") : "";
            text = c.length() > 500 ? c.substring(0, 500) : c;
        }
        final String embedText = text;
        if (embedText.isEmpty()) {
            return Mono.empty();
        }
        return embeddingClient.embed(embedText)
                .flatMap(vec -> {
                    if (vec.length == 0) {
                        log.warn("embedding empty for post {} (model service down?)", postId);
                        return Mono.<Void>empty();
                    }
                    log.info("embedding post {} (dim={}) → Milvus upsert", postId, vec.length);
                    return milvusService.upsertPost(postId, authorId, title, vec);
                })
                .doOnError(e -> log.warn("embed/upsert failed for {}: {}", postId, e.getMessage()));
    }
}
