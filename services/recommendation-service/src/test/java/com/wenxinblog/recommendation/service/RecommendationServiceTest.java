package com.wenxinblog.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenxinblog.recommendation.dto.FeedRecommendation;
import com.wenxinblog.recommendation.dto.TrendingPost;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.repository.UserInterestTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private MilvusService milvusService;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private UserInterestTagRepository interestTagRepository;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    @InjectMocks
    private RecommendationService recommendationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void getFeedRecommendations_WithCacheHit_ShouldReturnCachedData() throws Exception {
        // Given
        String userId = "user-123";
        int page = 0;
        int size = 10;
        String cacheKey = "recommend:feed:" + userId + ":" + page;

        List<FeedRecommendation> cachedItems = List.of(
                new FeedRecommendation("post-1", "Cached Post", "Summary", "Author", 0.9, "Cached")
        );
        String cachedJson = objectMapper.writeValueAsString(cachedItems);

        when(valueOps.get(cacheKey)).thenReturn(Mono.just(cachedJson));
        when(milvusService.searchByUserInterest(userId, size)).thenReturn(Flux.empty());

        // When
        Mono<List<FeedRecommendation>> result = recommendationService.getFeedRecommendations(userId, page, size);

        // Then - just verify we get the cached data back
        StepVerifier.create(result)
                .expectNextMatches(items -> items.size() == 1 && items.get(0).postId().equals("post-1"))
                .verifyComplete();
    }

    @Test
    void getFeedRecommendations_WithCacheMiss_ShouldFetchFromMilvus() {
        // Given
        String userId = "user-456";
        int page = 0;
        int size = 10;
        String cacheKey = "recommend:feed:" + userId + ":" + page;

        when(valueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(milvusService.searchByUserInterest(userId, size)).thenReturn(Flux.empty());

        // When
        Mono<List<FeedRecommendation>> result = recommendationService.getFeedRecommendations(userId, page, size);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(items -> items.size() == 5) // Mock feed has 5 items
                .verifyComplete();

        verify(milvusService).searchByUserInterest(userId, size);
    }

    @Test
    void getFeedRecommendations_WithCacheMiss_ShouldCacheResults() {
        // Given
        String userId = "user-789";
        int page = 0;
        int size = 10;
        String cacheKey = "recommend:feed:" + userId + ":" + page;

        when(valueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(milvusService.searchByUserInterest(userId, size)).thenReturn(Flux.empty());
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        // When
        Mono<List<FeedRecommendation>> result = recommendationService.getFeedRecommendations(userId, page, size);

        // Then
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getRelatedPosts_ShouldReturnMilvusResults() {
        // Given
        String postId = "post-123";
        int topK = 5;

        FeedRecommendation related = new FeedRecommendation("post-rel-1", "Related", "Summary", "Author", 0.85, "Similar");
        when(milvusService.searchSimilarPosts(postId, topK)).thenReturn(Flux.just(related));

        // When
        Mono<List<FeedRecommendation>> result = recommendationService.getRelatedPosts(postId, topK);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(items -> items.size() == 1 && items.get(0).postId().equals("post-rel-1"))
                .verifyComplete();
    }

    @Test
    void getRelatedPosts_WithEmptyMilvusResults_ShouldReturnMockData() {
        // Given
        String postId = "post-456";
        int topK = 10;

        when(milvusService.searchSimilarPosts(postId, topK)).thenReturn(Flux.empty());

        // When
        Mono<List<FeedRecommendation>> result = recommendationService.getRelatedPosts(postId, topK);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(items -> items.size() == 3 && items.get(0).postId().equals("post-rel-1"))
                .verifyComplete();
    }

    @Test
    void getTrendingPosts_WithCacheHit_ShouldReturnCachedData() throws Exception {
        // Given
        int limit = 10;
        String cacheKey = "recommend:trending:" + limit;

        List<TrendingPost> cachedItems = List.of(
                new TrendingPost("trend-1", "Trending Post", 1000L, 100L, 0.9, "up")
        );
        String cachedJson = objectMapper.writeValueAsString(cachedItems);

        when(valueOps.get(cacheKey)).thenReturn(Mono.just(cachedJson));

        // When
        Mono<List<TrendingPost>> result = recommendationService.getTrendingPosts(limit);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(items -> items.size() == 1 && items.get(0).postId().equals("trend-1"))
                .verifyComplete();
    }

    @Test
    void getTrendingPosts_WithCacheMiss_ShouldReturnMockData() {
        // Given
        int limit = 5;
        String cacheKey = "recommend:trending:" + limit;

        when(valueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        // When
        Mono<List<TrendingPost>> result = recommendationService.getTrendingPosts(limit);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(items -> items.size() == 5)
                .verifyComplete();

        verify(valueOps).set(eq(cacheKey), anyString(), any(Duration.class));
    }

    @Test
    void getUserRecommendations_ShouldReturnMockUsers() {
        // Given
        String userId = "user-123";
        int limit = 2;

        // When
        Mono<List<String>> result = recommendationService.getUserRecommendations(userId, limit);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(users -> users.size() == 2)
                .verifyComplete();
    }

    @Test
    void getInterestTags_ShouldReturnTagsFromRepository() {
        // Given
        String userId = "user-123";
        UserInterestTag tag1 = UserInterestTag.builder()
                .userId(userId).tag("java").weight(0.8).createdAt(LocalDateTime.now()).build();
        UserInterestTag tag2 = UserInterestTag.builder()
                .userId(userId).tag("spring").weight(0.6).createdAt(LocalDateTime.now()).build();

        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.just(tag1, tag2));

        // When
        Flux<UserInterestTag> result = recommendationService.getInterestTags(userId);

        // Then
        StepVerifier.create(result)
                .expectNext(tag1)
                .expectNext(tag2)
                .verifyComplete();
    }

    @Test
    void getInterestTags_WithEmptyResult_ShouldReturnEmpty() {
        // Given
        String userId = "user-456";
        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.empty());

        // When
        Flux<UserInterestTag> result = recommendationService.getInterestTags(userId);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void updateInterestTags_ShouldDeleteExistingAndSaveNew() {
        // Given
        String userId = "user-123";
        List<String> tags = List.of("java", "spring", "kafka");

        when(interestTagRepository.deleteByUserId(userId)).thenReturn(Mono.empty());
        when(interestTagRepository.save(any(UserInterestTag.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When
        Mono<List<UserInterestTag>> result = recommendationService.updateInterestTags(userId, tags);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(savedTags -> savedTags.size() == 3)
                .verifyComplete();

        verify(interestTagRepository).deleteByUserId(userId);
        verify(interestTagRepository, times(3)).save(any(UserInterestTag.class));
    }

    @Test
    void updateInterestTags_WithEmptyList_ShouldDeleteOnly() {
        // Given
        String userId = "user-456";
        List<String> tags = List.of();

        when(interestTagRepository.deleteByUserId(userId)).thenReturn(Mono.empty());

        // When
        Mono<List<UserInterestTag>> result = recommendationService.updateInterestTags(userId, tags);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(savedTags -> savedTags.isEmpty())
                .verifyComplete();

        verify(interestTagRepository).deleteByUserId(userId);
        verify(interestTagRepository, never()).save(any(UserInterestTag.class));
    }

    @Test
    void recordFeedback_ShouldInvalidateCache() {
        // Given
        String userId = "user-123";
        String postId = "post-456";
        String action = "like";
        String pattern = "recommend:feed:" + userId + ":*";

        when(redisTemplate.keys(pattern)).thenReturn(Flux.just("key1", "key2"));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        // When
        Mono<Void> result = recommendationService.recordFeedback(userId, postId, action);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(redisTemplate).keys(pattern);
        verify(redisTemplate, times(2)).delete(anyString());
    }

    @Test
    void recordFeedback_WithNoCacheKeys_ShouldComplete() {
        // Given
        String userId = "user-789";
        String postId = "post-999";
        String action = "dislike";
        String pattern = "recommend:feed:" + userId + ":*";

        when(redisTemplate.keys(pattern)).thenReturn(Flux.empty());

        // When
        Mono<Void> result = recommendationService.recordFeedback(userId, postId, action);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(redisTemplate).keys(pattern);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void getFeedRecommendations_WithInvalidCachedJson_ShouldFetchNewData() {
        // Given
        String userId = "user-999";
        int page = 0;
        int size = 10;
        String cacheKey = "recommend:feed:" + userId + ":" + page;

        when(valueOps.get(cacheKey)).thenReturn(Mono.just("invalid json"));
        when(milvusService.searchByUserInterest(userId, size)).thenReturn(Flux.empty());
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        // When
        Mono<List<FeedRecommendation>> result = recommendationService.getFeedRecommendations(userId, page, size);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(items -> items.size() == 5)
                .verifyComplete();
    }

    @Test
    void getTrendingPosts_WithInvalidCachedJson_ShouldReturnMockData() {
        // Given
        int limit = 10;
        String cacheKey = "recommend:trending:" + limit;

        when(valueOps.get(cacheKey)).thenReturn(Mono.just("invalid json"));
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        // When
        Mono<List<TrendingPost>> result = recommendationService.getTrendingPosts(limit);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(items -> items.size() == 6)
                .verifyComplete();
    }
}
