package com.wenxinblog.search.repository;

import com.wenxinblog.search.dto.SearchRequest;
import com.wenxinblog.search.model.BlogDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchPage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlogSearchRepositoryTest {

    @Mock
    private ReactiveElasticsearchOperations operations;

    @InjectMocks
    private BlogSearchRepository blogSearchRepository;

    private BlogDocument createTestBlogDocument() {
        return BlogDocument.builder()
                .id("blog-1")
                .title("Test Blog Title")
                .content("Test blog content about Java programming")
                .summary("A summary of the test blog")
                .authorId("author-1")
                .authorName("Test Author")
                .tags(List.of("java", "programming"))
                .category("tech")
                .status("published")
                .viewCount(150)
                .likeCount(25)
                .commentCount(5)
                .publishedAt(LocalDateTime.parse("2024-01-01T12:00:00"))
                .createdAt(LocalDateTime.parse("2024-01-01T12:00:00"))
                .build();
    }

    // ==================== indexBlog tests ====================

    @Test
    void indexBlog_Success_ShouldInvokeSave() {
        BlogDocument doc = createTestBlogDocument();
        when(operations.save(any(BlogDocument.class))).thenReturn(Mono.just(doc));

        assertDoesNotThrow(() -> blogSearchRepository.indexBlog(doc));
        verify(operations, times(1)).save(doc);
    }

    @Test
    void indexBlog_Error_ShouldNotThrowException() {
        BlogDocument doc = createTestBlogDocument();
        when(operations.save(any(BlogDocument.class)))
                .thenReturn(Mono.error(new RuntimeException("Connection failed")));

        assertDoesNotThrow(() -> blogSearchRepository.indexBlog(doc));
        verify(operations, times(1)).save(doc);
    }

    // ==================== updateBlog tests ====================

    @Test
    void updateBlog_Success_ShouldDelegateToIndexBlog() {
        BlogDocument doc = createTestBlogDocument();
        when(operations.save(any(BlogDocument.class))).thenReturn(Mono.just(doc));

        blogSearchRepository.updateBlog(doc);

        verify(operations, times(1)).save(doc);
    }

    // ==================== deleteBlog tests ====================

    @Test
    void deleteBlog_Success_ShouldInvokeDelete() {
        when(operations.delete(anyString(), eq(BlogDocument.class))).thenReturn(Mono.just("blog-1"));

        assertDoesNotThrow(() -> blogSearchRepository.deleteBlog("blog-1"));
        verify(operations, times(1)).delete("blog-1", BlogDocument.class);
    }

    @Test
    void deleteBlog_Error_ShouldNotThrowException() {
        when(operations.delete(anyString(), eq(BlogDocument.class)))
                .thenReturn(Mono.error(new RuntimeException("Delete failed")));

        assertDoesNotThrow(() -> blogSearchRepository.deleteBlog("blog-1"));
        verify(operations, times(1)).delete("blog-1", BlogDocument.class);
    }

    // ==================== searchBlogs tests ====================

    @Test
    void searchBlogs_SortByDate_ShouldReturnPage() {
        SearchRequest request = new SearchRequest("java", 0, 10, "date", null, null, null);
        SearchPage<BlogDocument> page = createMockSearchPage(List.of(), 0L);
        when(operations.searchForPage(any(), eq(BlogDocument.class))).thenReturn(Mono.just(page));

        SearchPage<BlogDocument> result = blogSearchRepository.searchBlogs(request).block();

        assertNotNull(result);
        verify(operations, times(1)).searchForPage(any(), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_SortByViews_ShouldReturnPage() {
        SearchRequest request = new SearchRequest("java", 0, 10, "views", null, null, null);
        SearchPage<BlogDocument> page = createMockSearchPage(List.of(), 0L);
        when(operations.searchForPage(any(), eq(BlogDocument.class))).thenReturn(Mono.just(page));

        SearchPage<BlogDocument> result = blogSearchRepository.searchBlogs(request).block();

        assertNotNull(result);
        verify(operations, times(1)).searchForPage(any(), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_SortByLikes_ShouldReturnPage() {
        SearchRequest request = new SearchRequest("java", 0, 10, "likes", null, null, null);
        SearchPage<BlogDocument> page = createMockSearchPage(List.of(), 0L);
        when(operations.searchForPage(any(), eq(BlogDocument.class))).thenReturn(Mono.just(page));

        SearchPage<BlogDocument> result = blogSearchRepository.searchBlogs(request).block();

        assertNotNull(result);
        verify(operations, times(1)).searchForPage(any(), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_SortByRelevance_ShouldReturnHits() {
        SearchRequest request = new SearchRequest("java", 0, 10, "relevance", null, null, null);
        BlogDocument doc = createTestBlogDocument();
        SearchHit<BlogDocument> hit = createMockHit(doc, 2.5f, Map.of(
                "title", List.of("<em>Java</em> Programming"),
                "content", List.of("Learn <em>Java</em> programming")
        ));
        SearchPage<BlogDocument> page = createMockSearchPage(List.of(hit), 1L);
        when(operations.searchForPage(any(), eq(BlogDocument.class))).thenReturn(Mono.just(page));

        SearchPage<BlogDocument> result = blogSearchRepository.searchBlogs(request).block();

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("blog-1", result.getContent().get(0).getContent().getId());
        verify(operations, times(1)).searchForPage(any(), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_WithPagination_ShouldReturnPage() {
        SearchRequest request = new SearchRequest("java", 2, 20, "date", null, null, null);
        SearchPage<BlogDocument> page = createMockSearchPage(List.of(), 0L);
        when(operations.searchForPage(any(), eq(BlogDocument.class))).thenReturn(Mono.just(page));

        blogSearchRepository.searchBlogs(request).block();

        verify(operations, times(1)).searchForPage(any(), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_WithResults_ShouldReturnHits() {
        SearchRequest request = new SearchRequest("test query", 0, 10, "relevance", null, null, null);
        BlogDocument doc1 = createTestBlogDocument();
        BlogDocument doc2 = BlogDocument.builder()
                .id("blog-2")
                .title("Another Blog")
                .content("More content")
                .authorId("author-2")
                .authorName("Another Author")
                .build();
        SearchHit<BlogDocument> hit1 = createMockHit(doc1, 2.5f, Map.of());
        SearchHit<BlogDocument> hit2 = createMockHit(doc2, 1.8f, Map.of());
        SearchPage<BlogDocument> page = createMockSearchPage(List.of(hit1, hit2), 2L);
        when(operations.searchForPage(any(), eq(BlogDocument.class))).thenReturn(Mono.just(page));

        SearchPage<BlogDocument> result = blogSearchRepository.searchBlogs(request).block();

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("blog-1", result.getContent().get(0).getContent().getId());
        assertEquals("blog-2", result.getContent().get(1).getContent().getId());
        assertEquals(2L, result.getTotalElements());
    }

    // ==================== suggestBlog tests ====================

    @Test
    void suggestBlog_WithResults_ShouldReturnTitles() {
        BlogDocument doc1 = createTestBlogDocument();
        BlogDocument doc2 = BlogDocument.builder()
                .id("blog-2")
                .title("Java Spring Boot Guide")
                .build();
        SearchHit<BlogDocument> hit1 = createMockHit(doc1, 2.0f, null);
        SearchHit<BlogDocument> hit2 = createMockHit(doc2, 1.5f, null);
        when(operations.search(any(), eq(BlogDocument.class)))
                .thenReturn(Flux.just(hit1, hit2));

        List<String> suggestions = blogSearchRepository.suggestBlog("Java", 5).collectList().block();

        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());
        assertEquals("Test Blog Title", suggestions.get(0));
        assertEquals("Java Spring Boot Guide", suggestions.get(1));
        verify(operations, times(1)).search(any(), eq(BlogDocument.class));
    }

    @Test
    void suggestBlog_WithEmptyResults_ShouldReturnEmptyList() {
        when(operations.search(any(), eq(BlogDocument.class))).thenReturn(Flux.empty());

        List<String> suggestions = blogSearchRepository.suggestBlog("nonexistent", 5).collectList().block();

        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    void suggestBlog_Error_ShouldReturnEmptyList() {
        when(operations.search(any(), eq(BlogDocument.class)))
                .thenReturn(Flux.error(new RuntimeException("Suggest failed")));

        List<String> suggestions = blogSearchRepository.suggestBlog("Java", 5).collectList().block();

        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    // ==================== Helper methods ====================

    @SuppressWarnings("unchecked")
    private SearchHit<BlogDocument> createMockHit(BlogDocument source, float score,
                                                   Map<String, List<String>> highlights) {
        SearchHit<BlogDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(source);
        when(hit.getScore()).thenReturn(score);
        when(hit.getHighlightFields()).thenReturn(highlights != null ? highlights : Map.of());
        return hit;
    }

    @SuppressWarnings("unchecked")
    private SearchPage<BlogDocument> createMockSearchPage(List<SearchHit<BlogDocument>> hits, long total) {
        SearchPage<BlogDocument> page = mock(SearchPage.class);
        when(page.getContent()).thenReturn(hits);
        when(page.getTotalElements()).thenReturn(total);
        when(page.getTotalPages()).thenReturn(hits.isEmpty() ? 0 : 1);
        return page;
    }
}
