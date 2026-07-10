package com.wenxinblog.ad.repository;

import com.wenxinblog.ad.entity.AdEvent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface AdEventRepository extends ReactiveCrudRepository<AdEvent, Long> {
    @Query("SELECT COUNT(*) FROM ad_events WHERE campaign_id = :campaignId AND event_type = :eventType")
    Mono<Long> countByCampaignIdAndEventType(Long campaignId, String eventType);

    @Query("SELECT COALESCE(SUM(CASE WHEN event_type = 'IMPRESSION' THEN 1 ELSE 0 END), 0) as impressions, " +
            "COALESCE(SUM(CASE WHEN event_type = 'CLICK' THEN 1 ELSE 0 END), 0) as clicks, " +
            "COALESCE(SUM(CASE WHEN event_type = 'CONVERSION' THEN 1 ELSE 0 END), 0) as conversions " +
            "FROM ad_events WHERE campaign_id = :campaignId")
    Mono<CampaignMetrics> getMetrics(Long campaignId);

    interface CampaignMetrics {
        long getImpressions();
        long getClicks();
        long getConversions();
    }
}
