package com.wenxinblog.ad.service;

import com.wenxinblog.ad.dto.CampaignRequest;
import com.wenxinblog.ad.dto.CampaignStats;
import com.wenxinblog.ad.entity.AdCampaign;
import com.wenxinblog.ad.repository.AdCampaignRepository;
import com.wenxinblog.ad.repository.AdEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdCampaignService {

    private final AdCampaignRepository campaignRepo;
    private final AdEventRepository eventRepo;

    public Mono<AdCampaign> createCampaign(String advertiserId, CampaignRequest req) {
        AdCampaign campaign = AdCampaign.builder()
                .advertiserId(advertiserId)
                .name(req.name())
                .description(req.description())
                .budget(req.budget())
                .dailyBudget(req.dailyBudget())
                .bidStrategy(req.bidStrategy() != null ? req.bidStrategy() : "CPM")
                .bidAmount(req.bidAmount())
                .targeting(req.targeting() != null ? req.targeting() : "{}")
                .status("DRAFT")
                .startDate(req.startDate())
                .endDate(req.endDate())
                .spent(BigDecimal.ZERO)
                .dailySpent(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return campaignRepo.save(campaign);
    }

    public Mono<AdCampaign> updateCampaign(String advertiserId, Long id, CampaignRequest req) {
        return campaignRepo.findById(id)
                .filter(c -> advertiserId != null && c.getAdvertiserId().equals(advertiserId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner")))
                .flatMap(campaign -> {
                    if (req.name() != null) campaign.setName(req.name());
                    if (req.description() != null) campaign.setDescription(req.description());
                    if (req.budget() != null) campaign.setBudget(req.budget());
                    if (req.dailyBudget() != null) campaign.setDailyBudget(req.dailyBudget());
                    if (req.bidStrategy() != null) campaign.setBidStrategy(req.bidStrategy());
                    if (req.bidAmount() != null) campaign.setBidAmount(req.bidAmount());
                    if (req.targeting() != null) campaign.setTargeting(req.targeting());
                    if (req.startDate() != null) campaign.setStartDate(req.startDate());
                    if (req.endDate() != null) campaign.setEndDate(req.endDate());
                    campaign.setUpdatedAt(LocalDateTime.now());
                    return campaignRepo.save(campaign);
                });
    }

    public Mono<AdCampaign> getCampaign(String advertiserId, Long id) {
        return campaignRepo.findById(id)
                .filter(c -> advertiserId != null && c.getAdvertiserId().equals(advertiserId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner")));
    }

    public Flux<AdCampaign> listCampaigns(String advertiserId, String status) {
        if (advertiserId != null && status != null) {
            return campaignRepo.findByAdvertiserIdAndStatus(advertiserId, status);
        } else if (status != null) {
            return campaignRepo.findByStatus(status);
        }
        return campaignRepo.findAll();
    }

    public Mono<AdCampaign> pauseCampaign(String advertiserId, Long id) {
        return campaignRepo.findById(id)
                .filter(c -> advertiserId != null && c.getAdvertiserId().equals(advertiserId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner")))
                .flatMap(campaign -> {
                    campaign.setStatus("PAUSED");
                    campaign.setUpdatedAt(LocalDateTime.now());
                    return campaignRepo.save(campaign);
                });
    }

    public Mono<AdCampaign> activateCampaign(String advertiserId, Long id) {
        return campaignRepo.findById(id)
                .filter(c -> advertiserId != null && c.getAdvertiserId().equals(advertiserId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner")))
                .flatMap(campaign -> {
                    if (campaign.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.error(new IllegalArgumentException("Insufficient budget"));
                    }
                    campaign.setStatus("ACTIVE");
                    campaign.setDailySpent(BigDecimal.ZERO);
                    campaign.setUpdatedAt(LocalDateTime.now());
                    return campaignRepo.save(campaign);
                });
    }

    public Mono<CampaignStats> getCampaignStats(String advertiserId, Long id) {
        return campaignRepo.findById(id)
                .filter(c -> advertiserId != null && c.getAdvertiserId().equals(advertiserId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner")))
                .flatMap(campaign -> eventRepo.getMetrics(id)
                        .map(metrics -> new CampaignStats(
                                metrics.getImpressions(),
                                metrics.getClicks(),
                                metrics.getConversions(),
                                metrics.getImpressions() > 0
                                        ? (double) metrics.getClicks() / metrics.getImpressions() * 100
                                        : 0.0,
                                campaign.getSpent(),
                                campaign.getBudget().subtract(campaign.getSpent())
                        )));
    }

    /** 每日 00:05 重置所有 ACTIVE 活动的 daily_spent，开启新一轮每日预算计费。 */
    @Scheduled(cron = "0 5 0 * * *")
    public void scheduledResetDailySpent() {
        campaignRepo.resetDailySpent()
                .doOnNext(count -> log.info("Reset daily_spent to 0 for {} active campaign(s)", count))
                .doOnError(e -> log.error("Failed to reset daily_spent for active campaigns", e))
                .subscribe();
    }
}
