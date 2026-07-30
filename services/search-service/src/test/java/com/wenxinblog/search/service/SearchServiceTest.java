package com.wenxinblog.search.service;

import com.wenxinblog.search.dto.*;
import com.wenxinblog.search.model.BlogDocument;
import com.wenxinblog.search.model.UserDocument;
import com.wenxinblog.search.repository.BlogSearchRepository;
import com.wenxinblog.search.repository.UserSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceTest {

    @Mock
    private BlogSearchRepository blogRepo;

    @Mock
    private UserSearchRepository userRepo;

    @Mock
    private ReactiveStringRedisTemplate redis;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(blogRepo, userRepo, redis);
    }

    @Test
    void searchBlogs_Success_ShouldReturnResultsWithHighlights() {
        SearchRequest request = new SearchRequest("test query", 0, 10, "relevance", null, null, null);

        BlogDocument doc = BlogDocument.builder()
                .id("1")
                .title("Test Blog")
                .content("Test content with query")
                .summary("Test summary")
                .authorId("author1")
                .authorName("Author Name")
                .tags(List.of("tag1", "tag2"))
                .category("tech")
                .viewCount(100)
                .likeCount(10)
                .publishedAt(LocalDateTime.parse("2024-01-01T12:00:00"))
                .build();

        Hit<BlogDocument> hit = createMockHit(doc, 2.5, Map.of(
                "title", List.of("<em>Test</em> Blog"),
                "content", List.of("Test content with <em>query</em>")
        ));

        SearchResponse<BlogDocument> response = createMockSearchResponse(List.of(hit), 1L);

        when(blogRepo.searchBlogs(any(SearchRequest.class))).thenReturn(response);

        StepVerifier.create(searchService.searchBlogs(request))
                .expectNextMatches(result ->
                        result.total() == 1 &&
                                result.items().size() == 1 &&
                                result.items().get(0).id().equals("1") &&
                                result.items().get(0).score() == 2.5 &&
                                result.items().get(0).highlightTitle().contains("<em>Test</em> Blog") &&
                                result.items().get(0).highlightContent().contains("Test content with <em>query</em>"))
                .verifyComplete();
    }

    @Test
    void searchBlogs_EmptyResult_ShouldReturnEmptyPageResult() {
        SearchRequest request = new SearchRequest("no results", 0, 10, "relevance", null, null, null);

        SearchResponse<BlogDocument> response = createMockSearchResponse(List.of(), 0L);

        when(blogRepo.searchBlogs(any(SearchRequest.class))).thenReturn(response);

        StepVerifier.create(searchService.searchBlogs(request))
                .expectNextMatches(result ->
                        result.total() == 0 && result.items().isEmpty())
                .verifyComplete();
    }

    @Test
    void searchBlogs_WithHighlighting_ShouldIncludeHighlightFields() {
        SearchRequest request = new SearchRequest("highlighted", 0, 10, "relevance", null, null, null);

        BlogDocument doc = BlogDocument.builder()
                .id("1")
                .title("Highlighted Title")
                .content("Content with highlighted text")
                .authorId("author1")
                .authorName("Author")
                .build();

        Hit<BlogDocument> hit = createMockHit(doc, 1.0, Map.of(
                "title", List.of("<em>Highlighted</em> Title"),
                "content", List.of("Content with <em>highlighted</em> text")
        ));

        SearchResponse<BlogDocument> response = createMockSearchResponse(List.of(hit), 1L);

        when(blogRepo.searchBlogs(any(SearchRequest.class))).thenReturn(response);

        StepVerifier.create(searchService.searchBlogs(request))
                .expectNextMatches(result ->
                        !result.items().isEmpty() &&
                                !result.items().get(0).highlightTitle().isEmpty() &&
                                !result.items().get(0).highlightContent().isEmpty())
                .verifyComplete();
    }

    @Test
    void searchUsers_Success_ShouldReturnUserResults() {
        UserDocument doc = UserDocument.builder()
                .id("1")
                .displayName("Test User")
                .username("testuser")
                .bio("Test bio")
                .avatarUrl("http://example.com/avatar.jpg")
                .followerCount(100)
                .postCount(50)
                .build();

        Hit<UserDocument> hit = createMockHit(doc, 1.5);

        SearchResponse<UserDocument> response = createMockSearchResponse(List.of(hit), 1L);

        when(userRepo.searchUsers(eq("test"), eq(0), eq(10))).thenReturn(response);

        StepVerifier.create(searchService.searchUsers("test", 0, 10))
                .expectNextMatches(result ->
                        result.total() == 1 &&
                                result.items().size() == 1 &&
                                result.items().get(0).id().equals("1") &&
                                result.items().get(0).displayName().equals("Test User") &&
                                result.items().get(0).score() == 1.5)
                .verifyComplete();
    }

    @Test
    void suggest_WithBlogType_ShouldReturnBlogSuggestions() {
        when(blogRepo.suggestBlog(eq("test"), eq(10))).thenReturn(List.of("Test Blog 1", "Test Blog 2"));

        StepVerifier.create(searchService.suggest("test", "blog"))
                .expectNextMatches(suggestions ->
                        suggestions.size() == 2 &&
                                suggestions.get(0).text().equals("Test Blog 1") &&
                                suggestions.get(0).type().equals("blog") &&
                                suggestions.get(1).text().equals("Test Blog 2"))
                .verifyComplete();

        verify(blogRepo).suggestBlog("test", 10);
        verify(userRepo, never()).suggestUsers(any(), anyInt());
    }

    @Test
    void suggest_WithUserType_ShouldReturnUserSuggestions() {
        when(userRepo.suggestUsers(eq("test"), eq(10))).thenReturn(List.of("User One", "User Two"));

        StepVerifier.create(searchService.suggest("test", "user"))
                .expectNextMatches(suggestions ->
                        suggestions.size() == 2 &&
                                suggestions.get(0).text().equals("User One") &&
                                suggestions.get(0).type().equals("user") &&
                                suggestions.get(1).text().equals("User Two"))
                .verifyComplete();

        verify(userRepo).suggestUsers("test", 10);
        verify(blogRepo, never()).suggestBlog(any(), anyInt());
    }

    @Test
    void getTrendingSearches_ShouldReturnTrendingQueries() {
        @SuppressWarnings("unchecked")
        ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange(anyString(), any()))
                .thenReturn(Flux.just("java", "spring", "kotlin"));

        StepVerifier.create(searchService.getTrendingSearches(3))
                .expectNextMatches(trending ->
                        trending.size() == 3 &&
                                trending.contains("java") &&
                                trending.contains("spring") &&
                                trending.contains("kotlin"))
                .verifyComplete();
    }

    @Test
    void getTrendingTags_ShouldReturnTrendingTags() {
        @SuppressWarnings("unchecked")
        ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange(anyString(), any()))
                .thenReturn(Flux.just("tech", "java", "programming"));

        StepVerifier.create(searchService.getTrendingTags(3))
                .expectNextMatches(tags ->
                        tags.size() == 3 &&
                                tags.contains("tech") &&
                                tags.contains("java") &&
                                tags.contains("programming"))
                .verifyComplete();
    }

    @Test
    void recordSearch_ShouldIncrementSearchCount() {
        @SuppressWarnings("unchecked")
        ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.incrementScore(anyString(), eq("test query"), eq(1.0)))
                .thenReturn(Mono.just(1.0));

        StepVerifier.create(searchService.recordSearch("test query"))
                .verifyComplete();

        verify(zSetOps).incrementScore("search:trending", "test query", 1.0);
    }

    @Test
    void searchBlogs_WithNullDocument_ShouldSkipNullDocuments() {
        SearchRequest request = new SearchRequest("test", 0, 10, "relevance", null, null, null);

        Hit<BlogDocument> nullHit = mock(Hit.class);
        when(nullHit.source()).thenReturn(null);

        BlogDocument validDoc = BlogDocument.builder()
                .id("2")
                .title("Valid Doc")
                .authorId("author1")
                .authorName("Author")
                .build();

        Hit<BlogDocument> validHit = createMockHit(validDoc, 1.0, Map.of());

        SearchResponse<BlogDocument> response = createMockSearchResponse(List.of(nullHit, validHit), 2L);

        when(blogRepo.searchBlogs(any(SearchRequest.class))).thenReturn(response);

        StepVerifier.create(searchService.searchBlogs(request))
                .expectNextMatches(result ->
                        result.total() == 2 &&
                                result.items().size() == 1 &&
                                result.items().get(0).id().equals("2"))
                .verifyComplete();
    }

    @Test
    void searchBlogs_WithoutHighlights_ShouldReturnEmptyHighlightLists() {
        SearchRequest request = new SearchRequest("test", 0, 10, "relevance", null, null, null);

        BlogDocument doc = BlogDocument.builder()
                .id("1")
                .title("Test Blog")
                .authorId("author1")
                .authorName("Author")
                .build();

        Hit<BlogDocument> hit = createMockHit(doc, 1.0, null);

        SearchResponse<BlogDocument> response = createMockSearchResponse(List.of(hit), 1L);

        when(blogRepo.searchBlogs(any(SearchRequest.class))).thenReturn(response);

        StepVerifier.create(searchService.searchBlogs(request))
                .expectNextMatches(result ->
                        !result.items().isEmpty() &&
                                result.items().get(0).highlightTitle().isEmpty() &&
                                result.items().get(0).highlightContent().isEmpty())
                .verifyComplete();
    }

    @Test
    void getTrendingSearches_EmptyRedis_ShouldReturnEmptyList() {
        @SuppressWarnings("unchecked")
        ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange(anyString(), any())).thenReturn(Flux.empty());

        StepVerifier.create(searchService.getTrendingSearches(10))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
    }

    private <T> Hit<T> createMockHit(T source, double score) {
        return createMockHit(source, score, null);
    }

    @SuppressWarnings("unchecked")
    private <T> Hit<T> createMockHit(T source, double score, Map<String, List<String>> highlights) {
        Hit<T> hit = mock(Hit.class);
        when(hit.source()).thenReturn(source);
        when(hit.score()).thenReturn(score);

        if (highlights != null) {
            when(hit.highlight()).thenReturn(highlights);
        } else {
            when(hit.highlight()).thenReturn(null);
        }

        return hit;
    }

    @SuppressWarnings("unchecked")
    private <T> SearchResponse<T> createMockSearchResponse(List<Hit<T>> hits, long total) {
        SearchResponse<T> response = mock(SearchResponse.class);
        HitsMetadata<T> hitsMetadata = mock(HitsMetadata.class);

        when(hitsMetadata.hits()).thenReturn(hits);

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(total);
        when(totalHits.relation()).thenReturn(TotalHitsRelation.Eq);

        when(hitsMetadata.total()).thenReturn(totalHits);
        when(response.hits()).thenReturn(hitsMetadata);

        return response;
    }
}
