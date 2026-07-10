package com.wenxinblog.ad.repository;

import com.wenxinblog.ad.entity.AdCampaign;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AdCampaignRepository extends ReactiveCrudRepository<AdCampaign, Long> {
    Flux<AdCampaign> findByStatus(String status);
    Flux<AdCampaign> findByAdvertiserIdAndStatus(String advertiserId, String status);
    Mono<AdCampaign> findByIdAndStatus(Long id, String status);

    @Query("UPDATE ad_campaigns SET daily_spent = 0, updated_at = NOW() WHERE status = 'ACTIVE'")
    Mono<Void> resetDailySpent();
}
