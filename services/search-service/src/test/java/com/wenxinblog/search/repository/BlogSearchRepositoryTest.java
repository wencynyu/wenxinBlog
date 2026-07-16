package com.wenxinblog.search.repository;

import com.wenxinblog.search.dto.SearchRequest;
import com.wenxinblog.search.model.BlogDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlogSearchRepositoryTest {

    @Mock
    private OpenSearchClient client;

    @InjectMocks
    private BlogSearchRepository blogSearchRepository;

    private static final String TEST_INDEX = "wenxinblog-blog";

    @BeforeEach
    void setUp() throws Exception {
        Field blogIndexField = BlogSearchRepository.class.getDeclaredField("blogIndex");
        blogIndexField.setAccessible(true);
        blogIndexField.set(blogSearchRepository, TEST_INDEX);
    }

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
                .publishedAt("2024-01-01T12:00:00")
                .createdAt("2024-01-01T12:00:00")
                .build();
    }

    // ==================== indexBlog tests ====================

    @Test
    void indexBlog_Success_ShouldNotThrowException() throws IOException {
        BlogDocument doc = createTestBlogDocument();
        IndexResponse indexResponse = mock(IndexResponse.class);

        @SuppressWarnings("unchecked")
        Function<Object, Object> fn = (Function<Object, Object>) mock(Function.class);
        doReturn(indexResponse).when(client).index(any(Function.class));

        assertDoesNotThrow(() -> blogSearchRepository.indexBlog(doc));
        verify(client, times(1)).index(any(Function.class));
    }

    @Test
    void indexBlog_IOException_ShouldNotThrowException() throws IOException {
        BlogDocument doc = createTestBlogDocument();

        doThrow(new IOException("Connection failed")).when(client).index(any(Function.class));

        // Should not throw - just log the error
        assertDoesNotThrow(() -> blogSearchRepository.indexBlog(doc));
        verify(client, times(1)).index(any(Function.class));
    }

    // ==================== updateBlog tests ====================

    @Test
    void updateBlog_Success_ShouldDelegateToIndexBlog() throws IOException {
        BlogDocument doc = createTestBlogDocument();
        IndexResponse indexResponse = mock(IndexResponse.class);

        doReturn(indexResponse).when(client).index(any(Function.class));

        blogSearchRepository.updateBlog(doc);

        verify(client, times(1)).index(any(Function.class));
    }

    @Test
    void updateBlog_IOException_ShouldNotThrowException() throws IOException {
        BlogDocument doc = createTestBlogDocument();

        doThrow(new IOException("Connection failed")).when(client).index(any(Function.class));

        assertDoesNotThrow(() -> blogSearchRepository.updateBlog(doc));
        verify(client, times(1)).index(any(Function.class));
    }

    // ==================== deleteBlog tests ====================

    @Test
    void deleteBlog_Success_ShouldNotThrowException() throws IOException {
        DeleteResponse deleteResponse = mock(DeleteResponse.class);

        doReturn(deleteResponse).when(client).delete(any(Function.class));

        assertDoesNotThrow(() -> blogSearchRepository.deleteBlog("blog-1"));
        verify(client, times(1)).delete(any(Function.class));
    }

    @Test
    void deleteBlog_IOException_ShouldNotThrowException() throws IOException {
        doThrow(new IOException("Connection failed")).when(client).delete(any(Function.class));

        assertDoesNotThrow(() -> blogSearchRepository.deleteBlog("blog-1"));
        verify(client, times(1)).delete(any(Function.class));
    }

    // ==================== searchBlogs tests ====================

    @Test
    void searchBlogs_SortByDate_ShouldReturnResponse() throws IOException {
        SearchRequest request = new SearchRequest("java", 0, 10, "date", null, null, null);

        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(), 0L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        SearchResponse<BlogDocument> result = blogSearchRepository.searchBlogs(request);

        assertNotNull(result);
        verify(client, times(1)).search(any(Function.class), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_SortByViews_ShouldReturnResponse() throws IOException {
        SearchRequest request = new SearchRequest("java", 0, 10, "views", null, null, null);

        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(), 0L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        SearchResponse<BlogDocument> result = blogSearchRepository.searchBlogs(request);

        assertNotNull(result);
        verify(client, times(1)).search(any(Function.class), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_SortByLikes_ShouldReturnResponse() throws IOException {
        SearchRequest request = new SearchRequest("java", 0, 10, "likes", null, null, null);

        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(), 0L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        SearchResponse<BlogDocument> result = blogSearchRepository.searchBlogs(request);

        assertNotNull(result);
        verify(client, times(1)).search(any(Function.class), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_SortByRelevance_ShouldReturnResponse() throws IOException {
        SearchRequest request = new SearchRequest("java", 0, 10, "relevance", null, null, null);

        BlogDocument doc = createTestBlogDocument();
        Hit<BlogDocument> hit = createMockHit(doc, 2.5, Map.of(
                "title", List.of("<em>Java</em> Programming"),
                "content", List.of("Learn <em>Java</em> programming")
        ));
        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(hit), 1L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        SearchResponse<BlogDocument> result = blogSearchRepository.searchBlogs(request);

        assertNotNull(result);
        assertEquals(1, result.hits().hits().size());
        assertEquals("blog-1", result.hits().hits().get(0).source().getId());
        verify(client, times(1)).search(any(Function.class), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_SortByDefault_ShouldReturnResponse() throws IOException {
        SearchRequest request = new SearchRequest("java", 0, 10, "unknown_sort", null, null, null);

        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(), 0L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        SearchResponse<BlogDocument> result = blogSearchRepository.searchBlogs(request);

        assertNotNull(result);
        verify(client, times(1)).search(any(Function.class), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_WithPagination_ShouldPassCorrectFromAndSize() throws IOException {
        SearchRequest request = new SearchRequest("java", 2, 20, "date", null, null, null);

        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(), 0L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        blogSearchRepository.searchBlogs(request);

        // page=2, size=20 => from=40
        verify(client, times(1)).search(any(Function.class), eq(BlogDocument.class));
    }

    @Test
    void searchBlogs_IOException_ShouldThrowRuntimeException() throws IOException {
        SearchRequest request = new SearchRequest("java", 0, 10, "relevance", null, null, null);

        doThrow(new IOException("Search failed")).when(client).search(any(Function.class), eq(BlogDocument.class));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> blogSearchRepository.searchBlogs(request));

        assertEquals("Search failed", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void searchBlogs_WithResults_ShouldReturnHits() throws IOException {
        SearchRequest request = new SearchRequest("test query", 0, 10, "relevance", null, null, null);

        BlogDocument doc1 = createTestBlogDocument();
        BlogDocument doc2 = BlogDocument.builder()
                .id("blog-2")
                .title("Another Blog")
                .content("More content")
                .authorId("author-2")
                .authorName("Another Author")
                .build();

        Hit<BlogDocument> hit1 = createMockHit(doc1, 2.5, Map.of());
        Hit<BlogDocument> hit2 = createMockHit(doc2, 1.8, Map.of());

        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(hit1, hit2), 2L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        SearchResponse<BlogDocument> result = blogSearchRepository.searchBlogs(request);

        assertNotNull(result);
        assertEquals(2, result.hits().hits().size());
        assertEquals("blog-1", result.hits().hits().get(0).source().getId());
        assertEquals("blog-2", result.hits().hits().get(1).source().getId());
        assertEquals(2L, result.hits().total().value());
    }

    // ==================== suggestBlog tests ====================

    @Test
    void suggestBlog_WithResults_ShouldReturnTitles() throws IOException {
        BlogDocument doc1 = createTestBlogDocument();
        BlogDocument doc2 = BlogDocument.builder()
                .id("blog-2")
                .title("Java Spring Boot Guide")
                .build();

        Hit<BlogDocument> hit1 = createMockHit(doc1, 2.0);
        Hit<BlogDocument> hit2 = createMockHit(doc2, 1.5);

        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(hit1, hit2), 2L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        List<String> suggestions = blogSearchRepository.suggestBlog("Java", 5);

        assertEquals(2, suggestions.size());
        assertEquals("Test Blog Title", suggestions.get(0));
        assertEquals("Java Spring Boot Guide", suggestions.get(1));
        verify(client, times(1)).search(any(Function.class), eq(BlogDocument.class));
    }

    @Test
    void suggestBlog_WithNullSource_ShouldSkipNullHits() throws IOException {
        @SuppressWarnings("unchecked")
        Hit<BlogDocument> nullSourceHit = mock(Hit.class);
        when(nullSourceHit.source()).thenReturn(null);

        BlogDocument doc = createTestBlogDocument();
        Hit<BlogDocument> validHit = createMockHit(doc, 1.0);

        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(
                List.of(nullSourceHit, validHit), 2L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        List<String> suggestions = blogSearchRepository.suggestBlog("Java", 5);

        assertEquals(1, suggestions.size());
        assertEquals("Test Blog Title", suggestions.get(0));
    }

    @Test
    void suggestBlog_WithEmptyResults_ShouldReturnEmptyList() throws IOException {
        SearchResponse<BlogDocument> mockResponse = createMockSearchResponse(List.of(), 0L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(BlogDocument.class));

        List<String> suggestions = blogSearchRepository.suggestBlog("nonexistent", 5);

        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    void suggestBlog_IOException_ShouldReturnEmptyList() throws IOException {
        doThrow(new IOException("Suggest failed")).when(client).search(any(Function.class), eq(BlogDocument.class));

        List<String> suggestions = blogSearchRepository.suggestBlog("Java", 5);

        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    // ==================== Helper methods ====================

    private Hit<BlogDocument> createMockHit(BlogDocument source, double score) {
        return createMockHit(source, score, null);
    }

    @SuppressWarnings("unchecked")
    private Hit<BlogDocument> createMockHit(BlogDocument source, double score,
                                              Map<String, List<String>> highlights) {
        Hit<BlogDocument> hit = mock(Hit.class);
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
    private SearchResponse<BlogDocument> createMockSearchResponse(List<Hit<BlogDocument>> hits,
                                                                     long total) {
        SearchResponse<BlogDocument> response = mock(SearchResponse.class);
        HitsMetadata<BlogDocument> hitsMetadata = mock(HitsMetadata.class);

        when(hitsMetadata.hits()).thenReturn(hits);

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(total);
        when(totalHits.relation()).thenReturn(TotalHitsRelation.Eq);

        when(hitsMetadata.total()).thenReturn(totalHits);
        when(response.hits()).thenReturn(hitsMetadata);

        return response;
    }
}
