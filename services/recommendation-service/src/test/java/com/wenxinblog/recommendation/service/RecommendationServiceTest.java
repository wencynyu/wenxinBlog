package com.wenxinblog.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wenxinblog.recommendation.client.EmbeddingClient;
import com.wenxinblog.recommendation.dto.TrendingPost;
import com.wenxinblog.recommendation.entity.Post;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.repository.PostReadRepository;
import com.wenxinblog.recommendation.repository.UserInterestTagRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RecommendationService 单测。新版 trending 走 blog_db（PostReadRepository），
 * related/feed 走 embedding + Milvus（EmbeddingClient + MilvusService）。
 * 这里覆盖 DB/缓存/兴趣/反馈路径；related/feed 的真实向量路径由集成测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private MilvusService milvusService;
    @Mock private EmbeddingClient embeddingClient;
    @Mock private ReactiveStringRedisTemplate redisTemplate;
    @Mock private UserInterestTagRepository interestTagRepository;
    @Mock private PostReadRepository postReadRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOps;

    private RecommendationService recommendationService;
    private MeterRegistry meterRegistry;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        recommendationService = new RecommendationService(
                milvusService, embeddingClient, redisTemplate, interestTagRepository,
                postReadRepository, kafkaTemplate, meterRegistry);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private Post samplePost(String title) {
        Post p = new Post();
        p.setId(UUID.randomUUID());
        p.setAuthorId(UUID.randomUUID());
        p.setTitle(title);
        p.setViewCount(10);
        p.setLikeCount(5);
        p.setCommentCount(2);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    // ---- 热门 ----
    @Test
    void getTrendingPosts_cacheMiss_fetchesFromDbAndCaches() {
        int limit = 10;
        when(valueOps.get("recommend:trending:" + limit)).thenReturn(Mono.empty());
        when(postReadRepository.findTrending(limit)).thenReturn(Flux.just(samplePost("A"), samplePost("B")));
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(recommendationService.getTrendingPosts(limit))
                .expectNextMatches(items -> items.size() == 2 && items.get(0) instanceof TrendingPost)
                .verifyComplete();
        verify(postReadRepository).findTrending(limit);
        verify(valueOps).set(eq("recommend:trending:" + limit), anyString(), any(Duration.class));
    }

    @Test
    void getTrendingPosts_cacheHit_returnsCached() throws Exception {
        int limit = 10;
        TrendingPost cached = new TrendingPost("id-1", "Cached", 100, 50, 3, null, LocalDateTime.now());
        when(valueOps.get("recommend:trending:" + limit)).thenReturn(Mono.just(mapper.writeValueAsString(List.of(cached))));

        StepVerifier.create(recommendationService.getTrendingPosts(limit))
                .expectNextMatches(items -> items.size() == 1 && "id-1".equals(items.get(0).id()))
                .verifyComplete();
        verify(postReadRepository, never()).findTrending(anyInt());
    }

    @Test
    void getTrendingPosts_clampsLimit() {
        when(valueOps.get("recommend:trending:50")).thenReturn(Mono.empty());
        when(postReadRepository.findTrending(50)).thenReturn(Flux.empty());

        StepVerifier.create(recommendationService.getTrendingPosts(9999))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
        verify(postReadRepository).findTrending(50); // 上限 50
    }

    // ---- 兴趣标签 ----
    @Test
    void getInterestTags_returnsFromRepository() {
        String userId = "u1";
        UserInterestTag tag = UserInterestTag.builder().userId(userId).tag("java").weight(0.8).createdAt(LocalDateTime.now()).build();
        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.just(tag));

        StepVerifier.create(recommendationService.getInterestTags(userId))
                .expectNext(tag)
                .verifyComplete();
    }

    @Test
    void updateInterestTags_deletesAndSaves() {
        String userId = "u1";
        when(interestTagRepository.deleteByUserId(userId)).thenReturn(Mono.empty());
        when(interestTagRepository.save(any(UserInterestTag.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(recommendationService.updateInterestTags(userId, List.of("a", "b")))
                .expectNextMatches(saved -> saved.size() == 2)
                .verifyComplete();
        verify(interestTagRepository).deleteByUserId(userId);
        verify(interestTagRepository, times(2)).save(any(UserInterestTag.class));
    }

    // ---- 反馈 ----
    @Test
    void recordFeedback_invalidatesCache() {
        String userId = "u1";
        when(redisTemplate.keys("recommend:feed:" + userId + ":*")).thenReturn(Flux.just("k1", "k2"));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(recommendationService.recordFeedback(userId, "p1", "like")).verifyComplete();
        verify(redisTemplate, times(2)).delete(anyString());
    }

    // ---- user-to-user 协同过滤「可能认识的人」 ----
    @Test
    void getUserRecommendations_fromUserVector_searchesSimilarUsers() {
        String userId = "u1";
        float[] vec = {0.1f, 0.2f};
        when(valueOps.get("recommend:users:u1:5")).thenReturn(Mono.empty());
        when(milvusService.getUserVector(userId)).thenReturn(Mono.just(vec));
        when(milvusService.searchUsersByVector(vec, 5, userId)).thenReturn(Mono.just(List.of(
                new MilvusService.UserHit("u2", 0.9),
                new MilvusService.UserHit("u3", 0.8))));
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(recommendationService.getUserRecommendations(userId, 5))
                .expectNextMatches(ids -> ids.equals(List.of("u2", "u3")))
                .verifyComplete();
        verify(milvusService).searchUsersByVector(vec, 5, userId);
        verify(valueOps).set(eq("recommend:users:u1:5"), anyString(), any(Duration.class));
    }

    @Test
    void getUserRecommendations_missingVector_recomputesFromInterestTags() {
        String userId = "u1";
        // recompute 会做 L2 归一化，stub 向量必须是单位向量才能与下游调用参数对上
        float[] vec = {1.0f};
        when(valueOps.get("recommend:users:u1:5")).thenReturn(Mono.empty());
        when(milvusService.getUserVector(userId)).thenReturn(Mono.just(new float[0]));
        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.just(
                UserInterestTag.builder().userId(userId).tag("java").weight(1.0).createdAt(LocalDateTime.now()).build()));
        when(embeddingClient.embedBatch(List.of("java"))).thenReturn(Mono.just(List.of(vec)));
        when(milvusService.upsertUserVector(userId, vec)).thenReturn(Mono.empty());
        when(milvusService.searchUsersByVector(vec, 5, userId)).thenReturn(Mono.just(List.of(new MilvusService.UserHit("u9", 0.7))));
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(recommendationService.getUserRecommendations(userId, 5))
                .expectNextMatches(ids -> ids.equals(List.of("u9")))
                .verifyComplete();
        verify(interestTagRepository).findByUserId(userId);
    }

    @Test
    void getUserRecommendations_coldStart_returnsEmpty() {
        String userId = "u1";
        when(valueOps.get("recommend:users:u1:5")).thenReturn(Mono.empty());
        when(milvusService.getUserVector(userId)).thenReturn(Mono.just(new float[0]));
        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.empty());

        StepVerifier.create(recommendationService.getUserRecommendations(userId, 5))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
        verify(milvusService, never()).searchUsersByVector(any(), anyInt(), anyString());
    }

    @Test
    void getUserRecommendations_cacheHit_returnsCached() throws Exception {
        String cached = mapper.writeValueAsString(List.of("u7", "u8"));
        when(valueOps.get("recommend:users:u1:5")).thenReturn(Mono.just(cached));

        StepVerifier.create(recommendationService.getUserRecommendations("u1", 5))
                .expectNextMatches(ids -> ids.equals(List.of("u7", "u8")))
                .verifyComplete();
        verify(milvusService, never()).getUserVector(anyString());
    }

    // ---- 相关博文：非法 postId → 降级 trending ----
    @Test
    void getRelatedPosts_invalidUuid_fallsBackToTrending() {
        when(valueOps.get(startsWith("recommend:trending:"))).thenReturn(Mono.empty());
        when(postReadRepository.findTrending(anyInt())).thenReturn(Flux.just(samplePost("T")));

        StepVerifier.create(recommendationService.getRelatedPosts("not-a-uuid", 5))
                .expectNextMatches(items -> !items.isEmpty())
                .verifyComplete();
        verify(embeddingClient, never()).embed(anyString());
    }

    // ---- backfill ----
    @Test
    void backfill_embedsAndUpsertsAllPosts() {
        Post p1 = samplePost("A");
        Post p2 = samplePost("B");
        when(postReadRepository.findAllPublished(50)).thenReturn(Flux.just(p1, p2));
        when(embeddingClient.embed(anyString())).thenReturn(Mono.just(new float[1024]));
        when(milvusService.upsertPost(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());

        StepVerifier.create(recommendationService.backfill(50))
                .expectNext(2)
                .verifyComplete();
        verify(milvusService, times(2)).upsertPost(anyString(), anyString(), anyString(), any());
    }
}
