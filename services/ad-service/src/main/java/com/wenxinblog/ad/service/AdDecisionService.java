package com.wenxinblog.ad.service;

import com.wenxinblog.ad.dto.AdDecisionRequest;
import com.wenxinblog.ad.dto.AdDecisionResponse;
import com.wenxinblog.ad.entity.AdCampaign;
import com.wenxinblog.ad.entity.AdCreative;
import com.wenxinblog.ad.entity.AdEvent;
import com.wenxinblog.ad.repository.AdCampaignRepository;
import com.wenxinblog.ad.repository.AdCreativeRepository;
import com.wenxinblog.ad.repository.AdEventRepository;
import com.wenxinblog.ad.repository.AdPositionRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdDecisionService {

    private final AdCampaignRepository campaignRepo;
    private final AdCreativeRepository creativeRepo;
    private final AdEventRepository eventRepo;
    private final AdPositionRepository positionRepo;
    private final ReactiveStringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public Flux<AdDecisionResponse> decide(AdDecisionRequest request) {
        log.info("Ad decision: positionType={}, userId={}, count={}", request.positionType(), request.userId(), request.count());

        return campaignRepo.findByStatus("ACTIVE")
                .filter(campaign -> hasBudget(campaign))
                .filter(campaign -> isWithinDateRange(campaign))
                .filter(campaign -> matchesTargeting(campaign, request))
                .flatMap(campaign -> creativeRepo.findByCampaignIdAndIsActiveTrue(campaign.getId())
                        .next()
                        .map(creative -> toDecisionResponse(creative, campaign)))
                .sort(Comparator.comparing(AdDecisionResponse::bidAmount).reversed())
                .take(request.count())
                .flatMap(response -> checkFrequencyCap(request.userId(), response.campaignId())
                        ? recordImpression(request, response).thenReturn(response)
                        : Mono.empty());
    }

    private boolean hasBudget(AdCampaign campaign) {
        BigDecimal remaining = campaign.getBudget().subtract(campaign.getSpent());
        BigDecimal dailyRemaining = campaign.getDailyBudget() != null
                ? campaign.getDailyBudget().subtract(campaign.getDailySpent())
                : campaign.getBudget();
        return remaining.compareTo(BigDecimal.ZERO) > 0 && dailyRemaining.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isWithinDateRange(AdCampaign campaign) {
        LocalDateTime now = LocalDateTime.now();
        if (campaign.getStartDate() != null && now.isBefore(campaign.getStartDate())) return false;
        if (campaign.getEndDate() != null && now.isAfter(campaign.getEndDate())) return false;
        return true;
    }

    private boolean matchesTargeting(AdCampaign campaign, AdDecisionRequest request) {
        if (campaign.getTargeting() == null || campaign.getTargeting().equals("{}")) return true;
        // Mock: always pass targeting for now
        return true;
    }

    private boolean checkFrequencyCap(String userId, Long campaignId) {
        if (userId == null) return true;
        String key = String.format("ad:freq:%s:%d", userId, campaignId);
        try {
            Long count = redis.opsForValue().increment(key).block(Duration.ofSeconds(1));
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofHours(1)).subscribe();
            }
            return count == null || count <= 5;
        } catch (Exception e) {
            log.warn("Frequency cap check failed: {}", e.getMessage());
            return true;
        }
    }

    private Mono<Void> recordImpression(AdDecisionRequest request, AdDecisionResponse response) {
        AdEvent event = AdEvent.builder()
                .campaignId(response.campaignId())
                .creativeId(response.creativeId())
                .userId(request.userId())
                .eventType("IMPRESSION")
                .ipAddress(request.ipAddress())
                .userAgent(request.userAgent())
                .createdAt(LocalDateTime.now())
                .build();
        return eventRepo.save(event).then();
    }

    private AdDecisionResponse toDecisionResponse(AdCreative creative, AdCampaign campaign) {
        return new AdDecisionResponse(
                creative.getId(),
                campaign.getId(),
                creative.getTitle(),
                creative.getImageUrl(),
                creative.getLandingUrl(),
                creative.getCreativeType(),
                campaign.getBidAmount()
        );
    }
}
