package com.wenxinblog.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wenxinblog.recommendation.client.EmbeddingClient;
import com.wenxinblog.recommendation.dto.AuthorDto;
import com.wenxinblog.recommendation.dto.FeedRecommendation;
import com.wenxinblog.recommendation.dto.TrendingPost;
import com.wenxinblog.recommendation.entity.Post;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.repository.PostReadRepository;
import com.wenxinblog.recommendation.repository.UserInterestTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final MilvusService milvusService;
    private final EmbeddingClient embeddingClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserInterestTagRepository interestTagRepository;
    private final PostReadRepository postReadRepository;

    // Redis 缓存 JSON 序列化用（带 JavaTime）；不注入 Spring 的 ObjectMapper，避免多 bean 冲突
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    // ============ 推荐流（内容相似） ============
    public Mono<List<FeedRecommendation>> getFeedRecommendations(String userId, int page, int size) {
        String cacheKey = String.format("recommend:feed:%s:%d", userId, page);
        return cacheGetRaw(cacheKey)
                .flatMap(json -> readJson(json, new TypeReference<List<FeedRecommendation>>() {}))
                .switchIfEmpty(Mono.defer(() -> computeFeed(userId, size)
                        .flatMap(items -> cachePut(cacheKey, items, Duration.ofMinutes(10)).thenReturn(items))));
    }

    private Mono<List<FeedRecommendation>> computeFeed(String userId, int size) {
        // 用户兴趣标签 → 拼成一段文本 → embedding → Milvus ANN → 丰富。
        // 无兴趣标签（冷启动）或 embedding/Milvus 不可用 → 降级 trending。
        return interestTagRepository.findByUserId(userId)
                .map(UserInterestTag::getTag)
                .collectList()
                .flatMap(tags -> tags.isEmpty()
                        ? trendingAsFeed(size)
                        : embeddingClient.embed(String.join(" ", tags))
                                .flatMap(vec -> vec.length == 0
                                        ? Mono.just(List.<MilvusService.SearchHit>of())
                                        : milvusService.searchByVector(vec, size))
                                .flatMap(this::enrich)
                                .filter(list -> !list.isEmpty())
                                .switchIfEmpty(trendingAsFeed(size)));
    }

    // ============ 相关博文（内容相似） ============
    public Mono<List<FeedRecommendation>> getRelatedPosts(String postId, int topK) {
        final UUID id;
        try {
            id = UUID.fromString(postId);
        } catch (IllegalArgumentException e) {
            return trendingAsFeed(topK);
        }
        // 取该帖文本 → embedding → Milvus 搜索 → 去掉自己 → 丰富。帖不存在/空结果 → 降级 trending。
        return postReadRepository.findById(id)
                .flatMap(post -> embeddingClient.embed(embeddingText(post))
                        .flatMap(vec -> vec.length == 0
                                ? Mono.just(List.<MilvusService.SearchHit>of())
                                : milvusService.searchByVector(vec, topK + 1)
                                        .map(hits -> hits.stream()
                                                .filter(h -> !postId.equals(h.postId()))
                                                .limit(topK)
                                                .toList())))
                .flatMap(this::enrich)
                .filter(list -> !list.isEmpty())
                .switchIfEmpty(trendingAsFeed(topK));
    }

    /** Milvus 命中 → 批量取 post 详情 + 标签 → 组装 FeedRecommendation（带相似度 score）。 */
    private Mono<List<FeedRecommendation>> enrich(List<MilvusService.SearchHit> hits) {
        if (hits.isEmpty()) {
            return Mono.just(List.of());
        }
        List<UUID> ids = hits.stream().map(h -> UUID.fromString(h.postId())).distinct().toList();
        return Mono.zip(
                        postReadRepository.findByIds(ids).collectList(),
                        postReadRepository.findTagsForPosts(ids))
                .map(t -> {
                    Map<UUID, Post> byId = t.getT1().stream()
                            .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));
                    Map<UUID, List<String>> tagMap = t.getT2();
                    List<FeedRecommendation> result = new ArrayList<>();
                    for (MilvusService.SearchHit h : hits) {
                        UUID pid = UUID.fromString(h.postId());
                        Post p = byId.get(pid);
                        if (p != null) {
                            result.add(FeedRecommendation.from(p, tagMap.getOrDefault(pid, List.of()), h.score()));
                        }
                    }
                    return result;
                });
    }

    /** 把 trending 结果映射成 FeedRecommendation（冷启动/降级用；score=0）。 */
    private Mono<List<FeedRecommendation>> trendingAsFeed(int size) {
        return getTrendingPosts(size).map(list -> list.stream().map(tp -> new FeedRecommendation(
                tp.id(), tp.title(), null, null,
                tp.author() != null ? tp.author().id() : null,
                tp.author(), List.of(),
                (int) Math.min(tp.likeCount(), Integer.MAX_VALUE),
                (int) Math.min(tp.commentCount(), Integer.MAX_VALUE),
                0.0,
                tp.createdAt())).toList());
    }

    /** 嵌入用文本：标题 + 摘要（无摘要则截取正文前 500 字）。 */
    private String embeddingText(Post p) {
        StringBuilder sb = new StringBuilder();
        if (p.getTitle() != null) {
            sb.append(p.getTitle());
        }
        if (p.getSummary() != null && !p.getSummary().isBlank()) {
            sb.append(' ').append(p.getSummary());
        } else if (p.getContent() != null) {
            sb.append(' ').append(p.getContent(), 0, Math.min(p.getContent().length(), 500));
        }
        return sb.toString();
    }

    // ============ Backfill：把已有已发布帖子批量嵌入 Milvus（启动后/手动触发） ============
    public Mono<Integer> backfill(int limit) {
        return postReadRepository.findAllPublished(limit)
                .flatMap(post -> embeddingClient.embed(embeddingText(post))
                        .flatMap(vec -> vec.length == 0
                                ? Mono.just(false)
                                : milvusService.upsertPost(
                                        post.getId().toString(),
                                        post.getAuthorId() != null ? post.getAuthorId().toString() : "",
                                        post.getTitle(), vec)
                                        .thenReturn(true))
                        .onErrorResume(e -> {
                            log.warn("backfill embed failed for {}: {}", post.getId(), e.getMessage());
                            return Mono.just(false);
                        }))
                .collectList()
                .map(list -> (int) list.stream().filter(Boolean::booleanValue).count());
    }

    // ============ 热门（blog_db 真实信号 × 时间衰减） ============
    public Mono<List<TrendingPost>> getTrendingPosts(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String cacheKey = "recommend:trending:" + safeLimit;
        return cacheGetRaw(cacheKey)
                .flatMap(json -> readJson(json, new TypeReference<List<TrendingPost>>() {}))
                .switchIfEmpty(Mono.defer(() -> postReadRepository.findTrending(safeLimit)
                        .map(this::toTrendingPost)
                        .collectList()
                        .flatMap(items -> cachePut(cacheKey, items, Duration.ofMinutes(10)).thenReturn(items))));
    }

    private TrendingPost toTrendingPost(Post p) {
        AuthorDto author = new AuthorDto(
                p.getAuthorId() != null ? p.getAuthorId().toString() : null,
                p.getAuthorUsername(),
                p.getAuthorDisplayName(),
                p.getAuthorAvatarUrl());
        return new TrendingPost(
                p.getId().toString(),
                p.getTitle(),
                p.getViewCount(),
                p.getLikeCount(),
                p.getCommentCount(),
                author,
                p.getCreatedAt());
    }

    // ============ 用户推荐 / 兴趣 / 反馈 ============
    public Mono<List<String>> getUserRecommendations(String userId, int limit) {
        // TODO Phase 3: user-to-user 协同过滤
        return Mono.just(List.<String>of());
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
        // 失效该用户的推荐流缓存
        String pattern = "recommend:feed:" + userId + ":*";
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then();
    }

    // ---- Redis 缓存小工具 ----
    private Mono<String> cacheGetRaw(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    private Mono<Void> cachePut(String key, Object value, Duration ttl) {
        try {
            return redisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(value), ttl).then();
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    private <T> Mono<T> readJson(String json, TypeReference<T> type) {
        try {
            return Mono.just(MAPPER.readValue(json, type));
        } catch (Exception e) {
            return Mono.empty();
        }
    }
}
