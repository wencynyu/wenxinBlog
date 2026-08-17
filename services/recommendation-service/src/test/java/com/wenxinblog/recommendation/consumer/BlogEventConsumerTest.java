package com.wenxinblog.recommendation.consumer;

import com.wenxinblog.recommendation.client.EmbeddingClient;
import com.wenxinblog.recommendation.service.MilvusService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BlogEventConsumer 单测（按仓库惯例直接调 @KafkaListener 方法传 ConsumerRecord）：
 * CREATE/UPDATE published → embed+upsert；非发布/DELETE → remove；正文截 500 字等分支。
 */
@ExtendWith(MockitoExtension.class)
class BlogEventConsumerTest {

    @Mock
    private MilvusService milvusService;

    @Mock
    private EmbeddingClient embeddingClient;

    @InjectMocks
    private BlogEventConsumer consumer;

    @BeforeEach
    void setUp() {
        // lenient：非法 JSON 等分支用不到这些桩
        org.mockito.Mockito.lenient().when(milvusService.upsertPost(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient().when(milvusService.removePost(anyString())).thenReturn(Mono.empty());
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("wenxinblog.blog.events", 0, 0, "key", json);
    }

    @Test
    void consume_createPublished_embedsTitleAndSummary_andUpsertsMilvus() {
        when(embeddingClient.embed(anyString())).thenReturn(Mono.just(new float[]{0.1f, 0.2f}));
        String json = """
                {"eventType":"CREATE","data":{"id":"11111111-1111-1111-1111-111111111111",
                 "title":"标题","summary":"摘要","content":"正文内容","authorId":"a1","status":"published"}}""";

        StepVerifier.create(consumer.consume(record(json))).verifyComplete();

        org.mockito.Mockito.verify(embeddingClient).embed(eq("标题 摘要"));
        verify(milvusService).upsertPost(eq("11111111-1111-1111-1111-111111111111"), eq("a1"), eq("标题"),
                any(float[].class));
    }

    @Test
    void consume_noTitleNoSummary_truncatesContentTo500Chars() {
        when(embeddingClient.embed(anyString())).thenReturn(Mono.just(new float[]{0.1f}));
        String longContent = "字".repeat(600);
        String json = """
                {"eventType":"UPDATE","data":{"id":"22222222-2222-2222-2222-222222222222",
                 "title":"","summary":"","content":"%s","status":"published"}}""".formatted(longContent);

        StepVerifier.create(consumer.consume(record(json))).verifyComplete();

        // 标题/摘要都为空 → 用正文前 500 字嵌入
        org.mockito.Mockito.verify(embeddingClient).embed(eq("字".repeat(500)));
        verify(milvusService).upsertPost(eq("22222222-2222-2222-2222-222222222222"), eq(""), eq(""),
                any(float[].class));
    }

    @Test
    void consume_draftStatus_removesFromMilvus() {
        String json = """
                {"eventType":"CREATE","data":{"id":"33333333-3333-3333-3333-333333333333",
                 "title":"草稿","status":"draft"}}""";

        StepVerifier.create(consumer.consume(record(json))).verifyComplete();

        verify(milvusService).removePost("33333333-3333-3333-3333-333333333333");
        verify(milvusService, never()).upsertPost(anyString(), anyString(), anyString(), any());
    }

    @Test
    void consume_deleteEvent_removesFromMilvus() {
        String json = """
                {"eventType":"DELETE","data":{"id":"44444444-4444-4444-4444-444444444444"}}""";

        StepVerifier.create(consumer.consume(record(json))).verifyComplete();

        verify(milvusService).removePost("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void consume_emptyEmbedding_skipsUpsert() {
        when(embeddingClient.embed(anyString())).thenReturn(Mono.just(new float[0]));
        String json = """
                {"eventType":"CREATE","data":{"id":"55555555-5555-5555-5555-555555555555",
                 "title":"嵌入失败","status":"published"}}""";

        StepVerifier.create(consumer.consume(record(json))).verifyComplete();

        verify(milvusService, never()).upsertPost(anyString(), anyString(), anyString(), any());
    }

    @Test
    void consume_missingPostId_orUnknownType_isIgnored() {
        StepVerifier.create(consumer.consume(record("{\"eventType\":\"CREATE\",\"data\":{\"id\":\"\"}}")))
                .verifyComplete();
        StepVerifier.create(consumer.consume(record(
                "{\"eventType\":\"WEIRD\",\"data\":{\"id\":\"66666666-6666-6666-6666-666666666666\"}}")))
                .verifyComplete();

        verify(milvusService, never()).upsertPost(anyString(), anyString(), anyString(), any());
        verify(milvusService, never()).removePost(anyString());
    }

    @Test
    void consume_invalidJson_propagatesError() {
        StepVerifier.create(consumer.consume(record("not-json")))
                .expectError()
                .verify();
    }
}
