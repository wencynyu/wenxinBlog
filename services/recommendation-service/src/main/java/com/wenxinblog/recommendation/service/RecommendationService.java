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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

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
        // Phase 3：优先用 user_embeddings 里持久化的用户向量；缺失则按兴趣标签加权聚合一次并写入；
        // 都没有（冷启动）或 embedding/Milvus 不可用 → 降级 trending。
        Mono<float[]> vecMono = milvusService.getUserVector(userId)
                .filter(v -> v.length > 0)
                .switchIfEmpty(Mono.defer(() -> recomputeUserVector(userId).filter(v -> v.length > 0)));
        return vecMono
                .flatMap(vec -> milvusService.searchByVector(vec, size))
                .flatMap(hits -> enrich(hits, true))  // 混合排序（相似+热度+新鲜）
                .filter(list -> !list.isEmpty())
                .switchIfEmpty(trendingAsFeed(size));
    }

    /** 公共入口：重算并持久化用户向量（行为事件/兴趣标签变更后由 consumer 或 interests 端点触发）。 */
    public Mono<float[]> refreshUserVector(String userId) {
        return recomputeUserVector(userId);
    }

    /**
     * 把用户交互过的帖子向量用 EMA 融入用户向量（item-CF lite）。
     * new_uv = normalize((1-α)·uv + α·pv)，α = clamp(weight, 0.05, 1)；uv 不存在则从 pv 起步。
     * 比纯标签聚合更细：直接捕捉交互过的帖子内容语义。
     */
    public Mono<Void> updateUserVectorWithPost(String userId, String postId, double weight) {
        double alpha = Math.min(1.0, Math.max(0.05, weight));
        return Mono.zip(
                        milvusService.getPostVector(postId).onErrorResume(e -> Mono.just(new float[0])),
                        milvusService.getUserVector(userId).onErrorResume(e -> Mono.just(new float[0])))
                .flatMap(tuple -> {
                    float[] pv = tuple.getT1();
                    float[] uv = tuple.getT2();
                    if (pv.length == 0) {
                        return Mono.<Void>empty(); // 帖子尚未嵌入
                    }
                    int dim = pv.length;
                    float[] newUser = new float[dim];
                    if (uv.length == 0) {
                        System.arraycopy(pv, 0, newUser, 0, dim);
                    } else {
                        for (int d = 0; d < dim; d++) {
                            newUser[d] = (float) (uv[d] * (1 - alpha) + pv[d] * alpha);
                        }
                    }
                    normalizeInPlace(newUser);
                    return milvusService.upsertUserVector(userId, newUser);
                })
                .onErrorResume(e -> {
                    log.warn("updateUserVectorWithPost failed for {}: {}", userId, e.getMessage());
                    return Mono.empty();
                });
    }

    private static void normalizeInPlace(float[] v) {
        double norm = 0;
        for (float f : v) {
            norm += f * f;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-12) {
            for (int d = 0; d < v.length; d++) {
                v[d] /= (float) norm;
            }
        }
    }

    /** 兴趣标签按 weight（来自用户行为权重）加权聚合 → 归一化 → 写入 user_embeddings，返回该向量。 */
    private Mono<float[]> recomputeUserVector(String userId) {
        return interestTagRepository.findByUserId(userId).collectList().flatMap(tags -> {
            if (tags.isEmpty()) {
                return Mono.just(new float[0]);
            }
            List<String> names = tags.stream().map(UserInterestTag::getTag).toList();
            return embeddingClient.embedBatch(names).flatMap(vectors -> {
                if (vectors.isEmpty()) {
                    return Mono.just(new float[0]);
                }
                int dim = vectors.get(0).length;
                float[] acc = new float[dim];
                double totalW = 0;
                for (int i = 0; i < tags.size() && i < vectors.size(); i++) {
                    double w = tags.get(i).getWeight() != null ? tags.get(i).getWeight() : 1.0;
                    float[] vec = vectors.get(i);
                    for (int d = 0; d < dim; d++) {
                        acc[d] += vec[d] * (float) w;
                    }
                    totalW += w;
                }
                if (totalW > 0) {
                    for (int d = 0; d < dim; d++) {
                        acc[d] /= (float) totalW;
                    }
                }
                double norm = 0;
                for (float f : acc) {
                    norm += f * f;
                }
                norm = Math.sqrt(norm);
                if (norm > 1e-12) {
                    for (int d = 0; d < dim; d++) {
                        acc[d] /= (float) norm;
                    }
                }
                float[] finalVec = acc;
                return milvusService.upsertUserVector(userId, finalVec).thenReturn(finalVec);
            });
        });
    }

    // ============ 图文混合：以封面图找相关博文（VL 模型，图像/文本同空间）============
    public Mono<List<FeedRecommendation>> getRelatedByImage(String postId, int topK) {
        final UUID id;
        try {
            id = UUID.fromString(postId);
        } catch (IllegalArgumentException e) {
            return trendingAsFeed(topK);
        }
        // 取帖子封面图 → 嵌图 → 检索 blog_embeddings（文本向量）→ 丰富。
        // 无封面图/帖不存在 → 降级 trending。
        return postReadRepository.findById(id)
                .filter(p -> p.getCoverImage() != null && !p.getCoverImage().isBlank())
                .flatMap(p -> embeddingClient.embedImage(p.getCoverImage())
                        .flatMap(vec -> vec.length == 0
                                ? Mono.just(List.<MilvusService.SearchHit>of())
                                : milvusService.searchByVector(vec, topK))
                        .flatMap(hits -> enrich(hits, false)))
                .filter(list -> !list.isEmpty())
                .switchIfEmpty(trendingAsFeed(topK));
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
                .flatMap(hits -> enrich(hits, false))
                .filter(list -> !list.isEmpty())
                .switchIfEmpty(trendingAsFeed(topK));
    }

    /**
     * Milvus 命中 → 批量取 post 详情 + 标签 → 组装 FeedRecommendation（score=相似度）。
     * hybrid=true 时按"相似+热度+新鲜"混合分重排（用于推荐流）；相关博文用 pure 相似度（false）。
     */
    private Mono<List<FeedRecommendation>> enrich(List<MilvusService.SearchHit> hits, boolean hybrid) {
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
                    if (hybrid && result.size() > 1) {
                        sortByHybrid(result);
                    }
                    return result;
                });
    }

    /** 混合分 = 0.6×相似 + 0.3×热度(归一) + 0.1×新鲜度。原地重排；score 字段保持相似度不变。 */
    private void sortByHybrid(List<FeedRecommendation> items) {
        double maxPop = items.stream()
                .mapToDouble(x -> x.likeCount() + x.commentCount() * 2.0)
                .max().orElse(1.0);
        long nowMs = System.currentTimeMillis();
        items.sort(Comparator.comparingDouble((FeedRecommendation x) -> {
            double sim = x.score();
            double pop = (x.likeCount() + x.commentCount() * 2.0) / maxPop;
            double ageDays = 999;
            if (x.createdAt() != null) {
                long createdMs = x.createdAt().toEpochSecond(ZoneOffset.UTC) * 1000L;
                ageDays = Math.max(0, (nowMs - createdMs) / 86400000.0);
            }
            double fresh = 1.0 / (1.0 + ageDays);
            return 0.6 * sim + 0.3 * pop + 0.1 * fresh;
        }).reversed());
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
                .collectList()
                // 标签变更后异步重算用户向量，刷新 user_embeddings（best-effort，失败不影响主流程）
                .doOnNext(saved -> {
                    try {
                        recomputeUserVector(userId)
                                .doOnError(e -> log.warn("recomputeUserVector failed for {}: {}", userId, e.getMessage()))
                                .onErrorResume(e -> Mono.empty())
                                .subscribe();
                    } catch (Exception e) {
                        log.warn("recomputeUserVector trigger failed for {}: {}", userId, e.getMessage());
                    }
                });
    }

    /**
     * 用户行为反馈（前端 view/like/comment/share）：把行为 + 帖子标签发到 user-behavior-events，
     * BehaviorEventConsumer 据此更新兴趣标签并刷新用户向量；同时失效该用户的推荐流缓存。
     */
    public Mono<Void> recordFeedback(String userId, String postId, String action) {
        log.info("Recording feedback: userId={}, postId={}, action={}", userId, postId, action);
        String eventType = (action == null || action.endsWith("_post")) ? action : action + "_post";
        Mono<Void> produce = produceBehaviorEvent(userId, eventType, postId);
        Mono<Void> invalidate = redisTemplate.keys("recommend:feed:" + userId + ":*")
                .flatMap(redisTemplate::delete).then();
        return Mono.when(produce, invalidate);
    }

    private Mono<Void> produceBehaviorEvent(String userId, String eventType, String postId) {
        final UUID pid;
        try {
            pid = UUID.fromString(postId);
        } catch (IllegalArgumentException e) {
            return Mono.empty();
        }
        // defer：让 findTagsForPosts/kafkaTemplate 的异常在订阅期抛出，被 onErrorResume 兜住（best-effort）
        return Mono.defer(() -> postReadRepository.findTagsForPosts(List.of(pid))
                .map(tagMap -> tagMap.getOrDefault(pid, List.of()))
                .flatMap(tags -> Mono.fromRunnable(() -> {
                    try {
                        String json = MAPPER.writeValueAsString(
                                Map.of("eventType", eventType == null ? "view_post" : eventType,
                                        "userId", userId, "postId", pid.toString(), "tags", tags));
                        kafkaTemplate.send("user-behavior-events", userId, json)
                                .whenComplete((r, ex) -> {
                                    if (ex != null) {
                                        log.warn("produce behavior event failed: {}", ex.getMessage());
                                    }
                                });
                    } catch (Exception e) {
                        log.warn("serialize behavior event failed: {}", e.getMessage());
                    }
                }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).then()))
                .onErrorResume(e -> {
                    log.warn("produce behavior event failed: {}", e.getMessage());
                    return Mono.empty();
                });
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
