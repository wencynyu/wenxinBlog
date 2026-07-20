package com.wenxinblog.recommendation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenxinblog.recommendation.dto.AuthorDto;
import com.wenxinblog.recommendation.dto.FeedRecommendation;
import com.wenxinblog.recommendation.dto.Result;
import com.wenxinblog.recommendation.dto.TrendingPost;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationService recommendationService;

    private WebTestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RecommendationController controller = new RecommendationController(recommendationService);
        client = WebTestClient.bindToController(controller).build();
    }

    private FeedRecommendation feedRec(String id, String title, double score) {
        return new FeedRecommendation(id, title, "summary", null, "a-" + id,
                new AuthorDto("a-" + id, "user", "User", null),
                List.of(), 5, 1, score, LocalDateTime.now());
    }

    private TrendingPost trendingPost(String id, String title) {
        return new TrendingPost(id, title, 100, 5, 1,
                new AuthorDto("a-" + id, "user", "User", null), LocalDateTime.now());
    }

    @Test
    void getFeed_ShouldReturnFeedRecommendations() {
        when(recommendationService.getFeedRecommendations("user-123", 0, 10))
                .thenReturn(Mono.just(List.of(feedRec("post-1", "Spring Boot Guide", 0.9), feedRec("post-2", "Java Tips", 0.8))));

        client.get().uri("/api/v1/recommend/feed?userId=user-123&page=0&size=10").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(2, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void getFeed_WithDefaultPagination_ShouldUseDefaults() {
        when(recommendationService.getFeedRecommendations("user-456", 0, 10)).thenReturn(Mono.just(List.of()));
        client.get().uri("/api/v1/recommend/feed?userId=user-456").exchange().expectStatus().isOk();
    }

    @Test
    void getFeed_WithCustomPageAndSize_ShouldPassParameters() {
        when(recommendationService.getFeedRecommendations("user-789", 2, 20)).thenReturn(Mono.just(List.of()));
        client.get().uri("/api/v1/recommend/feed?userId=user-789&page=2&size=20").exchange().expectStatus().isOk();
    }

    @Test
    void getRelatedPosts_ShouldReturnRelatedPosts() {
        when(recommendationService.getRelatedPosts("post-123", 10))
                .thenReturn(Mono.just(List.of(feedRec("post-rel-1", "Related", 0.85), feedRec("post-rel-2", "Another", 0.75))));

        client.get().uri("/api/v1/recommend/related/post-123?topK=10").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> assertEquals(2, ((List<?>) result.getData()).size()));
    }

    @Test
    void getRelatedPosts_WithSmallTopK_ShouldLimitResults() {
        when(recommendationService.getRelatedPosts("post-limit", 3))
                .thenReturn(Mono.just(List.of(feedRec("r1", "R1", 0.9), feedRec("r2", "R2", 0.8), feedRec("r3", "R3", 0.7))));

        client.get().uri("/api/v1/recommend/related/post-limit?topK=3").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> assertEquals(3, ((List<?>) result.getData()).size()));
    }

    @Test
    void getRelatedPosts_WithDefaultTopK_ShouldUseDefault() {
        when(recommendationService.getRelatedPosts("post-456", 10)).thenReturn(Mono.just(List.of()));
        client.get().uri("/api/v1/recommend/related/post-456").exchange().expectStatus().isOk();
    }

    @Test
    void getTrending_ShouldReturnTrendingPosts() {
        when(recommendationService.getTrendingPosts(10))
                .thenReturn(Mono.just(List.of(trendingPost("trend-1", "Hot Topic"), trendingPost("trend-2", "Another Hot"))));

        client.get().uri("/api/v1/recommend/trending?limit=10").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> assertEquals(2, ((List<?>) result.getData()).size()));
    }

    @Test
    void getTrending_WithDefaultLimit_ShouldUseDefault() {
        when(recommendationService.getTrendingPosts(10)).thenReturn(Mono.just(List.of()));
        client.get().uri("/api/v1/recommend/trending").exchange().expectStatus().isOk();
    }

    @Test
    void getUserRecommendations_ShouldReturnUsers() {
        when(recommendationService.getUserRecommendations("user-123", 10))
                .thenReturn(Mono.just(List.of("user-001", "user-002", "user-003")));

        client.get().uri("/api/v1/recommend/users?userId=user-123&limit=10").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> assertEquals(3, ((List<?>) result.getData()).size()));
    }

    @Test
    void getInterests_ShouldReturnUserInterestTags() {
        String userId = "user-123";
        UserInterestTag tag1 = UserInterestTag.builder().userId(userId).tag("java").weight(0.8).createdAt(LocalDateTime.now()).build();
        UserInterestTag tag2 = UserInterestTag.builder().userId(userId).tag("spring").weight(0.6).createdAt(LocalDateTime.now()).build();
        when(recommendationService.getInterestTags(userId)).thenReturn(Flux.just(tag1, tag2));

        client.get().uri("/api/v1/recommend/interests?userId=user-123").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> assertEquals(2, ((List<?>) result.getData()).size()));
    }

    @Test
    void updateInterests_ShouldReturnUpdatedTags() throws Exception {
        String userId = "user-123";
        List<String> tags = List.of("java", "spring", "kafka");
        when(recommendationService.updateInterestTags(userId, tags))
                .thenReturn(Mono.just(tags.stream().map(t -> UserInterestTag.builder().userId(userId).tag(t).weight(1.0).createdAt(LocalDateTime.now()).build()).toList()));

        client.put().uri("/api/v1/recommend/interests?userId=user-123")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(objectMapper.writeValueAsString(tags)).exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> assertEquals(3, ((List<?>) result.getData()).size()));
    }

    @Test
    void feedback_ShouldRecordFeedbackAndReturnSuccess() {
        when(recommendationService.recordFeedback("user-123", "post-456", "like")).thenReturn(Mono.empty());

        client.post().uri("/api/v1/recommend/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"user-123\",\"postId\":\"post-456\",\"action\":\"like\"}").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals("ok", result.getData());
                });
    }

    @Test
    void backfill_ShouldReturnCount() {
        when(recommendationService.backfill(1000)).thenReturn(Mono.just(14));

        client.post().uri("/api/v1/recommend/admin/backfill").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> assertEquals(14, result.getData()));
    }
}
