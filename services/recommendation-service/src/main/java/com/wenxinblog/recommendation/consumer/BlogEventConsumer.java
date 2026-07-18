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
import reactor.core.scheduler.Schedulers;

/**
 * 消费 wenxinblog.blog.events：博文创建/更新 → embedding → upsert Milvus；删除 → 移除。
 * 与 search-service（group=search-service）互不干扰（本服务 group=recommendation-service）。
 * 非发布态不进推荐库（已存在则移除）。所有异步链 fire-and-forget，错误不抛回 Kafka。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogEventConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MilvusService milvusService;
    private final EmbeddingClient embeddingClient;

    @KafkaListener(topics = "wenxinblog.blog.events", groupId = "recommendation-service")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();
            JsonNode data = root.path("data");
            String postId = data.path("id").asText();
            if (postId.isEmpty()) {
                return;
            }
            switch (eventType) {
                case "CREATE", "UPDATE" -> handleUpsert(data, postId);
                case "DELETE" -> milvusService.removePost(postId)
                        .doOnError(e -> log.warn("Milvus removePost failed {}: {}", postId, e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
                default -> log.debug("ignore blog event type: {}", eventType);
            }
        } catch (Exception e) {
            log.warn("Failed to handle blog event: {}", e.getMessage());
        }
    }

    private void handleUpsert(JsonNode data, String postId) {
        String status = data.path("status").asText();
        if (!"published".equalsIgnoreCase(status)) {
            milvusService.removePost(postId)
                    .onErrorResume(e -> Mono.empty())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
            return;
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
            return;
        }
        embeddingClient.embed(embedText)
                .flatMap(vec -> vec.length == 0
                        ? Mono.<Void>empty()
                        : milvusService.upsertPost(postId, authorId, title, vec))
                .doOnError(e -> log.warn("embed/upsert failed for {}: {}", postId, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
