package com.wenxin.blog.service;

import com.wenxin.blog.entity.Post;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlogEventPublisherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private BlogEventPublisher publisher;

    private Post samplePost() {
        Post p = new Post();
        p.setId(UUID.randomUUID());
        p.setAuthorId(UUID.randomUUID());
        p.setTitle("测试标题");
        p.setContent("内容");
        p.setSummary("摘要");
        p.setStatus("published");
        p.setPublishedAt(LocalDateTime.now());
        p.setViewCount(0);
        p.setLikeCount(1);
        p.setCommentCount(2);
        return p;
    }

    @Test
    void publishCreate_sendsKafkaEvent() throws Exception {
        Post post = samplePost();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventType\":\"CREATE\"}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishCreate(post, List.of("Go", "微服务"));

        verify(kafkaTemplate).send(eq("wenxinblog.blog.events"), eq(post.getId().toString()), anyString());
    }

    @Test
    void publishDelete_sendsKafkaEvent() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventType\":\"DELETE\"}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishDelete("post-123");

        verify(kafkaTemplate).send(eq("wenxinblog.blog.events"), eq("post-123"), anyString());
    }

    @Test
    void publishCreate_serializeError_doesNotThrow() throws Exception {
        Post post = samplePost();
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialize error"));

        publisher.publishCreate(post, List.of("tag")); // should not throw
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void sortColumnOf_whitelistAndInjectionPrevention() {
        // 已知值映射
        assert "like_count".equals(PostService.sortColumnOf("likeCount"));
        assert "comment_count".equals(PostService.sortColumnOf("commentCount"));
        assert "created_at".equals(PostService.sortColumnOf("createdAt"));
        // 注入企图 → 安全回落
        assert "published_at".equals(PostService.sortColumnOf("1=1; DROP TABLE posts"));
        assert "published_at".equals(PostService.sortColumnOf(null));
    }
}
