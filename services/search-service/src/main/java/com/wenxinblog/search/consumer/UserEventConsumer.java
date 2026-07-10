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

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserSearchRepository userRepo;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "wenxinblog.user.events", groupId = "search-service")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventType = node.get("eventType").asText();
            JsonNode data = node.get("data");

            log.info("Consumed user event: type={}", eventType);

            switch (eventType) {
                case "CREATE", "UPDATE", "PROFILE_UPDATE" -> handleUpsert(data);
                case "DELETE" -> handleDelete(data);
                default -> log.warn("Unknown user event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process user event: {}", e.getMessage(), e);
        }
    }

    private void handleUpsert(JsonNode data) {
        UserDocument doc = UserDocument.builder()
                .id(getText(data, "id"))
                .displayName(getText(data, "displayName"))
                .username(getText(data, "username"))
                .bio(getText(data, "bio"))
                .avatarUrl(getText(data, "avatarUrl"))
                .followerCount(getInt(data, "followerCount", 0))
                .postCount(getInt(data, "postCount", 0))
                .build();

        userRepo.indexUser(doc);
    }

    private void handleDelete(JsonNode data) {
        // User deletion not commonly needed in search index
        log.info("User delete event received, skipping search index removal");
    }

    private String getText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private int getInt(JsonNode node, String field, int defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : defaultValue;
    }
}
