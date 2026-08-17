package com.wenxinblog.ad.service;

import com.wenxinblog.ad.entity.AdCreative;
import com.wenxinblog.ad.entity.AdEvent;
import com.wenxinblog.ad.repository.AdCreativeRepository;
import com.wenxinblog.ad.repository.AdEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdTrackingServiceTest {

    private static final Long CREATIVE_ID = 1L;
    private static final Long CAMPAIGN_ID = 100L;

    @Mock
    private AdEventRepository eventRepo;

    @Mock
    private AdCreativeRepository creativeRepo;

    @Mock
    private ReactiveStringRedisTemplate redis;

    @Mock
    private AdEventPublisher eventPublisher;

    private AdTrackingService trackingService;

    @BeforeEach
    void setUp() {
        trackingService = new AdTrackingService(eventRepo, creativeRepo, redis, eventPublisher);
    }

    private void stubCreative(Long creativeId, Long campaignId) {
        AdCreative creative = AdCreative.builder().id(creativeId).campaignId(campaignId).build();
        when(creativeRepo.findById(creativeId)).thenReturn(Mono.just(creative));
    }

    @SuppressWarnings("unchecked")
    private ReactiveValueOperations<String, String> stubNewClick() {
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(true));
        return mockOps;
    }

    private AdEvent savedEvent() {
        return AdEvent.builder()
                .id(1L)
                .campaignId(CAMPAIGN_ID)
                .creativeId(CREATIVE_ID)
                .eventType("CLICK")
                .build();
    }

    @Test
    void recordClick_WithNewClick_ShouldRecordAndPublishToKafka() {
        String userId = "user123";
        ReactiveValueOperations<String, String> mockOps = stubNewClick();
        stubCreative(CREATIVE_ID, CAMPAIGN_ID);
        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent()));

        StepVerifier.create(trackingService.recordClick(CREATIVE_ID, userId, "127.0.0.1", "Mozilla"))
                .expectNextMatches(event ->
                        event.getCreativeId().equals(CREATIVE_ID) &&
                        event.getEventType().equals("CLICK"))
                .verifyComplete();

        verify(mockOps).setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)));
        // 归因必须是反查到的真实 campaign，而非 creativeId；Kafka 发布也带同一归因
        verify(eventRepo).save(argThat(e ->
                e.getCampaignId().equals(CAMPAIGN_ID) && e.getCreativeId().equals(CREATIVE_ID)));
        verify(eventPublisher).publish(eq("CLICK"), eq(CAMPAIGN_ID), eq(CREATIVE_ID), eq(userId), isNull(), any());
    }

    @Test
    void recordClick_WithDuplicateClick_ShouldReturnEmpty() {
        String userId = "user123";

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(false));

        StepVerifier.create(trackingService.recordClick(CREATIVE_ID, userId, null, null))
                .verifyComplete();

        verify(mockOps).setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)));
        verify(eventRepo, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(creativeRepo);
    }

    @Test
    void recordClick_WhenCreativeNotFound_ShouldReturnEmptyAndNotSave() {
        stubNewClick();
        when(creativeRepo.findById(CREATIVE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(trackingService.recordClick(CREATIVE_ID, "user123", null, null))
                .verifyComplete();

        verify(eventRepo, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void recordConversion_WhenCreativeNotFound_ShouldReturnEmptyAndNotSave() {
        when(creativeRepo.findById(CREATIVE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(trackingService.recordConversion(CREATIVE_ID, "user123", null, null))
                .verifyComplete();

        verify(eventRepo, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void recordConversion_ShouldRecordAndPublishToKafka() {
        String userId = "user123";
        stubCreative(CREATIVE_ID, CAMPAIGN_ID);
        AdEvent conversion = AdEvent.builder()
                .id(1L)
                .campaignId(CAMPAIGN_ID)
                .creativeId(CREATIVE_ID)
                .userId(userId)
                .eventType("CONVERSION")
                .build();
        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(conversion));

        StepVerifier.create(trackingService.recordConversion(CREATIVE_ID, userId, "127.0.0.1", "Mozilla"))
                .expectNextMatches(event ->
                        event.getCreativeId().equals(CREATIVE_ID) &&
                        event.getUserId().equals(userId) &&
                        event.getEventType().equals("CONVERSION"))
                .verifyComplete();

        verify(eventRepo).save(argThat(e ->
                e.getEventType().equals("CONVERSION") &&
                        e.getCampaignId().equals(CAMPAIGN_ID) &&
                        e.getCreativeId().equals(CREATIVE_ID) &&
                        e.getUserId().equals(userId)));
        verify(eventPublisher).publish(eq("CONVERSION"), eq(CAMPAIGN_ID), eq(CREATIVE_ID), eq(userId), isNull(), any());
    }

    @Test
    void recordClick_WithAnonymousUser_ShouldDedupeByIpAddress() {
        stubNewClick();
        stubCreative(CREATIVE_ID, CAMPAIGN_ID);
        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent()));

        StepVerifier.create(trackingService.recordClick(CREATIVE_ID, null, "1.2.3.4", "Mozilla"))
                .expectNextCount(1)
                .verifyComplete();

        // 匿名时 key 必须落到 IP 上，而不是所有匿名用户共享 "null"
        verify(redis.opsForValue())
                .setIfAbsent(eq("ad:click:ip:1.2.3.4:1"), eq("1"), eq(Duration.ofHours(24)));
        // 匿名 userId 原样传给发布器（Kafka key 为 null）
        verify(eventPublisher).publish(eq("CLICK"), eq(CAMPAIGN_ID), eq(CREATIVE_ID), isNull(), isNull(), any());
    }
}
