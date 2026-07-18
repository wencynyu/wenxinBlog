package com.wenxin.blog.service;

import tools.jackson.databind.ObjectMapper;
import com.wenxin.blog.entity.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发博文生命周期事件到 Kafka topic wenxinblog.blog.events。
 *
 * <p>事件 schema（与 search-service/BlogEventConsumer 的契约一致）：
 * <pre>{"eventType":"CREATE|UPDATE|DELETE","data":{id,title,content,summary,authorId,status,tags[],publishedAt,viewCount,likeCount,commentCount}}</pre>
 *
 * <p>反应式安全：KafkaTemplate.send 返回 future，用 whenComplete 回调仅记日志，绝不把异常
 * 传播回博文保存流程（发事件失败不能影响主流程）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogEventPublisher {

    public static final String TOPIC = "wenxinblog.blog.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCreate(Post post, List<String> tags) {
        publish("CREATE", post, tags);
    }

    public void publishUpdate(Post post, List<String> tags) {
        publish("UPDATE", post, tags);
    }

    public void publishDelete(String postId) {
        send(postId, Map.of("eventType", "DELETE", "data", Map.of("id", postId)));
    }

    private void publish(String eventType, Post post, List<String> tags) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", post.getId().toString());
        data.put("title", post.getTitle());
        data.put("content", post.getContent());
        data.put("summary", post.getSummary());
        data.put("authorId", post.getAuthorId().toString());
        data.put("status", post.getStatus());
        data.put("tags", tags != null ? tags : List.of());
        if (post.getPublishedAt() != null) {
            data.put("publishedAt", post.getPublishedAt().toString());
        }
        data.put("viewCount", post.getViewCount());
        data.put("likeCount", post.getLikeCount());
        data.put("commentCount", post.getCommentCount());
        send(post.getId().toString(), Map.of("eventType", eventType, "data", data));
    }

    private void send(String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish blog event key={}: {}", key, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize blog event key={}: {}", key, e.getMessage());
        }
    }
}
