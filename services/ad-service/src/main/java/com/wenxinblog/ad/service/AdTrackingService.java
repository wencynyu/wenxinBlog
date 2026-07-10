package com.wenxinblog.ad.service;

import com.wenxinblog.ad.entity.AdCampaign;
import com.wenxinblog.ad.entity.AdEvent;
import com.wenxinblog.ad.repository.AdCampaignRepository;
import com.wenxinblog.ad.repository.AdEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdTrackingService {

    private final AdEventRepository eventRepo;
    private final AdCampaignRepository campaignRepo;
    private final ReactiveStringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;

    public Mono<AdEvent> recordClick(Long creativeId, String userId, String ipAddress, String userAgent) {
        // Dedup check
        String dedupKey = String.format("ad:click:%s:%d", userId, creativeId);
        return redis.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(24))
                .flatMap(isNew -> {
                    if (Boolean.FALSE.equals(isNew)) {
                        log.debug("Duplicate click ignored: userId={}, creativeId={}", userId, creativeId);
                        return Mono.<AdEvent>empty();
                    }

                    // Find campaign by creative (mock: use creativeId as campaignId for now)
                    AdEvent event = AdEvent.builder()
                            .campaignId(creativeId)
                            .creativeId(creativeId)
                            .userId(userId)
                            .eventType("CLICK")
                            .ipAddress(ipAddress)
                            .userAgent(userAgent)
                            .createdAt(LocalDateTime.now())
                            .build();

                    // Publish to Kafka
                    publishEvent(event, "CLICK");

                    return eventRepo.save(event);
                });
    }

    public Mono<AdEvent> recordConversion(Long creativeId, String userId, String ipAddress, String userAgent) {
        AdEvent event = AdEvent.builder()
                .campaignId(creativeId)
                .creativeId(creativeId)
                .userId(userId)
                .eventType("CONVERSION")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(LocalDateTime.now())
                .build();

        publishEvent(event, "CONVERSION");
        return eventRepo.save(event);
    }

    private void publishEvent(AdEvent event, String type) {
        try {
            kafka.send("ad-events", event.getUserId(), java.util.Map.of(
                    "eventType", type,
                    "campaignId", event.getCampaignId(),
                    "creativeId", event.getCreativeId(),
                    "userId", event.getUserId() != null ? event.getUserId() : "",
                    "timestamp", event.getCreatedAt().toString()
            ).toString()).whenComplete((result, ex) -> {
                if (ex != null) log.warn("Failed to publish ad event to Kafka: {}", ex.getMessage());
            });
        } catch (Exception e) {
            log.warn("Failed to publish ad event to Kafka: {}", e.getMessage());
        }
    }
}
