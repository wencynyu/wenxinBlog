package com.wenxinblog.search.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wenxinblog.search.model.UserDocument;
import com.wenxinblog.search.repository.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserSearchRepository userRepo;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "wenxinblog.user.events", groupId = "search-service")
    public Mono<Void> consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventType = node.get("eventType").asText();
            JsonNode data = node.get("data");

            log.info("Consumed user event: type={}", eventType);

            return switch (eventType) {
                case "CREATE", "UPDATE", "PROFILE_UPDATE" -> handleUpsert(data);
                case "DELETE" -> {
                    log.info("User delete event received, skipping search index removal");
                    yield Mono.empty();
                }
                default -> {
                    log.warn("Unknown user event type: {}", eventType);
                    yield Mono.empty();
                }
            };
        } catch (Exception e) {
            // 反序列化/字段解析错误：事件本身不可处理，记录后跳过（offset 正常提交）。
            // ES 写入失败不在此处捕获——由返回的 Mono error 触发 DefaultErrorHandler 重试。
            log.error("Failed to process user event: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }

    private Mono<Void> handleUpsert(JsonNode data) {
        UserDocument doc = UserDocument.builder()
                .id(getText(data, "id"))
                .displayName(getText(data, "displayName"))
                .username(getText(data, "username"))
                .bio(getText(data, "bio"))
                .avatarUrl(getText(data, "avatarUrl"))
                .followerCount(getInt(data, "followerCount", 0))
                .postCount(getInt(data, "postCount", 0))
                .build();

        return userRepo.indexUser(doc);
    }

    private String getText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private int getInt(JsonNode node, String field, int defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : defaultValue;
    }
}
