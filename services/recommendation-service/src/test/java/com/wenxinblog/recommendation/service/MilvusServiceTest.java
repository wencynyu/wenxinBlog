package com.wenxinblog.recommendation.service;

import com.wenxinblog.recommendation.dto.FeedRecommendation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MilvusServiceTest {

    @InjectMocks
    private MilvusService milvusService;

    @Test
    void initCollections_ShouldReturnEmptyMono() {
        // When
        Mono<Void> result = milvusService.initCollections();

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void searchSimilarPosts_ShouldReturnEmptyFlux() {
        // When
        Flux<FeedRecommendation> result = milvusService.searchSimilarPosts("post-123", 10);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void searchSimilarPosts_WithDifferentTopK_ShouldReturnEmptyFlux() {
        // When
        Flux<FeedRecommendation> result = milvusService.searchSimilarPosts("post-456", 5);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void searchByUserInterest_ShouldReturnEmptyFlux() {
        // When
        Flux<FeedRecommendation> result = milvusService.searchByUserInterest("user-123", 10);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void searchByUserInterest_WithDifferentTopK_ShouldReturnEmptyFlux() {
        // When
        Flux<FeedRecommendation> result = milvusService.searchByUserInterest("user-456", 20);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void insertPostEmbedding_ShouldReturnEmptyMono() {
        // Given
        float[] vector = {0.1f, 0.2f, 0.3f};
        Map<String, Object> metadata = Map.of("title", "Test Post");

        // When
        Mono<Void> result = milvusService.insertPostEmbedding("post-123", vector, metadata);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void insertPostEmbedding_WithEmptyVector_ShouldReturnEmptyMono() {
        // Given
        float[] vector = {};
        Map<String, Object> metadata = Map.of();

        // When
        Mono<Void> result = milvusService.insertPostEmbedding("post-456", vector, metadata);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void insertUserEmbedding_ShouldReturnEmptyMono() {
        // Given
        float[] vector = {0.5f, 0.6f, 0.7f};

        // When
        Mono<Void> result = milvusService.insertUserEmbedding("user-123", vector);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void insertUserEmbedding_WithLargeVector_ShouldReturnEmptyMono() {
        // Given
        float[] vector = new float[768]; // Simulating BERT embedding size
        for (int i = 0; i < vector.length; i++) {
            vector[i] = 0.1f;
        }

        // When
        Mono<Void> result = milvusService.insertUserEmbedding("user-456", vector);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void allMethods_ShouldNotThrowExceptions() {
        // This test ensures none of the methods throw exceptions during normal operation
        assertDoesNotThrow(() -> milvusService.initCollections().block());
        assertDoesNotThrow(() -> milvusService.searchSimilarPosts("post-1", 10).collectList().block());
        assertDoesNotThrow(() -> milvusService.searchByUserInterest("user-1", 10).collectList().block());
        assertDoesNotThrow(() -> milvusService.insertPostEmbedding("post-1", new float[]{1f}, Map.of()).block());
        assertDoesNotThrow(() -> milvusService.insertUserEmbedding("user-1", new float[]{1f}).block());
    }

    private void assertDoesNotThrow(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception, but got: " + e.getMessage(), e);
        }
    }
}
