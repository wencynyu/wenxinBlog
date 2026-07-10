package com.wenxinblog.recommendation.repository;

import com.wenxinblog.recommendation.entity.RecommendationConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RecommendationConfigRepository extends ReactiveCrudRepository<RecommendationConfig, Long> {
    Mono<RecommendationConfig> findByUserId(String userId);
}
