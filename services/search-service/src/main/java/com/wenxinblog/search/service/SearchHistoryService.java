package com.wenxinblog.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SearchHistoryService {

    private final ReactiveStringRedisTemplate redis;

    public Mono<Void> saveSearchHistory(String userId, String query) {
        String key = "search:history:" + userId;
        return redis.opsForList().leftPush(key, query)
                .then(redis.opsForList().trim(key, 0, 49))
                .then(redis.expire(key, Duration.ofDays(30)))
                .then();
    }

    public Flux<String> getSearchHistory(String userId, int limit) {
        String key = "search:history:" + userId;
        return redis.opsForList().range(key, 0, limit - 1);
    }

    public Mono<Boolean> clearSearchHistory(String userId) {
        String key = "search:history:" + userId;
        return redis.delete(key).map(count -> count > 0);
    }
}
