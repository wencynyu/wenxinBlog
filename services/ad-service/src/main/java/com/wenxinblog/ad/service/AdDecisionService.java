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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdDecisionService {

    private final AdCampaignRepository campaignRepo;
    private final AdCreativeRepository creativeRepo;
    private final AdEventRepository eventRepo;
    private final AdPositionRepository positionRepo;
    private final ReactiveStringRedisTemplate redis;
    private final AdEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public Flux<AdDecisionResponse> decide(AdDecisionRequest request) {
        log.info("Ad decision: positionType={}, userId={}, count={}", request.positionType(), request.userId(), request.count());

        return campaignRepo.findByStatus("ACTIVE")
                .filter(campaign -> hasBudget(campaign))
                .filter(campaign -> isWithinDateRange(campaign))
                .filter(campaign -> matchesTargeting(campaign, request))
                .flatMap(campaign -> creativeRepo.findByCampaignIdAndIsActiveTrue(campaign.getId())
                        .collectList()
                        .flatMap(creatives -> creatives.isEmpty()
                                ? Mono.empty()
                                // 多创意随机轮播（无状态）；单创意时等价于原来的 .next()
                                : Mono.just(creatives.get(ThreadLocalRandom.current().nextInt(creatives.size()))))
                        .map(creative -> toDecisionResponse(creative, campaign)))
                .sort(Comparator.comparing(AdDecisionResponse::bidAmount).reversed())
                .take(request.count())
                // concatMap（而非 flatMap）：频控/计费是异步内层，flatMap 会按完成顺序乱序合并，
                // 破坏前面 sort 的出价排序；concatMap 按序逐个执行，保住「出价高者在前」
                .concatMap(response -> checkFrequencyCap(request.userId(), response.campaignId())
                        .flatMap(allowed -> allowed
                                ? recordImpression(request, response).thenReturn(response)
                                : Mono.empty()));
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

    /** 定向：目前支持 positions（如 {"positions":["FEED","SIDEBAR"]}），为空/缺省 = 不限投所有广告位。
     *  解析失败按不限定处理（fail-open），避免配置写错导致 campaign 全量下线。 */
    private boolean matchesTargeting(AdCampaign campaign, AdDecisionRequest request) {
        String targeting = campaign.getTargeting();
        if (targeting == null || targeting.isBlank() || targeting.equals("{}")) return true;
        try {
            JsonNode positions = objectMapper.readTree(targeting).path("positions");
            if (!positions.isArray() || positions.isEmpty()) return true;
            for (JsonNode position : positions) {
                if (position.asText().equalsIgnoreCase(request.positionType())) return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to parse targeting for campaign {}, treating as untargeted: {}",
                    campaign.getId(), e.getMessage());
            return true;
        }
    }

    /** 频控（每用户每 campaign ≤5 次/小时）。必须全程响应式：
     *  旧实现在此处 .block() 会触发 Reactor 的线程校验异常，导致线上永远 fail-open。 */
    private Mono<Boolean> checkFrequencyCap(String userId, Long campaignId) {
        if (userId == null) return Mono.just(true);
        String key = String.format("ad:freq:%s:%d", userId, campaignId);
        return redis.opsForValue().increment(key)
                .delayUntil(count -> count != null && count == 1
                        ? redis.expire(key, Duration.ofHours(1))
                        : Mono.empty())
                .map(count -> count == null || count <= 5)
                .onErrorResume(e -> {
                    log.warn("Frequency cap check failed: {}", e.getMessage());
                    return Mono.just(true);
                });
    }

    private Mono<Void> recordImpression(AdDecisionRequest request, AdDecisionResponse response) {
        return campaignRepo.findById(response.campaignId())
                .flatMap(campaign -> {
                    BigDecimal cost = costPerImpression(campaign);
                    return campaignRepo.debitSpend(campaign.getId(), cost)
                            .flatMap(rows -> {
                                if (rows == 0) {
                                    // 预算不足，条件更新未生效：不计费、不记 impression 事件
                                    log.warn("Impression not billed, budget exhausted: campaignId={}, cost={}",
                                            campaign.getId(), cost);
                                    return Mono.empty();
                                }
                                AdEvent event = AdEvent.builder()
                                        .campaignId(response.campaignId())
                                        .creativeId(response.creativeId())
                                        .userId(request.userId())
                                        .eventType("IMPRESSION")
                                        .ipAddress(request.ipAddress())
                                        .userAgent(request.userAgent())
                                        .createdAt(LocalDateTime.now())
                                        .build();
                                // 计费成功才发 IMPRESSION 到 Kafka（analytics 出报表依赖此事件）
                                return eventRepo.save(event)
                                        .doOnSuccess(saved -> eventPublisher.publish(
                                                "IMPRESSION", response.campaignId(), response.creativeId(),
                                                request.userId(), request.positionType(), event.getCreatedAt()))
                                        .then();
                            });
                });
    }

    private BigDecimal costPerImpression(AdCampaign campaign) {
        BigDecimal bid = campaign.getBidAmount();
        if (bid == null) return BigDecimal.ZERO;
        // CPM 按千次曝光计费，单次 impression 成本 = bid_amount / 1000
        if ("CPM".equalsIgnoreCase(campaign.getBidStrategy())) {
            return bid.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        }
        return bid;
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
