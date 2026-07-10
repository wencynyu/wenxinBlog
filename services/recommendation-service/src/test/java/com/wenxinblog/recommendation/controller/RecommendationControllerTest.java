package com.wenxinblog.recommendation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
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

    @Test
    void getFeed_ShouldReturnFeedRecommendations() {
        // Given
        String userId = "user-123";
        List<FeedRecommendation> feed = List.of(
                new FeedRecommendation("post-1", "Spring Boot Guide", "Summary", "Author", 0.9, "Interest-based"),
                new FeedRecommendation("post-2", "Java Tips", "Summary", "Author2", 0.8, "Trending")
        );
        when(recommendationService.getFeedRecommendations(userId, 0, 10))
                .thenReturn(Mono.just(feed));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/feed?userId=user-123&page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals("ok", result.getMessage());
                    assertEquals(2, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void getFeed_WithDefaultPagination_ShouldUseDefaults() {
        // Given
        when(recommendationService.getFeedRecommendations("user-456", 0, 10))
                .thenReturn(Mono.just(List.of()));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/feed?userId=user-456")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getFeed_WithCustomPageAndSize_ShouldPassParameters() {
        // Given
        when(recommendationService.getFeedRecommendations("user-789", 2, 20))
                .thenReturn(Mono.just(List.of()));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/feed?userId=user-789&page=2&size=20")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getRelatedPosts_ShouldReturnRelatedPosts() {
        // Given
        String postId = "post-123";
        List<FeedRecommendation> related = List.of(
                new FeedRecommendation("post-rel-1", "Related Article", "Summary", "Author", 0.85, "Similar content"),
                new FeedRecommendation("post-rel-2", "Another Related", "Summary", "Author2", 0.75, "Same tags")
        );
        when(recommendationService.getRelatedPosts(postId, 10))
                .thenReturn(Mono.just(related));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/related/post-123?topK=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(2, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void getRelatedPosts_WithDefaultTopK_ShouldUseDefault() {
        // Given
        when(recommendationService.getRelatedPosts("post-456", 10))
                .thenReturn(Mono.just(List.of()));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/related/post-456")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getTrending_ShouldReturnTrendingPosts() {
        // Given
        List<TrendingPost> trending = List.of(
                new TrendingPost("trend-1", "Hot Topic", 5000L, 200L, 0.95, "up"),
                new TrendingPost("trend-2", "Another Hot", 4000L, 150L, 0.90, "up")
        );
        when(recommendationService.getTrendingPosts(10))
                .thenReturn(Mono.just(trending));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/trending?limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(2, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void getTrending_WithDefaultLimit_ShouldUseDefault() {
        // Given
        when(recommendationService.getTrendingPosts(10))
                .thenReturn(Mono.just(List.of()));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/trending")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getUserRecommendations_ShouldReturnUsers() {
        // Given
        String userId = "user-123";
        List<String> users = List.of("user-001", "user-002", "user-003");
        when(recommendationService.getUserRecommendations(userId, 10))
                .thenReturn(Mono.just(users));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/users?userId=user-123&limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(3, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void getUserRecommendations_WithDefaultLimit_ShouldUseDefault() {
        // Given
        when(recommendationService.getUserRecommendations("user-456", 10))
                .thenReturn(Mono.just(List.of()));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/users?userId=user-456")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getInterests_ShouldReturnUserInterestTags() {
        // Given
        String userId = "user-123";
        UserInterestTag tag1 = UserInterestTag.builder()
                .userId(userId).tag("java").weight(0.8).createdAt(LocalDateTime.now()).build();
        UserInterestTag tag2 = UserInterestTag.builder()
                .userId(userId).tag("spring").weight(0.6).createdAt(LocalDateTime.now()).build();

        when(recommendationService.getInterestTags(userId))
                .thenReturn(Flux.just(tag1, tag2));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/interests?userId=user-123")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(2, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void getInterests_WithNoInterests_ShouldReturnEmptyList() {
        // Given
        when(recommendationService.getInterestTags("user-456"))
                .thenReturn(Flux.empty());

        // When/Then
        client.get()
                .uri("/api/v1/recommend/interests?userId=user-456")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(0, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void updateInterests_ShouldReturnUpdatedTags() throws Exception {
        // Given
        String userId = "user-123";
        List<String> tags = List.of("java", "spring", "kafka");
        String json = objectMapper.writeValueAsString(tags);

        List<UserInterestTag> savedTags = tags.stream()
                .map(tag -> UserInterestTag.builder()
                        .userId(userId).tag(tag).weight(1.0).createdAt(LocalDateTime.now()).build())
                .toList();

        when(recommendationService.updateInterestTags(userId, tags))
                .thenReturn(Mono.just(savedTags));

        // When/Then
        client.put()
                .uri("/api/v1/recommend/interests?userId=user-123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(3, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void updateInterests_WithEmptyList_ShouldReturnEmptyList() throws Exception {
        // Given
        String userId = "user-456";
        List<String> tags = List.of();
        String json = objectMapper.writeValueAsString(tags);

        when(recommendationService.updateInterestTags(userId, tags))
                .thenReturn(Mono.just(List.of()));

        // When/Then
        client.put()
                .uri("/api/v1/recommend/interests?userId=user-456")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(0, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void feedback_ShouldRecordFeedbackAndReturnSuccess() throws Exception {
        // Given
        String json = "{\"userId\":\"user-123\",\"postId\":\"post-456\",\"action\":\"like\"}";

        when(recommendationService.recordFeedback("user-123", "post-456", "like"))
                .thenReturn(Mono.empty());

        // When/Then
        client.post()
                .uri("/api/v1/recommend/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals("ok", result.getMessage());
                    assertEquals("ok", result.getData());
                });
    }

    @Test
    void feedback_WithDifferentActions_ShouldRecordSuccessfully() throws Exception {
        // Test with "dislike" action
        String json = "{\"userId\":\"user-789\",\"postId\":\"post-999\",\"action\":\"dislike\"}";

        when(recommendationService.recordFeedback("user-789", "post-999", "dislike"))
                .thenReturn(Mono.empty());

        // When/Then
        client.post()
                .uri("/api/v1/recommend/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void feedback_WithShareAction_ShouldRecordSuccessfully() throws Exception {
        // Test with "share" action
        String json = "{\"userId\":\"user-111\",\"postId\":\"post-222\",\"action\":\"share\"}";

        when(recommendationService.recordFeedback("user-111", "post-222", "share"))
                .thenReturn(Mono.empty());

        // When/Then
        client.post()
                .uri("/api/v1/recommend/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getFeed_WithLargePage_ShouldPassParameterCorrectly() {
        // Given
        when(recommendationService.getFeedRecommendations("user-999", 5, 50))
                .thenReturn(Mono.just(List.of()));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/feed?userId=user-999&page=5&size=50")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getTrending_WithLargeLimit_ShouldPassParameterCorrectly() {
        // Given
        when(recommendationService.getTrendingPosts(100))
                .thenReturn(Mono.just(List.of()));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/trending?limit=100")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void updateInterests_WithSingleTag_ShouldSaveSuccessfully() throws Exception {
        // Given
        String userId = "user-777";
        List<String> tags = List.of("reactive");
        String json = objectMapper.writeValueAsString(tags);

        UserInterestTag savedTag = UserInterestTag.builder()
                .userId(userId).tag("reactive").weight(1.0).createdAt(LocalDateTime.now()).build();

        when(recommendationService.updateInterestTags(userId, tags))
                .thenReturn(Mono.just(List.of(savedTag)));

        // When/Then
        client.put()
                .uri("/api/v1/recommend/interests?userId=user-777")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(1, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void getUserRecommendations_WithSmallLimit_ShouldLimitResults() {
        // Given
        when(recommendationService.getUserRecommendations("user-limit", 3))
                .thenReturn(Mono.just(List.of("user-001", "user-002", "user-003")));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/users?userId=user-limit&limit=3")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(3, ((List<?>) result.getData()).size());
                });
    }

    @Test
    void getRelatedPosts_WithSmallTopK_ShouldLimitResults() {
        // Given
        when(recommendationService.getRelatedPosts("post-limit", 3))
                .thenReturn(Mono.just(List.of(
                        new FeedRecommendation("r1", "R1", "S1", "A1", 0.9, "sim"),
                        new FeedRecommendation("r2", "R2", "S2", "A2", 0.8, "sim"),
                        new FeedRecommendation("r3", "R3", "S3", "A3", 0.7, "sim")
                )));

        // When/Then
        client.get()
                .uri("/api/v1/recommend/related/post-limit?topK=3")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertEquals(0, result.getCode());
                    assertEquals(3, ((List<?>) result.getData()).size());
                });
    }
}
