package com.wenxinblog.ad.repository;

import com.wenxinblog.ad.entity.AdPosition;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface AdPositionRepository extends ReactiveCrudRepository<AdPosition, Long> {
    Flux<AdPosition> findByPositionTypeAndIsActiveTrue(String positionType);
    Flux<AdPosition> findAllByIsActiveTrue();
}
