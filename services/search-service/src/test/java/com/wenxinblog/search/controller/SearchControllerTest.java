package com.wenxinblog.search.controller;

import com.wenxinblog.search.dto.*;
import com.wenxinblog.search.service.SearchHistoryService;
import com.wenxinblog.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private SearchHistoryService historyService;

    @InjectMocks
    private SearchController controller;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(controller).build();
        // Mock recordSearch to avoid NPE when controller calls it
        when(searchService.recordSearch(anyString())).thenReturn(Mono.empty());
    }

    @Test
    void searchBlogs_ShouldReturnBlogResults() {
        PageResult<BlogSearchResponse> result = new PageResult<>(
                List.of(new BlogSearchResponse(
                        "1", "Test Blog", "Content", "Summary",
                        new AuthorDto("author1", "Author", "Author", null), List.of("tag1"), "tech",
                        100, 10, 5, LocalDateTime.now(), 1.0, List.of(), List.of())),
                1L, 0, 10, 1
        );

        when(searchService.searchBlogs(any(SearchRequest.class))).thenReturn(Mono.just(result));

        webTestClient.get()
                .uri("/api/v1/search/blog?q=test&page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.total").isEqualTo(1)
                .jsonPath("$.data.items").isArray()
                .jsonPath("$.data.items.length()").isEqualTo(1);
    }

    @Test
    void searchBlogs_WithSortByParameter_ShouldIncludeSort() {
        PageResult<BlogSearchResponse> result = new PageResult<>(List.of(), 0L, 0, 10, 0);

        when(searchService.searchBlogs(any(SearchRequest.class))).thenReturn(Mono.just(result));

        webTestClient.get()
                .uri("/api/v1/search/blog?q=test&sortBy=date")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0);
    }

    @Test
    void searchBlogs_WithTagsAndCategory_ShouldIncludeFilters() {
        PageResult<BlogSearchResponse> result = new PageResult<>(List.of(), 0L, 0, 10, 0);

        when(searchService.searchBlogs(any(SearchRequest.class))).thenReturn(Mono.just(result));

        webTestClient.get()
                .uri("/api/v1/search/blog?q=test&tags=java,spring&category=tech")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0);
    }

    @Test
    void searchUsers_ShouldReturnUserResults() {
        PageResult<UserSearchResponse> result = new PageResult<>(
                List.of(new UserSearchResponse(
                        "1", "Test User", "testuser", "Bio", "avatar.jpg", 100, 50, 1.0)),
                1L, 0, 10, 1
        );

        when(searchService.searchUsers(eq("test"), eq(0), eq(10))).thenReturn(Mono.just(result));

        webTestClient.get()
                .uri("/api/v1/search/users?q=test&page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.total").isEqualTo(1)
                .jsonPath("$.data.items[0].displayName").isEqualTo("Test User");
    }

    @Test
    void suggest_WithBlogType_ShouldReturnBlogSuggestions() {
        List<SuggestResponse> suggestions = List.of(
                new SuggestResponse("Java Tutorial", "blog"),
                new SuggestResponse("Spring Boot", "blog")
        );

        when(searchService.suggest(eq("java"), eq("blog"))).thenReturn(Mono.just(suggestions));

        webTestClient.get()
                .uri("/api/v1/search/suggest?q=java&type=blog")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].type").isEqualTo("blog");
    }

    @Test
    void suggest_WithUserType_ShouldReturnUserSuggestions() {
        List<SuggestResponse> suggestions = List.of(
                new SuggestResponse("John Doe", "user")
        );

        when(searchService.suggest(eq("john"), eq("user"))).thenReturn(Mono.just(suggestions));

        webTestClient.get()
                .uri("/api/v1/search/suggest?q=john&type=user")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data[0].type").isEqualTo("user");
    }

    @Test
    void getTrending_ShouldReturnTrendingSearches() {
        List<String> trending = List.of("java", "spring", "kotlin");

        when(searchService.getTrendingSearches(eq(10))).thenReturn(Mono.just(trending));

        webTestClient.get()
                .uri("/api/v1/search/trending?limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(3)
                .jsonPath("$.data[0]").isEqualTo("java");
    }

    @Test
    void getTrendingTags_ShouldReturnTrendingTags() {
        List<String> tags = List.of("tech", "programming", "java");

        when(searchService.getTrendingTags(eq(20))).thenReturn(Mono.just(tags));

        webTestClient.get()
                .uri("/api/v1/search/trending/tags?limit=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(3);
    }

    @Test
    void getHistory_ShouldReturnUserSearchHistory() {
        List<String> history = List.of("query1", "query2", "query3");

        when(historyService.getSearchHistory(eq("user123"), eq(20)))
                .thenReturn(Flux.fromIterable(history));

        webTestClient.get()
                .uri("/api/v1/search/history?limit=20")
                .header("X-User-Id", "user123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(3);
    }

    @Test
    void clearHistory_ShouldReturnTrue() {
        when(historyService.clearSearchHistory(eq("user123"))).thenReturn(Mono.just(true));

        webTestClient.delete()
                .uri("/api/v1/search/history")
                .header("X-User-Id", "user123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isEqualTo(true);
    }

    @Test
    void searchBlogs_WithInvalidSizeParameter_ShouldUseDefaultSize() {
        PageResult<BlogSearchResponse> result = new PageResult<>(List.of(), 0L, 0, 10, 0);

        when(searchService.searchBlogs(any(SearchRequest.class))).thenReturn(Mono.just(result));

        webTestClient.get()
                .uri("/api/v1/search/blog?q=test&size=100") // Size > 50 should default to 10
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0);
    }
}
