package com.wenxinblog.ad.repository;

import com.wenxinblog.ad.entity.AdCampaign;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface AdCampaignRepository extends ReactiveCrudRepository<AdCampaign, Long> {
    Flux<AdCampaign> findByStatus(String status);
    Flux<AdCampaign> findByAdvertiserIdAndStatus(String advertiserId, String status);
    Mono<AdCampaign> findByIdAndStatus(Long id, String status);

    @Modifying
    @Query("UPDATE ad_campaigns SET daily_spent = 0, updated_at = NOW() WHERE status = 'ACTIVE'")
    Mono<Integer> resetDailySpent();

    /**
     * 原子扣减预算：仅当 spent + cost 与 daily_spent + cost 均不超过对应预算时更新，
     * 否则不更新（返回 0 行），由调用方判定计费失败。
     */
    @Modifying
    @Query("UPDATE ad_campaigns SET spent = spent + :cost, daily_spent = daily_spent + :cost, updated_at = NOW() " +
            "WHERE id = :id AND spent + :cost <= budget AND (daily_budget IS NULL OR daily_spent + :cost <= daily_budget)")
    Mono<Integer> debitSpend(@Param("id") Long id, @Param("cost") BigDecimal cost);
}
