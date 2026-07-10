package com.wenxinblog.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wenxinblog.recommendation.dto.FeedRecommendation;
import com.wenxinblog.recommendation.dto.TrendingPost;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.repository.UserInterestTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final MilvusService milvusService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserInterestTagRepository interestTagRepository;

    public Mono<List<FeedRecommendation>> getFeedRecommendations(String userId, int page, int size) {
        String cacheKey = String.format("recommend:feed:%s:%d", userId, page);

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                        List<FeedRecommendation> items = mapper.readValue(cached,
                                new TypeReference<List<FeedRecommendation>>() {});
                        return Mono.just(items);
                    } catch (Exception e) {
                        return Mono.<List<FeedRecommendation>>empty();
                    }
                })
                .switchIfEmpty(computeFeed(userId, size)
                        .flatMap(items -> cacheFeed(cacheKey, items).thenReturn(items)));
    }

    public Mono<List<FeedRecommendation>> getRelatedPosts(String postId, int topK) {
        return milvusService.searchSimilarPosts(postId, topK)
                .collectList()
                .flatMap(items -> items.isEmpty() ? generateMockRelated(postId, topK) : Mono.just(items));
    }

    public Mono<List<TrendingPost>> getTrendingPosts(int limit) {
        String cacheKey = "recommend:trending:" + limit;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        List<TrendingPost> items = mapper.readValue(cached,
                                new TypeReference<List<TrendingPost>>() {});
                        return Mono.just(items);
                    } catch (Exception e) {
                        return Mono.<List<TrendingPost>>empty();
                    }
                })
                .switchIfEmpty(generateMockTrending(limit)
                        .flatMap(items -> {
                            try {
                                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                String json = mapper.writeValueAsString(items);
                                redisTemplate.opsForValue().set(cacheKey, json, Duration.ofMinutes(30)).subscribe();
                            } catch (Exception ignored) {}
                            return Mono.just(items);
                        }));
    }

    public Mono<List<String>> getUserRecommendations(String userId, int limit) {
        // Mock: "Users you may know"
        return Mono.just(List.of(
                "user-001", "user-002", "user-003"
        ).stream().limit(limit).collect(Collectors.toList()));
    }

    public Flux<UserInterestTag> getInterestTags(String userId) {
        return interestTagRepository.findByUserId(userId);
    }

    public Mono<List<UserInterestTag>> updateInterestTags(String userId, List<String> tags) {
        return interestTagRepository.deleteByUserId(userId)
                .thenMany(Flux.fromIterable(tags)
                        .map(tag -> UserInterestTag.builder()
                                .userId(userId).tag(tag).weight(1.0)
                                .createdAt(java.time.LocalDateTime.now()).build())
                        .flatMap(interestTagRepository::save))
                .collectList();
    }

    public Mono<Void> recordFeedback(String userId, String postId, String action) {
        log.info("Recording feedback: userId={}, postId={}, action={}", userId, postId, action);
        // Invalidate feed cache
        String pattern = "recommend:feed:" + userId + ":*";
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then();
    }

    private Mono<List<FeedRecommendation>> computeFeed(String userId, int size) {
        return milvusService.searchByUserInterest(userId, size)
                .collectList()
                .flatMap(items -> items.isEmpty() ? generateMockFeed(userId, size) : Mono.just(items));
    }

    private Mono<List<FeedRecommendation>> generateMockFeed(String userId, int size) {
        String[] titles = {"Spring Boot 4 新特性", "Java 25 虚拟线程", "微服务架构实践",
                "Redis 高级用法", "Kafka 最佳实践"};
        String[] reasons = {"基于兴趣推荐", "热门内容", "协同过滤", "个性化推荐", "趋势内容"};

        List<FeedRecommendation> items = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(size, titles.length); i++) {
            items.add(new FeedRecommendation(
                    "post-" + (100 + i), titles[i],
                    "这是一篇关于" + titles[i] + "的推荐文章",
                    "author-" + i, 0.9 - i * 0.1, reasons[i]
            ));
        }
        return Mono.just(items);
    }

    private Mono<List<FeedRecommendation>> generateMockRelated(String postId, int topK) {
        return Mono.just(List.of(
                new FeedRecommendation("post-rel-1", "相关文章推荐 1", "与上文相关的深度分析", "author-a", 0.85, "内容相似"),
                new FeedRecommendation("post-rel-2", "相关文章推荐 2", "延伸阅读推荐", "author-b", 0.75, "标签匹配"),
                new FeedRecommendation("post-rel-3", "相关文章推荐 3", "同作者其他文章", "author-c", 0.70, "同作者")
        ).stream().limit(topK).collect(Collectors.toList()));
    }

    private Mono<List<TrendingPost>> generateMockTrending(int limit) {
        String[] titles = {"2024技术趋势", "Go vs Rust对比", "云原生架构",
                "AI应用开发", "低代码平台评测", "分布式系统设计"};
        List<TrendingPost> items = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(limit, titles.length); i++) {
            items.add(new TrendingPost(
                    "trending-" + i, titles[i],
                    10000 - i * 1500L, 500 - i * 80L,
                    0.95 - i * 0.08, i < 3 ? "up" : "stable"
            ));
        }
        return Mono.just(items);
    }

    private Mono<Void> cacheFeed(String key, List<FeedRecommendation> items) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String json = mapper.writeValueAsString(items);
            return redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(10)).then();
        } catch (Exception e) {
            return Mono.empty();
        }
    }
}
