package com.wenxinblog.recommendation.service;

import com.wenxinblog.recommendation.dto.FeedRecommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
public class MilvusService {

    // TODO: Integrate Milvus SDK when available
    // private final MilvusServiceClient milvusClient;

    public Mono<Void> initCollections() {
        log.info("Milvus collections initialization (mock - SDK not yet integrated)");
        // TODO: Create blog_embeddings and user_embeddings collections
        // Dimension: 768, Index: IVF_FLAT
        return Mono.empty();
    }

    public Flux<FeedRecommendation> searchSimilarPosts(String postId, int topK) {
        log.info("Searching similar posts for postId={}, topK={} (mock)", postId, topK);
        // TODO: Actual Milvus vector search
        return Flux.empty();
    }

    public Flux<FeedRecommendation> searchByUserInterest(String userId, int topK) {
        log.info("Searching by user interest for userId={}, topK={} (mock)", userId, topK);
        // TODO: Build user embedding from interest tags, search Milvus
        return Flux.empty();
    }

    public Mono<Void> insertPostEmbedding(String postId, float[] vector, Map<String, Object> metadata) {
        log.info("Inserting post embedding for postId={} (mock)", postId);
        // TODO: Milvus insert
        return Mono.empty();
    }

    public Mono<Void> insertUserEmbedding(String userId, float[] vector) {
        log.info("Inserting user embedding for userId={} (mock)", userId);
        // TODO: Milvus insert
        return Mono.empty();
    }
}
