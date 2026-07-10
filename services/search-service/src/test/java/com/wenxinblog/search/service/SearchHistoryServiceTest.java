package com.wenxinblog.search.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redis;

    private SearchHistoryService searchHistoryService;

    @BeforeEach
    void setUp() {
        searchHistoryService = new SearchHistoryService(redis);
    }

    @Test
    void saveSearchHistory_ShouldSaveQueryAndTrim() {
        String userId = "user123";
        String query = "test query";

        @SuppressWarnings("unchecked")
        ReactiveListOperations<String, String> listOps = mock(ReactiveListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.leftPush(anyString(), eq(query))).thenReturn(Mono.just(1L));
        when(listOps.trim(anyString(), eq(0L), eq(49L))).thenReturn(Mono.just(true));
        when(redis.expire(anyString(), eq(Duration.ofDays(30)))).thenReturn(Mono.just(true));

        StepVerifier.create(searchHistoryService.saveSearchHistory(userId, query))
                .verifyComplete();

        verify(listOps).leftPush("search:history:" + userId, query);
        verify(listOps).trim("search:history:" + userId, 0, 49);
        verify(redis).expire("search:history:" + userId, Duration.ofDays(30));
    }

    @Test
    void getSearchHistory_ShouldReturnHistoryQueries() {
        String userId = "user123";
        int limit = 5;

        @SuppressWarnings("unchecked")
        ReactiveListOperations<String, String> listOps = mock(ReactiveListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range(anyString(), eq(0L), eq(4L)))
                .thenReturn(Flux.just("query1", "query2", "query3", "query4", "query5"));

        StepVerifier.create(searchHistoryService.getSearchHistory(userId, limit))
                .expectNext("query1")
                .expectNext("query2")
                .expectNext("query3")
                .expectNext("query4")
                .expectNext("query5")
                .verifyComplete();

        verify(listOps).range("search:history:" + userId, 0, 4);
    }

    @Test
    void getSearchHistory_WithLimitOf10_ShouldReturn10Results() {
        String userId = "user123";

        @SuppressWarnings("unchecked")
        ReactiveListOperations<String, String> listOps = mock(ReactiveListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range(anyString(), eq(0L), eq(9L)))
                .thenReturn(Flux.fromIterable(List.of("q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8", "q9", "q10")));

        StepVerifier.create(searchHistoryService.getSearchHistory(userId, 10))
                .expectNextCount(10)
                .verifyComplete();
    }

    @Test
    void clearSearchHistory_ShouldReturnTrueWhenKeyExists() {
        String userId = "user123";

        when(redis.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(searchHistoryService.clearSearchHistory(userId))
                .expectNext(true)
                .verifyComplete();

        verify(redis).delete("search:history:" + userId);
    }

    @Test
    void clearSearchHistory_ShouldReturnFalseWhenKeyDoesNotExist() {
        String userId = "user123";

        when(redis.delete(anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(searchHistoryService.clearSearchHistory(userId))
                .expectNext(false)
                .verifyComplete();
    }
}
