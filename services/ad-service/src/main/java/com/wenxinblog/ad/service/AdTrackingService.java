package com.wenxinblog.ad.service;

import com.wenxinblog.ad.entity.AdCreative;
import com.wenxinblog.ad.entity.AdEvent;
import com.wenxinblog.ad.repository.AdCreativeRepository;
import com.wenxinblog.ad.repository.AdEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdTrackingService {

    private final AdEventRepository eventRepo;
    private final AdCreativeRepository creativeRepo;
    private final ReactiveStringRedisTemplate redis;
    private final AdEventPublisher eventPublisher;

    public Mono<AdEvent> recordClick(Long creativeId, String userId, String ipAddress, String userAgent) {
        // Dedup check（匿名用户回退用 IP，避免所有匿名用户共享同一个 key）
        String dedupKey = String.format("ad:click:%s:%d",
                userId != null ? userId : "ip:" + ipAddress, creativeId);
        return redis.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(24))
                .flatMap(isNew -> {
                    if (Boolean.FALSE.equals(isNew)) {
                        log.debug("Duplicate click ignored: userId={}, creativeId={}", userId, creativeId);
                        return Mono.<AdEvent>empty();
                    }
                    return saveEvent(creativeId, userId, ipAddress, userAgent, "CLICK");
                });
    }

    public Mono<AdEvent> recordConversion(Long creativeId, String userId, String ipAddress, String userAgent) {
        return saveEvent(creativeId, userId, ipAddress, userAgent, "CONVERSION");
    }

    private Mono<AdEvent> saveEvent(Long creativeId, String userId, String ipAddress, String userAgent, String eventType) {
        // 反查 creative 归属的真实 campaign；creative 不存在则丢弃事件
        return creativeRepo.findById(creativeId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Ad event dropped, creative not found: creativeId={}, type={}", creativeId, eventType);
                    return Mono.empty();
                }))
                .map(AdCreative::getCampaignId)
                .flatMap(campaignId -> {
                    AdEvent event = AdEvent.builder()
                            .campaignId(campaignId)
                            .creativeId(creativeId)
                            .userId(userId)
                            .eventType(eventType)
                            .ipAddress(ipAddress)
                            .userAgent(userAgent)
                            .createdAt(LocalDateTime.now())
                            .build();

                    eventPublisher.publish(eventType, campaignId, creativeId, userId, null, event.getCreatedAt());

                    return eventRepo.save(event);
                });
    }
}
