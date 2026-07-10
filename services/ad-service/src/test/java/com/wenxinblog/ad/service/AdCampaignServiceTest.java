package com.wenxinblog.ad.service;

import com.wenxinblog.ad.dto.CampaignRequest;
import com.wenxinblog.ad.dto.CampaignStats;
import com.wenxinblog.ad.entity.AdCampaign;
import com.wenxinblog.ad.repository.AdCampaignRepository;
import com.wenxinblog.ad.repository.AdEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdCampaignServiceTest {

    @Mock
    private AdCampaignRepository campaignRepo;

    @Mock
    private AdEventRepository eventRepo;

    private AdCampaignService campaignService;

    @BeforeEach
    void setUp() {
        campaignService = new AdCampaignService(campaignRepo, eventRepo);
    }

    @Test
    void createCampaign_ShouldSetDefaultValues() {
        String advertiserId = "advertiser123";
        CampaignRequest request = new CampaignRequest(
                "Test Campaign",
                "Description",
                new BigDecimal("1000"),
                new BigDecimal("100"),
                "CPC",
                new BigDecimal("0.50"),
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );

        AdCampaign savedCampaign = AdCampaign.builder()
                .id(1L)
                .advertiserId(advertiserId)
                .name("Test Campaign")
                .budget(new BigDecimal("1000"))
                .dailyBudget(new BigDecimal("100"))
                .bidStrategy("CPC")
                .bidAmount(new BigDecimal("0.50"))
                .targeting("{}")
                .status("DRAFT")
                .spent(BigDecimal.ZERO)
                .dailySpent(BigDecimal.ZERO)
                .build();

        when(campaignRepo.save(any(AdCampaign.class))).thenReturn(Mono.just(savedCampaign));

        StepVerifier.create(campaignService.createCampaign(advertiserId, request))
                .expectNextMatches(campaign ->
                        campaign.getAdvertiserId().equals(advertiserId) &&
                        campaign.getName().equals("Test Campaign") &&
                        campaign.getBidStrategy().equals("CPC") &&
                        campaign.getTargeting().equals("{}") &&
                        campaign.getStatus().equals("DRAFT") &&
                        campaign.getSpent().compareTo(BigDecimal.ZERO) == 0 &&
                        campaign.getDailySpent().compareTo(BigDecimal.ZERO) == 0)
                .verifyComplete();

        verify(campaignRepo).save(any(AdCampaign.class));
    }

    @Test
    void updateCampaign_WithAllFields_ShouldUpdateAllFields() {
        Long campaignId = 1L;
        CampaignRequest request = new CampaignRequest(
                "Updated Campaign",
                "Updated Description",
                new BigDecimal("2000"),
                new BigDecimal("200"),
                "CPM",
                new BigDecimal("1.00"),
                "{\"age\": \"18-35\"}",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(60)
        );

        AdCampaign existingCampaign = AdCampaign.builder()
                .id(campaignId)
                .advertiserId("advertiser123")
                .name("Old Campaign")
                .description("Old Description")
                .budget(new BigDecimal("1000"))
                .dailyBudget(new BigDecimal("100"))
                .bidStrategy("CPC")
                .bidAmount(new BigDecimal("0.50"))
                .targeting("{}")
                .status("DRAFT")
                .build();

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.just(existingCampaign));
        when(campaignRepo.save(any(AdCampaign.class))).thenReturn(Mono.just(existingCampaign));

        StepVerifier.create(campaignService.updateCampaign(campaignId, request))
                .expectNextMatches(campaign ->
                        campaign.getName().equals("Updated Campaign") &&
                        campaign.getDescription().equals("Updated Description") &&
                        campaign.getBudget().compareTo(new BigDecimal("2000")) == 0 &&
                        campaign.getDailyBudget().compareTo(new BigDecimal("200")) == 0 &&
                        campaign.getBidStrategy().equals("CPM") &&
                        campaign.getBidAmount().compareTo(new BigDecimal("1.00")) == 0 &&
                        campaign.getTargeting().equals("{\"age\": \"18-35\"}"))
                .verifyComplete();

        verify(campaignRepo).findById(campaignId);
        verify(campaignRepo).save(any(AdCampaign.class));
    }

    @Test
    void updateCampaign_WithPartialFields_ShouldUpdateOnlyProvidedFields() {
        Long campaignId = 1L;
        CampaignRequest request = new CampaignRequest(
                "Updated Name Only",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        AdCampaign existingCampaign = AdCampaign.builder()
                .id(campaignId)
                .name("Old Name")
                .description("Old Description")
                .budget(new BigDecimal("1000"))
                .build();

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.just(existingCampaign));
        when(campaignRepo.save(any(AdCampaign.class))).thenReturn(Mono.just(existingCampaign));

        StepVerifier.create(campaignService.updateCampaign(campaignId, request))
                .expectNextMatches(campaign ->
                        campaign.getName().equals("Updated Name Only") &&
                        campaign.getDescription().equals("Old Description"))
                .verifyComplete();

        verify(campaignRepo).findById(campaignId);
        verify(campaignRepo).save(any(AdCampaign.class));
    }

    @Test
    void updateCampaign_NotFound_ShouldReturnEmpty() {
        Long campaignId = 999L;
        CampaignRequest request = new CampaignRequest(
                "Updated Name",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.empty());

        StepVerifier.create(campaignService.updateCampaign(campaignId, request))
                .verifyComplete();

        verify(campaignRepo).findById(campaignId);
        verify(campaignRepo, never()).save(any());
    }

    @Test
    void getCampaign_ShouldReturnCampaign() {
        Long campaignId = 1L;
        AdCampaign campaign = AdCampaign.builder()
                .id(campaignId)
                .name("Test Campaign")
                .build();

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.just(campaign));

        StepVerifier.create(campaignService.getCampaign(campaignId))
                .expectNext(campaign)
                .verifyComplete();

        verify(campaignRepo).findById(campaignId);
    }

    @Test
    void listCampaigns_WithFilters_ShouldFilterByAdvertiserAndStatus() {
        String advertiserId = "advertiser123";
        String status = "ACTIVE";

        AdCampaign campaign1 = AdCampaign.builder().id(1L).advertiserId(advertiserId).status(status).build();
        AdCampaign campaign2 = AdCampaign.builder().id(2L).advertiserId(advertiserId).status(status).build();

        when(campaignRepo.findByAdvertiserIdAndStatus(advertiserId, status))
                .thenReturn(Flux.just(campaign1, campaign2));

        StepVerifier.create(campaignService.listCampaigns(advertiserId, status))
                .expectNext(campaign1)
                .expectNext(campaign2)
                .verifyComplete();

        verify(campaignRepo).findByAdvertiserIdAndStatus(advertiserId, status);
    }

    @Test
    void listCampaigns_WithOnlyStatus_ShouldFilterByStatus() {
        String status = "ACTIVE";

        AdCampaign campaign1 = AdCampaign.builder().id(1L).status(status).build();
        AdCampaign campaign2 = AdCampaign.builder().id(2L).status(status).build();

        when(campaignRepo.findByStatus(status)).thenReturn(Flux.just(campaign1, campaign2));

        StepVerifier.create(campaignService.listCampaigns(null, status))
                .expectNext(campaign1)
                .expectNext(campaign2)
                .verifyComplete();

        verify(campaignRepo).findByStatus(status);
    }

    @Test
    void listCampaigns_NoFilters_ShouldReturnAll() {
        AdCampaign campaign1 = AdCampaign.builder().id(1L).build();
        AdCampaign campaign2 = AdCampaign.builder().id(2L).build();

        when(campaignRepo.findAll()).thenReturn(Flux.just(campaign1, campaign2));

        StepVerifier.create(campaignService.listCampaigns(null, null))
                .expectNext(campaign1)
                .expectNext(campaign2)
                .verifyComplete();

        verify(campaignRepo).findAll();
    }

    @Test
    void pauseCampaign_ShouldSetStatusToPaused() {
        Long campaignId = 1L;
        AdCampaign campaign = AdCampaign.builder()
                .id(campaignId)
                .status("ACTIVE")
                .build();

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.just(campaign));
        when(campaignRepo.save(any(AdCampaign.class))).thenReturn(Mono.just(campaign));

        StepVerifier.create(campaignService.pauseCampaign(campaignId))
                .expectNextMatches(c -> c.getStatus().equals("PAUSED"))
                .verifyComplete();

        verify(campaignRepo).findById(campaignId);
        verify(campaignRepo).save(any(AdCampaign.class));
    }

    @Test
    void activateCampaign_WithValidBudget_ShouldSetActive() {
        Long campaignId = 1L;
        AdCampaign campaign = AdCampaign.builder()
                .id(campaignId)
                .status("PAUSED")
                .budget(new BigDecimal("1000"))
                .dailySpent(new BigDecimal("50"))
                .build();

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.just(campaign));
        when(campaignRepo.save(any(AdCampaign.class))).thenReturn(Mono.just(campaign));

        StepVerifier.create(campaignService.activateCampaign(campaignId))
                .expectNextMatches(c ->
                        c.getStatus().equals("ACTIVE") &&
                        c.getDailySpent().compareTo(BigDecimal.ZERO) == 0)
                .verifyComplete();

        verify(campaignRepo).findById(campaignId);
        verify(campaignRepo).save(any(AdCampaign.class));
    }

    @Test
    void activateCampaign_WithInsufficientBudget_ShouldThrowException() {
        Long campaignId = 1L;
        AdCampaign campaign = AdCampaign.builder()
                .id(campaignId)
                .status("PAUSED")
                .budget(BigDecimal.ZERO)
                .build();

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.just(campaign));

        StepVerifier.create(campaignService.activateCampaign(campaignId))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException &&
                        e.getMessage().equals("Insufficient budget"))
                .verify();

        verify(campaignRepo).findById(campaignId);
        verify(campaignRepo, never()).save(any());
    }

    @Test
    void getCampaignStats_ShouldReturnStatsWithCTR() {
        Long campaignId = 1L;
        AdCampaign campaign = AdCampaign.builder()
                .id(campaignId)
                .spent(new BigDecimal("100"))
                .budget(new BigDecimal("1000"))
                .build();

        AdEventRepository.CampaignMetrics metrics = new AdEventRepository.CampaignMetrics() {
            @Override
            public long getImpressions() { return 1000; }
            @Override
            public long getClicks() { return 50; }
            @Override
            public long getConversions() { return 5; }
        };

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.just(campaign));
        when(eventRepo.getMetrics(campaignId)).thenReturn(Mono.just(metrics));

        StepVerifier.create(campaignService.getCampaignStats(campaignId))
                .expectNextMatches(stats ->
                        stats.impressions() == 1000 &&
                        stats.clicks() == 50 &&
                        stats.conversions() == 5 &&
                        stats.ctr() == 5.0 &&
                        stats.spend().compareTo(new BigDecimal("100")) == 0 &&
                        stats.remainingBudget().compareTo(new BigDecimal("900")) == 0)
                .verifyComplete();

        verify(campaignRepo).findById(campaignId);
        verify(eventRepo).getMetrics(campaignId);
    }

    @Test
    void getCampaignStats_WithNoImpressions_ShouldReturnZeroCTR() {
        Long campaignId = 1L;
        AdCampaign campaign = AdCampaign.builder()
                .id(campaignId)
                .spent(BigDecimal.ZERO)
                .budget(new BigDecimal("1000"))
                .build();

        AdEventRepository.CampaignMetrics metrics = new AdEventRepository.CampaignMetrics() {
            @Override
            public long getImpressions() { return 0; }
            @Override
            public long getClicks() { return 0; }
            @Override
            public long getConversions() { return 0; }
        };

        when(campaignRepo.findById(campaignId)).thenReturn(Mono.just(campaign));
        when(eventRepo.getMetrics(campaignId)).thenReturn(Mono.just(metrics));

        StepVerifier.create(campaignService.getCampaignStats(campaignId))
                .expectNextMatches(stats -> stats.ctr() == 0.0)
                .verifyComplete();
    }
}
