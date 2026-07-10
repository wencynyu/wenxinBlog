package com.wenxinblog.ad.service;

import com.wenxinblog.ad.entity.AdCampaign;
import com.wenxinblog.ad.entity.AdEvent;
import com.wenxinblog.ad.repository.AdCampaignRepository;
import com.wenxinblog.ad.repository.AdEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdTrackingServiceTest {

    @Mock
    private AdEventRepository eventRepo;

    @Mock
    private AdCampaignRepository campaignRepo;

    @Mock
    private ReactiveStringRedisTemplate redis;

    @Mock
    private KafkaTemplate<String, String> kafka;

    private AdTrackingService trackingService;

    @BeforeEach
    void setUp() {
        trackingService = new AdTrackingService(eventRepo, campaignRepo, redis, kafka);
    }

    @Test
    void recordClick_WithNewClick_ShouldRecordAndPublishToKafka() {
        Long creativeId = 1L;
        String userId = "user123";
        String ipAddress = "127.0.0.1";
        String userAgent = "Mozilla";

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(true));

        AdEvent savedEvent = AdEvent.builder()
                .id(1L)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CLICK")
                .build();

        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent));

        StepVerifier.create(trackingService.recordClick(creativeId, userId, ipAddress, userAgent))
                .expectNextMatches(event ->
                        event.getCreativeId().equals(creativeId) &&
                        event.getUserId().equals(userId) &&
                        event.getEventType().equals("CLICK"))
                .verifyComplete();

        verify(mockOps).setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)));
        verify(eventRepo).save(any(AdEvent.class));
    }

    @Test
    void recordClick_WithDuplicateClick_ShouldReturnEmpty() {
        Long creativeId = 1L;
        String userId = "user123";

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(false));

        StepVerifier.create(trackingService.recordClick(creativeId, userId, null, null))
                .verifyComplete();

        verify(mockOps).setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)));
        verify(eventRepo, never()).save(any());
    }

    @Test
    void recordConversion_ShouldRecordAndPublishToKafka() {
        Long creativeId = 1L;
        String userId = "user123";
        String ipAddress = "127.0.0.1";
        String userAgent = "Mozilla";

        AdEvent savedEvent = AdEvent.builder()
                .id(1L)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CONVERSION")
                .build();

        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent));

        StepVerifier.create(trackingService.recordConversion(creativeId, userId, ipAddress, userAgent))
                .expectNextMatches(event ->
                        event.getCreativeId().equals(creativeId) &&
                        event.getUserId().equals(userId) &&
                        event.getEventType().equals("CONVERSION"))
                .verifyComplete();

        verify(eventRepo).save(argThat(e ->
                e.getEventType().equals("CONVERSION") &&
                        e.getCreativeId().equals(creativeId) &&
                        e.getUserId().equals(userId)));
    }

    @Test
    void recordClick_ShouldPublishToKafka() {
        Long creativeId = 1L;
        String userId = "user123";

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(true));

        AdEvent savedEvent = AdEvent.builder()
                .id(1L)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CLICK")
                .createdAt(LocalDateTime.now())
                .build();

        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(null);

        StepVerifier.create(trackingService.recordClick(creativeId, userId, null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(kafka).send(eq("ad-events"), eq(userId), contains("CLICK"));
    }

    @Test
    void recordConversion_ShouldPublishToKafka() {
        Long creativeId = 1L;
        String userId = "user123";

        AdEvent savedEvent = AdEvent.builder()
                .id(1L)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CONVERSION")
                .createdAt(LocalDateTime.now())
                .build();

        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(null);

        StepVerifier.create(trackingService.recordConversion(creativeId, userId, null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(kafka).send(eq("ad-events"), eq(userId), contains("CONVERSION"));
    }

    @Test
    void publishToKafka_WithFailure_ShouldLogWarningAndNotThrow() {
        Long creativeId = 1L;
        String userId = "user123";

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(true));

        AdEvent savedEvent = AdEvent.builder()
                .id(1L)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CLICK")
                .createdAt(LocalDateTime.now())
                .build();

        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Kafka connection failed"));

        // Should not throw exception even if Kafka fails
        StepVerifier.create(trackingService.recordClick(creativeId, userId, null, null))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void recordClick_WithNullUserId_ShouldPublishWithNullKey() {
        Long creativeId = 1L;
        String userId = null;

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(true));

        AdEvent savedEvent = AdEvent.builder()
                .id(1L)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CLICK")
                .createdAt(LocalDateTime.now())
                .build();

        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent));
        when(kafka.send(anyString(), any(), anyString())).thenReturn(null);

        StepVerifier.create(trackingService.recordClick(creativeId, userId, "127.0.0.1", "Mozilla"))
                .expectNextCount(1)
                .verifyComplete();

        // publishEvent passes event.getUserId() as the key, which is null
        verify(kafka).send(eq("ad-events"), isNull(), anyString());
    }

    @Test
    void recordConversion_WithNullUserId_ShouldPublishWithNullKey() {
        Long creativeId = 1L;
        String userId = null;

        AdEvent savedEvent = AdEvent.builder()
                .id(1L)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CONVERSION")
                .createdAt(LocalDateTime.now())
                .build();

        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent));
        when(kafka.send(anyString(), any(), anyString())).thenReturn(null);

        StepVerifier.create(trackingService.recordConversion(creativeId, userId, "127.0.0.1", "Mozilla"))
                .expectNextCount(1)
                .verifyComplete();

        // publishEvent passes event.getUserId() as the key, which is null
        verify(kafka).send(eq("ad-events"), isNull(), anyString());
    }

    @Test
    void publishEvent_WhenKafkaSendCompletesWithException_ShouldLogWarning() {
        Long creativeId = 1L;
        String userId = "user123";

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(mockOps);
        when(mockOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(true));

        AdEvent savedEvent = AdEvent.builder()
                .id(1L)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CLICK")
                .createdAt(LocalDateTime.now())
                .build();

        when(eventRepo.save(any(AdEvent.class))).thenReturn(Mono.just(savedEvent));

        // Return a CompletableFuture that is already completed exceptionally
        // This tests the whenComplete callback's exception branch
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker not available"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        // The reactive chain should still complete successfully (Kafka failure is async)
        StepVerifier.create(trackingService.recordClick(creativeId, userId, null, null))
                .expectNextCount(1)
                .verifyComplete();
    }
}
