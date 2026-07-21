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

/** userId 走网关注入的 X-User-Id header（不走 query param）。 */
@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationService recommendationService;

    private WebTestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new RecommendationController(recommendationService)).build();
    }

    private FeedRecommendation feedRec(String id, String title, double score) {
        return new FeedRecommendation(id, title, "summary", null, "a-" + id,
                new AuthorDto("a-" + id, "user", "User", null), List.of(), 5, 1, score, LocalDateTime.now());
    }

    private TrendingPost trendingPost(String id, String title) {
        return new TrendingPost(id, title, 100, 5, 1,
                new AuthorDto("a-" + id, "user", "User", null), LocalDateTime.now());
    }

    @Test
    void getFeed_ShouldReturnFeedRecommendations() {
        when(recommendationService.getFeedRecommendations("user-123", 0, 10))
                .thenReturn(Mono.just(List.of(feedRec("p1", "Spring", 0.9), feedRec("p2", "Java", 0.8))));
        client.get().uri("/api/v1/recommend/feed?page=0&size=10").header("X-User-Id", "user-123").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class).value(r -> assertEquals(2, ((List<?>) r.getData()).size()));
    }

    @Test
    void getFeed_Anonymous_NoHeader_ReturnsTrending() {
        when(recommendationService.getFeedRecommendations(null, 0, 10)).thenReturn(Mono.just(List.of()));
        client.get().uri("/api/v1/recommend/feed").exchange().expectStatus().isOk();
    }

    @Test
    void getRelatedPosts_ShouldReturnRelatedPosts() {
        when(recommendationService.getRelatedPosts("post-123", 10))
                .thenReturn(Mono.just(List.of(feedRec("r1", "R1", 0.85), feedRec("r2", "R2", 0.75))));
        client.get().uri("/api/v1/recommend/related/post-123?topK=10").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class).value(r -> assertEquals(2, ((List<?>) r.getData()).size()));
    }

    @Test
    void getTrending_ShouldReturnTrendingPosts() {
        when(recommendationService.getTrendingPosts(10))
                .thenReturn(Mono.just(List.of(trendingPost("t1", "Hot"), trendingPost("t2", "Hot2"))));
        client.get().uri("/api/v1/recommend/trending?limit=10").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class).value(r -> assertEquals(2, ((List<?>) r.getData()).size()));
    }

    @Test
    void getInterests_ShouldReturnUserInterestTags() {
        UserInterestTag tag = UserInterestTag.builder().userId("user-123").tag("java").weight(0.8).createdAt(LocalDateTime.now()).build();
        when(recommendationService.getInterestTags("user-123")).thenReturn(Flux.just(tag, tag));
        client.get().uri("/api/v1/recommend/interests").header("X-User-Id", "user-123").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class).value(r -> assertEquals(2, ((List<?>) r.getData()).size()));
    }

    @Test
    void updateInterests_ShouldReturnUpdatedTags() throws Exception {
        List<String> tags = List.of("java", "spring");
        when(recommendationService.updateInterestTags("user-123", tags))
                .thenReturn(Mono.just(tags.stream().map(t -> UserInterestTag.builder().userId("user-123").tag(t).weight(1.0).createdAt(LocalDateTime.now()).build()).toList()));
        client.put().uri("/api/v1/recommend/interests").header("X-User-Id", "user-123")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(objectMapper.writeValueAsString(tags)).exchange()
                .expectStatus().isOk()
                .expectBody(Result.class).value(r -> assertEquals(2, ((List<?>) r.getData()).size()));
    }

    @Test
    void feedback_ShouldRecordFeedback() {
        when(recommendationService.recordFeedback("user-123", "post-456", "like")).thenReturn(Mono.empty());
        client.post().uri("/api/v1/recommend/feedback").header("X-User-Id", "user-123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"postId\":\"post-456\",\"action\":\"like\"}").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class).value(r -> assertEquals("ok", r.getData()));
    }

    @Test
    void backfill_ShouldReturnCount() {
        when(recommendationService.backfill(1000)).thenReturn(Mono.just(14));
        client.post().uri("/api/v1/recommend/admin/backfill").exchange()
                .expectStatus().isOk()
                .expectBody(Result.class).value(r -> assertEquals(14, r.getData()));
    }
}
