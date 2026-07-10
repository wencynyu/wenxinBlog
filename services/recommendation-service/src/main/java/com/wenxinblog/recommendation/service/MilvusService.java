package com.wenxinblog.recommendation.service;

import com.wenxinblog.recommendation.dto.FeedRecommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 向量检索占位服务。
 *
 * <p>当前 Milvus SDK 尚未接入，所有方法均返回空（{@code Mono/Flux#empty()}）并打印 WARN 日志，
 * <b>不会</b>静默返回任何伪造数据。上游 {@link RecommendationService} 在结果为空时会显式标注
 * 返回的是演示数据。完整的向量检索实现（embedding 生成、向量入库、ANN 检索）见仓库
 * {@code docs/backend/recommendation-service.md} 中的待办项。
 */
@Slf4j
@Service
public class MilvusService {

    // TODO: Integrate Milvus SDK when available
    // private final MilvusServiceClient milvusClient;

    public Mono<Void> initCollections() {
        log.warn("initCollections: Milvus SDK not integrated (placeholder) — no collections created");
        // TODO: Create blog_embeddings and user_embeddings collections
        // Dimension: 768, Index: IVF_FLAT
        return Mono.empty();
    }

    public Flux<FeedRecommendation> searchSimilarPosts(String postId, int topK) {
        log.warn("searchSimilarPosts: Milvus SDK not integrated (placeholder) — returning empty for postId={}", postId);
        // TODO: Actual Milvus vector search
        return Flux.empty();
    }

    public Flux<FeedRecommendation> searchByUserInterest(String userId, int topK) {
        log.warn("searchByUserInterest: Milvus SDK not integrated (placeholder) — returning empty for userId={}", userId);
        // TODO: Build user embedding from interest tags, search Milvus
        return Flux.empty();
    }

    public Mono<Void> insertPostEmbedding(String postId, float[] vector, Map<String, Object> metadata) {
        log.warn("insertPostEmbedding: Milvus SDK not integrated (placeholder) — no-op for postId={}", postId);
        // TODO: Milvus insert
        return Mono.empty();
    }

    public Mono<Void> insertUserEmbedding(String userId, float[] vector) {
        log.warn("insertUserEmbedding: Milvus SDK not integrated (placeholder) — no-op for userId={}", userId);
        // TODO: Milvus insert
        return Mono.empty();
    }
}
