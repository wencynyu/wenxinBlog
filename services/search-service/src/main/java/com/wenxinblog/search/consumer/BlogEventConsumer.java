package com.wenxinblog.search.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wenxinblog.search.model.BlogDocument;
import com.wenxinblog.search.repository.BlogSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlogEventConsumer {

    private final BlogSearchRepository blogRepo;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "wenxinblog.blog.events", groupId = "search-service")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventType = node.get("eventType").asText();
            JsonNode data = node.get("data");

            log.info("Consumed blog event: type={}", eventType);

            switch (eventType) {
                case "CREATE", "UPDATE" -> handleUpsert(data, eventType);
                case "DELETE" -> handleDelete(data);
                default -> log.warn("Unknown blog event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process blog event: {}", e.getMessage(), e);
        }
    }

    private void handleUpsert(JsonNode data, String eventType) {
        BlogDocument doc = BlogDocument.builder()
                .id(getText(data, "id"))
                .title(getText(data, "title"))
                .content(getText(data, "content"))
                .summary(getText(data, "summary"))
                .authorId(getText(data, "authorId"))
                .authorName(getText(data, "authorName"))
                .category(getText(data, "category"))
                .status(getText(data, "status"))
                .viewCount(getInt(data, "viewCount", 0))
                .likeCount(getInt(data, "likeCount", 0))
                .commentCount(getInt(data, "commentCount", 0))
                .build();

        if (data.has("tags") && data.get("tags").isArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : data.get("tags")) {
                tags.add(tag.asText());
            }
            doc.setTags(tags);
        }

        if (data.has("publishedAt") && !data.get("publishedAt").isNull()) {
            doc.setPublishedAt(data.get("publishedAt").asString());
        }

        // eventType 在顶层（node.eventType），不在 data 里；之前误读 data._eventType 导致
        // CREATE 永远走 updateBlog。CREATE 全量 index，UPDATE 局部 update。
        if ("CREATE".equals(eventType)) {
            blogRepo.indexBlog(doc);
        } else {
            blogRepo.updateBlog(doc);
        }
    }

    private void handleDelete(JsonNode data) {
        String blogId = data.get("id").asString();
        blogRepo.deleteBlog(blogId);
    }

    private String getText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private int getInt(JsonNode node, String field, int defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : defaultValue;
    }
}
