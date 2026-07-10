package com.wenxinblog.ad.controller;

import com.wenxinblog.ad.dto.Result;
import com.wenxinblog.ad.entity.AdEvent;
import com.wenxinblog.ad.service.AdTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ads")
@RequiredArgsConstructor
public class AdTrackingController {

    private final AdTrackingService trackingService;

    @PostMapping("/click")
    public Mono<ResponseEntity<Result<AdEvent>>> click(@RequestBody Map<String, Object> body) {
        Long creativeId = Long.valueOf(body.get("creativeId").toString());
        String userId = body.get("userId") != null ? body.get("userId").toString() : null;
        String ipAddress = body.get("ipAddress") != null ? body.get("ipAddress").toString() : null;
        String userAgent = body.get("userAgent") != null ? body.get("userAgent").toString() : null;

        return trackingService.recordClick(creativeId, userId, ipAddress, userAgent)
                .map(event -> ResponseEntity.ok(Result.success(event)))
                .defaultIfEmpty(ResponseEntity.ok(Result.success(null)));
    }

    @PostMapping("/conversion")
    public Mono<Result<AdEvent>> conversion(@RequestBody Map<String, Object> body) {
        Long creativeId = Long.valueOf(body.get("creativeId").toString());
        String userId = body.get("userId") != null ? body.get("userId").toString() : null;
        String ipAddress = body.get("ipAddress") != null ? body.get("ipAddress").toString() : null;
        String userAgent = body.get("userAgent") != null ? body.get("userAgent").toString() : null;

        return trackingService.recordConversion(creativeId, userId, ipAddress, userAgent)
                .map(Result::success);
    }
}
