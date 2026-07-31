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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdDecisionServiceTest {

    @Mock
    private AdCampaignRepository campaignRepo;

    @Mock
    private AdCreativeRepository creativeRepo;

    @Mock
    private AdEventRepository eventRepo;

    @Mock
    private AdPositionRepository positionRepo;

    @Mock
    private ReactiveStringRedisTemplate redis;

    @Mock
    private KafkaTemplate<String, String> kafka;

    @Mock
    private ObjectMapper objectMapper;

    private AdDecisionService adDecisionService;

    @BeforeEach
    void setUp() {
        // Default mock for eventRepo.save() to avoid NPE
        when(eventRepo.save(any())).thenReturn(Mono.just(AdEvent.builder().build()));
        // Default mock for creativeRepo.findByCampaignIdAndIsActiveTrue() to avoid NPE
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(any())).thenReturn(Flux.empty());
        // Default mocks for budget billing path: campaign exists and debit succeeds
        when(campaignRepo.findById(any(Long.class))).thenReturn(Mono.just(createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"))));
        when(campaignRepo.debitSpend(any(), any())).thenReturn(Mono.just(1));

        adDecisionService = new AdDecisionService(campaignRepo, creativeRepo, eventRepo,
                positionRepo, redis, kafka, objectMapper);
    }

    @Test
    void decide_ShouldFilterInactiveCampaigns() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        AdCampaign activeCampaign = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCampaign noCreativeCampaign = createActiveCampaign(2L, new BigDecimal("1000"), new BigDecimal("100"));

        AdCreative creative = createCreative(1L, 1L);

        when(campaignRepo.findByStatus("ACTIVE"))
                .thenReturn(Flux.just(activeCampaign, noCreativeCampaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        // Campaign 2 has no active creatives, returns empty Flux
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(2L)).thenReturn(Flux.empty());
        mockRedisFrequencyCap(5);

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(creativeRepo).findByCampaignIdAndIsActiveTrue(1L);
        verify(creativeRepo).findByCampaignIdAndIsActiveTrue(2L);
    }

    @Test
    void decide_ShouldFilterCampaignsWithNoBudget() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        AdCampaign campaignWithBudget = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCampaign campaignNoBudget = createActiveCampaign(2L, BigDecimal.ZERO, BigDecimal.ZERO);

        AdCreative creative = createCreative(1L, 1L);

        when(campaignRepo.findByStatus("ACTIVE"))
                .thenReturn(Flux.just(campaignWithBudget, campaignNoBudget));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        mockRedisFrequencyCap(5);

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(creativeRepo).findByCampaignIdAndIsActiveTrue(1L);
        verify(creativeRepo, never()).findByCampaignIdAndIsActiveTrue(2L);
    }

    @Test
    void decide_ShouldFilterExpiredCampaigns() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        AdCampaign validCampaign = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        validCampaign.setStartDate(LocalDateTime.now().minusDays(1));
        validCampaign.setEndDate(LocalDateTime.now().plusDays(30));

        AdCampaign expiredCampaign = createActiveCampaign(2L, new BigDecimal("1000"), new BigDecimal("100"));
        expiredCampaign.setEndDate(LocalDateTime.now().minusDays(1));

        AdCreative creative = createCreative(1L, 1L);

        when(campaignRepo.findByStatus("ACTIVE"))
                .thenReturn(Flux.just(validCampaign, expiredCampaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        mockRedisFrequencyCap(5);

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(creativeRepo).findByCampaignIdAndIsActiveTrue(1L);
        verify(creativeRepo, never()).findByCampaignIdAndIsActiveTrue(2L);
    }

    @Test
    void decide_ShouldSortByBidAmountDescending() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        AdCampaign campaign1 = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        campaign1.setBidAmount(new BigDecimal("1.00"));

        AdCampaign campaign2 = createActiveCampaign(2L, new BigDecimal("1000"), new BigDecimal("100"));
        campaign2.setBidAmount(new BigDecimal("2.00"));

        AdCampaign campaign3 = createActiveCampaign(3L, new BigDecimal("1000"), new BigDecimal("100"));
        campaign3.setBidAmount(new BigDecimal("1.50"));

        AdCreative creative1 = createCreative(1L, 1L);
        AdCreative creative2 = createCreative(2L, 2L);
        AdCreative creative3 = createCreative(3L, 3L);

        when(campaignRepo.findByStatus("ACTIVE"))
                .thenReturn(Flux.just(campaign1, campaign2, campaign3));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative1));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(2L)).thenReturn(Flux.just(creative2));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(3L)).thenReturn(Flux.just(creative3));
        mockRedisFrequencyCap(5);

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextMatches(r -> r.bidAmount().compareTo(new BigDecimal("2.00")) == 0)
                .expectNextMatches(r -> r.bidAmount().compareTo(new BigDecimal("1.50")) == 0)
                .expectNextMatches(r -> r.bidAmount().compareTo(new BigDecimal("1.00")) == 0)
                .verifyComplete();
    }

    @Test
    void decide_ShouldLimitByCount() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 2);

        AdCampaign campaign1 = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCampaign campaign2 = createActiveCampaign(2L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCampaign campaign3 = createActiveCampaign(3L, new BigDecimal("1000"), new BigDecimal("100"));

        AdCreative creative1 = createCreative(1L, 1L);
        AdCreative creative2 = createCreative(2L, 2L);
        AdCreative creative3 = createCreative(3L, 3L);

        when(campaignRepo.findByStatus("ACTIVE"))
                .thenReturn(Flux.just(campaign1, campaign2, campaign3));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(any())).thenReturn(Flux.just(creative1, creative2, creative3));
        mockRedisFrequencyCap(5);

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void decide_ShouldRespectFrequencyCap() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        AdCampaign campaign1 = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCampaign campaign2 = createActiveCampaign(2L, new BigDecimal("1000"), new BigDecimal("100"));

        AdCreative creative2 = createCreative(2L, 2L);

        when(campaignRepo.findByStatus("ACTIVE"))
                .thenReturn(Flux.just(campaign1, campaign2));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.empty());
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(2L)).thenReturn(Flux.just(creative2));

        // Mock frequency cap - campaign1 over cap (6), campaign2 under cap (3)
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        // Match key containing ":1:" for campaign 1, and ":2:" for campaign 2
        when(mockOps.increment(contains(":1:"))).thenReturn(Mono.just(6L)); // Over cap
        when(mockOps.increment(contains(":2:"))).thenReturn(Mono.just(3L)); // Under cap
        when(redis.expire(anyString(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextMatches(r -> r.campaignId() == 2L)
                .verifyComplete();
    }

    @Test
    void decide_WithNoUserId_ShouldSkipFrequencyCheck() {
        AdDecisionRequest request = new AdDecisionRequest("banner", null, "127.0.0.1", "Mozilla", null, 3);

        AdCampaign campaign = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCreative creative = createCreative(1L, 1L);

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.just(campaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        when(eventRepo.save(any())).thenReturn(Mono.just(AdEvent.builder().build()));

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(redis, never()).opsForValue();
    }

    @Test
    void decide_ShouldRespectDateRange() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        AdCampaign futureCampaign = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        futureCampaign.setStartDate(LocalDateTime.now().plusDays(1));

        AdCampaign validCampaign = createActiveCampaign(2L, new BigDecimal("1000"), new BigDecimal("100"));
        validCampaign.setStartDate(LocalDateTime.now().minusDays(1));
        validCampaign.setEndDate(LocalDateTime.now().plusDays(30));

        AdCreative creative = createCreative(2L, 2L);

        when(campaignRepo.findByStatus("ACTIVE"))
                .thenReturn(Flux.just(futureCampaign, validCampaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(2L)).thenReturn(Flux.just(creative));
        mockRedisFrequencyCap(5);

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextMatches(r -> r.campaignId() == 2L)
                .verifyComplete();

        verify(creativeRepo, never()).findByCampaignIdAndIsActiveTrue(1L);
    }

    @Test
    void decide_ShouldRecordImpression() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 1);

        AdCampaign campaign = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCreative creative = createCreative(1L, 1L);

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.just(campaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        mockRedisFrequencyCap(5);
        when(eventRepo.save(argThat(e -> e.getEventType().equals("IMPRESSION"))))
                .thenReturn(Mono.just(AdEvent.builder().build()));

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(eventRepo).save(argThat(e ->
                e.getEventType().equals("IMPRESSION") &&
                e.getCampaignId().equals(1L) &&
                e.getCreativeId().equals(1L) &&
                e.getUserId().equals("user123") &&
                e.getIpAddress().equals("127.0.0.1") &&
                e.getUserAgent().equals("Mozilla")));
    }

    @Test
    void decide_WhenDebitFails_ShouldServeWithoutRecordingImpression() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 1);

        AdCampaign campaign = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCreative creative = createCreative(1L, 1L);

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.just(campaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        mockRedisFrequencyCap(1);
        // 预算已被耗尽：条件更新 0 行 → 不计费、不记 impression 事件
        when(campaignRepo.findById(1L)).thenReturn(Mono.just(campaign));
        when(campaignRepo.debitSpend(eq(1L), any())).thenReturn(Mono.just(0));

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(eventRepo, never()).save(any());
    }

    @Test
    void decide_WithEmptyResult_ShouldReturnEmpty() {
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.empty());

        StepVerifier.create(adDecisionService.decide(request))
                .verifyComplete();
    }

    private AdCampaign createActiveCampaign(Long id, BigDecimal budget, BigDecimal dailyBudget) {
        return AdCampaign.builder()
                .id(id)
                .status("ACTIVE")
                .budget(budget)
                .dailyBudget(dailyBudget)
                .spent(BigDecimal.ZERO)
                .dailySpent(BigDecimal.ZERO)
                .bidAmount(BigDecimal.ONE)
                .build();
    }

    private AdCreative createCreative(Long id, Long campaignId) {
        return AdCreative.builder()
                .id(id)
                .campaignId(campaignId)
                .title("Ad Title " + id)
                .imageUrl("http://example.com/image.jpg")
                .landingUrl("http://example.com")
                .creativeType("banner")
                .isActive(true)
                .build();
    }

    @Test
    void hasBudget_WhenDailyBudgetIsNull_ShouldFallBackToTotalBudget() {
        // dailyBudget is null, so hasBudget should fall back to total budget for dailyRemaining
        AdCampaign campaign = AdCampaign.builder()
                .id(1L)
                .status("ACTIVE")
                .budget(new BigDecimal("1000"))
                .dailyBudget(null) // null daily budget - triggers fallback branch
                .spent(BigDecimal.ZERO)
                .dailySpent(BigDecimal.ZERO)
                .bidAmount(BigDecimal.ONE)
                .build();

        AdCreative creative = createCreative(1L, 1L);
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.just(campaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        mockRedisFrequencyCap(1);

        // Campaign should pass budget check since dailyBudget null falls back to budget (1000 > 0)
        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void matchesTargeting_WithNonEmptyTargeting_ShouldStillPass() {
        // Non-empty targeting JSON triggers the true return path after the if check
        AdCampaign campaign = AdCampaign.builder()
                .id(1L)
                .status("ACTIVE")
                .budget(new BigDecimal("1000"))
                .dailyBudget(new BigDecimal("100"))
                .spent(BigDecimal.ZERO)
                .dailySpent(BigDecimal.ZERO)
                .bidAmount(BigDecimal.ONE)
                .targeting("{\"age\": \"18-35\"}")
                .build();

        AdCreative creative = createCreative(1L, 1L);
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.just(campaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        mockRedisFrequencyCap(1);

        // Targeting is non-null and non-empty, so matchesTargeting goes past the early return and returns true
        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void checkFrequencyCap_WhenCountBetween2And5_ShouldNotSetExpire() {
        // count=3 means it's > 1, so no expire is set, but count <= 5 so it returns true
        AdCampaign campaign = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCreative creative = createCreative(1L, 1L);
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.increment(anyString())).thenReturn(Mono.just(3L)); // count > 1, <= 5

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.just(campaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));
        when(eventRepo.save(any())).thenReturn(Mono.just(AdEvent.builder().build()));

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextCount(1)
                .verifyComplete();

        // count != 1, so expire should NOT be called
        verify(redis, never()).expire(anyString(), any());
    }

    @Test
    void checkFrequencyCap_WhenCountExceeds5_ShouldFilterAdOut() {
        // count=6 means it's > 5, so checkFrequencyCap returns false, ad gets filtered
        AdCampaign campaign = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCreative creative = createCreative(1L, 1L);
        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.increment(anyString())).thenReturn(Mono.just(6L)); // count > 5

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.just(campaign));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative));

        StepVerifier.create(adDecisionService.decide(request))
                .verifyComplete(); // No ads returned because frequency cap filtered it out

        verify(eventRepo, never()).save(any());
    }

    @Test
    void decide_WhenFrequencyCapReturnsFalse_ShouldFilterAdOut() {
        // Two campaigns: campaign1 over frequency cap, campaign2 under
        AdCampaign campaign1 = createActiveCampaign(1L, new BigDecimal("1000"), new BigDecimal("100"));
        AdCampaign campaign2 = createActiveCampaign(2L, new BigDecimal("1000"), new BigDecimal("100"));

        AdCreative creative1 = createCreative(1L, 1L);
        AdCreative creative2 = createCreative(2L, 2L);

        AdDecisionRequest request = new AdDecisionRequest("banner", "user123", "127.0.0.1", "Mozilla", null, 3);

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        // Key format is "ad:freq:user123:1" - endsWith matches the campaignId at the end
        when(mockOps.increment(endsWith(":1"))).thenReturn(Mono.just(10L)); // Way over cap
        when(mockOps.increment(endsWith(":2"))).thenReturn(Mono.just(2L));  // Under cap
        when(redis.expire(anyString(), any())).thenReturn(Mono.just(true));

        when(campaignRepo.findByStatus("ACTIVE")).thenReturn(Flux.just(campaign1, campaign2));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(1L)).thenReturn(Flux.just(creative1));
        when(creativeRepo.findByCampaignIdAndIsActiveTrue(2L)).thenReturn(Flux.just(creative2));
        when(eventRepo.save(any())).thenReturn(Mono.just(AdEvent.builder().build()));

        StepVerifier.create(adDecisionService.decide(request))
                .expectNextMatches(r -> r.campaignId() == 2L)
                .verifyComplete();

        // Only campaign2's impression should be recorded
        verify(eventRepo).save(argThat(e -> e.getCampaignId().equals(2L)));
    }

    private void mockRedisFrequencyCap(int count) {
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.increment(anyString())).thenReturn(Mono.just((long) count));
        when(redis.expire(anyString(), any())).thenReturn(Mono.just(true));
    }
}
