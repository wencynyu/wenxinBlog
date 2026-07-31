package com.wenxinblog.ad.controller;

import com.wenxinblog.ad.dto.CampaignRequest;
import com.wenxinblog.ad.dto.CampaignStats;
import com.wenxinblog.ad.entity.AdCampaign;
import com.wenxinblog.ad.service.AdCampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdCampaignControllerTest {

    @Mock
    private AdCampaignService campaignService;

    @InjectMocks
    private AdCampaignController controller;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void create_ShouldReturnCreatedCampaign() {
        AdCampaign testCampaign = AdCampaign.builder()
                .id(1L)
                .advertiserId("advertiser123")
                .name("Test Campaign")
                .description("Test Description")
                .budget(new BigDecimal("1000"))
                .dailyBudget(new BigDecimal("100"))
                .bidStrategy("CPC")
                .bidAmount(new BigDecimal("0.50"))
                .status("DRAFT")
                .build();

        CampaignRequest campaignRequest = new CampaignRequest(
                "Test Campaign",
                "Test Description",
                new BigDecimal("1000"),
                new BigDecimal("100"),
                "CPC",
                new BigDecimal("0.50"),
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );

        when(campaignService.createCampaign(eq("advertiser123"), any(CampaignRequest.class)))
                .thenReturn(Mono.just(testCampaign));

        webTestClient.post()
                .uri("/api/v1/campaigns")
                .header("X-User-Id", "advertiser123")
                .bodyValue(campaignRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.id").isEqualTo(1)
                .jsonPath("$.data.name").isEqualTo("Test Campaign");
    }

    @Test
    void update_ShouldReturnUpdatedCampaign() {
        AdCampaign testCampaign = AdCampaign.builder()
                .id(1L)
                .name("Updated Campaign")
                .build();

        CampaignRequest campaignRequest = new CampaignRequest(
                "Updated Campaign",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(campaignService.updateCampaign(eq("advertiser123"), eq(1L), any(CampaignRequest.class)))
                .thenReturn(Mono.just(testCampaign));

        webTestClient.put()
                .uri("/api/v1/campaigns/1")
                .header("X-User-Id", "advertiser123")
                .bodyValue(campaignRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.id").isEqualTo(1);
    }

    @Test
    void get_ShouldReturnCampaign() {
        AdCampaign testCampaign = AdCampaign.builder()
                .id(1L)
                .name("Test Campaign")
                .build();

        when(campaignService.getCampaign(eq("advertiser123"), eq(1L))).thenReturn(Mono.just(testCampaign));

        webTestClient.get()
                .uri("/api/v1/campaigns/1")
                .header("X-User-Id", "advertiser123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.id").isEqualTo(1)
                .jsonPath("$.data.name").isEqualTo("Test Campaign");
    }

    @Test
    void list_WithFilters_ShouldReturnFilteredCampaigns() {
        AdCampaign campaign1 = AdCampaign.builder().id(1L).advertiserId("adv1").status("ACTIVE").build();
        AdCampaign campaign2 = AdCampaign.builder().id(2L).advertiserId("adv1").status("ACTIVE").build();

        when(campaignService.listCampaigns("adv1", "ACTIVE"))
                .thenReturn(Flux.just(campaign1, campaign2));

        webTestClient.get()
                .uri("/api/v1/campaigns?advertiserId=adv1&status=ACTIVE")
                .header("X-User-Id", "adv1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(2);
    }

    @Test
    void list_NoFilters_ShouldReturnAllCampaigns() {
        AdCampaign testCampaign = AdCampaign.builder().id(1L).build();

        when(campaignService.listCampaigns("adv1", null))
                .thenReturn(Flux.just(testCampaign));

        webTestClient.get()
                .uri("/api/v1/campaigns")
                .header("X-User-Id", "adv1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(1);
    }

    @Test
    void pause_ShouldReturnPausedCampaign() {
        AdCampaign pausedCampaign = AdCampaign.builder()
                .id(1L)
                .status("PAUSED")
                .build();

        when(campaignService.pauseCampaign(eq("advertiser123"), eq(1L))).thenReturn(Mono.just(pausedCampaign));

        webTestClient.put()
                .uri("/api/v1/campaigns/1/pause")
                .header("X-User-Id", "advertiser123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.status").isEqualTo("PAUSED");
    }

    @Test
    void activate_ShouldReturnActivatedCampaign() {
        AdCampaign activeCampaign = AdCampaign.builder()
                .id(1L)
                .status("ACTIVE")
                .build();

        when(campaignService.activateCampaign(eq("advertiser123"), eq(1L))).thenReturn(Mono.just(activeCampaign));

        webTestClient.put()
                .uri("/api/v1/campaigns/1/activate")
                .header("X-User-Id", "advertiser123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.status").isEqualTo("ACTIVE");
    }

    @Test
    void stats_ShouldReturnCampaignStats() {
        CampaignStats stats = new CampaignStats(1000, 50, 5, 5.0,
                new BigDecimal("100"), new BigDecimal("900"));

        when(campaignService.getCampaignStats(eq("advertiser123"), eq(1L))).thenReturn(Mono.just(stats));

        webTestClient.get()
                .uri("/api/v1/campaigns/1/stats")
                .header("X-User-Id", "advertiser123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.impressions").isEqualTo(1000)
                .jsonPath("$.data.clicks").isEqualTo(50)
                .jsonPath("$.data.conversions").isEqualTo(5)
                .jsonPath("$.data.ctr").isEqualTo(5.0);
    }
}
