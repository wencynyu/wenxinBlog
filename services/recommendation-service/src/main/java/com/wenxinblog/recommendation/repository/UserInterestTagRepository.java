package com.wenxinblog.recommendation.repository;

import com.wenxinblog.recommendation.entity.UserInterestTag;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserInterestTagRepository extends ReactiveCrudRepository<UserInterestTag, Long> {
    Flux<UserInterestTag> findByUserId(String userId);
    Mono<Void> deleteByUserId(String userId);
}
