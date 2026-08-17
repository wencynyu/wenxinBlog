package com.wenxinblog.recommendation.service;

import com.wenxinblog.recommendation.AbstractIntegrationTest;
import com.wenxinblog.recommendation.dto.FeedRecommendation;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * RecommendationService 集成测试：真实 PostgreSQL + Redis + Kafka，
 * Milvus/embedding 用 mock 桩数据（两者的真实链路各自有专用测试）。
 * 覆盖此前零覆盖的 feed 主链路（召回→混合重排→已看过滤→缓存）、EMA 用户向量、
 * A/B 权重分桶、相关博文/图文相关、行为反馈闭环（Kafka 真消费）。
 */
class RecommendationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RecommendationService service;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private final UUID authorId = UUID.randomUUID();

    // ---------- feed：匿名降级 / 个性化混合排序 / 缓存 ----------

    @Test
    void getFeedRecommendations_anonymous_fallsBackToTrending() throws Exception {
        UUID hot = UUID.randomUUID();
        insertPublishedPost(hot, authorId, "热门帖", 50, 100, 10, LocalDateTime.now());

        StepVerifier.create(service.getFeedRecommendations(null, 0, 10))
                .assertNext(items -> {
                    assertThat(items).hasSize(1);
                    assertThat(items.get(0).id()).isEqualTo(hot.toString());
                    assertThat(items.get(0).score()).isEqualTo(0.0); // trending 降级 score=0
                })
                .verifyComplete();
    }

    @Test
    void getFeedRecommendations_personalized_hybridRerankAndRedisCache() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID highSim = UUID.randomUUID(), highPop = UUID.randomUUID();
        insertPublishedPost(highSim, authorId, "高相似", 0, 0, 0, LocalDateTime.now());
        insertPublishedPost(highPop, authorId, "高热度", 100, 0, 0, LocalDateTime.now());
        stubUserVectorAndSearch(userId, List.of(
                new MilvusService.SearchHit(highSim.toString(), 0.9),
                new MilvusService.SearchHit(highPop.toString(), 0.1)));

        // 默认权重 [0.6,0.3,0.1]：0.6×0.9≈0.54 > 0.6×0.1+0.3×1.0=0.36 → 高相似在前
        StepVerifier.create(service.getFeedRecommendations(userId, 0, 10))
                .assertNext(items -> {
                    assertThat(items).extracting(FeedRecommendation::title).containsExactly("高相似", "高热度");
                    assertThat(items.get(0).score()).isEqualTo(0.9); // score 保持相似度，排序才混合
                })
                .verifyComplete();

        // 第二次命中 Redis 缓存：不再触发 Milvus 检索
        StepVerifier.create(service.getFeedRecommendations(userId, 0, 10))
                .assertNext(items -> assertThat(items).hasSize(2))
                .verifyComplete();
        verify(milvusService).searchByVector(any(), anyInt()); // 只调了一次
    }

    @Test
    void getFeedRecommendations_abWeights_overrideHybridOrdering() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID highSim = UUID.randomUUID(), highPop = UUID.randomUUID();
        insertPublishedPost(highSim, authorId, "高相似", 0, 0, 0, LocalDateTime.now());
        insertPublishedPost(highPop, authorId, "高热度", 100, 0, 0, LocalDateTime.now());
        stubUserVectorAndSearch(userId, List.of(
                new MilvusService.SearchHit(highSim.toString(), 0.9),
                new MilvusService.SearchHit(highPop.toString(), 0.1)));

        // A/B 分桶写入 Redis：权重 [0.1,0.8,0.1] → 热度主导，高热度在前
        redisTemplate.opsForValue().set("ab:" + userId + ":recommendation",
                "{\"experimentId\":\"exp1\",\"variant\":\"b\",\"params\":{\"hybridWeights\":[0.1,0.8,0.1]}}").block();

        StepVerifier.create(service.getFeedRecommendations(userId, 0, 10))
                .assertNext(items -> assertThat(items).extracting(FeedRecommendation::title)
                        .containsExactly("高热度", "高相似"))
                .verifyComplete();
    }

    @Test
    void getFeedRecommendations_filtersViewedPosts() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID viewed = UUID.randomUUID(), unseen = UUID.randomUUID();
        insertPublishedPost(viewed, authorId, "看过的", 0, 0, 0, LocalDateTime.now());
        insertPublishedPost(unseen, authorId, "没看的", 0, 0, 0, LocalDateTime.now());
        stubUserVectorAndSearch(userId, List.of(
                new MilvusService.SearchHit(viewed.toString(), 0.9),
                new MilvusService.SearchHit(unseen.toString(), 0.8)));
        redisTemplate.opsForSet().add("user:viewed:" + userId, viewed.toString()).block();

        StepVerifier.create(service.getFeedRecommendations(userId, 0, 10))
                .assertNext(items -> assertThat(items).extracting(FeedRecommendation::title)
                        .containsExactly("没看的"))
                .verifyComplete();
    }

    @Test
    void getFeedRecommendations_coldStart_noVectorNoTags_fallsBackToTrending() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID hot = UUID.randomUUID();
        insertPublishedPost(hot, authorId, "冷启动热门", 50, 100, 10, LocalDateTime.now());
        // 用户向量与兴趣标签都缺失（getUserVector 空、recompute 也空）→ trending
        org.mockito.Mockito.when(milvusService.getUserVector(userId))
                .thenReturn(reactor.core.publisher.Mono.just(new float[0]));

        StepVerifier.create(service.getFeedRecommendations(userId, 0, 10))
                .assertNext(items -> assertThat(items).extracting(FeedRecommendation::title)
                        .containsExactly("冷启动热门"))
                .verifyComplete();
    }

    // ---------- EMA 用户向量 ----------

    @Test
    void updateUserVectorWithPost_blendsWithEmaAndNormalizes() {
        String userId = UUID.randomUUID().toString();
        org.mockito.Mockito.when(milvusService.getPostVector(anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new float[]{1f, 0f}));
        org.mockito.Mockito.when(milvusService.getUserVector(anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new float[]{0f, 1f}));

        StepVerifier.create(service.updateUserVectorWithPost(userId, UUID.randomUUID().toString(), 0.5))
                .verifyComplete();

        var captor = org.mockito.ArgumentCaptor.forClass(float[].class);
        verify(milvusService).upsertUserVector(eq(userId), captor.capture());
        float[] saved = captor.getValue();
        // new = 0.5×[0,1] + 0.5×[1,0] = [0.5,0.5] → L2 归一化 ≈ [0.707,0.707]
        assertThat(saved).hasSize(2);
        assertThat(saved[0]).isCloseTo(0.7071f, org.assertj.core.data.Offset.offset(1e-3f));
        assertThat(saved[1]).isCloseTo(0.7071f, org.assertj.core.data.Offset.offset(1e-3f));
    }

    @Test
    void updateUserVectorWithPost_firstEverPost_startsFromPostVector() {
        String userId = UUID.randomUUID().toString();
        org.mockito.Mockito.when(milvusService.getPostVector(anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new float[]{3f, 4f}));
        org.mockito.Mockito.when(milvusService.getUserVector(anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new float[0]));

        StepVerifier.create(service.updateUserVectorWithPost(userId, UUID.randomUUID().toString(), 0.3))
                .verifyComplete();

        var captor = org.mockito.ArgumentCaptor.forClass(float[].class);
        verify(milvusService).upsertUserVector(eq(userId), captor.capture());
        // 无历史向量 → 直接用帖子向量并归一化：[3,4]/5 = [0.6,0.8]
        assertThat(captor.getValue()[0]).isCloseTo(0.6f, org.assertj.core.data.Offset.offset(1e-3f));
        assertThat(captor.getValue()[1]).isCloseTo(0.8f, org.assertj.core.data.Offset.offset(1e-3f));
    }

    @Test
    void updateUserVectorWithPost_postNotEmbedded_skipsUpsert() {
        org.mockito.Mockito.when(milvusService.getPostVector(anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new float[0]));
        org.mockito.Mockito.when(milvusService.getUserVector(anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new float[0]));

        StepVerifier.create(service.updateUserVectorWithPost("u1", UUID.randomUUID().toString(), 0.5))
                .verifyComplete();
        verify(milvusService, org.mockito.Mockito.never()).upsertUserVector(anyString(), any());
    }

    // ---------- 兴趣标签 → 用户向量聚合 ----------

    @Test
    void updateInterestTags_savesTags_andRecomputesVectorAsynchronously() throws Exception {
        String userId = UUID.randomUUID().toString();
        // 兴趣标签 java/spring → embedBatch 返回两个正交向量，权重 1.0/1.0
        org.mockito.Mockito.when(embeddingClient.embedBatch(anyList()))
                .thenReturn(reactor.core.publisher.Mono.just(List.of(new float[]{1f, 0f}, new float[]{0f, 1f})));

        StepVerifier.create(service.updateInterestTags(userId, List.of("java", "spring")))
                .assertNext(saved -> assertThat(saved).hasSize(2))
                .verifyComplete();

        // 异步触发 recompute：均值 [0.5,0.5] → 归一化 [0.707,0.707]
        var captor = org.mockito.ArgumentCaptor.forClass(float[].class);
        verify(milvusService, timeout(5000)).upsertUserVector(eq(userId), captor.capture());
        assertThat(captor.getValue()[0]).isCloseTo(0.7071f, org.assertj.core.data.Offset.offset(1e-3f));
        assertThat(captor.getValue()[1]).isCloseTo(0.7071f, org.assertj.core.data.Offset.offset(1e-3f));

        StepVerifier.create(service.getInterestTags(userId).collectList())
                .assertNext(tags -> assertThat(tags).hasSize(2))
                .verifyComplete();
    }

    // ---------- 相关博文 / 图文相关 ----------

    @Test
    void getRelatedPosts_embedsPostText_andExcludesSelf() throws Exception {
        UUID self = UUID.randomUUID(), other = UUID.randomUUID();
        insertPublishedPost(self, authorId, "源帖 标题", 0, 0, 0, LocalDateTime.now());
        insertPublishedPost(other, authorId, "相似帖", 1, 0, 0, LocalDateTime.now());
        org.mockito.Mockito.when(embeddingClient.embed(anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new float[]{0.1f}));
        org.mockito.Mockito.when(milvusService.searchByVector(any(float[].class), anyInt()))
                .thenReturn(reactor.core.publisher.Mono.just(List.of(
                        new MilvusService.SearchHit(self.toString(), 0.99),   // 自己要被过滤
                        new MilvusService.SearchHit(other.toString(), 0.85))));

        StepVerifier.create(service.getRelatedPosts(self.toString(), 5))
                .assertNext(items -> {
                    assertThat(items).hasSize(1);
                    assertThat(items.get(0).id()).isEqualTo(other.toString());
                    assertThat(items.get(0).score()).isEqualTo(0.85);
                })
                .verifyComplete();
    }

    @Test
    void getRelatedPosts_postNotFound_fallsBackToTrending() throws Exception {
        UUID hot = UUID.randomUUID();
        insertPublishedPost(hot, authorId, "兜底热门", 10, 10, 1, LocalDateTime.now());

        StepVerifier.create(service.getRelatedPosts(UUID.randomUUID().toString(), 5))
                .assertNext(items -> assertThat(items).extracting(FeedRecommendation::title)
                        .containsExactly("兜底热门"))
                .verifyComplete();
    }

    @Test
    void getRelatedByImage_embedsCoverImageVector() throws Exception {
        UUID withCover = UUID.randomUUID(), candidate = UUID.randomUUID();
        insertPostWithCover(withCover, "图文源帖", "https://img.example.com/a.jpg");
        insertPublishedPost(candidate, authorId, "图文命中", 0, 0, 0, LocalDateTime.now());
        org.mockito.Mockito.when(embeddingClient.embedImage("https://img.example.com/a.jpg"))
                .thenReturn(reactor.core.publisher.Mono.just(new float[]{0.2f}));
        org.mockito.Mockito.when(milvusService.searchByVector(any(float[].class), anyInt()))
                .thenReturn(reactor.core.publisher.Mono.just(List.of(
                        new MilvusService.SearchHit(candidate.toString(), 0.7))));

        StepVerifier.create(service.getRelatedByImage(withCover.toString(), 5))
                .assertNext(items -> assertThat(items).extracting(FeedRecommendation::title)
                        .containsExactly("图文命中"))
                .verifyComplete();
    }

    @Test
    void getRelatedByImage_noCoverImage_fallsBackToTrending() throws Exception {
        UUID noCover = UUID.randomUUID();
        insertPublishedPost(noCover, authorId, "无封面", 10, 10, 1, LocalDateTime.now());

        // 无封面 → 图文相关为空 → 降级 trending（该帖本身是已发布帖，出现在 trending 里，score=0）
        StepVerifier.create(service.getRelatedByImage(noCover.toString(), 5))
                .assertNext(items -> {
                    assertThat(items).hasSize(1);
                    assertThat(items.get(0).id()).isEqualTo(noCover.toString());
                    assertThat(items.get(0).score()).isEqualTo(0.0);
                })
                .verifyComplete();
    }

    // ---------- trending / backfill / 反馈闭环 ----------

    @Test
    void getTrendingPosts_cachesInRedis_andClampLimit() throws Exception {
        UUID hot = UUID.randomUUID();
        insertPublishedPost(hot, authorId, "热门", 10, 10, 1, LocalDateTime.now());

        StepVerifier.create(service.getTrendingPosts(500)) // clamp 到 50
                .assertNext(list -> assertThat(list).hasSize(1))
                .verifyComplete();
        // 已写缓存（key 带 clamp 后的 limit）
        assertThat(redisTemplate.opsForValue().get("recommend:trending:50").block()).isNotBlank();
    }

    @Test
    void backfill_upsertsPublishedPosts_andCountsSuccess() throws Exception {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        insertPublishedPost(p1, authorId, "嵌入成功", 0, 0, 0, LocalDateTime.now());
        insertPublishedPost(p2, authorId, "嵌入失败", 0, 0, 0, LocalDateTime.now());
        org.mockito.Mockito.when(embeddingClient.embed(anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new float[]{0.5f}));
        org.mockito.Mockito.when(milvusService.upsertPost(eq(p1.toString()), anyString(), anyString(), any()))
                .thenReturn(reactor.core.publisher.Mono.empty());
        org.mockito.Mockito.when(milvusService.upsertPost(eq(p2.toString()), anyString(), anyString(), any()))
                .thenReturn(reactor.core.publisher.Mono.error(new RuntimeException("milvus down")));

        StepVerifier.create(service.backfill(10))
                .assertNext(success -> assertThat(success).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void recordFeedback_sendsKafkaEvent_andInvalidatesFeedCache() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID postId = UUID.randomUUID();
        insertPublishedPost(postId, authorId, "被反馈的帖", 0, 0, 0, LocalDateTime.now());
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO tags (name, slug) VALUES ('java', 'java')");
            st.execute("INSERT INTO post_tags (post_id, tag_id) SELECT '" + postId + "', id FROM tags WHERE name = 'java'");
        }
        redisTemplate.opsForValue().set("recommend:feed:" + userId + ":0:10", "stale").block();

        StepVerifier.create(service.recordFeedback(userId, postId.toString(), "like"))
                .verifyComplete();

        // feed 缓存被失效
        Boolean remains = redisTemplate.hasKey("recommend:feed:" + userId + ":0:10").block();
        assertThat(remains == null || !remains).isTrue();

        // Kafka 上真实收到事件（action "like" → "like_post"，携带 DB 里的标签）
        String json = consumeOne("user-behavior-events", userId, Duration.ofSeconds(20));
        assertThat(json).contains("\"eventType\":\"like_post\"");
        assertThat(json).contains("\"postId\":\"" + postId + "\"");
        assertThat(json).contains("java");
    }

    @Test
    void recordViewedPost_tracksInRedisSet() {
        String userId = UUID.randomUUID().toString();
        String postId = UUID.randomUUID().toString();

        StepVerifier.create(service.recordViewedPost(userId, postId)).verifyComplete();

        Boolean member = redisTemplate.opsForSet().isMember("user:viewed:" + userId, postId).block();
        assertThat(member).isTrue();

        // null 参数直接空完成，不写 Redis
        StepVerifier.create(service.recordViewedPost(null, postId)).verifyComplete();
    }

    // ---------- 工具 ----------

    private void stubUserVectorAndSearch(String userId, List<MilvusService.SearchHit> hits) {
        org.mockito.Mockito.when(milvusService.getUserVector(userId))
                .thenReturn(reactor.core.publisher.Mono.just(new float[]{0.1f}));
        org.mockito.Mockito.when(milvusService.searchByVector(any(float[].class), anyInt()))
                .thenReturn(reactor.core.publisher.Mono.just(hits));
    }

    private void insertPostWithCover(UUID id, String title, String coverImage) throws Exception {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO posts (id, author_id, title, status, cover_image, published_at, created_at) VALUES ("
                    + "'" + id + "', '" + authorId + "', '" + title + "', 'published', '" + coverImage + "', now(), now())");
        }
    }

    /** 用独立 consumer group 从 topic 拉取指定 key 的第一条消息。 */
    private String consumeOne(String topic, String key, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofSeconds(1))) {
                    if (key.equals(r.key())) {
                        return r.value();
                    }
                }
            }
            throw new AssertionError("未在 " + timeout + " 内消费到 key=" + key + " 的消息");
        }
    }
}
