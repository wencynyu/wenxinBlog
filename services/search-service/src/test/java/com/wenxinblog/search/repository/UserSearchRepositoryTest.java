package com.wenxinblog.search.repository;

import com.wenxinblog.search.model.UserDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSearchRepositoryTest {

    @Mock
    private ElasticsearchClient client;

    @InjectMocks
    private UserSearchRepository userSearchRepository;

    private static final String TEST_INDEX = "wenxinblog-user";

    @BeforeEach
    void setUp() throws Exception {
        Field userIndexField = UserSearchRepository.class.getDeclaredField("userIndex");
        userIndexField.setAccessible(true);
        userIndexField.set(userSearchRepository, TEST_INDEX);
    }

    private UserDocument createTestUserDocument() {
        return UserDocument.builder()
                .id("user-1")
                .displayName("Test User")
                .username("testuser")
                .bio("A test user bio")
                .avatarUrl("http://example.com/avatar.jpg")
                .followerCount(100)
                .postCount(50)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==================== indexUser tests ====================

    @Test
    void indexUser_Success_ShouldNotThrowException() throws IOException {
        UserDocument doc = createTestUserDocument();
        IndexResponse indexResponse = mock(IndexResponse.class);

        doReturn(indexResponse).when(client).index(any(Function.class));

        assertDoesNotThrow(() -> userSearchRepository.indexUser(doc));
        verify(client, times(1)).index(any(Function.class));
    }

    @Test
    void indexUser_IOException_ShouldNotThrowException() throws IOException {
        UserDocument doc = createTestUserDocument();

        doThrow(new IOException("Connection failed")).when(client).index(any(Function.class));

        // Should not throw - just log the error
        assertDoesNotThrow(() -> userSearchRepository.indexUser(doc));
        verify(client, times(1)).index(any(Function.class));
    }

    // ==================== updateUser tests ====================

    @Test
    void updateUser_Success_ShouldDelegateToIndexUser() throws IOException {
        UserDocument doc = createTestUserDocument();
        IndexResponse indexResponse = mock(IndexResponse.class);

        doReturn(indexResponse).when(client).index(any(Function.class));

        userSearchRepository.updateUser(doc);

        verify(client, times(1)).index(any(Function.class));
    }

    @Test
    void updateUser_IOException_ShouldNotThrowException() throws IOException {
        UserDocument doc = createTestUserDocument();

        doThrow(new IOException("Connection failed")).when(client).index(any(Function.class));

        assertDoesNotThrow(() -> userSearchRepository.updateUser(doc));
        verify(client, times(1)).index(any(Function.class));
    }

    // ==================== searchUsers tests ====================

    @Test
    void searchUsers_Success_ShouldReturnResponse() throws IOException {
        UserDocument doc = createTestUserDocument();
        Hit<UserDocument> hit = createMockHit(doc, 1.5);

        SearchResponse<UserDocument> mockResponse = createMockSearchResponse(List.of(hit), 1L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(UserDocument.class));

        SearchResponse<UserDocument> result = userSearchRepository.searchUsers("test", 0, 10);

        assertNotNull(result);
        assertEquals(1, result.hits().hits().size());
        assertEquals("user-1", result.hits().hits().get(0).source().getId());
        assertEquals("Test User", result.hits().hits().get(0).source().getDisplayName());
        verify(client, times(1)).search(any(Function.class), eq(UserDocument.class));
    }

    @Test
    void searchUsers_WithPagination_ShouldReturnPagedResults() throws IOException {
        UserDocument doc1 = createTestUserDocument();
        UserDocument doc2 = UserDocument.builder()
                .id("user-2")
                .displayName("Second User")
                .username("seconduser")
                .build();

        Hit<UserDocument> hit1 = createMockHit(doc1, 2.0);
        Hit<UserDocument> hit2 = createMockHit(doc2, 1.8);

        SearchResponse<UserDocument> mockResponse = createMockSearchResponse(List.of(hit1, hit2), 25L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(UserDocument.class));

        SearchResponse<UserDocument> result = userSearchRepository.searchUsers("test", 1, 10);

        assertNotNull(result);
        assertEquals(2, result.hits().hits().size());
        assertEquals(25L, result.hits().total().value());
        verify(client, times(1)).search(any(Function.class), eq(UserDocument.class));
    }

    @Test
    void searchUsers_EmptyResult_ShouldReturnEmptyHits() throws IOException {
        SearchResponse<UserDocument> mockResponse = createMockSearchResponse(List.of(), 0L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(UserDocument.class));

        SearchResponse<UserDocument> result = userSearchRepository.searchUsers("nonexistent", 0, 10);

        assertNotNull(result);
        assertEquals(0, result.hits().hits().size());
        assertEquals(0L, result.hits().total().value());
        verify(client, times(1)).search(any(Function.class), eq(UserDocument.class));
    }

    @Test
    void searchUsers_IOException_ShouldThrowRuntimeException() throws IOException {
        doThrow(new IOException("User search failed")).when(client).search(any(Function.class), eq(UserDocument.class));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userSearchRepository.searchUsers("test", 0, 10));

        assertEquals("User search failed", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    // ==================== suggestUsers tests ====================

    @Test
    void suggestUsers_WithResults_ShouldReturnDisplayNames() throws IOException {
        UserDocument doc1 = createTestUserDocument();
        UserDocument doc2 = UserDocument.builder()
                .id("user-2")
                .displayName("Test User Two")
                .build();

        Hit<UserDocument> hit1 = createMockHit(doc1, 2.0);
        Hit<UserDocument> hit2 = createMockHit(doc2, 1.5);

        SearchResponse<UserDocument> mockResponse = createMockSearchResponse(List.of(hit1, hit2), 2L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(UserDocument.class));

        List<String> suggestions = userSearchRepository.suggestUsers("Test", 5);

        assertEquals(2, suggestions.size());
        assertEquals("Test User", suggestions.get(0));
        assertEquals("Test User Two", suggestions.get(1));
        verify(client, times(1)).search(any(Function.class), eq(UserDocument.class));
    }

    @Test
    void suggestUsers_WithNullSource_ShouldSkipNullHits() throws IOException {
        @SuppressWarnings("unchecked")
        Hit<UserDocument> nullSourceHit = mock(Hit.class);
        when(nullSourceHit.source()).thenReturn(null);

        UserDocument doc = createTestUserDocument();
        Hit<UserDocument> validHit = createMockHit(doc, 1.0);

        SearchResponse<UserDocument> mockResponse = createMockSearchResponse(
                List.of(nullSourceHit, validHit), 2L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(UserDocument.class));

        List<String> suggestions = userSearchRepository.suggestUsers("Test", 5);

        assertEquals(1, suggestions.size());
        assertEquals("Test User", suggestions.get(0));
    }

    @Test
    void suggestUsers_WithEmptyResults_ShouldReturnEmptyList() throws IOException {
        SearchResponse<UserDocument> mockResponse = createMockSearchResponse(List.of(), 0L);
        doReturn(mockResponse).when(client).search(any(Function.class), eq(UserDocument.class));

        List<String> suggestions = userSearchRepository.suggestUsers("nonexistent", 5);

        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    void suggestUsers_IOException_ShouldReturnEmptyList() throws IOException {
        doThrow(new IOException("Suggest failed")).when(client).search(any(Function.class), eq(UserDocument.class));

        List<String> suggestions = userSearchRepository.suggestUsers("Test", 5);

        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    // ==================== Helper methods ====================

    @SuppressWarnings("unchecked")
    private Hit<UserDocument> createMockHit(UserDocument source, double score) {
        Hit<UserDocument> hit = mock(Hit.class);
        when(hit.source()).thenReturn(source);
        when(hit.score()).thenReturn(score);
        when(hit.highlight()).thenReturn(null);
        return hit;
    }

    @SuppressWarnings("unchecked")
    private SearchResponse<UserDocument> createMockSearchResponse(List<Hit<UserDocument>> hits,
                                                                     long total) {
        SearchResponse<UserDocument> response = mock(SearchResponse.class);
        HitsMetadata<UserDocument> hitsMetadata = mock(HitsMetadata.class);

        when(hitsMetadata.hits()).thenReturn(hits);

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(total);
        when(totalHits.relation()).thenReturn(TotalHitsRelation.Eq);

        when(hitsMetadata.total()).thenReturn(totalHits);
        when(response.hits()).thenReturn(hitsMetadata);

        return response;
    }
}
