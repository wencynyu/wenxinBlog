package com.wenxinblog.search.repository;

import com.wenxinblog.search.model.UserDocument;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSearchRepositoryTest {

    @Mock
    private ReactiveElasticsearchOperations operations;

    @InjectMocks
    private UserSearchRepository userSearchRepository;

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
    void indexUser_Success_ShouldInvokeSave() {
        UserDocument doc = createTestUserDocument();
        when(operations.save(any(UserDocument.class))).thenReturn(Mono.just(doc));

        assertDoesNotThrow(() -> userSearchRepository.indexUser(doc));
        verify(operations, times(1)).save(doc);
    }

    @Test
    void indexUser_Error_ShouldNotThrowException() {
        UserDocument doc = createTestUserDocument();
        when(operations.save(any(UserDocument.class)))
                .thenReturn(Mono.error(new RuntimeException("Connection failed")));

        assertDoesNotThrow(() -> userSearchRepository.indexUser(doc));
        verify(operations, times(1)).save(doc);
    }

    // ==================== updateUser tests ====================

    @Test
    void updateUser_Success_ShouldDelegateToIndexUser() {
        UserDocument doc = createTestUserDocument();
        when(operations.save(any(UserDocument.class))).thenReturn(Mono.just(doc));

        userSearchRepository.updateUser(doc);

        verify(operations, times(1)).save(doc);
    }

    // ==================== searchUsers tests ====================

    @Test
    void searchUsers_Success_ShouldReturnPage() {
        UserDocument doc = createTestUserDocument();
        SearchHit<UserDocument> hit = createMockHit(doc, 1.5f);
        SearchPage<UserDocument> page = createMockSearchPage(List.of(hit), 1L);
        when(operations.searchForPage(any(), eq(UserDocument.class))).thenReturn(Mono.just(page));

        SearchPage<UserDocument> result = userSearchRepository.searchUsers("test", 0, 10).block();

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("user-1", result.getContent().get(0).getContent().getId());
        assertEquals("Test User", result.getContent().get(0).getContent().getDisplayName());
        verify(operations, times(1)).searchForPage(any(), eq(UserDocument.class));
    }

    @Test
    void searchUsers_WithPagination_ShouldReturnPagedResults() {
        UserDocument doc1 = createTestUserDocument();
        UserDocument doc2 = UserDocument.builder()
                .id("user-2")
                .displayName("Second User")
                .username("seconduser")
                .build();
        SearchHit<UserDocument> hit1 = createMockHit(doc1, 2.0f);
        SearchHit<UserDocument> hit2 = createMockHit(doc2, 1.8f);
        SearchPage<UserDocument> page = createMockSearchPage(List.of(hit1, hit2), 25L);
        when(operations.searchForPage(any(), eq(UserDocument.class))).thenReturn(Mono.just(page));

        SearchPage<UserDocument> result = userSearchRepository.searchUsers("test", 1, 10).block();

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(25L, result.getTotalElements());
        verify(operations, times(1)).searchForPage(any(), eq(UserDocument.class));
    }

    @Test
    void searchUsers_EmptyResult_ShouldReturnEmptyPage() {
        SearchPage<UserDocument> page = createMockSearchPage(List.of(), 0L);
        when(operations.searchForPage(any(), eq(UserDocument.class))).thenReturn(Mono.just(page));

        SearchPage<UserDocument> result = userSearchRepository.searchUsers("nonexistent", 0, 10).block();

        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        assertEquals(0L, result.getTotalElements());
        verify(operations, times(1)).searchForPage(any(), eq(UserDocument.class));
    }

    // ==================== suggestUsers tests ====================

    @Test
    void suggestUsers_WithResults_ShouldReturnDisplayNames() {
        UserDocument doc1 = createTestUserDocument();
        UserDocument doc2 = UserDocument.builder()
                .id("user-2")
                .displayName("Test User Two")
                .build();
        SearchHit<UserDocument> hit1 = createMockHit(doc1, 2.0f);
        SearchHit<UserDocument> hit2 = createMockHit(doc2, 1.5f);
        when(operations.search(any(), eq(UserDocument.class)))
                .thenReturn(Flux.just(hit1, hit2));

        List<String> suggestions = userSearchRepository.suggestUsers("Test", 5).collectList().block();

        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());
        assertEquals("Test User", suggestions.get(0));
        assertEquals("Test User Two", suggestions.get(1));
        verify(operations, times(1)).search(any(), eq(UserDocument.class));
    }

    @Test
    void suggestUsers_WithEmptyResults_ShouldReturnEmptyList() {
        when(operations.search(any(), eq(UserDocument.class))).thenReturn(Flux.empty());

        List<String> suggestions = userSearchRepository.suggestUsers("nonexistent", 5).collectList().block();

        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    void suggestUsers_Error_ShouldReturnEmptyList() {
        when(operations.search(any(), eq(UserDocument.class)))
                .thenReturn(Flux.error(new RuntimeException("Suggest failed")));

        List<String> suggestions = userSearchRepository.suggestUsers("Test", 5).collectList().block();

        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    // ==================== Helper methods ====================

    @SuppressWarnings("unchecked")
    private SearchHit<UserDocument> createMockHit(UserDocument source, float score) {
        SearchHit<UserDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(source);
        when(hit.getScore()).thenReturn(score);
        when(hit.getHighlightFields()).thenReturn(java.util.Map.of());
        return hit;
    }

    @SuppressWarnings("unchecked")
    private SearchPage<UserDocument> createMockSearchPage(List<SearchHit<UserDocument>> hits, long total) {
        SearchPage<UserDocument> page = mock(SearchPage.class);
        when(page.getContent()).thenReturn(hits);
        when(page.getTotalElements()).thenReturn(total);
        when(page.getTotalPages()).thenReturn(hits.isEmpty() ? 0 : 1);
        return page;
    }
}
