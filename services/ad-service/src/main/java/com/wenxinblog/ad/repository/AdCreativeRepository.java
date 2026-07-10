package com.wenxinblog.ad.repository;

import com.wenxinblog.ad.entity.AdCreative;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface AdCreativeRepository extends ReactiveCrudRepository<AdCreative, Long> {
    Flux<AdCreative> findByCampaignIdAndIsActiveTrue(Long campaignId);
}
